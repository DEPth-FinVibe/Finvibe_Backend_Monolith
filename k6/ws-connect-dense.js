import { sleep } from 'k6';
import { WebSocket } from 'k6/websockets';

import { ensureRuntimeConfig, printRuntimeSummary, sharedRuntimeData } from './lib/data.js';
import { issueTokensFromCredentials } from './lib/auth.js';

function parseIntegerEnv(name, fallback, { min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER } = {}) {
	const raw = __ENV[name];
	if (raw === undefined || raw === null || String(raw).trim() === '') {
		return fallback;
	}

	const parsed = Number.parseInt(String(raw).trim(), 10);
	if (!Number.isFinite(parsed)) {
		return fallback;
	}

	if (parsed < min) return min;
	if (parsed > max) return max;
	return parsed;
}

function parsePreissuedTokens() {
	const rawJson = __ENV.WS_PREISSUED_TOKENS_JSON;
	if (rawJson && rawJson.trim()) {
		const parsed = JSON.parse(rawJson);
		if (!Array.isArray(parsed)) {
			throw new Error('WS_PREISSUED_TOKENS_JSON must be a JSON array of token strings');
		}
		return parsed.map((token) => String(token || '').trim()).filter((token) => token.length > 0);
	}

	const csv = __ENV.WS_PREISSUED_TOKENS;
	if (csv && csv.trim()) {
		return csv
			.split(',')
			.map((token) => token.trim())
			.filter((token) => token.length > 0);
	}

	return [];
}

const denseProfileName = __ENV.WS_DENSE_PROFILE || 'ws-connect-dense-10k';
const targetConnections = parseIntegerEnv('WS_TARGET_CONNECTIONS', 10_000, { min: 1, max: 200_000 });
const connectionsPerVu = parseIntegerEnv('WS_CONNECTIONS_PER_VU', 20, { min: 1, max: 200 });
const holdDurationMs = parseIntegerEnv('WS_DENSE_SESSION_DURATION_MS', 30_000, { min: 1_000, max: 300_000 });
const authWaitMs = parseIntegerEnv('WS_DENSE_AUTH_WAIT_MS', 10_000, { min: 1_000, max: 120_000 });
const openStaggerMs = parseIntegerEnv('WS_DENSE_OPEN_STAGGER_MS', 25, { min: 0, max: 10_000 });
const vuSpreadMs = parseIntegerEnv('WS_DENSE_VU_START_SPREAD_MS', 3_000, { min: 0, max: 60_000 });
const connectP95Ms = parseIntegerEnv('WS_DENSE_CONNECT_P95_MS', 2_000, { min: 100, max: 120_000 });
const vus = Math.ceil(targetConnections / connectionsPerVu);

export const options = {
	scenarios: {
		ws_connect_dense: {
			executor: 'per-vu-iterations',
			vus,
			iterations: 1,
			maxDuration: `${Math.ceil((holdDurationMs + authWaitMs + vuSpreadMs + 20_000) / 1000)}s`,
			tags: { scenario_group: 'ws_connect_dense' },
		},
	},
	thresholds: {
		ws_sessions: [`count>=${targetConnections}`],
		ws_connecting: [`p(95)<${connectP95Ms}`],
		ws_session_duration: [`p(95)>=${holdDurationMs}`],
	},
	summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function getWsUrl() {
	return sharedRuntimeData.baseUrl.replace(/^http/, 'ws') + '/market/ws';
}

export function setup() {
	ensureRuntimeConfig();
	let authTokens = parsePreissuedTokens();
	if (authTokens.length === 0) {
		authTokens = issueTokensFromCredentials(sharedRuntimeData.baseUrl, sharedRuntimeData.credentials);
	}

	printRuntimeSummary(denseProfileName, {
		wsStockPool: [],
		tokensLoaded: authTokens.length,
	});

	console.log(
		[
			`[k6-ws-dense] targetConnections=${targetConnections}`,
			`[k6-ws-dense] connectionsPerVu=${connectionsPerVu}`,
			`[k6-ws-dense] vus=${vus}`,
			`[k6-ws-dense] holdDurationMs=${holdDurationMs}`,
			`[k6-ws-dense] authWaitMs=${authWaitMs}`,
			`[k6-ws-dense] openStaggerMs=${openStaggerMs}`,
			`[k6-ws-dense] vuSpreadMs=${vuSpreadMs}`,
		].join('\n')
	);

	return {
		authTokens,
		wsUrl: getWsUrl(),
	};
}

export default function (data) {
	const tokens = Array.isArray(data?.authTokens) ? data.authTokens : [];
	const wsUrl = String(data?.wsUrl || '').trim();
	if (!wsUrl) {
		throw new Error('WebSocket URL was not resolved during setup');
	}
	if (tokens.length === 0) {
		throw new Error('Dense connect scenario requires at least one auth token');
	}

	const sockets = [];
	const startDelayMs = vus > 1 ? Math.floor(((__VU - 1) * vuSpreadMs) / vus) : 0;
	if (startDelayMs > 0) {
		sleep(startDelayMs / 1000);
	}

	for (let index = 0; index < connectionsPerVu; index += 1) {
		const globalConnectionIndex = (__VU - 1) * connectionsPerVu + index;
		if (globalConnectionIndex >= targetConnections) {
			break;
		}

		const token = tokens[globalConnectionIndex % tokens.length];

		const socket = new WebSocket(wsUrl);
		sockets.push(socket);

		setTimeout(() => {
			socket.close();
		}, authWaitMs + holdDurationMs + 1_000);

		socket.addEventListener('open', () => {
			socket.send(JSON.stringify({ type: 'auth', token }));
		});

		socket.addEventListener('message', (event) => {
			let msg;
			try {
				msg = JSON.parse(String(event.data || ''));
			} catch (_) {
				return;
			}

			switch (msg.type) {
				case 'auth': {
					const ok = msg.ok === true;
					if (ok) {
						setTimeout(() => {
							socket.close();
						}, holdDurationMs);
					} else {
						socket.close();
					}
					break;
				}
				case 'ping': {
					socket.send(JSON.stringify({ type: 'pong' }));
					break;
				}
				case 'error': {
					socket.close();
					break;
				}
			}
		});

		if (openStaggerMs > 0) {
			sleep(openStaggerMs / 1000);
		}
	}

	sleep((holdDurationMs + authWaitMs + 2_000) / 1000);
}

export function handleSummary(data) {
	const result = {
		stdout: [
			'',
			'k6 dense websocket connect summary',
			`profile: ${denseProfileName}`,
			`baseUrl: ${sharedRuntimeData.baseUrl}`,
			`targetConnections: ${targetConnections}`,
			`connectionsPerVu: ${connectionsPerVu}`,
			`vus: ${vus}`,
			JSON.stringify(data.metrics, null, 2),
			'',
		].join('\n'),
	};

	const summaryFile = __ENV.SUMMARY_OUTPUT_FILE;
	if (summaryFile) {
		result[summaryFile] = JSON.stringify(
			{
				profile: denseProfileName,
				baseUrl: sharedRuntimeData.baseUrl,
				targetConnections,
				connectionsPerVu,
				vus,
				metrics: data.metrics,
				thresholds: data.thresholds,
			},
			null,
			2
		);
	}

	return result;
}
