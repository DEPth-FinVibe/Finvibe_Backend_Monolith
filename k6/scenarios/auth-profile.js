import { requireAuthHeaders } from '../lib/auth.js';
import { getJson, randomThinkTime } from '../lib/http.js';

export function runAuthProfileFlow() {
	const headers = requireAuthHeaders();

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

	getJson('/wallets/balance', {
		headers,
		tags: { module: 'wallet', flow: 'asset_home', scope: 'auth' },
	});
	randomThinkTime(1, 2.5);

	getJson('/portfolios', {
		headers,
		tags: { module: 'portfolio', flow: 'asset_home', scope: 'auth' },
	});
	randomThinkTime(1, 2.5);

	getJson('/assets/top-100', {
		headers,
		tags: { module: 'asset', flow: 'asset_home', scope: 'auth' },
		expectedStatuses: [200, 204],
	});
	randomThinkTime(1, 2);

	getJson('/rankings/user-profit', {
		headers,
		tags: { module: 'ranking', flow: 'asset_home', scope: 'auth' },
	});
}
