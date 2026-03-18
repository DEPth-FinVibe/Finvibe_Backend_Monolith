import ws from 'k6/ws';

import { pickToken } from '../lib/data.js';
import {
	wsConnectRate,
	wsAuthRate,
	wsAuthFailCount,
	wsConnectFail,
	wsActiveConnections,
	wsSessionDuration,
	wsCleanCloseRate,
} from '../lib/ws-metrics.js';

const SESSION_DURATION_MS = 10_000;

export function runWsConnectFlow(wsUrl) {
	const token = pickToken();
	if (!token) {
		wsConnectFail.add(1);
		wsConnectRate.add(false);
		return;
	}

	const startMs = Date.now();
	let cleanClose = false;

	const response = ws.connect(wsUrl, {}, function (socket) {
		wsActiveConnections.add(1);

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
						// 인증 성공 후 10초 뒤 정상 종료
						socket.setTimeout(function () {
							cleanClose = true;
							socket.close();
						}, SESSION_DURATION_MS);
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
			wsActiveConnections.add(-1);
			wsSessionDuration.add(Date.now() - startMs);
			wsCleanCloseRate.add(cleanClose);
		});
	});

	if (response && response.status !== 101) {
		wsConnectRate.add(false);
		wsConnectFail.add(1);
	}
}
