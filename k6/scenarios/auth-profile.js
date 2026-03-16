import { requireAuthHeaders } from '../lib/auth.js';
import { getJson, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

export function runAuthProfileFlow() {
	const headers = requireAuthHeaders();
	const portfolioId = pickFrom(sharedRuntimeData.portfolioIds);

	// 프로필 개요
	getJson('/members/me', {
		headers,
		tags: { module: 'member', flow: 'profile_overview', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	getJson('/members/favorite-stocks', {
		headers,
		tags: { module: 'member', flow: 'profile_overview', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	// 지갑 잔고
	getJson('/wallets/balance', {
		headers,
		tags: { module: 'wallet', flow: 'asset_home', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	// 포트폴리오 목록 + 상세 + 비교 + 자산 배분
	getJson('/portfolios', {
		headers,
		tags: { module: 'portfolio', flow: 'asset_home', scope: 'auth' },
	});
	randomThinkTime(0.5, 1.5);

	getJson(`/portfolios/${portfolioId}/assets`, {
		headers,
		tags: { module: 'portfolio', flow: 'portfolio_detail', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	getJson('/portfolios/comparison', {
		headers,
		tags: { module: 'portfolio', flow: 'portfolio_analysis', scope: 'auth' },
	});
	randomThinkTime(0.5, 1.5);

	getJson('/assets/allocation', {
		headers,
		tags: { module: 'asset', flow: 'portfolio_analysis', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	// 전체 보유 상위 종목 + 수익률 랭킹
	getJson('/assets/top-100', {
		headers,
		tags: { module: 'asset', flow: 'asset_home', scope: 'auth' },
		expectedStatuses: [200, 204],
	});
	randomThinkTime(1, 2);

	getJson('/rankings/user-profit', {
		headers,
		query: { type: Math.random() < 0.5 ? 'WEEKLY' : 'MONTHLY' },
		tags: { module: 'ranking', flow: 'asset_home', scope: 'auth' },
	});
}
