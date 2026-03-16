import { getJson, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

const RANKING_PERIODS = ['DAILY', 'WEEKLY', 'MONTHLY'];

export function runPublicLeaderboardFlow() {
	const userId = pickFrom(sharedRuntimeData.userIds);
	const period = pickFrom(RANKING_PERIODS);

	// XP 사용자 랭킹(퍼블릭) — 기간별 분산
	getJson('/xp/users/ranking', {
		query: { period },
		tags: { module: 'xp', flow: 'public_leaderboard', scope: 'public' },
	});
	randomThinkTime(0.5, 1.5);

	// 전체 배지 목록(퍼블릭)
	getJson('/badges', {
		tags: { module: 'badge', flow: 'public_leaderboard', scope: 'public' },
	});
	randomThinkTime(0.5, 1.2);

	// 다른 유저 프로필 조회(퍼블릭)
	getJson(`/members/${userId}`, {
		tags: { module: 'member', flow: 'user_profile', scope: 'public' },
		expectedStatuses: [200, 404],
	});
}
