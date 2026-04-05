import ws from 'k6/ws';

import { pickFrom, pickToken } from '../../lib/data.js';
import {
	wsHotkeyConnectRate,
	wsHotkeyAuthRate,
	wsHotkeySubscribeAckLatency,
	wsHotkeyInitialSnapshotLatency,
	wsHotkeyConnectFailCount,
	wsHotkeyAuthFailCount,
	wsHotkeySubscribeFailCount,
	wsHotkeyRejectedTopicCount,
	wsHotkeySnapshotMissCount,
	wsHotkeyDisconnectCount,
} from '../lib/metrics.js';

function buildSubscribeTopics(wsStockPool, options) {
	const scenarioMode = options?.scenarioMode || 'hot-key';
	if (scenarioMode === 'hot-key' || scenarioMode === 'churn') {
		return [`quote:${options.hotStockId}`];
	}

	const targetCount = Math.max(1, Number(options?.distributedTopicCount || 1));
	const stockPool = Array.isArray(wsStockPool) ? wsStockPool.filter((id) => Number.isFinite(Number(id))) : [];

	if (stockPool.length === 0) {
		return [`quote:${options.hotStockId}`];
	}

	const topics = [];
	const dedup = new Set();
	while (topics.length < targetCount && dedup.size < stockPool.length) {
		const stockId = Number(pickFrom(stockPool));
		if (!dedup.has(stockId)) {
			dedup.add(stockId);
			topics.push(`quote:${stockId}`);
		}
	}
	return topics;
}

function getScenarioTags(options) {
	return {
		scenario_mode: options?.scenarioMode || 'hot-key',
		hot_stock_id: String(options?.hotStockId || ''),
	};
}

function parseMessage(rawMessage) {
	try {
		return JSON.parse(rawMessage);
	} catch (_) {
		return null;
	}
}

export function runWsHotkeySubscribeFlow(wsUrl, wsStockPool, tokens, runtimeOptions) {
	const token = pickToken(tokens);
	if (!token) {
		wsHotkeyConnectRate.add(false);
		wsHotkeyConnectFailCount.add(1);
		return;
	}

	const options = runtimeOptions || {};
	const topics = buildSubscribeTopics(wsStockPool, options);
	const scenarioTags = getScenarioTags(options);
	const waitTimeoutMs = Number(options.waitTimeoutMs || 8_000);
	const churnRounds = Number(options.churnRounds || 3);
	const holdMs = Number(options.holdMs || 2_000);

	if (!Array.isArray(topics) || topics.length === 0) {
		wsHotkeySubscribeFailCount.add(1, scenarioTags);
		return;
	}

	let subscribeSentAtMs = 0;
	let pendingInitialTopics = new Set();
	let currentRequestId = 'sub-1';
	let churnStep = 0;
	let closeRequested = false;

	function sendSubscribe(socket) {
		subscribeSentAtMs = Date.now();
		pendingInitialTopics = new Set(topics);
		currentRequestId = `sub-${churnStep + 1}`;
		socket.send(
			JSON.stringify({
				type: 'subscribe',
				request_id: currentRequestId,
				topics,
			})
		);
	}

	function sendUnsubscribe(socket) {
		socket.send(
			JSON.stringify({
				type: 'unsubscribe',
				request_id: `unsub-${churnStep + 1}`,
				topics,
			})
		);
	}

	const response = ws.connect(wsUrl, {}, function (socket) {
		socket.on('open', function () {
			wsHotkeyConnectRate.add(true, scenarioTags);
			socket.send(JSON.stringify({ type: 'auth', token }));
		});

		socket.setTimeout(function () {
			if (!closeRequested && pendingInitialTopics.size > 0) {
				wsHotkeySnapshotMissCount.add(pendingInitialTopics.size, scenarioTags);
			}
			closeRequested = true;
			socket.close();
		}, waitTimeoutMs);

		socket.on('message', function (rawMsg) {
			const msg = parseMessage(rawMsg);
			if (!msg || !msg.type) {
				return;
			}

			switch (msg.type) {
				case 'auth': {
					const ok = msg.ok === true;
					wsHotkeyAuthRate.add(ok, scenarioTags);
					if (ok) {
						sendSubscribe(socket);
					} else {
						wsHotkeyAuthFailCount.add(1, scenarioTags);
						closeRequested = true;
						socket.close();
					}
					break;
				}

				case 'subscribe': {
					if (msg.request_id !== currentRequestId) {
						return;
					}
					if (subscribeSentAtMs > 0) {
						wsHotkeySubscribeAckLatency.add(Date.now() - subscribeSentAtMs, scenarioTags);
					}

					const rejected = Array.isArray(msg.rejected) ? msg.rejected : [];
					if (rejected.length > 0) {
						wsHotkeyRejectedTopicCount.add(rejected.length, scenarioTags);
						wsHotkeySubscribeFailCount.add(1, scenarioTags);
					}

					const subscribed = Array.isArray(msg.subscribed) ? msg.subscribed : [];
					if (subscribed.length === 0) {
						wsHotkeySubscribeFailCount.add(1, scenarioTags);
					}
					break;
				}

				case 'event': {
					const topic = typeof msg.topic === 'string' ? msg.topic : null;
					const isInitialSnapshot = msg.data && msg.data.initial === true;
					if (!isInitialSnapshot || !topic || !pendingInitialTopics.has(topic) || subscribeSentAtMs <= 0) {
						return;
					}

					wsHotkeyInitialSnapshotLatency.add(Date.now() - subscribeSentAtMs, scenarioTags);
					pendingInitialTopics.delete(topic);

					if (pendingInitialTopics.size > 0) {
						return;
					}

					if (options.scenarioMode === 'churn' && churnStep + 1 < churnRounds) {
						churnStep += 1;
						socket.setTimeout(function () {
							sendUnsubscribe(socket);
							socket.setTimeout(function () {
								sendSubscribe(socket);
							}, holdMs);
						}, holdMs);
						return;
					}

					closeRequested = true;
					socket.setTimeout(function () {
						socket.close();
					}, holdMs);
					break;
				}

				case 'error': {
					if (msg.code === 'UNAUTHORIZED') {
						wsHotkeyAuthRate.add(false, scenarioTags);
						wsHotkeyAuthFailCount.add(1, scenarioTags);
					} else {
						wsHotkeySubscribeFailCount.add(1, scenarioTags);
					}
					closeRequested = true;
					socket.close();
					break;
				}

				case 'ping': {
					socket.send(JSON.stringify({ type: 'pong' }));
					break;
				}
			}
		});

		socket.on('error', function () {
			wsHotkeyConnectFailCount.add(1, scenarioTags);
		});

		socket.on('close', function () {
			wsHotkeyDisconnectCount.add(1, scenarioTags);
		});
	});

	if (response && response.status !== 101) {
		wsHotkeyConnectRate.add(false, scenarioTags);
		wsHotkeyConnectFailCount.add(1, scenarioTags);
	}
}
