import { getJson, isoAtKstOffset, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

const SEARCH_KEYWORDS = [
	'삼성', '현대', 'SK', 'LG', '카카오', '네이버', '셀트리온', '기아', '포스코', '하이닉스',
];

export function runPublicMarketFlow() {
	const stockId = pickFrom(sharedRuntimeData.stockIds);
	const categoryId = pickFrom(sharedRuntimeData.categoryIds);
	const closingPriceStockIds = [
		pickFrom(sharedRuntimeData.stockIds),
		pickFrom(sharedRuntimeData.stockIds),
		pickFrom(sharedRuntimeData.stockIds),
	];

	// 마켓 홈 — 시장 상태 + 상위 종목(거래대금)
	getJson('/market/status', {
		tags: { module: 'market', flow: 'market_home', scope: 'public' },
	});
	randomThinkTime(0.3, 0.8);

	getJson('/market/stocks/top-by-value', {
		tags: { module: 'market', flow: 'market_home', scope: 'public' },
	});
	randomThinkTime(0.3, 0.8);

	// 탭 전환: 거래량 / 상승률 / 하락률 중 하나 랜덤
	const topTab = Math.random();
	if (topTab < 0.33) {
		getJson('/market/stocks/top-by-volume', {
			tags: { module: 'market', flow: 'market_home', scope: 'public' },
		});
	} else if (topTab < 0.66) {
		getJson('/market/stocks/top-rising', {
			tags: { module: 'market', flow: 'market_home', scope: 'public' },
		});
	} else {
		getJson('/market/stocks/top-falling', {
			tags: { module: 'market', flow: 'market_home', scope: 'public' },
		});
	}
	randomThinkTime(0.5, 1.5);

	// 종목 상세 + 단기 캔들(7일)
	getJson(`/market/stocks/${stockId}`, {
		tags: { module: 'market', flow: 'stock_detail', scope: 'public' },
	});
	randomThinkTime(0.5, 1.0);

	getJson(`/market/stocks/${stockId}/candles`, {
		query: {
			startTime: isoAtKstOffset({ days: -7 }),
			endTime: isoAtKstOffset({ hours: -1 }),
			timeframe: 'DAY',
		},
		tags: { module: 'market', flow: 'stock_candles', scope: 'public' },
	});
	randomThinkTime(0.5, 1.5);

	// 카테고리 탐색 or 검색 (60:40 분기)
	if (Math.random() < 0.6) {
		getJson('/market/categories', {
			tags: { module: 'market', flow: 'categories', scope: 'public' },
		});
		randomThinkTime(0.5, 1.0);

		getJson(`/market/categories/${categoryId}/stocks`, {
			tags: { module: 'market', flow: 'category_stocks', scope: 'public' },
		});
		randomThinkTime(0.3, 0.8);

		getJson(`/market/categories/${categoryId}/change-rate`, {
			tags: { module: 'market', flow: 'category_change_rate', scope: 'public' },
		});
	} else {
		// 종목 검색 후 마감가 일괄 조회
		const keyword = pickFrom(SEARCH_KEYWORDS);
		getJson('/market/stocks/search', {
			query: { query: keyword },
			tags: { module: 'market', flow: 'stock_search', scope: 'public' },
		});
		randomThinkTime(0.5, 1.2);

		getJson('/market/stocks/closing-prices', {
			query: { stockIds: closingPriceStockIds },
			tags: { module: 'market', flow: 'closing_prices', scope: 'public' },
		});
	}
}
