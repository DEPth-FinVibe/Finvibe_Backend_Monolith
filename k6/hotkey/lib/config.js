const DEFAULT_SCENARIO_MODE = 'hot-key';
const ALLOWED_SCENARIO_MODES = ['hot-key', 'baseline', 'churn', 'mixed'];

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

function buildMixedSubscribeScenario(executorConfig) {
	return {
		ws_redis_single_mixed: {
			...executorConfig,
			exec: 'default',
			tags: { scenario_group: 'ws_redis_single_mixed', test_kind: 'redis_single_mixed' },
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
	'redis-latency-smoke': {
		scenarios: buildCacheReadScenario({
			executor: 'constant-arrival-rate',
			rate: 500,
			timeUnit: '1s',
			duration: '1m',
			preAllocatedVUs: 100,
			maxVUs: 1000,
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.99'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<300', 'p(99)<500'],
			hotkey_cache_read_fail_count: ['count<10'],
		},
	},

	'redis-latency-ramp': {
		scenarios: buildCacheReadScenario({
			executor: 'ramping-arrival-rate',
			startRate: 1000,
			timeUnit: '1s',
			preAllocatedVUs: 1000,
			maxVUs: 6000,
			stages: [
				{ target: 1000, duration: '2m' },
				{ target: 1500, duration: '3m' },
				{ target: 2000, duration: '3m' },
				{ target: 2400, duration: '2m' },
			],
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.98'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<500', 'p(99)<800'],
			hotkey_cache_read_fail_count: ['count<100'],
		},
	},

	'redis-latency-hold': {
		scenarios: buildCacheReadScenario({
			executor: 'constant-arrival-rate',
			rate: 1100,
			timeUnit: '1s',
			duration: '10m',
			preAllocatedVUs: 1500,
			maxVUs: 3000,
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.95'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<800', 'p(99)<1200'],
			hotkey_cache_read_fail_count: ['count<300'],
		},
	},

	'redis-latency-spike': {
		scenarios: buildCacheReadScenario({
			executor: 'ramping-arrival-rate',
			startRate: 1000,
			timeUnit: '1s',
			preAllocatedVUs: 1500,
			maxVUs: 5000,
			stages: [
				{ target: 1000, duration: '1m' },
				{ target: 1400, duration: '30s' },
				{ target: 1600, duration: '2m' },
				{ target: 1000, duration: '1m' },
			],
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.90'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<1000', 'p(99)<1500'],
			hotkey_cache_read_fail_count: ['count<1000'],
		},
	},

	'redis-ramp-discovery': {
		scenarios: buildCacheReadScenario({
			executor: 'ramping-arrival-rate',
			startRate: 300,
			timeUnit: '1s',
			preAllocatedVUs: 600,
			maxVUs: 4000,
			stages: [
				{ target: 300, duration: '1m' },
				{ target: 500, duration: '1m' },
				{ target: 700, duration: '1m' },
				{ target: 850, duration: '1m' },
				{ target: 1000, duration: '1m' },
				{ target: 1100, duration: '1m' },
				{ target: 900, duration: '1m' },
			],
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.95'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<1200', 'p(99)<2000'],
			hotkey_cache_read_fail_count: ['count<1000'],
		},
	},

	'redis-ramp-narrow': {
		scenarios: buildCacheReadScenario({
			executor: 'ramping-arrival-rate',
			startRate: 700,
			timeUnit: '1s',
			preAllocatedVUs: 800,
			maxVUs: 4500,
			stages: [
				{ target: 700, duration: '1m' },
				{ target: 800, duration: '1m' },
				{ target: 850, duration: '1m' },
				{ target: 900, duration: '1m' },
				{ target: 950, duration: '1m' },
				{ target: 1000, duration: '1m' },
				{ target: 850, duration: '1m' },
			],
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.95'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<1200', 'p(99)<2000'],
			hotkey_cache_read_fail_count: ['count<1000'],
		},
	},

	'redis-step-guarded': {
		scenarios: buildCacheReadScenario({
			executor: 'ramping-arrival-rate',
			startRate: 500,
			timeUnit: '1s',
			preAllocatedVUs: 700,
			maxVUs: 4000,
			stages: [
				{ target: 500, duration: '2m' },
				{ target: 700, duration: '1m' },
				{ target: 900, duration: '1m' },
				{ target: 1000, duration: '30s' },
				{ target: 700, duration: '1m' },
			],
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.95'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<1200', 'p(99)<2000'],
			hotkey_cache_read_fail_count: ['count<1000'],
		},
	},

	'redis-ramp-fine': {
		scenarios: buildCacheReadScenario({
			executor: 'ramping-arrival-rate',
			startRate: 500,
			timeUnit: '1s',
			preAllocatedVUs: 700,
			maxVUs: 4000,
			stages: [
				{ target: 500, duration: '2m' },
				{ target: 600, duration: '2m' },
				{ target: 650, duration: '2m' },
				{ target: 700, duration: '2m' },
				{ target: 750, duration: '2m' },
				{ target: 800, duration: '2m' },
				{ target: 650, duration: '2m' },
			],
		}),
		thresholds: {
			hotkey_cache_read_rate: ['rate>0.95'],
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<1000', 'p(99)<2000'],
			hotkey_cache_read_fail_count: ['count<1000'],
		},
	},

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
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<500', 'p(99)<300'],
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
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<500', 'p(99)<300'],
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
			'hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}': ['p(95)<500', 'p(99)<300'],
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

	'redis-single-mixed-smoke': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'constant-vus',
			vus: 100,
			duration: '2m',
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.98'],
			ws_hotkey_auth_rate: ['rate>0.98'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<1500', 'p(99)<3000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<6000', 'p(99)<10000'],
			'ws_hotkey_mixed_session_duration_ms{scenario_group:ws_redis_single_mixed}': ['p(95)>60000'],
			ws_hotkey_connect_fail_count: ['count<20'],
			ws_hotkey_subscribe_fail_count: ['count<20'],
		},
	},

	'redis-single-mixed-ramp': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 200,
			stages: [
				{ target: 200, duration: '2m' },
				{ target: 1000, duration: '3m' },
				{ target: 3000, duration: '5m' },
				{ target: 5000, duration: '5m' },
			],
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.95'],
			ws_hotkey_auth_rate: ['rate>0.95'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<3000', 'p(99)<7000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<10000', 'p(99)<15000'],
			ws_hotkey_connect_fail_count: ['count<300'],
			ws_hotkey_subscribe_fail_count: ['count<300'],
		},
	},

	'redis-single-mixed-5m': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 200,
			stages: [
				{ target: 200, duration: '1m' },
				{ target: 1000, duration: '1m' },
				{ target: 3000, duration: '90s' },
				{ target: 5000, duration: '90s' },
			],
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.95'],
			ws_hotkey_auth_rate: ['rate>0.95'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<3000', 'p(99)<7000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<10000', 'p(99)<15000'],
			ws_hotkey_connect_fail_count: ['count<300'],
			ws_hotkey_subscribe_fail_count: ['count<300'],
		},
	},

	'redis-single-mixed-10k': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 500,
			stages: [
				{ target: 500, duration: '2m' },
				{ target: 3000, duration: '4m' },
				{ target: 7000, duration: '6m' },
				{ target: 10000, duration: '8m' },
				{ target: 10000, duration: '10m' },
			],
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.90'],
			ws_hotkey_auth_rate: ['rate>0.90'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<5000', 'p(99)<12000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<15000', 'p(99)<25000'],
			ws_hotkey_connect_fail_count: ['count<1500'],
			ws_hotkey_subscribe_fail_count: ['count<1500'],
		},
	},

	'redis-single-mixed-10k-compact': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 500,
			stages: [
				{ target: 500, duration: '2m' },
				{ target: 3000, duration: '3m' },
				{ target: 7000, duration: '4m' },
				{ target: 10000, duration: '4m' },
				{ target: 10000, duration: '5m' },
			],
			gracefulRampDown: '30s',
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.90'],
			ws_hotkey_auth_rate: ['rate>0.90'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<5000', 'p(99)<12000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<15000', 'p(99)<25000'],
			ws_hotkey_connect_fail_count: ['count<1500'],
			ws_hotkey_subscribe_fail_count: ['count<1500'],
		},
	},

	'redis-single-mixed-10k-10m': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 500,
			stages: [
				{ target: 500, duration: '1m' },
				{ target: 3000, duration: '2m' },
				{ target: 7000, duration: '2m' },
				{ target: 10000, duration: '3m' },
				{ target: 10000, duration: '2m' },
			],
			gracefulRampDown: '30s',
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.90'],
			ws_hotkey_auth_rate: ['rate>0.90'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<5000', 'p(99)<12000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<15000', 'p(99)<25000'],
			ws_hotkey_connect_fail_count: ['count<1500'],
			ws_hotkey_subscribe_fail_count: ['count<1500'],
		},
	},
	'redis-single-mixed-7k-10m': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 500,
			stages: [
				{ target: 500, duration: '1m' },
				{ target: 2000, duration: '2m' },
				{ target: 4500, duration: '2m' },
				{ target: 7000, duration: '3m' },
				{ target: 7000, duration: '2m' },
			],
			gracefulRampDown: '30s',
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.90'],
			ws_hotkey_auth_rate: ['rate>0.90'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<5000', 'p(99)<12000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<15000', 'p(99)<25000'],
			ws_hotkey_connect_fail_count: ['count<1000'],
			ws_hotkey_subscribe_fail_count: ['count<1000'],
		},
	},

	'redis-single-mixed-5k-20x10m': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 200,
			stages: [
				{ target: 200, duration: '1m' },
				{ target: 1000, duration: '2m' },
				{ target: 3000, duration: '2m' },
				{ target: 5000, duration: '3m' },
				{ target: 5000, duration: '2m' },
			],
			gracefulRampDown: '30s',
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.92'],
			ws_hotkey_auth_rate: ['rate>0.92'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<5000', 'p(99)<12000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<15000', 'p(99)<25000'],
			ws_hotkey_connect_fail_count: ['count<800'],
			ws_hotkey_subscribe_fail_count: ['count<800'],
		},
	},

	'redis-single-mixed-realistic-5m': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 200,
			stages: [
				{ target: 200, duration: '1m' },
				{ target: 1000, duration: '1m' },
				{ target: 3000, duration: '90s' },
				{ target: 5000, duration: '90s' },
			],
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.97'],
			ws_hotkey_auth_rate: ['rate>0.97'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<2000', 'p(99)<5000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<7000', 'p(99)<12000'],
			ws_hotkey_connect_fail_count: ['count<100'],
			ws_hotkey_subscribe_fail_count: ['count<100'],
		},
	},

	'redis-single-mixed-stress-5m': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 300,
			stages: [
				{ target: 300, duration: '1m' },
				{ target: 1500, duration: '1m' },
				{ target: 3500, duration: '90s' },
				{ target: 5000, duration: '90s' },
			],
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.95'],
			ws_hotkey_auth_rate: ['rate>0.95'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<3000', 'p(99)<7000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<10000', 'p(99)<15000'],
			ws_hotkey_connect_fail_count: ['count<300'],
			ws_hotkey_subscribe_fail_count: ['count<300'],
		},
	},

	'redis-single-mixed-upperbound-5m': {
		scenarios: buildMixedSubscribeScenario({
			executor: 'ramping-vus',
			startVUs: 500,
			stages: [
				{ target: 500, duration: '1m' },
				{ target: 2000, duration: '1m' },
				{ target: 4000, duration: '90s' },
				{ target: 5000, duration: '90s' },
			],
		}),
		thresholds: {
			ws_hotkey_connect_rate: ['rate>0.92'],
			ws_hotkey_auth_rate: ['rate>0.92'],
			'ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<4000', 'p(99)<9000'],
			'ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_redis_single_mixed}': ['p(95)<12000', 'p(99)<18000'],
			ws_hotkey_connect_fail_count: ['count<500'],
			ws_hotkey_subscribe_fail_count: ['count<500'],
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
	return __ENV.HOTKEY_LOAD_PROFILE || 'redis-latency-smoke';
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
	const sessionHoldMs = parseInteger(__ENV.HOTKEY_SESSION_HOLD_MS, 180_000, { min: 10_000, max: 1_800_000 });
	const mixedTopicCount = parseInteger(__ENV.HOTKEY_MIXED_TOPIC_COUNT, 3, { min: 1, max: 50 });
	const mixedMaxChurnCycles = parseInteger(__ENV.HOTKEY_MIXED_MAX_CHURN_CYCLES, 2, { min: 0, max: 100 });
	const mixedChurnIntervalMs = parseInteger(__ENV.HOTKEY_MIXED_CHURN_INTERVAL_MS, 15_000, { min: 1_000, max: 600_000 });
	const mixedHotRatio = Number(__ENV.HOTKEY_MIXED_HOT_RATIO || '0.5');
	const mixedChurnProbability = Number(__ENV.HOTKEY_MIXED_CHURN_PROBABILITY || '0.1');

	return {
		scenarioMode,
		hotStockId,
		distributedTopicCount,
		churnRounds,
		holdMs,
		waitTimeoutMs,
		sessionHoldMs,
		mixedTopicCount,
		mixedMaxChurnCycles,
		mixedChurnIntervalMs,
		mixedHotRatio: Number.isFinite(mixedHotRatio) ? Math.min(1, Math.max(0, mixedHotRatio)) : 0.5,
		mixedChurnProbability: Number.isFinite(mixedChurnProbability) ? Math.min(1, Math.max(0, mixedChurnProbability)) : 0.1,
	};
}
