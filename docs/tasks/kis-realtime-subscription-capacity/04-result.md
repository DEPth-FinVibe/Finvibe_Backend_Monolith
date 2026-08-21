# KIS 실시간 구독 용량 및 실패 처리 개선 Result

## 연결된 작업

- GitHub Issue: [#10 fix: KIS 실시간 구독 용량 및 실패 처리 개선](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/10)
- Overview: `docs/tasks/kis-realtime-subscription-capacity/01-overview.md`
- Plan: `docs/tasks/kis-realtime-subscription-capacity/02-plan.md`
- Decisions: `docs/tasks/kis-realtime-subscription-capacity/03-decisions.md`

## 실제 변경 내용

### 1. 실시간 구독 후보를 실제 용량 안에서 선정

- `SubscriptionCandidatePlanner`를 추가해 예약 → 활성 watcher → 보유 종목의 tier 순서를 적용했다.
- 여러 tier에 중복된 종목은 가장 높은 tier 하나에만 포함한다.
- 같은 tier에서는 현재 실제 구독 중인 종목을 먼저 유지하고 신규 후보는 `stockId` 오름차순으로 처리한다.
- 보유 종목을 quota 밖에서 모두 구독하던 Phase 1 예외를 제거했다.
- 현재 노드가 quota를 채우면 같은 tier와 낮은 tier의 나머지 후보는 종목별 분산 락 및 adapter 호출 전에 건너뛴다.
- 상위 tier 신규 후보가 들어오면 낮은 tier의 가장 최근 구독을 해제하고 자리를 배정한다.
- quota가 줄면 낮은 tier의 최근 구독부터 해제한다.
- 심볼 조회는 실제 구독 또는 해제 대상에만 수행해 7,384개 전체 심볼을 한 번에 조회·캐시하지 않는다.

### 2. 명시적인 구독 결과 계약 적용

- `MarketDataSubscriptionResult`에 다음 상태를 정의했다.
  - `SUBSCRIBED`
  - `ALREADY_SUBSCRIBED`
  - `NO_SESSION`
  - `NO_CAPACITY`
  - `SEND_FAILED`
- `MarketDataStreamPort.subscribe()`가 명시적 결과를 반환하도록 변경했다.
- KIS connection pool은 세션 부재와 연결 세션 만석을 구분하고 실패 시 종목·심볼 매핑을 롤백한다.
- mock adapter는 기존 무제한 구독 동작을 유지하면서 같은 결과 계약을 구현한다.
- scheduler는 성공 또는 실제 기존 구독으로 확인된 종목만 로컬 순서에 기록한다.
- 실패한 신규 구독은 획득한 분산 소유권을 즉시 해제한다.

### 3. 실제 연결 세션 기준 용량과 준비 Gate 적용

- pool Map 크기가 아니라 실제 연결된 세션만 세션 수와 총 용량에 포함한다.
- 연결 세션 총 용량과 남은 슬롯을 port 계약으로 제공한다.
- 세션 동기화와 닫힌 세션 정리 후 실제 용량이 0이면 후보 repository, 종목 조회, 종목별 분산 락과 구독을 수행하지 않고 해당 주기를 종료한다.
- 기존 5초 동기화 주기에서 Credential과 세션 상태를 다시 확인해 자동 복구한다.
- 같은 Credential의 WebSocket 연결이 진행 중이면 중복 연결을 시작하지 않는다.
- 연결 완료 전에 Credential 할당이 해제됐으면 해당 세션을 pool에 등록하지 않고 종료한다.

### 4. 로그와 메트릭 보강

- 예상된 세션 부재와 용량 부족에서 종목별 ERROR 로그를 제거했다.
- 세션 비가용 진입과 복구는 scheduler 상태 전환 로그로 확인한다.
- 동기화 완료 로그의 `스킵(다른 노드)`, `해제(FIFO)`처럼 실제 원인보다 좁았던 표현을 `스킵`, `해제`로 수정했다.
- KIS AppKey는 세션 등록·실패·종료 로그에서 마스킹한다.
- 다음 지표를 추가하거나 실제 연결 기준으로 보정했다.
  - `kis.sessions.available`
  - `kis.subscriptions.capacity`
  - `kis.subscriptions.remaining`
  - `kis.subscribe.results{result}`
  - `kis.sync.session.unavailable.cycles`
  - `kis.sync.capacity.limited.cycles`
  - `kis.sync.capacity.overflow`

## 결정 반영 결과

### D1 A: 예약 → watcher → 보유 우선순위

- `SubscriptionCandidatePlanner.Tier` 순서와 상위 tier 선점 로직으로 반영했다.
- 보유 종목은 예약과 watcher가 사용하지 않은 quota만 사용한다.

### D2 A: 기존 구독 유지 후 `stockId` 오름차순

- planner가 같은 tier의 실제 구독 종목을 먼저 배치하고 나머지를 `stockId`로 정렬한다.
- Redis collection 반환 순서가 달라도 신규 후보 선정 결과가 바뀌지 않는다.

### D3 A: 명시적 구독 결과 타입

- port와 KIS·mock adapter에 다섯 상태를 적용했다.
- scheduler가 결과와 실제 구독 여부를 모두 확인한 뒤 로컬 상태와 소유권을 갱신한다.

### D4 A: 세션 용량 0이면 즉시 주기 종료

- 후보 조회 전에 Gate를 적용했다.
- scheduler thread에서 연결 완료를 기다리지 않고 다음 5초 주기에 재시도한다.

### D5 A: Credential 락 TTL 60초·갱신 20초 유지

- Credential 락 설정은 변경하지 않았다.
- 락 인계 중 세션 0 상태는 D4 Gate로 처리해 종목별 오류와 잘못된 소유권을 만들지 않는다.

## 수행한 검증

### 핵심 단위 테스트

```bash
./gradlew test \
  --tests depth.finvibe.modules.market.infra.websocket.kis.KisConnectionPoolTest \
  --tests depth.finvibe.modules.market.infra.scheduler.KisSubscriptionSynchronizerTest
```

결과: 성공

검증한 주요 사례:

- 예약 → watcher → 보유 tier 순서
- tier 중복 제거
- 기존 구독 유지와 `stockId` 안정 정렬
- 보유 종목 7,384개에서 구독 및 소유권 시도 41개 제한
- 세션 용량 0에서 후보 repository와 분산 락 미호출
- adapter 실패 시 소유권 해제와 로컬 성공 상태 미기록
- 상위 tier 유입 시 하위 tier 교체
- quota 축소 시 낮은 tier 우선 해제
- mock provider 무제한 구독 유지
- KIS의 `NO_SESSION`, `NO_CAPACITY`, `SUBSCRIBED`, `SEND_FAILED` 결과
- 연결된 세션만 총 용량과 남은 슬롯에 포함

### 컴파일과 전체 회귀 테스트

```bash
./gradlew compileJava
./gradlew test
```

결과: 성공

## 구현 중 확인된 새 사실

1. 활성 watcher 등록·갱신·해제 application port는 존재하지만 운영 API 또는 메시지 호출 경로가 없다. 현재 운영에서는 예약 다음 tier가 대부분 보유 종목이 된다.
2. 전체 보유 종목 projection은 애플리케이션 시작 시 RDB의 양수 자산에서 재구축되므로 7,384개는 단순 Redis 잔여 key가 아니라 현재 자산 source에 기반한 값이다.
3. WebSocket에서 제외된 보유 종목 전체를 stale REST recovery가 보완하지 않는다. stale recovery는 활성 watcher만 대상으로 한다.
4. Credential 락을 유지한 상태에서 재시작 공백 자체는 최장 약 60초 남을 수 있다. 이번 변경은 공백 중 오류 폭증과 상태 오염을 차단하며 락 시간을 줄이지 않는다.

## 운영 영향

- API, DB schema, Redis 현재가 value 형식과 Kafka event 계약 변경은 없다.
- 운영 자산·포트폴리오 데이터와 보유 projection을 삭제하지 않는다.
- 실시간 구독 수는 실제 연결 세션 용량을 넘지 않는다.
- 전체 보유 종목 중 quota 밖 종목은 WebSocket 현재가 이벤트를 받지 못하며 캐시 또는 요청 시 REST 조회의 최신성에 의존한다.
- KIS Credential이 없는 동안 5초마다 세션 상태를 재확인하지만 7,384개 후보와 종목별 분산 락은 조회하지 않는다.
- 배포와 무중단 배포 설정은 이 작업에서 변경하지 않는다.

## 배포 후 확인 절차

1. `Allocated KIS credential lock` 이후 마스킹된 `KIS WebSocket 세션 등록 성공` 로그를 확인한다.
2. Credential 준비 중 `KIS WebSocket 세션이 모두 비가용 상태로 전환되었습니다`가 한 번 기록되고 종목별 `구독 가능한 ... 세션이 없습니다` ERROR가 반복되지 않는지 확인한다.
3. 다음 actuator 지표를 같은 시점에 비교한다.
   - `kis.sessions.available`
   - `kis.subscriptions.capacity`
   - `kis.subscriptions.active`
   - `kis.subscriptions.remaining`
   - `kis.sync.capacity.overflow`
4. `kis.subscriptions.active <= kis.subscriptions.capacity`가 유지되는지 확인한다.
5. 예약 종목이 존재하면 보유 종목보다 먼저 실제 구독되는지 DEBUG 로그 또는 테스트용 제한 환경에서 확인한다.
6. 구독 제외 종목의 단건 현재가 API가 기존 cache miss REST fallback으로 응답하는지 확인한다.

## 롤백 방법

- 애플리케이션 코드 롤백은 구현 커밋 `2a56354`를 revert한다.
- 별도 DB migration, Redis data migration과 설정 변경이 없어 데이터 롤백은 필요하지 않다.
- 롤백하면 보유 종목 quota 예외와 세션 부재 시 실패 오인 문제가 다시 발생하므로, 운영 중 반복 ERROR와 활성 구독 수를 함께 확인해야 한다.

## 남은 한계와 후속 과제

1. 활성 세션이 있을 때는 매 동기화 주기마다 전체 보유 종목 ID projection을 읽고 후보를 정렬한다. 심볼 대량 조회는 제거했지만 후보 수가 더 늘면 별도 최적화가 필요하다.
2. 동일 tier의 후순위 종목은 장중에 선택되지 않을 수 있다. 최근 활동 기반 우선순위는 watcher 호출 경로와 활동 projection을 포함한 별도 작업으로 다룬다.
3. 실제 사용자 화면이 watcher lifecycle을 등록하지 않으므로 활성 사용자 수요를 우선하려면 API 또는 연결 lifecycle 연동 작업이 필요하다.
4. 비정상 종료 후 Credential 재할당 지연을 더 줄이는 작업은 split-brain 위험과 함께 별도로 검토한다.
5. 배포 전 코드 검증은 완료했지만 실제 운영 로그와 actuator 지표 확인은 배포 후 수행해야 한다.
