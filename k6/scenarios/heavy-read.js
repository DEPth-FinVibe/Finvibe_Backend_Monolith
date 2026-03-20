import { buildAuthHeaders } from '../lib/auth.js';
import { getJson, isoAtKstOffset, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

export function runHeavyReadFlow(tokens) {
	const stockId = pickFrom(sharedRuntimeData.stockIds);
	const newsId = pickFrom(sharedRuntimeData.newsIds);
	const headers = buildAuthHeaders(tokens); // 토큰이 있으면 사용, 없어도 동작

	// 장기 캔들 데이터(90일)
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

	// 지수 캔들 — KOSPI/KOSDAQ 번갈아
	const indexType = Math.random() < 0.5 ? 'KOSPI' : 'KOSDAQ';
	getJson(`/market/indexes/${indexType}/candles`, {
		query: {
			startTime: isoAtKstOffset({ days: -2 }),
			endTime: isoAtKstOffset({ hours: -1 }),
		},
		tags: { module: 'market', flow: 'heavy_index_candles', cost: 'heavy' },
		timeout: '20s',
	});
	randomThinkTime(2, 4);

	// 대용량 뉴스 페이지(size=50)
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

	// 포트폴리오 성과 차트(90일 집계) — 토큰이 있을 때만
	if (headers.Authorization) {
		getJson('/portfolios/performance-chart', {
			headers,
			query: {
				startDate: isoAtKstOffset({ days: -90 }).slice(0, 10),
				endDate: isoAtKstOffset({ days: -1 }).slice(0, 10),
				interval: 'DAY',
			},
			tags: { module: 'portfolio', flow: 'heavy_perf_chart', cost: 'heavy' },
			timeout: '20s',
		});
		randomThinkTime(2, 4);
	}

	// 뉴스 상세
	getJson(`/news/${newsId}`, {
		headers,
		tags: { module: 'news', flow: 'heavy_news_detail', cost: 'heavy' },
		timeout: '15s',
	});
}
