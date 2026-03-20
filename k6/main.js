import { fail } from 'k6';
import exec from 'k6/execution';

import { getScenarioExecutors, getThresholds, loadProfileName } from './lib/config.js';
import { runPublicMarketFlow } from './scenarios/public-market.js';
import { runPublicNewsFlow } from './scenarios/public-news.js';
import { runPublicLeaderboardFlow } from './scenarios/public-leaderboard.js';
import { runAuthProfileFlow } from './scenarios/auth-profile.js';
import { runAuthTradeFlow } from './scenarios/auth-trade.js';
import { runAuthGamificationFlow } from './scenarios/auth-gamification.js';
import { runHeavyReadFlow } from './scenarios/heavy-read.js';
import { ensureRuntimeConfig, printRuntimeSummary, sharedRuntimeData } from './lib/data.js';
import { issueTokensFromCredentials } from './lib/auth.js';

const profileName = loadProfileName();
let issuedTokenCount = 0;

export const options = {
	scenarios: getScenarioExecutors(profileName),
	thresholds: getThresholds(),
	summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
	ensureRuntimeConfig();
	const authTokens = issueTokensFromCredentials(
		sharedRuntimeData.baseUrl,
		sharedRuntimeData.credentials
	);
	issuedTokenCount = authTokens.length;
	printRuntimeSummary(profileName, { tokensLoaded: authTokens.length });
	return {
		profileName,
		timestamp: new Date().toISOString(),
		authTokens,
	};
}

export default function (data) {
	const scenarioName = exec.scenario.name;
	const authTokens = data?.authTokens || [];

	switch (scenarioName) {
		case 'public_market_read':
			runPublicMarketFlow();
			return;
		case 'public_news_read':
			runPublicNewsFlow();
			return;
		case 'public_leaderboard_read':
			runPublicLeaderboardFlow();
			return;
		case 'auth_profile_read':
			runAuthProfileFlow(authTokens);
			return;
		case 'auth_trade_read':
			runAuthTradeFlow(authTokens);
			return;
		case 'auth_gamification_read':
			runAuthGamificationFlow(authTokens);
			return;
		case 'heavy_read_isolated':
			runHeavyReadFlow(authTokens);
			return;
		default:
			fail(`Unsupported scenario: ${scenarioName}`);
	}
}

export function handleSummary(data) {
	const result = {
		stdout: [
			'',
			'k6 load test summary',
			`profile: ${profileName}`,
			`baseUrl: ${sharedRuntimeData.baseUrl}`,
			`tokensLoaded: ${issuedTokenCount}`,
			`idsLoaded: ${sharedRuntimeData.idStatsSummary}`,
			JSON.stringify(data.metrics, null, 2),
			'',
		].join('\n'),
	};

	const summaryFile = __ENV.SUMMARY_OUTPUT_FILE;
	if (summaryFile) {
		result[summaryFile] = JSON.stringify(
			{
				profile: profileName,
				baseUrl: sharedRuntimeData.baseUrl,
				tokensLoaded: issuedTokenCount,
				idStatsSummary: sharedRuntimeData.idStatsSummary,
				metrics: data.metrics,
				thresholds: data.thresholds,
			},
			null,
			2
		);
	}

	return result;
}
