import { wsLoadProfileName, getWsScenarios, getWsThresholds } from './lib/ws-config.js';
import { ensureRuntimeConfig, pickWsStockPool, printRuntimeSummary, sharedRuntimeData } from './lib/data.js';
import { runWsQuoteFlow } from './scenarios/ws-quote.js';
import { runWsConnectFlow } from './scenarios/ws-connect.js';
import { issueTokensFromCredentials } from './lib/auth.js';

const profileName = wsLoadProfileName();
let issuedTokenCount = 0;

export const options = {
	scenarios: getWsScenarios(profileName),
	thresholds: getWsThresholds(profileName),
	summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
	ensureRuntimeConfig();
	const authTokens = issueTokensFromCredentials(
		sharedRuntimeData.baseUrl,
		sharedRuntimeData.credentials
	);
	issuedTokenCount = authTokens.length;
	const wsStockPool = pickWsStockPool();
	printRuntimeSummary(profileName, {
		wsStockPool,
		tokensLoaded: authTokens.length,
	});
	return {
		profileName,
		timestamp: new Date().toISOString(),
		wsStockPool,
		authTokens,
	};
}

function getWsUrl() {
	return sharedRuntimeData.baseUrl.replace(/^http/, 'ws') + '/market/ws';
}

export default function (data) {
	runWsQuoteFlow(getWsUrl(), data?.wsStockPool || [], data?.authTokens || []);
}

export function wsConnectFlow(data) {
	runWsConnectFlow(getWsUrl(), data?.authTokens || []);
}

export function handleSummary(data) {
	const result = {
		stdout: [
			'',
			'k6 WebSocket throughput test summary',
			`profile: ${profileName}`,
			`baseUrl: ${sharedRuntimeData.baseUrl}`,
			`tokensLoaded: ${issuedTokenCount}`,
			`idsLoaded: ${sharedRuntimeData.idStatsSummary}`,
			JSON.stringify(data.metrics, null, 2),
			'',
		].join('\n'),
	};

	const summaryFile = __ENV.SUMMARY_OUTPUT_FILE;
	if (summaryFile) {
		result[summaryFile] = JSON.stringify(
			{
				profile: profileName,
				baseUrl: sharedRuntimeData.baseUrl,
				tokensLoaded: issuedTokenCount,
				idStatsSummary: sharedRuntimeData.idStatsSummary,
				metrics: data.metrics,
				thresholds: data.thresholds,
			},
			null,
			2
		);
	}

	return result;
}
