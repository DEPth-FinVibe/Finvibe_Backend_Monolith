const DEFAULT_SCENARIO_MODE = 'hot-key';
const ALLOWED_SCENARIO_MODES = ['hot-key', 'baseline', 'churn'];

function buildCacheReadScenario(executorConfig) {
	return {
		http_hotkey_cache_read: {
			...executorConfig,
			exec: 'default',
			tags: { scenario_group: 'hotkey_cache_read' },
		},
	};
}

function buildSubscribeScenario(executorConfig) {
	return {
		ws_hotkey_subscribe: {
			...executorConfig,
			exec: 'default',
			tags: { scenario_group: 'ws_hotkey_subscribe' },
		},
	};
}

function buildRedisSpikeScenarios(readExecutorConfig, writeExecutorConfig) {
	return {
		http_hotkey_cache_read: {
			...readExecutorConfig,
			exec: 'cacheRead',
			tags: { scenario_group: 'hotkey_cache_read', test_kind: 'redis_spike' },
		},
		ws_hotkey_subscribe: {
			...writeExecutorConfig,
			exec: 'default',
			tags: { scenario_group: 'ws_hotkey_subscribe', test_kind: 'redis_spike' },
		},
	};
}

const HOTKEY_LOAD_PROFILES = {
	'hotkey-smoke': {
		scenarios: buildSubscribeScenario({
			executor: 'constant-vus',
			vus: 3,
			duration: '30s',
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.99'],
			ws_hotkey_auth_rate: ['rate>0.99'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<900', 'p(99)<2000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<7000', 'p(99)<8000'],
			ws_hotkey_subscribe_fail_count: ['count<5'],
			ws_hotkey_snapshot_miss_count: ['count<5'],
		},
	},

	'hotkey-ramp': {
		scenarios: buildSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 10,
			stages: [
				{ target: 10, duration: '2m' },
				{ target: 50, duration: '5m' },
				{ target: 120, duration: '8m' },
			],
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.98'],
			ws_hotkey_auth_rate: ['rate>0.98'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<1200', 'p(99)<2500'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<2200', 'p(99)<4000'],
			ws_hotkey_subscribe_fail_count: ['count<20'],
			ws_hotkey_snapshot_miss_count: ['count<20'],
		},
	},

	'hotkey-stress': {
		scenarios: buildSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 20,
			stages: [
				{ target: 20, duration: '1m' },
				{ target: 120, duration: '3m' },
				{ target: 300, duration: '4m' },
				{ target: 500, duration: '4m' },
				{ target: 700, duration: '4m' },
			],
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.95'],
			ws_hotkey_auth_rate: ['rate>0.95'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<2500', 'p(99)<6000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<4000', 'p(99)<8000'],
			ws_hotkey_subscribe_fail_count: ['count<100'],
			ws_hotkey_snapshot_miss_count: ['count<100'],
		},
	},

	'hotkey-cache-smoke': {
		scenarios: buildCacheReadScenario({
			executor: 'constant-vus',
			vus: 5,
			duration: '30s',
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.99'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<500', 'p(99)<1000'],
			hotkey_cache_read_fail_count: ['count<5'],
		},
	},

	'hotkey-cache-ramp': {
		scenarios: buildCacheReadScenario({
			executor: 'ramping-vus',
			startVUs: 10,
			stages: [
				{ target: 10, duration: '2m' },
				{ target: 50, duration: '5m' },
				{ target: 120, duration: '8m' },
			],
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.99'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<700', 'p(99)<1500'],
			hotkey_cache_read_fail_count: ['count<20'],
		},
	},

	'hotkey-cache-stress': {
		scenarios: buildCacheReadScenario({
			executor: 'ramping-vus',
			startVUs: 20,
			stages: [
				{ target: 20, duration: '1m' },
				{ target: 120, duration: '3m' },
				{ target: 300, duration: '4m' },
				{ target: 500, duration: '4m' },
				{ target: 700, duration: '4m' },
			],
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.98'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<1000', 'p(99)<2500'],
			hotkey_cache_read_fail_count: ['count<100'],
		},
	},

	'redis-spike-smoke': {
		scenarios: buildRedisSpikeScenarios(
			{
				executor: 'constant-vus',
				vus: 5,
				duration: '45s',
			},
			{
				executor: 'constant-vus',
				vus: 5,
				duration: '45s',
			}
		),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.99'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<700', 'p(99)<1500'],
			hotkey_cache_read_fail_count: ['count<10'],
			ws_hotkey_connect_rate: ['rate>0.99'],
			ws_hotkey_auth_rate: ['rate>0.99'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<1200', 'p(99)<2500'],
			ws_hotkey_subscribe_fail_count: ['count<10'],
			ws_hotkey_snapshot_miss_count: ['count<10'],
		},
	},

	'redis-spike-ramp': {
		scenarios: buildRedisSpikeScenarios(
			{
				executor: 'ramping-vus',
				startVUs: 10,
				stages: [
					{ target: 10, duration: '2m' },
					{ target: 80, duration: '2m' },
					{ target: 200, duration: '90s' },
					{ target: 20, duration: '90s' },
				],
			},
			{
				executor: 'ramping-vus',
				startVUs: 5,
				stages: [
					{ target: 5, duration: '2m' },
					{ target: 50, duration: '2m' },
					{ target: 120, duration: '90s' },
					{ target: 10, duration: '90s' },
				],
			}
		),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.98'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<1200', 'p(99)<2500'],
			hotkey_cache_read_fail_count: ['count<50'],
			ws_hotkey_connect_rate: ['rate>0.98'],
			ws_hotkey_auth_rate: ['rate>0.98'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<1800', 'p(99)<3500'],
			ws_hotkey_subscribe_fail_count: ['count<50'],
			ws_hotkey_snapshot_miss_count: ['count<50'],
		},
	},

	'redis-spike-stress': {
		scenarios: buildRedisSpikeScenarios(
			{
				executor: 'ramping-vus',
				startVUs: 20,
				stages: [
					{ target: 20, duration: '1m' },
					{ target: 150, duration: '2m' },
					{ target: 350, duration: '2m' },
					{ target: 500, duration: '2m' },
					{ target: 30, duration: '1m' },
				],
			},
			{
				executor: 'ramping-vus',
				startVUs: 10,
				stages: [
					{ target: 10, duration: '1m' },
					{ target: 80, duration: '2m' },
					{ target: 200, duration: '2m' },
					{ target: 300, duration: '2m' },
					{ target: 20, duration: '1m' },
				],
			}
		),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.95'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<2000', 'p(99)<4000'],
			hotkey_cache_read_fail_count: ['count<200'],
			ws_hotkey_connect_rate: ['rate>0.95'],
			ws_hotkey_auth_rate: ['rate>0.95'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_hotkey_subscribe}': ['p(95)<2500', 'p(99)<5000'],
			ws_hotkey_subscribe_fail_count: ['count<200'],
			ws_hotkey_snapshot_miss_count: ['count<200'],
		},
	},
};

function parseInteger(rawValue, fallback, { min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER } = {}) {
	if (rawValue === undefined || rawValue === null || String(rawValue).trim() === '') {
		return fallback;
	}
	const parsed = parseInt(String(rawValue), 10);
	if (!Number.isFinite(parsed)) {
		return fallback;
	}
	if (parsed < min) {
		return min;
	}
	if (parsed > max) {
		return max;
	}
	return parsed;
}

function resolveScenarioMode() {
	const raw = String(__ENV.HOTKEY_SCENARIO || DEFAULT_SCENARIO_MODE).trim().toLowerCase();
	if (!ALLOWED_SCENARIO_MODES.includes(raw)) {
		throw new Error(
			`Unknown HOTKEY_SCENARIO="${raw}". Supported: ${ALLOWED_SCENARIO_MODES.join(', ')}`
		);
	}
	return raw;
}

function parseHotStockId(wsStockPool) {
	const envValue = parseInteger(__ENV.HOTKEY_STOCK_ID, NaN, { min: 1 });
	if (Number.isFinite(envValue)) {
		return envValue;
	}
	if (!Array.isArray(wsStockPool) || wsStockPool.length === 0) {
		throw new Error('HOTKEY_STOCK_ID is required when WS stock pool is empty');
	}
	return Number(wsStockPool[0]);
}

export function hotkeyLoadProfileName() {
	return __ENV.HOTKEY_LOAD_PROFILE || 'hotkey-smoke';
}

export function getHotkeyScenarios(profileName) {
	const profile = HOTKEY_LOAD_PROFILES[profileName];
	if (!profile) {
		throw new Error(
			`Unknown HOTKEY_LOAD_PROFILE="${profileName}". Supported: ${Object.keys(HOTKEY_LOAD_PROFILES).join(', ')}`
		);
	}
	return profile.scenarios;
}

export function getHotkeyThresholds(profileName) {
	const profile = HOTKEY_LOAD_PROFILES[profileName];
	if (!profile) {
		throw new Error(
			`Unknown HOTKEY_LOAD_PROFILE="${profileName}". Supported: ${Object.keys(HOTKEY_LOAD_PROFILES).join(', ')}`
		);
	}
	return profile.thresholds;
}

export function resolveHotkeyRuntimeOptions(wsStockPool) {
	const scenarioMode = resolveScenarioMode();
	const hotStockId = parseHotStockId(wsStockPool);
	const distributedTopicCount = parseInteger(__ENV.HOTKEY_DISTRIBUTED_TOPIC_COUNT, 10, { min: 1, max: 30 });
	const churnRounds = parseInteger(__ENV.HOTKEY_CHURN_ROUNDS, 3, { min: 1, max: 30 });
	const holdMs = parseInteger(__ENV.HOTKEY_HOLD_MS, 2_000, { min: 250, max: 30_000 });
	const waitTimeoutMs = parseInteger(__ENV.HOTKEY_WAIT_TIMEOUT_MS, 8_000, { min: 1_000, max: 60_000 });

	return {
		scenarioMode,
		hotStockId,
		distributedTopicCount,
		churnRounds,
		holdMs,
		waitTimeoutMs,
	};
}
