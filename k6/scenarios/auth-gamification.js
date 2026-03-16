import { requireAuthHeaders } from '../lib/auth.js';
import { getJson, randomThinkTime } from '../lib/http.js';

function currentYearMonth() {
	const now = new Date();
	return { year: now.getUTCFullYear(), month: now.getUTCMonth() + 1 };
}

export function runAuthGamificationFlow() {
	const headers = requireAuthHeaders();
	const { year, month } = currentYearMonth();

	// XP 정보 + 스쿼드 랭킹 + 기여도
	getJson('/xp/me', {
		headers,
		tags: { module: 'xp', flow: 'gamification', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	getJson('/xp/squads/ranking', {
		headers,
		tags: { module: 'xp', flow: 'gamification', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	getJson('/xp/squads/contributions/me', {
		headers,
		tags: { module: 'xp', flow: 'gamification', scope: 'auth' },
		expectedStatuses: [200, 404],
	});
	randomThinkTime(1, 2);

	// 배지
	getJson('/badges/me', {
		headers,
		tags: { module: 'badge', flow: 'gamification', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	// 챌린지
	getJson('/challenges/me', {
		headers,
		tags: { module: 'challenge', flow: 'gamification', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	getJson('/challenges/completed', {
		headers,
		query: { year, month },
		tags: { module: 'challenge', flow: 'gamification', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	// 스쿼드
	getJson('/squads', {
		headers,
		tags: { module: 'squad', flow: 'gamification', scope: 'auth' },
	});
	randomThinkTime(1, 2);

	getJson('/squads/me', {
		headers,
		tags: { module: 'squad', flow: 'gamification', scope: 'auth' },
		expectedStatuses: [200, 404],
	});
}
