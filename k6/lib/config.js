const BASE_THRESHOLDS = {
	http_req_failed: ['rate<0.02'],
	http_req_duration: ['p(95)<1000', 'p(99)<2000'],
	'checks{scope:public}': ['rate>0.99'],
	'checks{scope:auth}': ['rate>0.99'],
	'checks{cost:heavy}': ['rate>0.95'],
	'http_req_duration{scope:public}': ['p(95)<700'],
	'http_req_duration{scope:auth}': ['p(95)<900'],
	'http_req_duration{cost:heavy}': ['p(95)<1500'],
};

const LOAD_PROFILES = {
	smoke: {
		public_market_read: {
			executor: 'constant-arrival-rate',
			rate: 2,
			timeUnit: '1s',
			duration: '5m',
			preAllocatedVUs: 10,
			maxVUs: 30,
			exec: 'default',
			tags: { scenario_group: 'public_market', scope: 'public' },
		},
		public_news_read: {
			executor: 'constant-arrival-rate',
			rate: 1,
			timeUnit: '1s',
			duration: '5m',
			preAllocatedVUs: 8,
			maxVUs: 20,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
		},
		auth_profile_read: {
			executor: 'constant-arrival-rate',
			rate: 1,
			timeUnit: '1s',
			duration: '5m',
			preAllocatedVUs: 8,
			maxVUs: 20,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
		},
		auth_activity_read: {
			executor: 'constant-arrival-rate',
			rate: 1,
			timeUnit: '1s',
			duration: '5m',
			preAllocatedVUs: 8,
			maxVUs: 20,
			exec: 'default',
			tags: { scenario_group: 'auth_activity', scope: 'auth' },
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1,
			timeUnit: '10s',
			duration: '5m',
			preAllocatedVUs: 4,
			maxVUs: 10,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},
	baseline: {
		public_market_read: {
			executor: 'ramping-arrival-rate',
			startRate: 4,
			timeUnit: '1s',
			preAllocatedVUs: 20,
			maxVUs: 80,
			exec: 'default',
			tags: { scenario_group: 'public_market', scope: 'public' },
			stages: [
				{ target: 4, duration: '5m' },
				{ target: 8, duration: '10m' },
				{ target: 10, duration: '10m' },
			],
		},
		public_news_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2,
			timeUnit: '1s',
			preAllocatedVUs: 12,
			maxVUs: 50,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 4, duration: '10m' },
				{ target: 5, duration: '10m' },
			],
		},
		auth_profile_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2,
			timeUnit: '1s',
			preAllocatedVUs: 12,
			maxVUs: 50,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 4, duration: '10m' },
				{ target: 4, duration: '10m' },
			],
		},
		auth_activity_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 10,
			maxVUs: 40,
			exec: 'default',
			tags: { scenario_group: 'auth_activity', scope: 'auth' },
			stages: [
				{ target: 1, duration: '5m' },
				{ target: 2, duration: '10m' },
				{ target: 2, duration: '10m' },
			],
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1,
			timeUnit: '5s',
			duration: '20m',
			preAllocatedVUs: 6,
			maxVUs: 16,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},
	stepup: {
		public_market_read: {
			executor: 'ramping-arrival-rate',
			startRate: 8,
			timeUnit: '1s',
			preAllocatedVUs: 40,
			maxVUs: 120,
			exec: 'default',
			tags: { scenario_group: 'public_market', scope: 'public' },
			stages: [
				{ target: 8, duration: '5m' },
				{ target: 16, duration: '10m' },
				{ target: 22, duration: '10m' },
			],
		},
		public_news_read: {
			executor: 'ramping-arrival-rate',
			startRate: 4,
			timeUnit: '1s',
			preAllocatedVUs: 20,
			maxVUs: 70,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
			stages: [
				{ target: 4, duration: '5m' },
				{ target: 8, duration: '10m' },
				{ target: 12, duration: '10m' },
			],
		},
		auth_profile_read: {
			executor: 'ramping-arrival-rate',
			startRate: 3,
			timeUnit: '1s',
			preAllocatedVUs: 20,
			maxVUs: 70,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
			stages: [
				{ target: 3, duration: '5m' },
				{ target: 7, duration: '10m' },
				{ target: 10, duration: '10m' },
			],
		},
		auth_activity_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2,
			timeUnit: '1s',
			preAllocatedVUs: 16,
			maxVUs: 50,
			exec: 'default',
			tags: { scenario_group: 'auth_activity', scope: 'auth' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 3, duration: '10m' },
				{ target: 4, duration: '10m' },
			],
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1,
			timeUnit: '3s',
			duration: '25m',
			preAllocatedVUs: 8,
			maxVUs: 20,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},
};

export function loadProfileName() {
	return __ENV.LOAD_PROFILE || 'smoke';
}

export function getScenarioExecutors(profileName) {
	const profile = LOAD_PROFILES[profileName];
	if (!profile) {
		throw new Error(
			`Unknown LOAD_PROFILE="${profileName}". Supported: ${Object.keys(LOAD_PROFILES).join(', ')}`
		);
	}
	return profile;
}

export function getThresholds() {
	return BASE_THRESHOLDS;
}
