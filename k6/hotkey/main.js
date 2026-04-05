import { hotkeyLoadProfileName, getHotkeyScenarios, getHotkeyThresholds, resolveHotkeyRuntimeOptions } from './lib/config.js';
import { ensureRuntimeConfig, pickWsStockPool, printRuntimeSummary, sharedRuntimeData } from '../lib/data.js';
import { issueTokensFromCredentials } from '../lib/auth.js';
import { runWsHotkeySubscribeFlow } from './scenarios/ws-hotkey-subscribe.js';

const profileName = hotkeyLoadProfileName();
let issuedTokenCount = 0;

export const options = {
	scenarios: getHotkeyScenarios(profileName),
	thresholds: getHotkeyThresholds(profileName),
	summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
	ensureRuntimeConfig();
	const authTokens = issueTokensFromCredentials(
		sharedRuntimeData.baseUrl,
		sharedRuntimeData.credentials
	);
	issuedTokenCount = authTokens.length;
	const wsStockPool = pickWsStockPool();
	const hotkeyOptions = resolveHotkeyRuntimeOptions(wsStockPool);

	printRuntimeSummary(profileName, {
		wsStockPool,
		tokensLoaded: authTokens.length,
	});
	console.log(
		[
			`[k6-hotkey] mode=${hotkeyOptions.scenarioMode}`,
			`[k6-hotkey] hotStockId=${hotkeyOptions.hotStockId}`,
			`[k6-hotkey] distributedTopicCount=${hotkeyOptions.distributedTopicCount}`,
			`[k6-hotkey] churnRounds=${hotkeyOptions.churnRounds}`,
		].join('\n')
	);

	return {
		profileName,
		timestamp: new Date().toISOString(),
		wsStockPool,
		hotkeyOptions,
		authTokens,
	};
}

function getWsUrl() {
	return sharedRuntimeData.baseUrl.replace(/^http/, 'ws') + '/market/ws';
}

export default function (data) {
	runWsHotkeySubscribeFlow(
		getWsUrl(),
		data?.wsStockPool || [],
		data?.authTokens || [],
		data?.hotkeyOptions
	);
}

export function handleSummary(data) {
	const result = {
		stdout: [
			'',
			'k6 WebSocket hotkey subscribe test summary',
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
