import { buildAuthHeaders } from '../lib/auth.js';
import { getJson, isoAtKstOffset, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

export function runHeavyReadFlow() {
	const stockId = pickFrom(sharedRuntimeData.stockIds);
	const newsId = pickFrom(sharedRuntimeData.newsIds);
	const headers = buildAuthHeaders();

	getJson(`/market/stocks/${stockId}/candles`, {
		query: {
			startTime: isoAtKstOffset({ days: -90 }),
			endTime: isoAtKstOffset({ hours: -1 }),
			timeframe: 'DAY',
		},
		tags: { module: 'market', flow: 'heavy_candles', cost: 'heavy' },
		timeout: '20s',
	});
	randomThinkTime(2, 4);

	getJson('/market/indexes/KOSPI/candles', {
		query: {
			startTime: isoAtKstOffset({ days: -2 }),
			endTime: isoAtKstOffset({ hours: -1 }),
		},
		tags: { module: 'market', flow: 'heavy_index_candles', cost: 'heavy' },
		timeout: '20s',
	});
	randomThinkTime(2, 4);

	getJson('/news', {
		query: {
			page: Math.floor(Math.random() * 5),
			size: 50,
			sortType: 'POPULAR',
		},
		tags: { module: 'news', flow: 'heavy_news_page', cost: 'heavy' },
		timeout: '15s',
	});
	randomThinkTime(2, 4);

	getJson(`/news/${newsId}`, {
		headers,
		tags: { module: 'news', flow: 'heavy_news_detail', cost: 'heavy' },
		timeout: '15s',
	});
}
