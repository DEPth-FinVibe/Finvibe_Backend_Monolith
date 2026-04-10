import ws from 'k6/ws';

import { pickFrom, pickToken } from '../../lib/data.js';
import {
	wsHotkeyAuthFailCount,
	wsHotkeyAuthRate,
	wsHotkeyConnectFailCount,
	wsHotkeyConnectRate,
	wsHotkeyDisconnectCount,
	wsHotkeyInitialSnapshotLatency,
	wsHotkeyMixedChurnCycles,
	wsHotkeyMixedEventCount,
	wsHotkeyMixedSessionDuration,
	wsHotkeyMixedSubscribeRounds,
	wsHotkeyRejectedTopicCount,
	wsHotkeySnapshotMissCount,
	wsHotkeySubscribeAckLatency,
	wsHotkeySubscribeFailCount,
} from '../lib/metrics.js';

function pickUniqueStockIds(wsStockPool, count, exclude = new Set()) {
	const pool = Array.isArray(wsStockPool)
		? wsStockPool.map((id) => Number(id)).filter((id) => Number.isFinite(id) && !exclude.has(id))
		: [];

	if (pool.length === 0) {
		return [];
	}

	const picked = [];
	const seen = new Set();
	while (picked.length < count && seen.size < pool.length) {
		const stockId = Number(pickFrom(pool));
		if (Number.isFinite(stockId) && !seen.has(stockId)) {
			seen.add(stockId);
			picked.push(stockId);
		}
	}
	return picked;
}

function buildMixedTopics(wsStockPool, options) {
	const hotStockId = Number(options.hotStockId);
	const mixedTopicCount = Math.max(1, Number(options.mixedTopicCount || 1));
	const hotRatio = Number(options.mixedHotRatio || 0.5);
	const shouldIncludeHotStock = Math.random() < hotRatio;
	const picked = [];

	if (shouldIncludeHotStock && Number.isFinite(hotStockId)) {
		picked.push(hotStockId);
	}

	const tailCount = Math.max(0, mixedTopicCount - picked.length);
	const tailIds = pickUniqueStockIds(wsStockPool, tailCount, new Set(picked));
	picked.push(...tailIds);

	if (picked.length === 0 && Number.isFinite(hotStockId)) {
		picked.push(hotStockId);
	}

	return picked.map((stockId) => `quote:${stockId}`);
}

function parseMessage(rawMessage) {
	try {
		return JSON.parse(rawMessage);
	} catch (_) {
		return null;
	}
}

function scenarioTags(options) {
	return {
		scenario_mode: options?.scenarioMode || 'mixed',
		hot_stock_id: String(options?.hotStockId || ''),
	};
}

export function runWsRedisMixedFlow(wsUrl, wsStockPool, tokens, runtimeOptions) {
	const token = pickToken(tokens);
	const options = runtimeOptions || {};
	const tags = scenarioTags(options);
	if (!token) {
		wsHotkeyConnectRate.add(false, tags);
		wsHotkeyConnectFailCount.add(1, tags);
		return;
	}

	const topics = buildMixedTopics(wsStockPool, options);
	if (!Array.isArray(topics) || topics.length === 0) {
		wsHotkeySubscribeFailCount.add(1, tags);
		return;
	}

	const sessionHoldMs = Number(options.sessionHoldMs || 180_000);
	const waitTimeoutMs = Number(options.waitTimeoutMs || 8_000);
	const churnEnabled = Math.random() < Number(options.mixedChurnProbability || 0);
	const churnIntervalMs = Number(options.mixedChurnIntervalMs || 15_000);
	const maxChurnCycles = Math.max(0, Number(options.mixedMaxChurnCycles || 0));

	let subscribeSentAtMs = 0;
	let pendingInitialTopics = new Set();
	let currentRequestId = 'sub-1';
	let closeRequested = false;
	let churnCycles = 0;
	let eventCount = 0;
	let sessionStartedAtMs = 0;

	function sendSubscribe(socket) {
		subscribeSentAtMs = Date.now();
		pendingInitialTopics = new Set(topics);
		currentRequestId = `sub-${churnCycles + 1}`;
		wsHotkeyMixedSubscribeRounds.add(1, tags);
		socket.send(JSON.stringify({
			type: 'subscribe',
			request_id: currentRequestId,
			topics,
		}));
	}

	function sendUnsubscribe(socket) {
		socket.send(JSON.stringify({
			type: 'unsubscribe',
			request_id: `unsub-${churnCycles + 1}`,
			topics,
		}));
	}

	function scheduleChurn(socket) {
		if (!churnEnabled || closeRequested || churnCycles >= maxChurnCycles) {
			return;
		}
		socket.setTimeout(function () {
			if (closeRequested) {
				return;
			}
			churnCycles += 1;
			wsHotkeyMixedChurnCycles.add(1, tags);
			sendUnsubscribe(socket);
			socket.setTimeout(function () {
				if (closeRequested) {
					return;
				}
				sendSubscribe(socket);
				scheduleChurn(socket);
			}, 1000);
		}, churnIntervalMs);
	}

	const response = ws.connect(wsUrl, {}, function (socket) {
		sessionStartedAtMs = Date.now();

		socket.on('open', function () {
			wsHotkeyConnectRate.add(true, tags);
			socket.send(JSON.stringify({ type: 'auth', token }));
		});

		socket.setTimeout(function () {
			if (closeRequested || pendingInitialTopics.size === 0) {
				return;
			}
			wsHotkeySnapshotMissCount.add(pendingInitialTopics.size, tags);
			closeRequested = true;
			socket.close();
		}, waitTimeoutMs);

		socket.setTimeout(function () {
			if (closeRequested) {
				return;
			}
			closeRequested = true;
			socket.close();
		}, sessionHoldMs);

		socket.on('message', function (rawMsg) {
			const msg = parseMessage(rawMsg);
			if (!msg || !msg.type) {
				return;
			}

			switch (msg.type) {
				case 'auth': {
					const ok = msg.ok === true;
					wsHotkeyAuthRate.add(ok, tags);
					if (!ok) {
						wsHotkeyAuthFailCount.add(1, tags);
						closeRequested = true;
						socket.close();
						return;
					}
					sendSubscribe(socket);
					scheduleChurn(socket);
					break;
				}

				case 'subscribe': {
					if (msg.request_id !== currentRequestId) {
						return;
					}
					if (subscribeSentAtMs > 0) {
						wsHotkeySubscribeAckLatency.add(Date.now() - subscribeSentAtMs, tags);
					}
					const rejected = Array.isArray(msg.rejected) ? msg.rejected : [];
					if (rejected.length > 0) {
						wsHotkeyRejectedTopicCount.add(rejected.length, tags);
						wsHotkeySubscribeFailCount.add(1, tags);
					}
					break;
				}

				case 'event': {
					eventCount += 1;
					wsHotkeyMixedEventCount.add(1, tags);
					const topic = typeof msg.topic === 'string' ? msg.topic : null;
					const hasExplicitInitialFlag = msg.data && msg.data.initial === true;
					const isPendingTopic = topic && pendingInitialTopics.has(topic);
					if ((hasExplicitInitialFlag || isPendingTopic) && topic && isPendingTopic && subscribeSentAtMs > 0) {
						wsHotkeyInitialSnapshotLatency.add(Date.now() - subscribeSentAtMs, tags);
						pendingInitialTopics.delete(topic);
					}
					break;
				}

				case 'error': {
					if (msg.code === 'UNAUTHORIZED') {
						wsHotkeyAuthRate.add(false, tags);
						wsHotkeyAuthFailCount.add(1, tags);
					} else {
						wsHotkeySubscribeFailCount.add(1, tags);
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
			wsHotkeyConnectFailCount.add(1, tags);
		});

		socket.on('close', function () {
			wsHotkeyDisconnectCount.add(1, tags);
			if (sessionStartedAtMs > 0) {
				wsHotkeyMixedSessionDuration.add(Date.now() - sessionStartedAtMs, tags);
			}
		});
	});

	if (response && response.status !== 101) {
		wsHotkeyConnectRate.add(false, tags);
		wsHotkeyConnectFailCount.add(1, tags);
	}
}
