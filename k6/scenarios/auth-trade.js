import { requireAuthHeaders } from '../lib/auth.js';
import { getJson, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

function currentYearMonth() {
	const now = new Date();
	return { year: now.getUTCFullYear(), month: now.getUTCMonth() + 1 };
}

export function runAuthTradeFlow() {
	const headers = requireAuthHeaders();
	const { year, month } = currentYearMonth();
	const userId = pickFrom(sharedRuntimeData.userIds);
	const tradeId = pickFrom(sharedRuntimeData.tradeIds);

	// 내 거래 내역
	getJson('/trades/history', {
		headers,
		query: { year, month },
		tags: { module: 'trade', flow: 'trade_history', scope: 'auth' },
	});
	randomThinkTime(1, 2.5);

	// 예약 매수/매도 종목 IDs
	getJson('/trades/reserved/stock-ids', {
		headers,
		tags: { module: 'trade', flow: 'trade_history', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	// 특정 거래 상세 조회(퍼블릭 엔드포인트)
	getJson(`/trades/${tradeId}`, {
		tags: { module: 'trade', flow: 'trade_detail', scope: 'public' },
		expectedStatuses: [200, 404],
	});
	randomThinkTime(0.5, 1.5);

	// 다른 유저 거래 내역 피어 조회
	getJson(`/trades/users/${userId}/history`, {
		headers,
		query: { year, month },
		tags: { module: 'trade', flow: 'trade_peer', scope: 'auth' },
		expectedStatuses: [200, 403, 404],
	});
}
