# K6 웹소켓 핫키 재현 및 관측 계획

## TL;DR

> **요약**: staging 환경에서 많은 인증된 웹소켓 구독을 동일한 `quote:<stockId>` 토픽에 집중시키는 k6 테스트를 설계해, 이미 최적화된 fanout 경로가 아니라 subscribe 시점의 initial snapshot/Redis current-price 조회 경로에서 핫키 병목이 발생하는지 검증한다.
>
> **산출물**:
> - `/market/ws` 대상 k6 웹소켓 핫키 시나리오
> - 대상 토픽, 인증, staging 안전 한계, 임계치 설정
> - subscribe 시점 snapshot/cache 지연을 fanout 지연과 분리해 볼 수 있는 추가 계측
> - 테스트 실행 및 결과 해석용 런북
>
> **예상 규모**: 중간
> **병렬 실행**: YES - 3개 wave
> **Critical Path**: T1/T2/T3 -> T5 -> T8 -> F1-F4

---

## Context

### Original Request
주식 current-price 데이터 주변의 핫키 문제를 의도적으로 유발하는 k6 테스트 계획을 만들고, 이를 관측하기 위해 필요한 모니터링이 부족하면 함께 추가한다.

### Interview Summary
**핵심 논의**:
- 이 레포에는 live current price를 직접 반환하는 public HTTP endpoint가 없다.
- 현실적인 핫키 경로는 steady-state fanout이 아니라 웹소켓 subscribe 경로다.
- 웹소켓 fanout은 이미 가상 스레드로 최적화되었으므로, 이번 계획은 subscribe 시점 current-price 조회 부하에 집중해야 한다.
- 주 대상 환경은 staging이다.

**조사 결과**:
- 웹소켓 endpoint는 `/market/ws` 이다.
- subscribe 토픽 형식은 `quote:<stockId>` 이다.
- `MarketQuoteWebSocketHandler.sendInitialPriceSnapshots()` 가 `marketQueryUseCase.getCurrentPrices(stockIds)` 를 호출한다.
- `MarketQueryService.getCurrentPrices()` 는 Redis 기반 current price 저장소인 `CurrentPriceRepository` 를 읽는다.
- Redis current price 키는 `market:current-price:<stockId>` 와 `market:current-price-updated-at:<stockId>` 이며 TTL은 5분이다.
- 기존 메트릭으로 `ws.subscribe.duration`, `ws.fanout.duration` 등이 있지만, registry 처리 시간 / watcher 등록 시간 / snapshot fetch 시간 / Redis/cache 지연 / 첫 이벤트 전달 시간까지 분리해서 보기엔 부족할 가능성이 높다.

### Metis Review
**확인된 갭** (이 계획에 반영함):
- steady-state fanout 측정을 주 성공 조건으로 삼지 않도록 guardrail을 명시했다.
- acceptance criteria에 subscribe 시점 지연과 Redis/cache 지표를 분리해 증명하도록 확장했다.
- 중요한 설정 결정은 숨긴 가정으로 두지 않고 명시적 placeholder 또는 기본값으로 처리했다.

---

## Work Objectives

### Core Objective
동일 종목 토픽에 대한 대량 동시 구독이 subscribe 시점 지연 악화를 유발하는지, 그리고 그 현상이 Redis 핫키 패턴과 일치하는지를 재현 가능하게 검증할 수 있는 부하 테스트/관측 체계를 만든다.

### Concrete Deliverables
- `/market/ws` 대상 k6 웹소켓 burst 테스트 스크립트
- 인증 토큰 주입, 대상 토픽, 연결 수, ramp profile, 실행 시간 등을 조절할 수 있는 설정 체계
- 아래를 구분해 볼 수 있는 subscribe 경로 계측 및 대시보드/쿼리
  - subscribe ack latency
  - initial snapshot latency
  - Redis/current-price repository latency
  - watcher registration latency
  - first pushed event latency(보조 지표)
- staging에서 안전하게 실행하고 결과를 해석하는 런북

### Definition of Done
- [ ] k6 시나리오가 인증된 웹소켓 세션을 열고, 동일 `quote:<stockId>` 토픽을 subscribe하며, subscribe/initial snapshot 지연 메트릭을 수집할 수 있다.
- [ ] 관측 체계를 통해 동일 토픽 집중 subscribe burst에서 latency가 커지는지 확인할 수 있다.
- [ ] staging 안전 한계와 중단 기준이 계획에 명시되어 있다.
- [ ] 결과를 보고 slowdown이 subscribe 시점 cache pressure인지, fanout인지, 혹은 둘 다인지 구분할 수 있다.

### Must Have
- 웹소켓 subscribe 시점 initial snapshot 경로에 초점을 맞춘다.
- fanout 메트릭은 유지하되 보조 지표로만 사용한다.
- staging-safe 하도록 burst ceiling과 abort threshold를 명시한다.
- 토픽/인증 입력은 하드코딩하지 않고 설정 가능하게 설계한다.

### Must NOT Have (Guardrails)
- steady-state fanout latency를 primary pass/fail 지표로 사용하지 않는다.
- 첫 검증 환경으로 production을 사용하지 않는다.
- 실제 credential/secret을 k6 스크립트에 하드코딩하지 않는다.
- public HTTP current-price endpoint가 있다고 가정하지 않는다.
- 사람이 눈으로만 확인하는 방식에 의존하지 않고, 기계적으로 관측 가능한 신호를 정의한다.

---

## Verification Strategy (MANDATORY)

> **사람 개입 없이 검증 가능해야 함** — 모든 검증은 실행 가능한 형태여야 한다.

### Test Decision
- **인프라 존재 여부**: YES (프로젝트 테스트/빌드 인프라는 존재하며, k6/perf tooling은 추가 또는 문서화 필요)
- **자동화 테스트 전략**: Tests-after
- **프레임워크**: k6 + 코드/설정 변경에 대한 기존 빌드/테스트 검증

### QA Policy
모든 작업은 agent-executed QA 시나리오를 포함해야 한다. 증거는 `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}` 에 저장한다.

- **WebSocket**: Playwright 또는 k6 웹소켓 실행으로 connect, auth, subscribe, 메시지 타이밍 검증
- **API/Backend**: Bash/curl/metrics endpoint로 계측 결과 확인
- **Library/Module**: 기존 테스트 프레임워크 또는 직접 호출로 metric tag/timer 동작 검증

---

## Execution Strategy

### Parallel Execution Waves

```text
Wave 1 (즉시 시작 — baseline 및 기반 정리):
├── Task 1: 웹소켓 핫키 경로와 대상 종목 선택 확정 [quick]
├── Task 2: k6 시나리오 계약과 staging 안전 한계 정의 [writing]
├── Task 3: subscribe 경로의 기존 메트릭/로그/알람 매핑 [quick]
├── Task 4: 관측 갭과 증거 스키마 설계 [unspecified-high]
└── Task 5: k6용 인증 토큰 획득/주입 전략 정의 [quick]

Wave 2 (Wave 1 이후 — 구현 준비 완료 수준의 테스트/관측 설계):
├── Task 6: k6 웹소켓 burst 시나리오 설계 [deep]
├── Task 7: subscribe snapshot 경로 백엔드 계측 설계 [unspecified-high]
├── Task 8: 대시보드/쿼리/임계치 해석 설계 [writing]
└── Task 9: staging 실행 런북 및 abort 기준 설계 [writing]

Wave 3 (Wave 2 이후 — 검증 및 정교화):
├── Task 10: hot-key와 다른 병목을 구분하는 비교 시나리오 설계 [deep]
├── Task 11: 증거 수집 및 회귀 해석 워크플로 설계 [writing]
└── Task 12: 문서화 및 실행 handoff 패키지 설계 [writing]

Wave FINAL (모든 작업 이후 — 4개 병렬 리뷰 후 사용자 승인):
├── Task F1: 계획 준수 감사 (oracle)
├── Task F2: 코드/계획 품질 리뷰 (unspecified-high)
├── Task F3: 실제 QA walkthrough (unspecified-high)
└── Task F4: 범위 충실도 점검 (deep)
```

### Dependency Matrix
- **1**: — -> 6, 7, 8, 9
- **2**: — -> 6, 8, 9
- **3**: — -> 7, 8, 10
- **4**: 3 -> 7, 8, 11
- **5**: — -> 6, 9
- **6**: 1, 2, 5 -> 10, 11, 12
- **7**: 1, 3, 4 -> 8, 10, 11, 12
- **8**: 2, 3, 4, 7 -> 11, 12
- **9**: 1, 2, 5 -> 12
- **10**: 3, 6, 7 -> 12
- **11**: 4, 6, 7, 8 -> 12
- **12**: 6, 7, 8, 9, 10, 11 -> FINAL

### Agent Dispatch Summary
- **Wave 1**: T1 `quick`, T2 `writing`, T3 `quick`, T4 `unspecified-high`, T5 `quick`
- **Wave 2**: T6 `deep`, T7 `unspecified-high`, T8 `writing`, T9 `writing`
- **Wave 3**: T10 `deep`, T11 `writing`, T12 `writing`
- **FINAL**: F1 `oracle`, F2 `unspecified-high`, F3 `unspecified-high`, F4 `deep`

---

## TODOs

- [ ] 1. subscribe 시점 핫키 대상 경로 확정

  **What to do**:
  - Redis current-price cache를 실제로 타는 subscribe 경로를 확정한다: `/market/ws` -> `subscribe` -> `sendInitialPriceSnapshots()` -> `getCurrentPrices()` -> `CurrentPriceRepository`.
  - k6에 필요한 최소 메시지 교환을 정리한다: connect, auth, subscribe, subscribe ack, initial snapshot.
  - 기본 모드는 단일 종목 burst로 하고, 비교용 multi-stock 시나리오는 선택 옵션으로 둔다.

  **Must NOT do**:
  - steady-state fanout 중심으로 대상을 재정의하지 않는다.
  - HTTP current-price endpoint가 있다고 가정하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 소수 파일 기준의 경로 확인 작업이다.
  - **Skills**: `[]`
  - **Skills Evaluated but Omitted**:
    - `playwright`: 코드 경로 확인에는 불필요하다.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (Tasks 2, 3, 4, 5와 병렬)
  - **Blocks**: 6, 7, 8, 9
  - **Blocked By**: None

  **References**:
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java` - subscribe handler 및 initial snapshot 흐름
  - `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java` - `getCurrentPrices()` 조회 경로
  - `src/main/java/depth/finvibe/modules/market/infra/redis/CurrentPriceRepositoryImpl.java` - Redis current-price 키 및 TTL

  **Acceptance Criteria**:
  - [ ] 계획서가 fanout 경로가 아니라 subscribe 시점 snapshot 경로를 primary target으로 명시한다.
  - [ ] 토픽 형식과 메시지 순서가 문서화되어 있다.

  **QA Scenarios**:
  ```
  Scenario: subscribe 시점 lookup 경로 검증
    Tool: Bash (grep/read 기반 검증)
    Preconditions: 워크스페이스 접근 가능
    Steps:
      1. websocket handler를 확인해 `sendInitialPriceSnapshots()` 가 subscribe 이후 실행되는지 본다.
      2. market query service를 확인해 `getCurrentPrices()` 가 repository를 통해 current price를 읽는지 본다.
      3. current price repository를 확인해 Redis current-price 키 사용이 있는지 본다.
    Expected Result: subscribe -> Redis lookup 경로가 증거로 남는다.
    Failure Indicators: Redis를 우회하거나 다른 endpoint에 의존한다.
    Evidence: .sisyphus/evidence/task-1-subscribe-path.txt

  Scenario: fanout 경로와 분리되어 있는지 검증
    Tool: Bash (grep/read 기반 검증)
    Preconditions: 워크스페이스 접근 가능
    Steps:
      1. `MarketRedisEventConsumer` 와 `MarketWebSocketPublisher` 를 확인한다.
      2. 실시간 업데이트는 subscription 이후 pub/sub fanout으로 흐른다는 점을 확인한다.
    Expected Result: subscribe 시점 lookup과 event fanout이 분리되어 있음을 증명한다.
    Evidence: .sisyphus/evidence/task-1-fanout-separation.txt
  ```

  **Commit**: NO

- [ ] 2. 시나리오 계약 및 staging 안전 한계 정의

  **What to do**:
  - k6 입력 항목을 정의한다: websocket URL, auth token source, target stockId(s), VUs/connections, burst profile, sustain duration, 선택적 think time.
  - staging-safe ceiling 및 abort threshold를 연결 수, subscribe rate, error rate 기준으로 정의한다.
  - baseline 시나리오(분산 토픽)와 hot-key 시나리오(단일 토픽 집중) 전환 옵션을 명시한다.

  **Must NOT do**:
  - staging 한계를 암묵적으로 두지 않는다.
  - secret을 테스트 스크립트에 하드코딩하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 계약과 안전 정책 정의 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 6, 8, 9
  - **Blocked By**: None

  **References**:
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java` - rate limit 및 auth 동작
  - 현재 staging perf test 운영 관례 - 안전 ceiling 설정 참고

  **Acceptance Criteria**:
  - [ ] 필요한 환경변수와 config schema가 정리되어 있다.
  - [ ] abort threshold가 명시되어 있다.
  - [ ] baseline vs hot-key 시나리오 정의가 명확하다.

  **QA Scenarios**:
  ```
  Scenario: 시나리오 계약 완전성 검증
    Tool: Bash (config/read 검증)
    Preconditions: 시나리오 계약 초안 존재
    Steps:
      1. URL, auth, topic, burst, sustain, abort, threshold 항목이 모두 있는지 확인한다.
      2. secret literal이 없는지 확인한다.
    Expected Result: 계약이 완전하고 secret-free 상태다.
    Evidence: .sisyphus/evidence/task-2-scenario-contract.txt

  Scenario: staging 안전 가드레일 검증
    Tool: Bash (read 검증)
    Preconditions: safety section 존재
    Steps:
      1. 최대 동시 연결 수, 최대 실행 시간, abort 기준이 명시되어 있는지 확인한다.
      2. production이 첫 실행 대상에서 제외되어 있는지 확인한다.
    Expected Result: staging-safe envelope가 문서화되어 있다.
    Evidence: .sisyphus/evidence/task-2-safety-envelope.txt
  ```

  **Commit**: NO

- [ ] 3. subscribe 경로의 기존 메트릭과 로그 인벤토리 작성

  **What to do**:
  - subscribe timing, auth, rate limiting, Redis event flow, fanout 관련 기존 메트릭을 식별한다.
  - subscribe burst 하에서 이미 관측 가능한 latency/failure 신호와, 추가가 필요한 신호를 구분한다.
  - “이미 존재” 와 “추가 필요” 를 명확히 나눈다.

  **Must NOT do**:
  - fanout 메트릭과 snapshot/cache 메트릭을 섞지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 제한된 코드 범위의 inventory 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 7, 8, 10
  - **Blocked By**: None

  **References**:
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java` - `ws.subscribe.duration`, rate-limit 메트릭
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketWebSocketPublisher.java` - fanout 메트릭(주진단 지표로 과의존 금지)
  - `src/main/java/depth/finvibe/modules/market/infra/redis/MarketRedisEventConsumer.java` - Redis event deserialization 메트릭
  - `src/main/resources/application.yml` - Prometheus actuator 노출 및 histogram 설정 확인
  - `build.gradle` - Micrometer Prometheus registry 의존성 존재 확인

  **Acceptance Criteria**:
  - [ ] subscribe-path와 fanout-path 메트릭이 구분되어 정리되어 있다.
  - [ ] 메트릭 갭이 명시적으로 적혀 있다.

  **QA Scenarios**:
  ```
  Scenario: 기존 subscribe 관련 메트릭 확인
    Tool: Bash (read 검증)
    Preconditions: 메트릭 inventory 초안 존재
    Steps:
      1. `ws.subscribe.duration` 가 포함되어 있는지 확인한다.
      2. fanout 메트릭이 보조 지표로만 표시되어 있는지 확인한다.
    Expected Result: 현재 timing 신호가 분리되어 정리되어 있다.
    Evidence: .sisyphus/evidence/task-3-metrics-inventory.txt

  Scenario: 메트릭 갭 목록의 실행 가능성 검증
    Tool: Bash (read 검증)
    Preconditions: gap section 초안 존재
    Steps:
      1. 각 missing metric에 대상 코드 경로와 이유가 있는지 확인한다.
      2. 단순히 “메트릭 더 추가” 같은 추상적 placeholder가 없는지 확인한다.
    Expected Result: 갭 목록이 구체적이다.
    Evidence: .sisyphus/evidence/task-3-metric-gaps.txt
  ```

  **Commit**: NO

- [ ] 4. 부족한 관측 항목과 증거 스키마 정의

  **What to do**:
  - 핫키 진단에 필요한 최소 신규 신호를 정의한다. 예:
    - subscribe ack latency
    - initial snapshot fetch latency
    - Redis current-price repository read latency
    - watcher registration latency
    - initial snapshot success/miss/failure counters
  - 기본 tag 집합을 정의한다: stockId/topic, scenario type (baseline/hot-key), environment, result code.
  - 실행 후 반드시 남겨야 할 증거 파일과 dashboard screenshot/export 항목을 정의한다.

  **Must NOT do**:
  - 진단 가치가 없는 중복 메트릭을 만들지 않는다.
  - 캡처된 증거 없이 사람 해석에만 의존하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 불필요한 중복 없이 정밀한 observability 설계가 필요하다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 7, 8, 11
  - **Blocked By**: None

  **References**:
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java` - subscribe 및 initial snapshot 계측 지점
  - `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java` - current price query 계측 지점
  - `src/main/java/depth/finvibe/modules/market/infra/redis/CurrentPriceRepositoryImpl.java` - Redis read 계측 지점

  **Acceptance Criteria**:
  - [ ] 각 메트릭에 목적, 위치, tag set이 있다.
  - [ ] 증거 캡처 목록이 k6 출력과 서비스 측 메트릭을 모두 포함한다.

  **QA Scenarios**:
  ```
  Scenario: observability map 완전성 검증
    Tool: Bash (read 검증)
    Preconditions: observability 설계 초안 존재
    Steps:
      1. subscribe ack, snapshot fetch, repository latency, error counter가 모두 정의되어 있는지 확인한다.
      2. 각 메트릭이 특정 코드 경로에 매핑되는지 확인한다.
    Expected Result: observability 설계가 구체적이고 완전하다.
    Evidence: .sisyphus/evidence/task-4-observability-map.txt

  Scenario: evidence schema 실용성 검증
    Tool: Bash (read 검증)
    Preconditions: evidence schema 초안 존재
    Steps:
      1. 기대되는 evidence 파일명과 내용이 정의되어 있는지 확인한다.
      2. 성공/실패 실행 모두를 포괄하는지 확인한다.
    Expected Result: evidence schema가 바로 실행 가능하다.
    Evidence: .sisyphus/evidence/task-4-evidence-schema.txt
  ```

  **Commit**: NO

- [ ] 5. k6 인증 토큰 획득 및 주입 전략 정의

  **What to do**:
  - `/market/ws` 용 인증을 k6가 실제 secret을 포함하지 않고 얻는 방법을 정한다.
  - 환경변수 기반 token injection 또는 staging 전용 test user/token을 우선 고려한다.
  - 장시간 테스트에서 token rotation/expiry 처리 방식을 문서화한다.

  **Must NOT do**:
  - 실제 token을 레포에 저장하지 않는다.
  - 익명 websocket subscribe가 가능하다고 가정하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 보안 guardrail이 있는 계약 결정이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 6, 9
  - **Blocked By**: None

  **References**:
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java` - auth 메시지 요구사항 및 token 사용
  - `src/main/java/depth/finvibe/modules/user/infra/security/JwtTokenProvider.java` - token 기대 형식

  **Acceptance Criteria**:
  - [ ] k6용 auth 흐름이 end-to-end 로 문서화되어 있다.
  - [ ] secret이 커밋되지 않는다.

  **QA Scenarios**:
  ```
  Scenario: token injection 전략 검증
    Tool: Bash (read 검증)
    Preconditions: auth 전략 초안 존재
    Steps:
      1. token이 env 또는 secure runtime input에서 공급되는지 확인한다.
      2. repository 파일에 token literal이 없는지 확인한다.
    Expected Result: auth 전략이 안전하고 실행 가능하다.
    Evidence: .sisyphus/evidence/task-5-auth-strategy.txt

  Scenario: auth failure 처리 계획 검증
    Tool: Bash (read 검증)
    Preconditions: failure-path 초안 존재
    Steps:
      1. expired/invalid token 동작이 문서화되어 있는지 확인한다.
      2. 테스트 abort 규칙이 auth failure와 hot-key latency를 구분하는지 확인한다.
    Expected Result: auth failure가 cache 문제로 오진되지 않는다.
    Evidence: .sisyphus/evidence/task-5-auth-failures.txt
  ```

  **Commit**: NO

- [ ] 6. k6 웹소켓 핫키 burst 시나리오 설계

  **What to do**:
  - 많은 웹소켓 세션이 `/market/ws` 에 연결하고 auth 후 짧은 burst 윈도우 안에 동일 `quote:<stockId>` 를 subscribe 하도록 k6 시나리오를 설계한다.
  - 아래 파라미터를 포함한다:
    - baseline distributed-topic 모드
    - hot-key single-topic 모드
    - optional top-3 topic skew 모드
  - k6가 직접 측정해야 할 항목을 정의한다: connect time, auth ack time, subscribe ack time, initial snapshot event 도달 시간, error rate, disconnect rate.
  - p50/p95/p99 subscribe / initial-snapshot timing 임계치를 정의한다.

  **Must NOT do**:
  - 장시간 fanout throughput 중심 시나리오로 설계하지 않는다.
  - 설정 불가능한 테스트로 만들지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 올바른 병목을 분리하고 진단 제어군까지 포함해야 한다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (Tasks 7, 8, 9와 병렬)
  - **Blocks**: 10, 11, 12
  - **Blocked By**: 1, 2, 5

  **References**:
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java` - 메시지 계약과 타이밍 관측 지점
  - k6 WebSocket 공식 문서 - connection/subscription 시나리오 구조 참고

  **Acceptance Criteria**:
  - [ ] baseline과 hot-key 비교 모드가 모두 포함되어 있다.
  - [ ] subscribe ack 및 initial snapshot 시간이 모두 측정된다.
  - [ ] threshold와 예상 failure pattern이 문서화되어 있다.

  **QA Scenarios**:
  ```
  Scenario: hot-key 시나리오 설계 검증
    Tool: Bash (read 검증)
    Preconditions: k6 시나리오 설계 초안 존재
    Steps:
      1. single-topic burst 모드가 포함되어 있는지 확인한다.
      2. baseline distributed-topic 모드가 포함되어 있는지 확인한다.
      3. subscribe 및 initial snapshot timing이 모두 측정되는지 확인한다.
    Expected Result: 집중 토픽과 baseline을 구분 비교할 수 있다.
    Evidence: .sisyphus/evidence/task-6-k6-scenario.txt

  Scenario: failure-path 포괄성 검증
    Tool: Bash (read 검증)
    Preconditions: threshold 및 failure section 초안 존재
    Steps:
      1. auth failure, timeout, missing snapshot, rate-limit 동작이 모두 포함되어 있는지 확인한다.
      2. 각 항목에 분류 규칙이 있는지 확인한다.
    Expected Result: 흔한 false positive를 걸러낼 수 있다.
    Evidence: .sisyphus/evidence/task-6-failure-paths.txt
  ```

  **Commit**: YES
  - Message: `test(perf): add websocket hot-key k6 scenario scaffold`
  - Files: `perf/k6/*` 또는 동등한 성능 테스트 경로
  - Pre-commit: 적용 가능한 프로젝트 문법/테스트 명령

- [ ] 7. subscribe snapshot 및 cache lookup용 백엔드 계측 설계

  **What to do**:
  - 아래 지점에 계측을 추가하도록 설계한다:
    - subscribe handler end-to-end timing
    - initial snapshot fetch timing
    - `getCurrentPrices()` timing
    - `CurrentPriceRepository` read timing
    - watcher registration timing/counts
    - snapshot not found / empty result / exception counters
  - hot-key vs baseline 실행을 비교할 수 있는 tagging을 설계한다.
  - 과도한 로그 대신 최소한의 타깃형 timer/counter를 선호한다.

  **Must NOT do**:
  - 모든 메서드에 무차별 계측을 넣지 않는다.
  - metrics storage를 폭발시키는 고카디널리티 label을 만들지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 애플리케이션 코드 관측 지점을 정밀하게 설계해야 한다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 8, 10, 11, 12
  - **Blocked By**: 1, 3, 4

  **References**:
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java` - subscribe 및 initial snapshot 경계
  - `src/main/java/depth/finvibe/modules/market/application/MarketQueryService.java` - current-price query timing 경계
  - `src/main/java/depth/finvibe/modules/market/infra/redis/CurrentPriceRepositoryImpl.java` - Redis read timing 경계
  - websocket publisher/handler의 기존 Micrometer 패턴 - 네이밍과 스타일 재사용
  - `src/main/resources/application.yml` - 기존 `/actuator/prometheus` 노출을 활용하고 별도 metrics 경로를 중복 생성하지 않음

  **Acceptance Criteria**:
  - [ ] 계측 지점이 제한적이고, 이름과 tag가 명확하다.
  - [ ] subscribe 작업과 fanout 작업을 구분할 수 있다.
  - [ ] hot-key 진단이 log scraping에만 의존하지 않는다.

  **QA Scenarios**:
  ```
  Scenario: 계측 배치 검증
    Tool: Bash (read 검증)
    Preconditions: instrumentation 초안 존재
    Steps:
      1. timer/counter가 subscribe handler, snapshot fetch, query service, repository 경계에 매핑되는지 확인한다.
      2. fanout 메트릭을 primary diagnosis source로 재사용하지 않는지 확인한다.
    Expected Result: 계측이 hot-key 가설에 직접 대응한다.
    Evidence: .sisyphus/evidence/task-7-instrumentation-placement.txt

  Scenario: tag 설계 검증
    Tool: Bash (read 검증)
    Preconditions: metric tag schema 초안 존재
    Steps:
      1. scenario/environment/result tag가 포함되는지 확인한다.
      2. stockId/topic tag가 통제된 형태로 설계되어 있는지 확인한다.
    Expected Result: 메트릭이 진단 가능하면서도 안전하다.
    Evidence: .sisyphus/evidence/task-7-tag-design.txt
  ```

  **Commit**: YES
  - Message: `feat(observability): add subscribe snapshot latency instrumentation`
  - Files: websocket handler/query/repository 관측 지점
  - Pre-commit: 적용 가능한 프로젝트 문법/테스트 명령

- [ ] 8. 대시보드, 쿼리, 임계치 해석 규칙 설계

  **What to do**:
  - 핫키 여부 판단에 필요한 정확한 차트/쿼리를 정의한다:
    - k6 subscribe latency percentile
    - k6 initial snapshot latency percentile
    - 서비스 측 subscribe timer percentile
    - repository/Redis timer percentile
    - websocket auth/subscription failure
    - 인프라에서 제공 가능하면 Redis ops/latency
  - baseline과 hot-key 실행을 나란히 비교하는 구조를 정의한다.
  - auth 문제, rate limit, fanout regression과 hot-key 증상을 구분하는 해석 규칙을 추가한다.

  **Must NOT do**:
  - generic dashboard로 끝내지 않는다.
  - 단일 percentile만 보고 판단하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 진단 패키지와 해석 가이드 문서 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 11, 12
  - **Blocked By**: 2, 3, 4, 7

  **References**:
  - websocket 컴포넌트의 기존 Micrometer 메트릭 이름들
  - 현재 staging monitoring stack 관례(Grafana/Prometheus/DataDog 등)
  - `src/main/resources/application.yml` - `/actuator/prometheus` 가 이미 노출되어 있으므로 우선 이 경로를 활용

  **Acceptance Criteria**:
  - [ ] 필요한 차트/쿼리가 열거되어 있다.
  - [ ] false positive 상황을 해석하는 규칙이 있다.
  - [ ] baseline vs hot-key 비교 방식이 명시적이다.

  **QA Scenarios**:
  ```
  Scenario: dashboard/query 체크리스트 검증
    Tool: Bash (read 검증)
    Preconditions: dashboard/query 계획 초안 존재
    Steps:
      1. 필요한 latency/error 차트가 모두 나열되어 있는지 확인한다.
      2. baseline과 hot-key 실행이 side-by-side 비교되도록 정의되어 있는지 확인한다.
    Expected Result: 모니터링 출력만으로도 진단이 가능하다.
    Evidence: .sisyphus/evidence/task-8-dashboards.txt

  Scenario: 해석 규칙 검증
    Tool: Bash (read 검증)
    Preconditions: interpretation section 초안 존재
    Steps:
      1. auth/rate-limit/fanout regression과 hot-key를 구분하는 규칙이 있는지 확인한다.
      2. 메트릭이 애매할 때 escalation rule이 정의되어 있는지 확인한다.
    Expected Result: 결과를 일관되게 해석할 수 있다.
    Evidence: .sisyphus/evidence/task-8-interpretation.txt
  ```

  **Commit**: NO

- [ ] 9. staging 실행 런북 및 abort 기준 설계

  **What to do**:
  - 단계별 실행 런북을 작성한다:
    - smoke scenario
    - moderate burst
    - full hot-key burst
  - error rate, connection instability, infra impact 기준의 abort 조건을 정의한다.
  - 각 단계 이후 반드시 남겨야 할 증거를 정의한다.

  **Must NOT do**:
  - 처음부터 최대 부하로 시작하지 않는다.
  - rollback/abort 지침을 생략하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 운영용 실행 가이드와 안전 지침 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 12
  - **Blocked By**: 1, 2, 5

  **References**:
  - Task 2의 scenario contract
  - Task 5의 auth plan

  **Acceptance Criteria**:
  - [ ] 단계별 실행 절차와 stop condition이 있다.
  - [ ] 증거 수집과 관련자 알림 절차가 포함되어 있다.

  **QA Scenarios**:
  ```
  Scenario: 단계별 실행 흐름 검증
    Tool: Bash (read 검증)
    Preconditions: runbook 초안 존재
    Steps:
      1. smoke, moderate, full burst 단계가 모두 정의되어 있는지 확인한다.
      2. 각 단계에 success/abort 조건이 있는지 확인한다.
    Expected Result: 런북이 안전하고 실행 가능하다.
    Evidence: .sisyphus/evidence/task-9-runbook.txt

  Scenario: abort 기준 검증
    Tool: Bash (read 검증)
    Preconditions: abort section 초안 존재
    Steps:
      1. error-rate, timeout, infra impact trigger가 있는지 확인한다.
      2. 즉시 중단 행동이 정의되어 있는지 확인한다.
    Expected Result: 안전한 탈출 조건이 명확하다.
    Evidence: .sisyphus/evidence/task-9-abort-criteria.txt
  ```

  **Commit**: NO

- [ ] 10. hot-key와 다른 병목을 구분하는 비교 시나리오 설계

  **What to do**:
  - 최소 3개의 비교 시나리오를 정의한다:
    - single hot topic burst
    - evenly distributed topic burst
    - 동일 토픽 반복 subscribe/unsubscribe churn
  - 실제 Redis hot-key 문제라면 어떤 신호 차이가 나와야 하는지 정리한다.
  - “fanout은 정상인데 subscribe만 느려짐” 과 “fanout도 함께 느려짐” 을 구분하는 control을 둔다.

  **Must NOT do**:
  - baseline 없는 단일 시나리오로 끝내지 않는다.
  - 모든 slowdown을 hot-key로 단정하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 실험 설계의 해석 가능성이 중요하다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (Tasks 11, 12와 병렬)
  - **Blocks**: 12
  - **Blocked By**: 3, 6, 7

  **References**:
  - Task 6 시나리오 설계
  - Task 7 계측 설계
  - `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketWebSocketPublisher.java` - 비교용 fanout 경로

  **Acceptance Criteria**:
  - [ ] 최소 1개의 baseline과 1개의 concentrated-topic 시나리오가 정의되어 있다.
  - [ ] 예상되는 진단 차이가 문서화되어 있다.

  **QA Scenarios**:
  ```
  Scenario: 실험 control 검증
    Tool: Bash (read 검증)
    Preconditions: comparison matrix 초안 존재
    Steps:
      1. single-topic과 distributed-topic 시나리오가 모두 있는지 확인한다.
      2. 기대되는 메트릭 차이가 적혀 있는지 확인한다.
    Expected Result: 테스트가 인과 해석을 지원한다.
    Evidence: .sisyphus/evidence/task-10-controls.txt

  Scenario: false-positive 방지 검증
    Tool: Bash (read 검증)
    Preconditions: analysis notes 초안 존재
    Steps:
      1. auth/rate-limit/fanout regression이 대체 설명으로 포함되어 있는지 확인한다.
      2. 각 대체 설명을 구분하는 신호가 정의되어 있는지 확인한다.
    Expected Result: 오분류를 줄일 수 있다.
    Evidence: .sisyphus/evidence/task-10-false-positives.txt
  ```

  **Commit**: NO

- [ ] 11. 증거 수집 및 회귀 해석 워크플로 설계

  **What to do**:
  - 각 실행이 반드시 남겨야 할 artifact를 정의한다:
    - k6 summary output
    - raw percentile export(가능 시)
    - dashboard screenshot 또는 query export
    - subscribe 경로 application log/metric snapshot
  - 직전 known-good baseline과 어떻게 비교할지 정의한다.
  - pass/fail/needs-investigation 판정 규칙을 정의한다.

  **Must NOT do**:
  - evidence를 선택 사항으로 두지 않는다.
  - 판정 규칙을 모호하게 두지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 증거와 판정 패키지를 반복 가능하게 만들어야 한다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: 12
  - **Blocked By**: 4, 6, 7, 8

  **References**:
  - Task 4 evidence schema
  - Task 8 dashboard/query 정의

  **Acceptance Criteria**:
  - [ ] 실행 artifact가 필수이며 이름 규칙이 정해져 있다.
  - [ ] 판정 규칙이 반복 실행에 충분히 결정적이다.

  **QA Scenarios**:
  ```
  Scenario: evidence checklist 검증
    Tool: Bash (read 검증)
    Preconditions: evidence workflow 초안 존재
    Steps:
      1. k6 output, service metrics, dashboard evidence가 모두 필수인지 확인한다.
      2. evidence 파일명/위치가 정의되어 있는지 확인한다.
    Expected Result: 모든 실행이 완전한 증거 패키지를 남긴다.
    Evidence: .sisyphus/evidence/task-11-evidence-checklist.txt

  Scenario: verdict workflow 검증
    Tool: Bash (read 검증)
    Preconditions: verdict rules 초안 존재
    Steps:
      1. pass/fail/needs-investigation 규칙이 존재하는지 확인한다.
      2. threshold breach 시 다음 액션이 정의되어 있는지 확인한다.
    Expected Result: 실행 결과 해석이 반복 가능하다.
    Evidence: .sisyphus/evidence/task-11-verdict-rules.txt
  ```

  **Commit**: NO

- [ ] 12. 문서화 및 실행 handoff 패키지 설계

  **What to do**:
  - 최종 문서 패키지에 아래를 포함한다:
    - scenario overview
    - config reference
    - auth instructions
    - runbook
    - metrics/dashboard checklist
    - interpretation guide
  - 이 대화 맥락 없이도 실행자가 작업을 수행할 수 있게 만든다.

  **Must NOT do**:
  - 숨은 가정이 대화에만 남게 두지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 최종 handoff와 실행 명확성 확보 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: FINAL
  - **Blocked By**: 6, 7, 8, 9, 10, 11

  **References**:
  - Tasks 6-11의 산출물

  **Acceptance Criteria**:
  - [ ] 실행자용 패키지가 완전하고 self-contained 하다.
  - [ ] 대화 히스토리에만 남아 있는 critical context가 없다.

  **QA Scenarios**:
  ```
  Scenario: handoff 완전성 검증
    Tool: Bash (read 검증)
    Preconditions: handoff package 초안 존재
    Steps:
      1. scenario, auth, runbook, monitoring, interpretation section이 모두 있는지 확인한다.
      2. 모든 [DECISION NEEDED] placeholder가 해결되었거나 눈에 띄게 표시되어 있는지 확인한다.
    Expected Result: 실행자가 숨은 맥락 없이 진행할 수 있다.
    Evidence: .sisyphus/evidence/task-12-handoff.txt

  Scenario: 범위 충실도 검증
    Tool: Bash (read 검증)
    Preconditions: final package 초안 존재
    Steps:
      1. 문서가 subscribe 시점 hot-key 재현에 계속 초점을 두는지 확인한다.
      2. 무관한 websocket 최적화 작업이 포함되지 않았는지 확인한다.
    Expected Result: 범위가 흔들리지 않는다.
    Evidence: .sisyphus/evidence/task-12-scope-fidelity.txt
  ```

  **Commit**: YES
  - Message: `docs(perf): add staging runbook and diagnosis guide`
  - Files: perf/load-test 문서 및 실행 노트
  - Pre-commit: 적용 가능한 프로젝트 문법/테스트 명령

---

## Final Verification Wave

> 4개의 리뷰 에이전트를 병렬로 실행하고, 모두 승인해야 한다. 결과를 사용자에게 보여주고 명시적 okay를 받은 뒤 완료 처리한다.

- [ ] F1. **계획 준수 감사** — `oracle`
  모든 deliverable, guardrail, acceptance criterion이 계획에 반영되었는지 검증한다. generic websocket load testing으로 흐르면 reject 한다.

- [ ] F2. **코드/계획 품질 리뷰** — `unspecified-high`
  테스트/관측 변경 계획이 최소한이면서도 실행 가능하고, 과도한 설계 없이 구체적인지 확인한다.

- [ ] F3. **실제 QA Walkthrough** — `unspecified-high`
  auth, connect, subscribe burst, metric 관측, abort criteria, evidence capture까지 end-to-end 로 walkthrough 한다.

- [ ] F4. **범위 충실도 점검** — `deep`
  계획이 subscribe 시점 current-price 접근 주변의 hot-key 재현과 모니터링에만 집중하는지 확인하고, 무관한 websocket 최적화 작업이 섞이지 않았는지 점검한다.

---

## Commit Strategy

- **1**: `test(perf): add websocket hot-key k6 scenario scaffold`
- **2**: `feat(observability): add subscribe snapshot latency instrumentation`
- **3**: `docs(perf): add staging runbook and diagnosis guide`

---

## Success Criteria

### Verification Commands
```bash
k6 run perf/k6/ws-hot-key-subscribe.js \
  -e WS_URL=wss://<staging-host>/market/ws \
  -e WS_AUTH_TOKEN=<staging-token> \
  -e HOT_STOCK_ID=<stock-id> \
  -e SCENARIO=hot-key
# Expected: k6 run succeeds and exports subscribe/initial-snapshot metrics
```

### Final Checklist
- [ ] 동일 토픽 subscription burst를 재현할 수 있다.
- [ ] baseline vs hot-key 비교 시나리오가 정의되어 있다.
- [ ] subscribe ack latency를 측정할 수 있다.
- [ ] initial snapshot latency를 측정할 수 있다.
- [ ] Redis/current-price repository latency를 계측 또는 추론할 수 있다.
- [ ] staging abort 기준이 정의되어 있다.
- [ ] evidence 경로와 해석 규칙이 문서화되어 있다.
