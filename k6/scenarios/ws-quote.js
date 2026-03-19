import { check } from 'k6';
import ws from 'k6/ws';

import { pickToken, sharedRuntimeData } from '../lib/data.js';
import { getWsSubscribeCount } from '../lib/ws-config.js';
import {
	wsDeliveryLag,
	wsConnectRate,
	wsAuthRate,
	wsEventsReceived,
	wsConnectFail,
	wsActiveConnections,
	wsAuthFailCount,
} from '../lib/ws-metrics.js';

function pickRandomSubset(arr, count) {
	const shuffled = arr.slice().sort(() => Math.random() - 0.5);
	return shuffled.slice(0, Math.min(count, arr.length));
}

export function runWsQuoteFlow(wsUrl) {
	const token = pickToken();
	if (!token) {
		wsConnectFail.add(1);
		wsConnectRate.add(false);
		return;
	}

	const subscribeCount = getWsSubscribeCount();
	const selectedStockIds = pickRandomSubset(sharedRuntimeData.stockIds, subscribeCount);
	const topics = selectedStockIds.map((id) => `quote:${id}`);
	let authSentAtMs = 0;
	let clockOffsetMs = 0;

	const response = ws.connect(wsUrl, {}, function (socket) {
		wsActiveConnections.add(1);

		socket.on('open', function () {
			wsConnectRate.add(true);
			authSentAtMs = Date.now();
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
						if (typeof msg.ts === 'number' && authSentAtMs > 0) {
							const authReceivedAtMs = Date.now();
							const rttMs = authReceivedAtMs - authSentAtMs;
							clockOffsetMs = msg.ts - (authSentAtMs + rttMs / 2);
						}
						socket.send(
							JSON.stringify({
								type: 'subscribe',
								request_id: 'r1',
								topics,
							})
						);
					} else {
						socket.close();
					}
					break;
				}

				case 'subscribe': {
					check(msg, {
						'subscribe ack received': (m) => Array.isArray(m.subscribed),
						'no rejected topics': (m) =>
							!Array.isArray(m.rejected) || m.rejected.length === 0,
					});
					break;
				}

				case 'event': {
					if (typeof msg.ts === 'number') {
						const lag = Date.now() + clockOffsetMs - msg.ts;
						wsDeliveryLag.add(lag);
					}
					wsEventsReceived.add(1);
					break;
				}

				case 'ping': {
					socket.send(JSON.stringify({ type: 'pong' }));
					break;
				}

				case 'error': {
					// UNAUTHORIZED: 서버가 토큰 거부(만료·서명 오류 등) → 메트릭 기록 후 종료
					if (msg.code === 'UNAUTHORIZED') {
						wsAuthRate.add(false);
						wsAuthFailCount.add(1);
						socket.close();
					}
					break;
				}
			}
		});

		socket.on('error', function (e) {
			wsConnectFail.add(1);
		});

		socket.on('close', function () {
			wsActiveConnections.add(-1);
		});
	});

	// 연결 자체가 실패한 경우 (HTTP 핸드셰이크 오류 등)
	if (response && response.status !== 101) {
		wsConnectRate.add(false);
		wsConnectFail.add(1);
	}
}
