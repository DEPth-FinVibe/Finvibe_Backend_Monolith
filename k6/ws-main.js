import { wsLoadProfileName, getWsScenarios, getWsThresholds } from './lib/ws-config.js';
import { ensureRuntimeConfig, printRuntimeSummary, sharedRuntimeData } from './lib/data.js';
import { runWsQuoteFlow } from './scenarios/ws-quote.js';

const profileName = wsLoadProfileName();

export const options = {
	scenarios: getWsScenarios(profileName),
	thresholds: getWsThresholds(profileName),
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
	const baseUrl = sharedRuntimeData.baseUrl;
	// http(s):// → ws(s)://
	const wsUrl = baseUrl.replace(/^http/, 'ws') + '/market/ws';
	runWsQuoteFlow(wsUrl);
}

export function handleSummary(data) {
	const result = {
		stdout: [
			'',
			'k6 WebSocket throughput test summary',
			`profile: ${profileName}`,
			`baseUrl: ${sharedRuntimeData.baseUrl}`,
			`tokensLoaded: ${sharedRuntimeData.tokens.length}`,
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
				tokensLoaded: sharedRuntimeData.tokens.length,
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
