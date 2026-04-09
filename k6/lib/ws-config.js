// ──────────────────────────────────────────────────────────────────────────────
// WebSocket 부하 프로파일 정의
//
// executor: ramping-vus — VU 수 = 동시 WebSocket 연결 수
//
// 프로파일:
//   ws-connect — 5 VU, 30초: 연결·인증·10초 유지 기본 검증
//   ws-smoke   — 10 VU, 5분: 기능 검증
//   ws-ramp    — 10→50→100 VU, 15분: 점진 부하, lag 추이 관찰
//   ws-stress  — 20→100→300→500→800→1200→1800→2400→3000 VU, 20분: 빠른 한계점 탐색
//   ws-fast-stress — 20→500→1500→3000→5000→7500→10000 VU, 10분: 매우 빠른 한계점 탐색
//   ws-spike   — 20→200→20 VU, 10분: 급증 대응 및 복구
// ──────────────────────────────────────────────────────────────────────────────

const WS_LOAD_PROFILES = {
	'ws-connect': {
		scenarios: {
			ws_connect: {
				executor: 'constant-vus',
				vus: 5,
				duration: '30s',
				exec: 'wsConnectFlow',
				tags: { scenario_group: 'ws_connect' },
			},
		},
		thresholds: {
			ws_connect_rate: ['rate>0.99'],
			ws_auth_rate: ['rate>0.99'],
			ws_clean_close_rate: ['rate>0.99'],
			ws_session_duration_ms: ['p(95)>=10000'],
			ws_auth_fail_count: ['count==0'],
		},
	},

	'ws-connect-10k': {
		scenarios: {
			ws_connect: {
				executor: 'ramping-vus',
				startVUs: 100,
				stages: [
					{ target: 100, duration: '1m' },
					{ target: 2000, duration: '2m' },
					{ target: 5000, duration: '2m' },
					{ target: 8000, duration: '2m' },
					{ target: 10000, duration: '3m' },
				],
				exec: 'wsConnectFlow',
				tags: { scenario_group: 'ws_connect' },
			},
		},
		thresholds: {
			ws_connect_rate: ['rate>0.95'],
			ws_auth_rate: ['rate>0.95'],
			ws_auth_fail_count: ['count==0'],
		},
	},

	'ws-smoke': {
		scenarios: {
			ws_quote: {
				executor: 'constant-vus',
				vus: 10,
				duration: '5m',
				exec: 'default',
				tags: { scenario_group: 'ws_quote' },
			},
		},
		thresholds: {
			ws_connect_rate: ['rate>0.99'],
			ws_auth_rate: ['rate>0.99'],
			'ws_delivery_lag_ms{scenario_group:ws_quote}': ['p(95)<500', 'p(99)<1000'],
		},
	},

	'ws-ramp': {
		scenarios: {
			ws_quote: {
				executor: 'ramping-vus',
				startVUs: 10,
				stages: [
					{ target: 10, duration: '2m' },
					{ target: 50, duration: '5m' },
					{ target: 100, duration: '8m' },
				],
				exec: 'default',
				tags: { scenario_group: 'ws_quote' },
			},
		},
		thresholds: {
			ws_connect_rate: ['rate>0.98'],
			ws_auth_rate: ['rate>0.98'],
			'ws_delivery_lag_ms{scenario_group:ws_quote}': ['p(95)<800', 'p(99)<2000'],
		},
	},

	'ws-stress': {
		scenarios: {
			ws_quote: {
				executor: 'ramping-vus',
				startVUs: 20,
				stages: [
					{ target: 20, duration: '1m' },
					{ target: 100, duration: '2m' },
					{ target: 300, duration: '3m' },
					{ target: 500, duration: '2m' },
					{ target: 800, duration: '2m' },
					{ target: 1200, duration: '2m' },
					{ target: 1800, duration: '2m' },
					{ target: 2400, duration: '3m' },
					{ target: 3000, duration: '3m' },
				],
				exec: 'default',
				tags: { scenario_group: 'ws_quote' },
			},
		},
		thresholds: {
			ws_connect_rate: ['rate>0.95'],
			ws_auth_rate: ['rate>0.95'],
			'ws_delivery_lag_ms{scenario_group:ws_quote}': ['p(95)<2000', 'p(99)<5000'],
		},
	},

	'ws-fast-stress': {
		scenarios: {
			ws_quote: {
				executor: 'ramping-vus',
				startVUs: 20,
				stages: [
					{ target: 20, duration: '1m' },
					{ target: 500, duration: '1m' },
					{ target: 1500, duration: '2m' },
					{ target: 3000, duration: '2m' },
					{ target: 5000, duration: '2m' },
					{ target: 7500, duration: '1m' },
					{ target: 10000, duration: '1m' },
				],
				exec: 'default',
				tags: { scenario_group: 'ws_quote' },
			},
		},
		thresholds: {
			ws_connect_rate: ['rate>0.95'],
			ws_auth_rate: ['rate>0.95'],
			'ws_delivery_lag_ms{scenario_group:ws_quote}': ['p(95)<2000', 'p(99)<5000'],
		},
	},

	'ws-spike': {
		scenarios: {
			ws_quote: {
				executor: 'ramping-vus',
				startVUs: 20,
				stages: [
					{ target: 20,  duration: '2m' },
					{ target: 200, duration: '30s' },
					{ target: 200, duration: '3m' },
					{ target: 20,  duration: '1m' },
					{ target: 20,  duration: '3m30s' },
				],
				exec: 'default',
				tags: { scenario_group: 'ws_quote' },
			},
		},
		thresholds: {
			ws_connect_rate: ['rate>0.95'],
			ws_auth_rate: ['rate>0.95'],
			'ws_delivery_lag_ms{scenario_group:ws_quote}': ['p(95)<3000'],
		},
	},
};

export function wsLoadProfileName() {
	return __ENV.WS_LOAD_PROFILE || 'ws-smoke';
}

export function getWsScenarios(profileName) {
	const profile = WS_LOAD_PROFILES[profileName];
	if (!profile) {
		throw new Error(
			`Unknown WS_LOAD_PROFILE="${profileName}". Supported: ${Object.keys(WS_LOAD_PROFILES).join(', ')}`
		);
	}
	return profile.scenarios;
}

export function getWsThresholds(profileName) {
	const profile = WS_LOAD_PROFILES[profileName];
	if (!profile) {
		throw new Error(
			`Unknown WS_LOAD_PROFILE="${profileName}". Supported: ${Object.keys(WS_LOAD_PROFILES).join(', ')}`
		);
	}
	return profile.thresholds;
}

export function getWsSubscribeCount() {
	const raw = __ENV.WS_SUBSCRIBE_COUNT;
	if (!raw) return 10;
	const count = parseInt(raw, 10);
	if (!Number.isFinite(count) || count < 1) return 10;
	return Math.min(count, 30);
}
