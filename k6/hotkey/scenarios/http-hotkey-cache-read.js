import { getJson } from '../../lib/http.js';
import { pickFrom } from '../../lib/data.js';
import {
	hotkeyCacheReadFailCount,
	hotkeyCacheReadLatency,
	hotkeyCacheReadRate,
} from '../lib/metrics.js';

function buildTargetStockIds(wsStockPool, options) {
	const scenarioMode = options?.scenarioMode || 'hot-key';
	if (scenarioMode === 'hot-key' || scenarioMode === 'churn') {
		return [Number(options.hotStockId)];
	}

	const targetCount = Math.max(1, Number(options?.distributedTopicCount || 1));
	const stockPool = Array.isArray(wsStockPool) ? wsStockPool.filter((id) => Number.isFinite(Number(id))) : [];
	if (stockPool.length === 0) {
		return [Number(options.hotStockId)];
	}

	return stockPool.slice(0, Math.min(targetCount, stockPool.length)).map((id) => Number(id));
}

function getScenarioTags(options, stockId) {
	return {
		test_kind: 'cache-read',
		scenario_mode: options?.scenarioMode || 'hot-key',
		hot_stock_id: String(options?.hotStockId || ''),
		stock_id: String(stockId),
	};
}

export function prewarmCurrentPriceCache(targetStockIds) {
	if (!Array.isArray(targetStockIds) || targetStockIds.length === 0) {
		return;
	}

	targetStockIds.forEach((stockId) => {
		getJson(`/market/stocks/${stockId}/current-price`, {
			tags: { module: 'market', flow: 'hotkey_cache_prewarm', scenario_group: 'hotkey_cache_read' },
			timeout: '10s',
		});
	});
}

export function runHotkeyCacheReadFlow(wsStockPool, runtimeOptions) {
	const options = runtimeOptions || {};
	const targetStockIds = buildTargetStockIds(wsStockPool, options);
	const stockId = Number(pickFrom(targetStockIds));
	const tags = getScenarioTags(options, stockId);

	const response = getJson(`/market/stocks/${stockId}/current-price`, {
		tags: { module: 'market', flow: 'hotkey_cache_read', scenario_group: 'hotkey_cache_read', ...tags },
		timeout: '10s',
	});

	const ok = response.status === 200;
	hotkeyCacheReadRate.add(ok, tags);
	hotkeyCacheReadLatency.add(response.timings.duration, tags);

	if (!ok) {
		hotkeyCacheReadFailCount.add(1, tags);
	}
}
