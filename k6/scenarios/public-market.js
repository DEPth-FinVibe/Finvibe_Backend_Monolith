import { getJson, isoAtKstOffset, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

export function runPublicMarketFlow() {
	const stockId = pickFrom(sharedRuntimeData.stockIds);
	const categoryId = pickFrom(sharedRuntimeData.categoryIds);

	getJson('/market/status', {
		tags: { module: 'market', flow: 'market_home', scope: 'public' },
	});
	randomThinkTime(0.5, 1.2);

	getJson('/market/stocks/top-by-value', {
		tags: { module: 'market', flow: 'market_home', scope: 'public' },
	});
	randomThinkTime(0.5, 1.2);

	getJson(`/market/stocks/${stockId}`, {
		tags: { module: 'market', flow: 'stock_detail', scope: 'public' },
	});
	randomThinkTime(0.5, 1.5);

	getJson(`/market/stocks/${stockId}/candles`, {
		query: {
			startTime: isoAtKstOffset({ days: -7 }),
			endTime: isoAtKstOffset({ hours: -1 }),
			timeframe: 'DAY',
		},
		tags: { module: 'market', flow: 'stock_candles', scope: 'public' },
	});
	randomThinkTime(0.5, 1.5);

	getJson('/market/categories', {
		tags: { module: 'market', flow: 'categories', scope: 'public' },
	});
	randomThinkTime(0.5, 1.2);

	getJson(`/market/categories/${categoryId}/stocks`, {
		tags: { module: 'market', flow: 'categories', scope: 'public' },
	});
}
