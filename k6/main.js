import { fail } from 'k6';
import exec from 'k6/execution';

import { getScenarioExecutors, getThresholds, loadProfileName } from './lib/config.js';
import { runPublicMarketFlow } from './scenarios/public-market.js';
import { runPublicNewsFlow } from './scenarios/public-news.js';
import { runAuthProfileFlow } from './scenarios/auth-profile.js';
import { runAuthActivityFlow } from './scenarios/auth-activity.js';
import { runHeavyReadFlow } from './scenarios/heavy-read.js';
import { ensureRuntimeConfig, printRuntimeSummary, sharedRuntimeData } from './lib/data.js';

const profileName = loadProfileName();

export const options = {
	scenarios: getScenarioExecutors(profileName),
	thresholds: getThresholds(),
	summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
	ensureRuntimeConfig();
	printRuntimeSummary(profileName);
	return {
		profileName,
		timestamp: new Date().toISOString(),
	};
}

export default function () {
	const scenarioName = exec.scenario.name;

	switch (scenarioName) {
		case 'public_market_read':
			runPublicMarketFlow();
			return;
		case 'public_news_read':
			runPublicNewsFlow();
			return;
		case 'auth_profile_read':
			runAuthProfileFlow();
			return;
		case 'auth_activity_read':
			runAuthActivityFlow();
			return;
		case 'heavy_read_isolated':
			runHeavyReadFlow();
			return;
		default:
			fail(`Unsupported scenario: ${scenarioName}`);
	}
}

export function handleSummary(data) {
	return {
		stdout: [
			'',
			'k6 load test summary',
			`profile: ${profileName}`,
			`baseUrl: ${sharedRuntimeData.baseUrl}`,
			`tokensLoaded: ${sharedRuntimeData.tokens.length}`,
			`idsLoaded: ${sharedRuntimeData.idStatsSummary}`,
			JSON.stringify(data.metrics, null, 2),
			'',
		].join('\n'),
	};
}
