# K6 Hotkey 패키지 분리 및 기존 방식 정렬 계획

## TL;DR

> **요약**: Redis hot-key 재현용 웹소켓 부하테스트를 기존 `k6` 흐름과 동일한 사용감으로 운영할 수 있도록, `k6/hotkey` 전용 패키지로 분리하고 entrypoint/config/metrics/scenario/env/docs/runner 연동까지 일관된 구조로 설계한다.
>
> **산출물**:
> - `k6/hotkey` 전용 패키지 구조
> - 기존 websocket 테스트 스타일을 따르는 hot-key 전용 entrypoint/config/metrics/scenario
> - `.env.*`, README, 실행 예시, 보고 흐름
> - 필요 시 `k6/run.sh` 에서 hot-key 테스트를 선택할 수 있는 동일 UX
>
> **예상 규모**: 중간
> **병렬 실행**: YES - 3개 wave
> **Critical Path**: T1/T2/T3 -> T5/T6 -> T8 -> F1-F4

---

## Context

### Original Request
기존 k6 패키지 구조를 보존하면서, hot-key 테스트는 `k6/hotkey` 아래 별도 패키지로 분리하고, 사용 방식도 지금 하던 것과 최대한 동일하게 유지하고 싶다.

### Interview Summary
**핵심 논의**:
- 사용자는 일반 k6 패키지와 hot-key 테스트를 분리된 구조로 두길 원한다.
- 사용자는 “원래 하던 방식이랑 똑같이” 사용하길 원한다.
- 이번 hot-key 테스트는 Redis hot-key 재현용이며, steady-state fanout이 아니라 subscribe 시점 initial snapshot/current-price 경로를 겨냥한다.

**조사 결과**:
- 현재 k6 구조는 이미 다음 패턴을 갖고 있다:
  - `k6/main.js`
  - `k6/ws-main.js`
  - `k6/lib/*`
  - `k6/scenarios/*`
  - `k6/run.sh`
  - `k6/README.md`
  - `.env.*`, `reports/`
- 웹소켓 테스트는 이미 profile-driven 구조를 사용한다:
  - `k6/lib/ws-config.js`
  - `k6/lib/ws-metrics.js`
  - `k6/scenarios/ws-quote.js`
- 기존 `k6/run.sh` 는 대화형으로 REST/WebSocket 프로파일을 선택하게 해준다.

### Metis Review
**Identified Gaps**:
- Metis 응답이 시간 초과되어 직접 결과를 받지 못했다.
- 대신 아래 guardrail을 계획에 직접 반영했다:
  - 기존 일반 k6 구조를 불필요하게 흔들지 않기
  - hot-key 전용 구조를 별도 패키지로 유지하되 UX는 기존과 맞추기
  - generic websocket stress와 hot-key 재현 시나리오를 혼합하지 않기

---

## Work Objectives

### Core Objective
`k6/hotkey` 전용 패키지를 설계해, Redis hot-key 재현용 웹소켓 테스트를 기존 k6 사용 흐름과 동일한 방식으로 실행·해석할 수 있게 한다.

### Concrete Deliverables
- `k6/hotkey/` 전용 구조 설계
- hot-key 전용 entrypoint/config/metrics/scenario 설계
- hot-key 전용 `.env.*`/README/실행 예시 설계
- 기존 `k6/run.sh` 와 동일 UX를 유지하는 연동 설계
- subscribe-time hot-key와 일반 websocket fanout 테스트를 구분하는 기준

### Definition of Done
- [ ] `k6/hotkey` 패키지 구조가 기존 k6 관례와 일관되게 정의되어 있다.
- [ ] hot-key 전용 실행 진입점과 프로파일 방식이 문서화되어 있다.
- [ ] 기존 `k6/run.sh` 와의 연동 여부와 방식이 명확하다.
- [ ] 측정 대상이 hot-key 재현에 필요한 subscribe/snapshot/cache 경로 중심으로 좁혀져 있다.

### Must Have
- `k6/hotkey` 아래로 분리된 구조
- 기존 k6와 유사한 profile/env/runner/doc/report 흐름
- websocket hot-key 전용 목적에 맞는 시나리오 분리
- 기존 일반 k6 테스트와의 책임 경계 명확화

### Must NOT Have (Guardrails)
- 일반 `k6` 패키지 전체를 대규모로 재구성하지 않는다.
- 기존 websocket stress 테스트를 hot-key 패키지로 억지로 흡수하지 않는다.
- hot-key 재현 목적과 무관한 API 부하테스트까지 섞지 않는다.
- 실행 UX를 새롭게 복잡하게 만들지 않는다.

---

## Verification Strategy

### Test Decision
- **인프라 존재 여부**: YES
- **자동화 테스트 전략**: Tests-after
- **주요 검증 축**: 구조 일관성 + 실행 진입점 일관성 + 메트릭/시나리오 목적 적합성

### QA Policy
- 모든 작업은 실행자가 파일 구조, 참조 관계, 실행 명령, 문서 흐름을 기계적으로 확인할 수 있어야 한다.
- 증거는 `.sisyphus/evidence/task-{N}-{slug}.txt` 또는 `.json` 으로 남긴다.

---

## Execution Strategy

### Parallel Execution Waves

```text
Wave 1 (즉시 시작 — 현재 구조 파악 및 분리 경계 확정):
├── Task 1: 기존 k6 구조/관례 기준선 확정 [quick]
├── Task 2: hotkey 패키지 디렉터리 계약 정의 [writing]
├── Task 3: 기존 websocket 테스트 재사용/복사/분리 경계 정의 [quick]
└── Task 4: 실행 UX 및 run.sh 연동 방침 정의 [writing]

Wave 2 (Wave 1 이후 — 구현 설계 핵심):
├── Task 5: hotkey 전용 entrypoint/config/env/profile 설계 [deep]
├── Task 6: hotkey 전용 metrics/scenario 설계 [deep]
├── Task 7: hotkey README/reporting/runbook 설계 [writing]
└── Task 8: 상위 k6 runner 통합 설계 [unspecified-high]

Wave 3 (Wave 2 이후 — 안전성 및 handoff 정교화):
├── Task 9: 일반 websocket 테스트와 hotkey 테스트의 책임 분리 검증 설계 [deep]
├── Task 10: 최종 handoff 및 증거 패키지 설계 [writing]
└── Task 11: commit 단위 및 실행 순서 설계 [writing]

Wave FINAL:
├── Task F1: 계획 준수 감사 (oracle)
├── Task F2: 구조/품질 리뷰 (unspecified-high)
├── Task F3: 실행 walkthrough 검증 (unspecified-high)
└── Task F4: 범위 충실도 점검 (deep)
```

### Dependency Matrix
- **1**: — -> 5, 6, 7, 8
- **2**: — -> 5, 7, 8, 10
- **3**: — -> 5, 6, 9
- **4**: — -> 7, 8, 10, 11
- **5**: 1, 2, 3 -> 8, 9, 10, 11
- **6**: 1, 3 -> 7, 9, 10
- **7**: 1, 2, 4, 6 -> 10, 11
- **8**: 2, 4, 5 -> 10, 11
- **9**: 3, 5, 6 -> 10
- **10**: 2, 5, 6, 7, 8, 9 -> 11, FINAL
- **11**: 4, 5, 7, 8, 10 -> FINAL

### Agent Dispatch Summary
- **Wave 1**: T1 `quick`, T2 `writing`, T3 `quick`, T4 `writing`
- **Wave 2**: T5 `deep`, T6 `deep`, T7 `writing`, T8 `unspecified-high`
- **Wave 3**: T9 `deep`, T10 `writing`, T11 `writing`
- **FINAL**: F1 `oracle`, F2 `unspecified-high`, F3 `unspecified-high`, F4 `deep`

---

## TODOs

- [ ] 1. 기존 k6 구조와 관례 기준선 확정

  **What to do**:
  - 현재 `k6` 패키지의 공통 규칙을 확정한다: entrypoint, `lib`, `scenarios`, `.env.*`, `README`, `run.sh`, reports 흐름.
  - WebSocket 테스트가 현재 어떤 방식으로 profile/config/metrics/scenario를 분리하는지 기준선을 만든다.

  **Must NOT do**:
  - 파일 몇 개만 보고 관례를 섣불리 추정하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 소수 핵심 파일 기준의 패턴 추출 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 5, 6, 7, 8
  - **Blocked By**: None

  **References**:
  - `k6/README.md` - 전체 구조와 사용법 기준선
  - `k6/main.js` - 일반 entrypoint 패턴
  - `k6/ws-main.js` - websocket entrypoint 패턴
  - `k6/run.sh` - 사용자 실행 UX 기준선
  - `k6/lib/ws-config.js` - 프로파일/threshold 설계 기준선

  **Acceptance Criteria**:
  - [ ] 기존 k6 스타일의 공통 규칙이 문장으로 정리되어 있다.
  - [ ] hotkey 패키지가 무엇을 “같게” 따라야 하는지 명확하다.

  **QA Scenarios**:
  ```
  Scenario: 기존 k6 패턴 기준선 검증
    Tool: Bash (read 검증)
    Preconditions: 기준선 문서 초안 존재
    Steps:
      1. README, main/ws-main, run.sh, ws-config 참조가 모두 포함되어 있는지 확인한다.
      2. entrypoint/lib/scenarios/env/docs/runner 패턴이 정리되어 있는지 확인한다.
    Expected Result: hotkey 패키지가 따라야 할 기준선이 명확하다.
    Evidence: .sisyphus/evidence/task-1-k6-baseline.txt

  Scenario: websocket 패턴 별도 식별 검증
    Tool: Bash (read 검증)
    Preconditions: websocket 패턴 섹션 존재
    Steps:
      1. ws-main, ws-config, ws-metrics, ws-quote 역할이 분리 서술되어 있는지 확인한다.
      2. generic REST 테스트와 websocket 테스트가 혼동되지 않는지 확인한다.
    Expected Result: websocket 전용 패턴이 분리되어 설명된다.
    Evidence: .sisyphus/evidence/task-1-ws-pattern.txt
  ```

  **Commit**: NO

- [ ] 2. `k6/hotkey` 디렉터리 계약 정의

  **What to do**:
  - `k6/hotkey` 아래 어떤 하위 구조를 둘지 정의한다.
  - 기본안은 다음과 같이 둔다:
    - `k6/hotkey/main.js`
    - `k6/hotkey/lib/*`
    - `k6/hotkey/scenarios/*`
    - `k6/hotkey/.env.*`
    - `k6/hotkey/README.md`
    - `k6/hotkey/reports/` 또는 기존 reports 재사용 규칙
  - 기존 상위 `k6/lib` 재사용 범위와 hotkey 전용 로컬 lib 범위를 나눈다.

  **Must NOT do**:
  - 디렉터리 구조를 느슨하게 남겨 실행자가 임의로 해석하게 두지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 구조 계약을 명시적으로 문서화하는 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 5, 7, 8, 10
  - **Blocked By**: None

  **References**:
  - `k6/README.md` - 현재 top-level 파일 구성
  - `k6/scenarios/ws-quote.js` - scenario 위치 기준
  - `k6/lib/ws-metrics.js` - lib 분리 기준

  **Acceptance Criteria**:
  - [ ] `k6/hotkey` 하위 구조가 구체적 경로로 정의되어 있다.
  - [ ] 상위 `k6` 와 하위 `k6/hotkey` 의 책임 경계가 적혀 있다.

  **QA Scenarios**:
  ```
  Scenario: hotkey 디렉터리 계약 검증
    Tool: Bash (read 검증)
    Preconditions: 디렉터리 계약 초안 존재
    Steps:
      1. main/lib/scenarios/env/README 경로가 모두 정의되어 있는지 확인한다.
      2. 각 경로의 역할 설명이 있는지 확인한다.
    Expected Result: 실행자가 폴더 구조를 임의 해석하지 않아도 된다.
    Evidence: .sisyphus/evidence/task-2-directory-contract.txt

  Scenario: 재사용 경계 검증
    Tool: Bash (read 검증)
    Preconditions: 재사용 정책 초안 존재
    Steps:
      1. 상위 공통 lib 재사용 대상과 hotkey 전용 lib 분리 대상이 구분되는지 확인한다.
      2. 불필요한 중복 복사가 계획되지 않았는지 확인한다.
    Expected Result: 구조는 분리되지만 중복은 통제된다.
    Evidence: .sisyphus/evidence/task-2-reuse-boundary.txt
  ```

  **Commit**: NO

- [ ] 3. 기존 websocket 테스트와 hotkey 테스트의 분리 경계 정의

  **What to do**:
  - 현재 `k6/ws-main.js`, `k6/lib/ws-config.js`, `k6/lib/ws-metrics.js`, `k6/scenarios/ws-quote.js` 중 무엇을 그대로 재사용하고 무엇을 hotkey 전용으로 복제/확장할지 결정한다.
  - “기존 websocket 연결/lag 테스트” 와 “subscribe-time hot-key 재현 테스트” 의 목적을 분리한다.

  **Must NOT do**:
  - 기존 websocket 스트레스 테스트 의미를 hotkey 테스트로 덮어쓰지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 현재 파일 역할과 분리 포인트를 짚는 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 5, 6, 9
  - **Blocked By**: None

  **References**:
  - `k6/ws-main.js` - 현재 websocket 진입점
  - `k6/lib/ws-config.js` - websocket profile 구조
  - `k6/lib/ws-metrics.js` - websocket metrics 구조
  - `k6/scenarios/ws-quote.js` - 현재 websocket quote 흐름

  **Acceptance Criteria**:
  - [ ] 재사용/복제/분리 대상이 파일 단위로 정리되어 있다.
  - [ ] 기존 websocket 부하테스트와 hotkey 테스트의 목적 차이가 문장으로 분리되어 있다.

  **QA Scenarios**:
  ```
  Scenario: 분리 경계 검증
    Tool: Bash (read 검증)
    Preconditions: 경계 정의 초안 존재
    Steps:
      1. ws-main/ws-config/ws-metrics/ws-quote 각각에 대해 재사용 또는 분리 방침이 적혀 있는지 확인한다.
      2. 이유가 각 파일별로 설명되어 있는지 확인한다.
    Expected Result: 구현자가 임의로 섞지 않아도 된다.
    Evidence: .sisyphus/evidence/task-3-boundary-map.txt

  Scenario: 목적 분리 검증
    Tool: Bash (read 검증)
    Preconditions: 목적 비교 표 존재
    Steps:
      1. 기존 websocket 테스트의 목표와 hotkey 테스트의 목표가 따로 적혀 있는지 확인한다.
      2. fanout 테스트와 hotkey 테스트가 동일한 것으로 설명되지 않는지 확인한다.
    Expected Result: 테스트 목적이 혼동되지 않는다.
    Evidence: .sisyphus/evidence/task-3-purpose-separation.txt
  ```

  **Commit**: NO

- [ ] 4. 실행 UX 및 `k6/run.sh` 연동 방침 정의

  **What to do**:
  - 사용자가 “원래 하던 방식이랑 똑같이” 쓸 수 있도록 top-level `k6/run.sh` 에 hotkey 선택지를 넣을지 결정한다.
  - 기본 방침은 다음과 같이 둔다:
    - 기존 `k6/run.sh` 는 유지
    - 새 메뉴로 `Hotkey WebSocket 테스트` 추가
    - 내부적으로는 `k6/hotkey/main.js` 및 `k6/hotkey/.env.*` 를 사용
  - 동시에 직접 실행 명령도 병행 지원한다.

  **Must NOT do**:
  - hotkey 테스트만 별도 전혀 다른 UX로 만들어 기존 흐름을 깨지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: UX/운영 방침 문서화 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 7, 8, 10, 11
  - **Blocked By**: None

  **References**:
  - `k6/run.sh` - 현재 대화형 실행 UX
  - `k6/README.md` - 현재 실행 예시

  **Acceptance Criteria**:
  - [ ] run.sh 통합 여부와 방식이 명시되어 있다.
  - [ ] 직접 명령 실행 방식도 함께 문서화되어 있다.

  **QA Scenarios**:
  ```
  Scenario: runner UX 일관성 검증
    Tool: Bash (read 검증)
    Preconditions: runner 방침 초안 존재
    Steps:
      1. 기존 run.sh 유지 방침이 적혀 있는지 확인한다.
      2. hotkey 선택지 추가 방식이 문서화되어 있는지 확인한다.
    Expected Result: 기존 사용자 경험이 보존된다.
    Evidence: .sisyphus/evidence/task-4-runner-ux.txt

  Scenario: 직접 실행 경로 검증
    Tool: Bash (read 검증)
    Preconditions: 실행 예시 섹션 존재
    Steps:
      1. run.sh 방식 외에 직접 k6 run 명령 예시가 있는지 확인한다.
      2. env 파일과 entrypoint 경로가 명시되어 있는지 확인한다.
    Expected Result: runner 없이도 실행 가능하다.
    Evidence: .sisyphus/evidence/task-4-direct-run.txt
  ```

  **Commit**: NO

- [ ] 5. hotkey 전용 entrypoint/config/env/profile 설계

  **What to do**:
  - `k6/hotkey/main.js` 를 중심으로 기존 `k6/ws-main.js` 와 비슷한 진입 패턴을 설계한다.
  - `k6/hotkey/lib/config.js` 또는 동등 구조로 hot-key 전용 profile/threshold를 설계한다.
  - `.env.hotkey-smoke`, `.env.hotkey-ramp`, `.env.hotkey-stress` 등 기존 스타일과 맞는 프로파일 세트를 설계한다.
  - `setup()` 단계에서 기존 credential/token bootstrap 재사용 여부를 명확히 한다.

  **Must NOT do**:
  - 기존 websocket profile과 이름/역할이 충돌하게 만들지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 실행 진입점과 profile 시스템이 전체 UX를 결정한다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 8, 9, 10, 11
  - **Blocked By**: 1, 2, 3

  **References**:
  - `k6/ws-main.js` - websocket 진입점 패턴
  - `k6/lib/ws-config.js` - profile/threshold 패턴
  - `k6/lib/data.js` - env/data/bootstrap 패턴
  - `k6/lib/auth.js` - token issuance 패턴

  **Acceptance Criteria**:
  - [ ] hotkey 전용 entrypoint/config/env 구성이 경로까지 포함해 설계되어 있다.
  - [ ] profile 이름과 역할이 기존 websocket profile과 충돌하지 않는다.
  - [ ] token bootstrap 흐름이 기존 방식과 일관된다.

  **QA Scenarios**:
  ```
  Scenario: hotkey entrypoint/profile 설계 검증
    Tool: Bash (read 검증)
    Preconditions: entrypoint/profile 초안 존재
    Steps:
      1. main.js, config.js, env 파일 경로가 모두 정의되어 있는지 확인한다.
      2. 각 profile의 목적이 smoke/ramp/stress 수준으로 구분되어 있는지 확인한다.
    Expected Result: 기존과 같은 방식으로 profile-driven 실행이 가능하다.
    Evidence: .sisyphus/evidence/task-5-hotkey-profiles.txt

  Scenario: bootstrap 일관성 검증
    Tool: Bash (read 검증)
    Preconditions: setup/auth section 초안 존재
    Steps:
      1. 기존 token issuance 흐름 재사용 여부가 명시되어 있는지 확인한다.
      2. 별도 secret 하드코딩 계획이 없는지 확인한다.
    Expected Result: 인증 bootstrap 방식이 기존 k6와 일관된다.
    Evidence: .sisyphus/evidence/task-5-bootstrap.txt
  ```

  **Commit**: YES
  - Message: `test(k6-hotkey): add dedicated hotkey profiles and entrypoint`
  - Files: `k6/hotkey/main.js`, `k6/hotkey/lib/*`, `k6/hotkey/.env.*`
  - Pre-commit: k6 script syntax + project baseline test command

- [ ] 6. hotkey 전용 metrics 및 scenario 설계

  **What to do**:
  - `k6/hotkey/scenarios/*` 에서 subscribe-time hot-key 재현용 흐름을 정의한다.
  - 기존 `ws-quote.js` 의 연결/auth/subscribe 흐름을 참고하되, 아래 지표를 hot-key 목적에 맞게 강화한다:
    - subscribe ack latency
    - initial snapshot arrival latency
    - missing snapshot count
    - disconnect/error rate
    - optional watcher/register 관련 신호
  - 동일 종목 집중 / 분산 종목 baseline / subscribe-unsubscribe churn 비교 시나리오를 설계한다.

  **Must NOT do**:
  - `ws_delivery_lag_ms` 같은 fanout 중심 지표만으로 hot-key를 판단하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 시나리오와 메트릭이 hot-key 진단의 핵심이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 7, 9, 10
  - **Blocked By**: 1, 3

  **References**:
  - `k6/scenarios/ws-quote.js` - 현재 websocket quote 시나리오 기반
  - `k6/lib/ws-metrics.js` - 현재 websocket metric 패턴
  - `.sisyphus/plans/k6-hot-key-websocket-test.md` - 기존 hot-key 테스트 의도 및 관측 목표

  **Acceptance Criteria**:
  - [ ] hot-key 전용 metric 목록이 기존 fanout metric과 구분되어 있다.
  - [ ] hot-key/ baseline / churn 시나리오가 정의되어 있다.
  - [ ] subscribe-time 병목을 직접 읽을 수 있는 지표가 포함된다.

  **QA Scenarios**:
  ```
  Scenario: hotkey metric 설계 검증
    Tool: Bash (read 검증)
    Preconditions: metrics/scenario 초안 존재
    Steps:
      1. subscribe ack, initial snapshot, disconnect/error 관련 지표가 정의되어 있는지 확인한다.
      2. fanout lag만 주지표로 사용하지 않는지 확인한다.
    Expected Result: 메트릭이 hot-key 진단 목적에 맞다.
    Evidence: .sisyphus/evidence/task-6-hotkey-metrics.txt

  Scenario: 비교 시나리오 설계 검증
    Tool: Bash (read 검증)
    Preconditions: 시나리오 매트릭스 존재
    Steps:
      1. same-topic 집중 시나리오가 있는지 확인한다.
      2. distributed baseline과 churn control이 함께 있는지 확인한다.
    Expected Result: hot-key 여부를 비교 해석할 수 있다.
    Evidence: .sisyphus/evidence/task-6-scenario-matrix.txt
  ```

  **Commit**: YES
  - Message: `test(k6-hotkey): add websocket subscribe hotkey scenarios`
  - Files: `k6/hotkey/scenarios/*`, `k6/hotkey/lib/metrics*`
  - Pre-commit: k6 script syntax + project baseline test command

- [ ] 7. hotkey 전용 README / 보고 / 런북 설계

  **What to do**:
  - `k6/hotkey/README.md` 에 목적, 범위, 실행 순서, env 변수, 프로파일, 예시 명령을 정리한다.
  - 기존 README 스타일을 따라 summary/report 흐름을 정의한다.
  - 결과 저장 위치(`k6/hotkey/reports/` vs 기존 `k6/reports/`)를 결정하고 근거를 남긴다.

  **Must NOT do**:
  - 기존 README 내용을 중복 복붙만 하고 hot-key 특화 설명을 빼먹지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 사용자-facing 문서와 운영 가이드 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 10, 11
  - **Blocked By**: 1, 2, 4, 6

  **References**:
  - `k6/README.md` - 문서 스타일 기준선
  - `k6/ws-main.js` - summary 출력 패턴
  - `k6/run.sh` - 사용자 실행 흐름

  **Acceptance Criteria**:
  - [ ] hotkey README만 읽어도 실행과 해석 흐름을 이해할 수 있다.
  - [ ] env/profile/명령 예시가 포함되어 있다.
  - [ ] 보고 파일 위치와 이유가 적혀 있다.

  **QA Scenarios**:
  ```
  Scenario: hotkey README 완전성 검증
    Tool: Bash (read 검증)
    Preconditions: README 초안 존재
    Steps:
      1. 목적, 범위, env 변수, 프로파일, 실행 예시가 모두 있는지 확인한다.
      2. 기존 k6 README 스타일과 크게 어긋나지 않는지 확인한다.
    Expected Result: 기존 사용자도 낯설지 않게 읽을 수 있다.
    Evidence: .sisyphus/evidence/task-7-readme.txt

  Scenario: report 흐름 검증
    Tool: Bash (read 검증)
    Preconditions: reporting section 초안 존재
    Steps:
      1. summary output과 report 저장 위치가 정의되어 있는지 확인한다.
      2. 파일명 규칙 또는 저장 방식이 문서화되어 있는지 확인한다.
    Expected Result: 실행 후 결과 보관 방식이 모호하지 않다.
    Evidence: .sisyphus/evidence/task-7-reporting.txt
  ```

  **Commit**: YES
  - Message: `docs(k6-hotkey): add hotkey package usage guide`
  - Files: `k6/hotkey/README.md` 및 관련 문서
  - Pre-commit: 문서 링크/명령 검토

- [ ] 8. 상위 `k6/run.sh` 통합 설계

  **What to do**:
  - top-level `k6/run.sh` 에 hotkey 전용 메뉴를 추가하는 설계를 만든다.
  - 기존 REST / WebSocket / WS 모니터링 흐름과 충돌하지 않도록 분기 구조를 설계한다.
  - hotkey용 env 파일/entrypoint를 자동 선택하는 흐름을 정의한다.

  **Must NOT do**:
  - run.sh 전체를 다시 쓰는 대규모 리팩터링을 하지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 사용자 UX를 유지하면서 안전하게 통합해야 한다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 10, 11
  - **Blocked By**: 2, 4, 5

  **References**:
  - `k6/run.sh` - 현재 메뉴/분기 구조
  - `k6/.env.ws-*` - 현재 websocket env 파일 운용 방식

  **Acceptance Criteria**:
  - [ ] run.sh 에서 hotkey 선택지가 어디에 들어가는지 명확하다.
  - [ ] env/entrypoint 연결 방식이 충돌 없이 정의되어 있다.
  - [ ] 기존 사용자 흐름을 깨지 않는 변경 범위로 제한되어 있다.

  **QA Scenarios**:
  ```
  Scenario: run.sh 통합 포인트 검증
    Tool: Bash (read 검증)
    Preconditions: run.sh 통합 설계 초안 존재
    Steps:
      1. 새 메뉴 위치와 분기 흐름이 정의되어 있는지 확인한다.
      2. 기존 메뉴 1~3 흐름을 깨지 않는지 확인한다.
    Expected Result: hotkey 옵션이 추가돼도 UX가 자연스럽다.
    Evidence: .sisyphus/evidence/task-8-runsh-integration.txt

  Scenario: env 연결 검증
    Tool: Bash (read 검증)
    Preconditions: env wiring 초안 존재
    Steps:
      1. 선택한 hotkey 프로파일이 어떤 env 파일을 읽는지 정의되어 있는지 확인한다.
      2. 실행되는 k6 entrypoint 경로가 문서화되어 있는지 확인한다.
    Expected Result: runner 동작이 예측 가능하다.
    Evidence: .sisyphus/evidence/task-8-env-wiring.txt
  ```

  **Commit**: YES
  - Message: `feat(k6): wire hotkey package into runner`
  - Files: `k6/run.sh` 및 관련 env/entrypoint 참조
  - Pre-commit: 쉘 문법 검증 + 실행 경로 검토

- [ ] 9. 일반 websocket 테스트와 hotkey 테스트의 책임 분리 검증 설계

  **What to do**:
  - 기존 websocket 테스트는 “연결 수/lag/fanout 건강도 확인”, hotkey 테스트는 “subscribe 시점 snapshot/cache pressure 재현” 으로 책임을 분리한다.
  - 두 테스트가 동시에 존재할 때 어떤 경우에 어떤 것을 실행해야 하는지 선택 기준을 문서화한다.

  **Must NOT do**:
  - 둘 다 사실상 같은 테스트가 되게 만들지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 테스트 포트폴리오 설계 문제다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: 10
  - **Blocked By**: 3, 5, 6

  **References**:
  - `k6/scenarios/ws-quote.js` - 기존 websocket 목적
  - `.sisyphus/plans/k6-hot-key-websocket-test.md` - hot-key 재현 목표

  **Acceptance Criteria**:
  - [ ] 기존 websocket 테스트와 hotkey 테스트의 실행 목적이 표로 분리되어 있다.
  - [ ] 어떤 장애 가설에 어떤 테스트를 써야 하는지 적혀 있다.

  **QA Scenarios**:
  ```
  Scenario: 테스트 책임 분리 검증
    Tool: Bash (read 검증)
    Preconditions: 테스트 포지셔닝 표 존재
    Steps:
      1. 기존 websocket 테스트 목표와 hotkey 테스트 목표가 구분되어 있는지 확인한다.
      2. fanout 문제와 hotkey 문제에 대한 추천 테스트가 다르게 적혀 있는지 확인한다.
    Expected Result: 테스트 포트폴리오가 중복되지 않는다.
    Evidence: .sisyphus/evidence/task-9-test-positioning.txt

  Scenario: 선택 기준 검증
    Tool: Bash (read 검증)
    Preconditions: decision guide 존재
    Steps:
      1. 어떤 증상일 때 어떤 테스트를 돌릴지 기준이 있는지 확인한다.
      2. ambiguous case에 대한 fallback 가이드가 있는지 확인한다.
    Expected Result: 운영자가 테스트를 잘못 선택할 가능성이 줄어든다.
    Evidence: .sisyphus/evidence/task-9-decision-guide.txt
  ```

  **Commit**: NO

- [ ] 10. 최종 handoff 및 증거 패키지 설계

  **What to do**:
  - 구현자가 바로 작업할 수 있도록 최종 패키지에 다음을 포함한다:
    - 최종 디렉터리 트리
    - 파일별 책임
    - 실행 예시
    - env/profile 목록
    - run.sh 통합 방식
    - metrics/scenario 역할
    - README/reporting 위치
  - 대화 맥락 없이도 작업 가능한 수준으로 self-contained 하게 만든다.

  **Must NOT do**:
  - 중요한 결정이 대화에만 남게 두지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 최종 handoff 문서화 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: 11, FINAL
  - **Blocked By**: 2, 5, 6, 7, 8, 9

  **References**:
  - Tasks 2, 5, 6, 7, 8, 9 산출물

  **Acceptance Criteria**:
  - [ ] 최종 구조와 실행 흐름이 self-contained 하다.
  - [ ] 구현자가 대화 없이도 착수할 수 있다.

  **QA Scenarios**:
  ```
  Scenario: handoff 완전성 검증
    Tool: Bash (read 검증)
    Preconditions: 최종 handoff 초안 존재
    Steps:
      1. 디렉터리 트리, 파일 책임, 실행 명령, env/profile, run.sh 통합 내용이 모두 있는지 확인한다.
      2. 대화 히스토리 의존 문장이 없는지 확인한다.
    Expected Result: 구현자가 바로 작업을 시작할 수 있다.
    Evidence: .sisyphus/evidence/task-10-handoff.txt

  Scenario: 누락 결정 검증
    Tool: Bash (read 검증)
    Preconditions: final package 존재
    Steps:
      1. 남은 [DECISION NEEDED] 가 있는지 확인한다.
      2. 있으면 눈에 띄게 표시되어 있는지 확인한다.
    Expected Result: 숨은 불확실성이 없다.
    Evidence: .sisyphus/evidence/task-10-decisions.txt
  ```

  **Commit**: NO

- [ ] 11. commit 단위 및 실행 순서 설계

  **What to do**:
  - 구현 시 commit 을 원자적으로 나눈다:
    1. hotkey package skeleton
    2. hotkey scenario/config/metrics
    3. run.sh wiring
    4. docs/reporting
  - 각 commit 의 검증 명령과 영향을 받는 파일 묶음을 명시한다.

  **Must NOT do**:
  - 구조/시나리오/runner/docs 를 한 번에 거대한 단일 commit 으로 묶지 않는다.

  **Recommended Agent Profile**:
  - **Category**: `writing`
    - Reason: 실행 순서와 변경 단위 명확화 작업이다.
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: FINAL
  - **Blocked By**: 4, 5, 7, 8, 10

  **References**:
  - 전체 계획 산출물

  **Acceptance Criteria**:
  - [ ] commit 단위가 원자적이다.
  - [ ] 각 commit 에 필요한 검증 명령이 있다.

  **QA Scenarios**:
  ```
  Scenario: commit 전략 검증
    Tool: Bash (read 검증)
    Preconditions: commit 계획 초안 존재
    Steps:
      1. 최소 3개 이상의 원자적 commit 단위가 정의되어 있는지 확인한다.
      2. 각 commit 에 파일 범위와 검증 명령이 적혀 있는지 확인한다.
    Expected Result: 구현 및 리뷰가 쉬운 변경 단위가 된다.
    Evidence: .sisyphus/evidence/task-11-commit-plan.txt

  Scenario: 실행 순서 검증
    Tool: Bash (read 검증)
    Preconditions: execution order 존재
    Steps:
      1. skeleton -> scenario/config -> runner -> docs 순으로 흐름이 정리되어 있는지 확인한다.
      2. 선후관계가 뒤엉키지 않았는지 확인한다.
    Expected Result: 구현 순서가 자연스럽다.
    Evidence: .sisyphus/evidence/task-11-execution-order.txt
  ```

  **Commit**: NO

---

## Final Verification Wave

- [ ] F1. **계획 준수 감사** — `oracle`
  `k6/hotkey` 분리, 기존 방식 정렬, run.sh UX 유지, websocket hot-key 목적 유지가 모두 반영되었는지 확인한다.

- [ ] F2. **구조/품질 리뷰** — `unspecified-high`
  중복 복사, 과도한 구조 분리, 불필요한 상위 `k6` 리팩터링이 없는지 검토한다.

- [ ] F3. **실행 walkthrough 검증** — `unspecified-high`
  사용자가 기존처럼 `run.sh` 또는 직접 `k6 run` 방식으로 자연스럽게 실행 가능한지 시뮬레이션한다.

- [ ] F4. **범위 충실도 점검** — `deep`
  이 계획이 “k6 hotkey 패키지 분리와 기존 방식 정렬” 에만 집중하고 있는지 확인한다.

---

## Commit Strategy

- **1**: `test(k6-hotkey): add dedicated hotkey package skeleton`
- **2**: `test(k6-hotkey): add websocket subscribe hotkey scenarios`
- **3**: `feat(k6): wire hotkey package into runner`
- **4**: `docs(k6-hotkey): add hotkey package usage guide`

---

## Success Criteria

### Verification Commands
```bash
k6 run k6/hotkey/main.js
# Expected: hotkey 전용 entrypoint가 기존 websocket 테스트와 유사한 summary 형식으로 실행된다.

bash k6/run.sh
# Expected: 기존 메뉴 흐름을 유지하면서 hotkey 테스트 선택지가 보인다.
```

### Final Checklist
- [ ] `k6/hotkey` 전용 패키지 구조가 정의되어 있다.
- [ ] 기존 websocket 테스트와 hotkey 테스트의 책임이 구분되어 있다.
- [ ] 기존과 유사한 profile/env/runner 사용 흐름이 정의되어 있다.
- [ ] hot-key 전용 scenario/metrics가 fanout 테스트와 구분된다.
- [ ] README/runbook/report 흐름이 문서화되어 있다.
