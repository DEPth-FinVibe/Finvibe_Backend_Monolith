import { SharedArray } from 'k6/data';

function readRequiredEnv(name) {
	const value = __ENV[name];
	if (!value || !value.trim()) {
		throw new Error(`Missing required environment variable: ${name}`);
	}
	return value.trim();
}

function readJsonFile(path, fallbackFactory) {
	try {
		return JSON.parse(open(path));
	} catch (error) {
		if (fallbackFactory) {
			return fallbackFactory();
		}
		throw new Error(`Unable to load JSON file "${path}": ${String(error)}`);
	}
}

const DEFAULT_IDS = {
	stockIds: [1, 2, 3],
	newsIds: [1, 2, 3],
	categoryIds: [1, 2, 3],
	userIds: ['00000000-0000-0000-0000-000000000001'],
	portfolioIds: [1],
	tradeIds: [1],
};

const idsFilePath = __ENV.IDS_FILE || './k6/data/ids.sample.json';
const tokensFilePath = __ENV.TOKENS_FILE || './k6/data/tokens.sample.json';

const idsPayload = new SharedArray('ids-payload', () => [readJsonFile(idsFilePath, () => DEFAULT_IDS)])[0];
const tokenPayload = new SharedArray('tokens-payload', () =>
		readJsonFile(tokensFilePath, () => ({ tokens: [] })).tokens || []
	);

function normalizeNumberArray(values, fallback) {
	if (!Array.isArray(values) || values.length === 0) {
		return fallback.slice();
	}
	return values.map((value) => Number(value)).filter((value) => Number.isFinite(value));
}

function normalizeStringArray(values, fallback) {
	if (!Array.isArray(values) || values.length === 0) {
		return fallback.slice();
	}
	return values.map((value) => String(value));
}

export const sharedRuntimeData = {
	baseUrl: readRequiredEnv('BASE_URL'),
	idsFilePath,
	tokensFilePath,
	tokens: normalizeStringArray(tokenPayload, []),
	stockIds: normalizeNumberArray(idsPayload.stockIds, DEFAULT_IDS.stockIds),
	newsIds: normalizeNumberArray(idsPayload.newsIds, DEFAULT_IDS.newsIds),
	categoryIds: normalizeNumberArray(idsPayload.categoryIds, DEFAULT_IDS.categoryIds),
	userIds: normalizeStringArray(idsPayload.userIds, DEFAULT_IDS.userIds),
	portfolioIds: normalizeNumberArray(idsPayload.portfolioIds, DEFAULT_IDS.portfolioIds),
	tradeIds: normalizeNumberArray(idsPayload.tradeIds, DEFAULT_IDS.tradeIds),
};

sharedRuntimeData.idStatsSummary = [
	`stocks=${sharedRuntimeData.stockIds.length}`,
	`news=${sharedRuntimeData.newsIds.length}`,
	`categories=${sharedRuntimeData.categoryIds.length}`,
	`users=${sharedRuntimeData.userIds.length}`,
	`portfolios=${sharedRuntimeData.portfolioIds.length}`,
	`trades=${sharedRuntimeData.tradeIds.length}`,
].join(', ');

export function ensureRuntimeConfig() {
	if (!sharedRuntimeData.baseUrl) {
		throw new Error('BASE_URL is required');
	}
}

export function printRuntimeSummary(profileName) {
	console.log(
		[
			`[k6] profile=${profileName}`,
			`[k6] baseUrl=${sharedRuntimeData.baseUrl}`,
			`[k6] idsFile=${sharedRuntimeData.idsFilePath}`,
			`[k6] tokensFile=${sharedRuntimeData.tokensFilePath}`,
			`[k6] tokensLoaded=${sharedRuntimeData.tokens.length}`,
			`[k6] idsLoaded=${sharedRuntimeData.idStatsSummary}`,
		].join('\n')
	);
}

export function pickFrom(values) {
	return values[Math.floor(Math.random() * values.length)];
}

export function pickToken() {
	if (sharedRuntimeData.tokens.length === 0) {
		return null;
	}
	return pickFrom(sharedRuntimeData.tokens);
}
