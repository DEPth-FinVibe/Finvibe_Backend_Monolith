// ──────────────────────────────────────────────────────────────────────────────
// 시나리오별 부하 프로파일 정의
//
// 시나리오 구성 (7개):
//   public_market_read     — 마켓 홈 / 종목 탐색 / 검색 / 카테고리
//   public_news_read       — 뉴스 피드 / 상세 / 테마 / 트렌딩 키워드
//   public_leaderboard_read— XP 랭킹(퍼블릭) / 배지 목록 / 유저 프로필 조회
//   auth_profile_read      — 대시보드 / 포트폴리오 비교·배분·상세 / 수익 랭킹
//   auth_trade_read        — 거래 내역 / 예약 종목 / 피어 거래 조회
//   auth_gamification_read — XP·스쿼드·배지·챌린지 게임화 피드
//   heavy_read_isolated    — 장기 캔들 / 지수 캔들 / 대용량 페이지네이션 / 성과 차트
// ──────────────────────────────────────────────────────────────────────────────

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
	// ── quick: CI 빠른 동작 확인 (3초) ─────────────────────────────────────
	quick: {
		public_market_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '3s',
			preAllocatedVUs: 3, maxVUs: 8,
			exec: 'default',
			tags: { scenario_group: 'public_market', scope: 'public' },
		},
		public_news_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '3s',
			preAllocatedVUs: 3, maxVUs: 6,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
		},
		public_leaderboard_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '3s',
			preAllocatedVUs: 2, maxVUs: 5,
			exec: 'default',
			tags: { scenario_group: 'public_leaderboard', scope: 'public' },
		},
		auth_profile_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '3s',
			preAllocatedVUs: 3, maxVUs: 8,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
		},
		auth_trade_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '3s',
			preAllocatedVUs: 2, maxVUs: 6,
			exec: 'default',
			tags: { scenario_group: 'auth_trade', scope: 'auth' },
		},
		auth_gamification_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '3s',
			preAllocatedVUs: 3, maxVUs: 8,
			exec: 'default',
			tags: { scenario_group: 'auth_gamification', scope: 'auth' },
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '3s', duration: '3s',
			preAllocatedVUs: 1, maxVUs: 2,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},

	// ── smoke: 저부하 기능 검증 (5분) ──────────────────────────────────────
	smoke: {
		public_market_read: {
			executor: 'constant-arrival-rate',
			rate: 2, timeUnit: '1s', duration: '5m',
			preAllocatedVUs: 12, maxVUs: 35,
			exec: 'default',
			tags: { scenario_group: 'public_market', scope: 'public' },
		},
		public_news_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '5m',
			preAllocatedVUs: 8, maxVUs: 20,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
		},
		public_leaderboard_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '5m',
			preAllocatedVUs: 5, maxVUs: 12,
			exec: 'default',
			tags: { scenario_group: 'public_leaderboard', scope: 'public' },
		},
		auth_profile_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '5m',
			preAllocatedVUs: 18, maxVUs: 40,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
		},
		auth_trade_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '5m',
			preAllocatedVUs: 8, maxVUs: 20,
			exec: 'default',
			tags: { scenario_group: 'auth_trade', scope: 'auth' },
		},
		auth_gamification_read: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '1s', duration: '5m',
			preAllocatedVUs: 14, maxVUs: 30,
			exec: 'default',
			tags: { scenario_group: 'auth_gamification', scope: 'auth' },
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '10s', duration: '5m',
			preAllocatedVUs: 4, maxVUs: 10,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},

	// ── ramp10: 완만한 워밍업 램프 (10분) ──────────────────────────────────
	ramp10: {
		public_market_read: {
			executor: 'ramping-arrival-rate',
			startRate: 3, timeUnit: '1s',
			preAllocatedVUs: 16, maxVUs: 55,
			exec: 'default',
			tags: { scenario_group: 'public_market', scope: 'public' },
			stages: [
				{ target: 3, duration: '2m' },
				{ target: 5, duration: '4m' },
				{ target: 6, duration: '4m' },
			],
		},
		public_news_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 10, maxVUs: 30,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
			stages: [
				{ target: 1, duration: '2m' },
				{ target: 2, duration: '4m' },
				{ target: 3, duration: '4m' },
			],
		},
		public_leaderboard_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 5, maxVUs: 15,
			exec: 'default',
			tags: { scenario_group: 'public_leaderboard', scope: 'public' },
			stages: [
				{ target: 1, duration: '2m' },
				{ target: 1, duration: '4m' },
				{ target: 2, duration: '4m' },
			],
		},
		auth_profile_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 18, maxVUs: 45,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
			stages: [
				{ target: 1, duration: '2m' },
				{ target: 2, duration: '4m' },
				{ target: 2, duration: '4m' },
			],
		},
		auth_trade_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 8, maxVUs: 24,
			exec: 'default',
			tags: { scenario_group: 'auth_trade', scope: 'auth' },
			stages: [
				{ target: 1, duration: '2m' },
				{ target: 1, duration: '4m' },
				{ target: 2, duration: '4m' },
			],
		},
		auth_gamification_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 14, maxVUs: 35,
			exec: 'default',
			tags: { scenario_group: 'auth_gamification', scope: 'auth' },
			stages: [
				{ target: 1, duration: '2m' },
				{ target: 1, duration: '4m' },
				{ target: 1, duration: '4m' },
			],
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '8s', duration: '10m',
			preAllocatedVUs: 4, maxVUs: 12,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},

	// ── baseline: 운영 기준 부하 측정 (25분) ────────────────────────────────
	// 총 피크 ~24 RPS: market 10 / news 5 / leaderboard 2 / profile 4 / trade 1.5 / gamification 1 / heavy 0.2
	baseline: {
		public_market_read: {
			executor: 'ramping-arrival-rate',
			startRate: 4, timeUnit: '1s',
			preAllocatedVUs: 25, maxVUs: 90,
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
			startRate: 2, timeUnit: '1s',
			preAllocatedVUs: 14, maxVUs: 50,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 4, duration: '10m' },
				{ target: 5, duration: '10m' },
			],
		},
		public_leaderboard_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 6, maxVUs: 20,
			exec: 'default',
			tags: { scenario_group: 'public_leaderboard', scope: 'public' },
			stages: [
				{ target: 1, duration: '5m' },
				{ target: 1, duration: '10m' },
				{ target: 2, duration: '10m' },
			],
		},
		auth_profile_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2, timeUnit: '1s',
			preAllocatedVUs: 30, maxVUs: 70,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 3, duration: '10m' },
				{ target: 4, duration: '10m' },
			],
		},
		auth_trade_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 10, maxVUs: 30,
			exec: 'default',
			tags: { scenario_group: 'auth_trade', scope: 'auth' },
			stages: [
				{ target: 1, duration: '5m' },
				{ target: 1, duration: '10m' },
				{ target: 2, duration: '10m' },
			],
		},
		auth_gamification_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 14, maxVUs: 35,
			exec: 'default',
			tags: { scenario_group: 'auth_gamification', scope: 'auth' },
			stages: [
				{ target: 1, duration: '5m' },
				{ target: 1, duration: '10m' },
				{ target: 1, duration: '10m' },
			],
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '5s', duration: '20m',
			preAllocatedVUs: 6, maxVUs: 18,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},

	// ── stepup: baseline 2배 수준 내구성 확인 (25분) ────────────────────────
	stepup: {
		public_market_read: {
			executor: 'ramping-arrival-rate',
			startRate: 8, timeUnit: '1s',
			preAllocatedVUs: 50, maxVUs: 150,
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
			startRate: 4, timeUnit: '1s',
			preAllocatedVUs: 25, maxVUs: 75,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
			stages: [
				{ target: 4, duration: '5m' },
				{ target: 8, duration: '10m' },
				{ target: 10, duration: '10m' },
			],
		},
		public_leaderboard_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2, timeUnit: '1s',
			preAllocatedVUs: 10, maxVUs: 30,
			exec: 'default',
			tags: { scenario_group: 'public_leaderboard', scope: 'public' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 3, duration: '10m' },
				{ target: 4, duration: '10m' },
			],
		},
		auth_profile_read: {
			executor: 'ramping-arrival-rate',
			startRate: 3, timeUnit: '1s',
			preAllocatedVUs: 50, maxVUs: 110,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
			stages: [
				{ target: 3, duration: '5m' },
				{ target: 6, duration: '10m' },
				{ target: 8, duration: '10m' },
			],
		},
		auth_trade_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2, timeUnit: '1s',
			preAllocatedVUs: 16, maxVUs: 45,
			exec: 'default',
			tags: { scenario_group: 'auth_trade', scope: 'auth' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 2, duration: '10m' },
				{ target: 3, duration: '10m' },
			],
		},
		auth_gamification_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 20, maxVUs: 50,
			exec: 'default',
			tags: { scenario_group: 'auth_gamification', scope: 'auth' },
			stages: [
				{ target: 1, duration: '5m' },
				{ target: 2, duration: '10m' },
				{ target: 2, duration: '10m' },
			],
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '3s', duration: '25m',
			preAllocatedVUs: 8, maxVUs: 22,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},

	// ── stress: 한계점(breaking point) 탐색 — stepup 대비 ~3배 수준 (30분) ─
	stress: {
		public_market_read: {
			executor: 'ramping-arrival-rate',
			startRate: 10, timeUnit: '1s',
			preAllocatedVUs: 90, maxVUs: 320,
			exec: 'default',
			tags: { scenario_group: 'public_market', scope: 'public' },
			stages: [
				{ target: 10, duration: '5m' },
				{ target: 30, duration: '8m' },
				{ target: 50, duration: '8m' },
				{ target: 60, duration: '9m' },
			],
		},
		public_news_read: {
			executor: 'ramping-arrival-rate',
			startRate: 5, timeUnit: '1s',
			preAllocatedVUs: 45, maxVUs: 160,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
			stages: [
				{ target: 5, duration: '5m' },
				{ target: 15, duration: '8m' },
				{ target: 25, duration: '8m' },
				{ target: 30, duration: '9m' },
			],
		},
		public_leaderboard_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2, timeUnit: '1s',
			preAllocatedVUs: 15, maxVUs: 50,
			exec: 'default',
			tags: { scenario_group: 'public_leaderboard', scope: 'public' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 5, duration: '8m' },
				{ target: 8, duration: '8m' },
				{ target: 10, duration: '9m' },
			],
		},
		auth_profile_read: {
			executor: 'ramping-arrival-rate',
			startRate: 4, timeUnit: '1s',
			preAllocatedVUs: 70, maxVUs: 200,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
			stages: [
				{ target: 4, duration: '5m' },
				{ target: 10, duration: '8m' },
				{ target: 18, duration: '8m' },
				{ target: 22, duration: '9m' },
			],
		},
		auth_trade_read: {
			executor: 'ramping-arrival-rate',
			startRate: 3, timeUnit: '1s',
			preAllocatedVUs: 25, maxVUs: 80,
			exec: 'default',
			tags: { scenario_group: 'auth_trade', scope: 'auth' },
			stages: [
				{ target: 3, duration: '5m' },
				{ target: 5, duration: '8m' },
				{ target: 8, duration: '8m' },
				{ target: 10, duration: '9m' },
			],
		},
		auth_gamification_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2, timeUnit: '1s',
			preAllocatedVUs: 30, maxVUs: 90,
			exec: 'default',
			tags: { scenario_group: 'auth_gamification', scope: 'auth' },
			stages: [
				{ target: 2, duration: '5m' },
				{ target: 4, duration: '8m' },
				{ target: 6, duration: '8m' },
				{ target: 8, duration: '9m' },
			],
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '2s', duration: '30m',
			preAllocatedVUs: 12, maxVUs: 32,
			exec: 'default',
			tags: { scenario_group: 'heavy_read', cost: 'heavy' },
		},
	},

	// ── spike: 갑작스러운 트래픽 폭증 대응 테스트 (15분) ──────────────────
	// 베이스라인 저부하 → 30초 내 급증 → 2분 유지 → 복구 관찰
	spike: {
		public_market_read: {
			executor: 'ramping-arrival-rate',
			startRate: 5, timeUnit: '1s',
			preAllocatedVUs: 110, maxVUs: 420,
			exec: 'default',
			tags: { scenario_group: 'public_market', scope: 'public' },
			stages: [
				{ target: 5,  duration: '2m' },
				{ target: 80, duration: '30s' },
				{ target: 80, duration: '2m' },
				{ target: 5,  duration: '1m' },
				{ target: 5,  duration: '9m30s' },
			],
		},
		public_news_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2, timeUnit: '1s',
			preAllocatedVUs: 55, maxVUs: 220,
			exec: 'default',
			tags: { scenario_group: 'public_news', scope: 'public' },
			stages: [
				{ target: 2,  duration: '2m' },
				{ target: 40, duration: '30s' },
				{ target: 40, duration: '2m' },
				{ target: 2,  duration: '1m' },
				{ target: 2,  duration: '9m30s' },
			],
		},
		public_leaderboard_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 20, maxVUs: 80,
			exec: 'default',
			tags: { scenario_group: 'public_leaderboard', scope: 'public' },
			stages: [
				{ target: 1,  duration: '2m' },
				{ target: 15, duration: '30s' },
				{ target: 15, duration: '2m' },
				{ target: 1,  duration: '1m' },
				{ target: 1,  duration: '9m30s' },
			],
		},
		auth_profile_read: {
			executor: 'ramping-arrival-rate',
			startRate: 2, timeUnit: '1s',
			preAllocatedVUs: 60, maxVUs: 200,
			exec: 'default',
			tags: { scenario_group: 'auth_profile', scope: 'auth' },
			stages: [
				{ target: 2,  duration: '2m' },
				{ target: 30, duration: '30s' },
				{ target: 30, duration: '2m' },
				{ target: 2,  duration: '1m' },
				{ target: 2,  duration: '9m30s' },
			],
		},
		auth_trade_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 20, maxVUs: 70,
			exec: 'default',
			tags: { scenario_group: 'auth_trade', scope: 'auth' },
			stages: [
				{ target: 1,  duration: '2m' },
				{ target: 12, duration: '30s' },
				{ target: 12, duration: '2m' },
				{ target: 1,  duration: '1m' },
				{ target: 1,  duration: '9m30s' },
			],
		},
		auth_gamification_read: {
			executor: 'ramping-arrival-rate',
			startRate: 1, timeUnit: '1s',
			preAllocatedVUs: 18, maxVUs: 60,
			exec: 'default',
			tags: { scenario_group: 'auth_gamification', scope: 'auth' },
			stages: [
				{ target: 1,  duration: '2m' },
				{ target: 10, duration: '30s' },
				{ target: 10, duration: '2m' },
				{ target: 1,  duration: '1m' },
				{ target: 1,  duration: '9m30s' },
			],
		},
		heavy_read_isolated: {
			executor: 'constant-arrival-rate',
			rate: 1, timeUnit: '5s', duration: '15m',
			preAllocatedVUs: 8, maxVUs: 22,
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
