import ws from 'k6/ws';

import { pickToken } from '../lib/data.js';
import {
	wsConnectRate,
	wsAuthRate,
	wsAuthFailCount,
	wsConnectFail,
	wsConnectionsOpened,
	wsConnectionsClosed,
	wsSessionDuration,
	wsCleanCloseRate,
} from '../lib/ws-metrics.js';

function sessionDurationMs() {
	const raw = __ENV.WS_CONNECT_SESSION_DURATION_MS;
	if (!raw) {
		return 10_000;
	}

	const parsed = Number(raw);
	if (!Number.isFinite(parsed) || parsed < 1_000) {
		return 10_000;
	}

	return Math.floor(parsed);
}

export function runWsConnectFlow(wsUrl, tokens) {
	const token = pickToken(tokens);
	if (!token) {
		wsConnectFail.add(1);
		wsConnectRate.add(false);
		return;
	}

	const startMs = Date.now();
	const holdDurationMs = sessionDurationMs();
	let cleanClose = false;

	const response = ws.connect(wsUrl, {}, function (socket) {
		wsConnectionsOpened.add(1);

		socket.on('open', function () {
			wsConnectRate.add(true);
			socket.send(JSON.stringify({ type: 'auth', token }));
		});

		socket.on('message', function (rawMsg) {
			let msg;
			try {
				msg = JSON.parse(rawMsg);
			} catch (_) {
				return;
			}

			switch (msg.type) {
				case 'auth': {
					const ok = msg.ok === true;
					wsAuthRate.add(ok);
					if (ok) {
						socket.setTimeout(function () {
							cleanClose = true;
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
					if (msg.code === 'UNAUTHORIZED') {
						wsAuthRate.add(false);
						wsAuthFailCount.add(1);
					}
					socket.close();
					break;
				}
			}
		});

		socket.on('error', function () {
			wsConnectFail.add(1);
		});

		socket.on('close', function () {
			wsConnectionsClosed.add(1);
			wsSessionDuration.add(Date.now() - startMs);
			wsCleanCloseRate.add(cleanClose);
		});
	});

	if (response && response.status !== 101) {
		wsConnectRate.add(false);
		wsConnectFail.add(1);
	}
}
