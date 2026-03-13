import { requireAuthHeaders } from '../lib/auth.js';
import { getJson, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

function currentYearMonth() {
	const now = new Date();
	return {
		year: now.getUTCFullYear(),
		month: now.getUTCMonth() + 1,
	};
}

export function runAuthActivityFlow() {
	const headers = requireAuthHeaders();
	const { year, month } = currentYearMonth();
	const userId = pickFrom(sharedRuntimeData.userIds);

	getJson('/trades/history', {
		headers,
		query: { year, month },
		tags: { module: 'trade', flow: 'activity', scope: 'auth' },
	});
	randomThinkTime(1, 3);

	getJson('/trades/reserved/stock-ids', {
		headers,
		tags: { module: 'trade', flow: 'activity', scope: 'auth' },
	});
	randomThinkTime(1, 2.5);

	getJson(`/trades/users/${userId}/history`, {
		headers,
		query: { year, month },
		tags: { module: 'trade', flow: 'activity_peer', scope: 'auth' },
		expectedStatuses: [200, 403, 404],
	});
	randomThinkTime(1, 2.5);

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

	getJson('/badges/me', {
		headers,
		tags: { module: 'badge', flow: 'gamification', scope: 'auth' },
	});
	randomThinkTime(1, 2);

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
