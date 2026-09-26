import { check } from 'k6';
import ws from 'k6/ws';

import { sharedRuntimeData, pickToken } from '../lib/data.js';
import { getWsSubscribeCount } from '../lib/ws-config.js';
import {
	wsDeliveryLag,
	wsConnectRate,
	wsAuthRate,
	wsEventsReceived,
	wsConnectFail,
	wsConnectionsOpened,
	wsConnectionsClosed,
	wsAuthFailCount,
	wsE2eLag,
} from '../lib/ws-metrics.js';

// 게스트 모드(#17 부하 시험): 토큰 없이 연결 직후 구독한다. 게스트 구독은 서버가 인증 없이 허용한다.
const GUEST_MODE = String(__ENV.WS_GUEST || '').toLowerCase() === 'true';

function pickRandomSubset(arr, count) {
	const shuffled = arr.slice().sort(() => Math.random() - 0.5);
	return shuffled.slice(0, Math.min(count, arr.length));
}

export function runWsQuoteFlow(wsUrl, wsStockPool, tokens) {
	const token = GUEST_MODE ? null : pickToken(tokens);
	if (!GUEST_MODE && !token) {
		wsConnectFail.add(1);
		wsConnectRate.add(false);
		return;
	}

	if (!Array.isArray(wsStockPool) || wsStockPool.length === 0) {
		wsConnectFail.add(1);
		wsConnectRate.add(false);
		return;
	}

	const subscribeCount = getWsSubscribeCount();
	const selectedStockIds = pickRandomSubset(wsStockPool, subscribeCount);
	const topics = selectedStockIds.map((id) => `quote:${id}`);
	let authSentAtMs = 0;
	let clockOffsetMs = 0;

	const response = ws.connect(wsUrl, {}, function (socket) {
		wsConnectionsOpened.add(1);

		socket.on('open', function () {
			wsConnectRate.add(true);
			if (GUEST_MODE) {
				socket.send(JSON.stringify({ type: 'subscribe', request_id: 'r1', topics }));
				return;
			}
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
					// 모놀리식에서 틱이 만들어진 시각(eventTs)부터 k6 수신까지. 클라이언트·서버 시계는 NTP 동기 가정.
					if (msg.data && typeof msg.data.eventTs === 'number' && !msg.data.initial) {
						wsE2eLag.add(Date.now() + clockOffsetMs - msg.data.eventTs);
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
			wsConnectionsClosed.add(1);
		});
	});

	// 연결 자체가 실패한 경우 (HTTP 핸드셰이크 오류 등)
	if (response && response.status !== 101) {
		wsConnectRate.add(false);
		wsConnectFail.add(1);
	}
}
