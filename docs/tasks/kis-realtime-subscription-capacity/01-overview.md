# KIS 실시간 구독 용량 및 실패 처리 개선 Overview

## 연결된 Issue

- GitHub Issue: [#10 fix: KIS 실시간 구독 용량 및 실패 처리 개선](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/10)

## 작업 배경과 실제 증상

운영 서버 재시작 과정에서 다음 로그가 확인됐다.

```text
2026-08-21 04:12:25.315 ERROR [market-scheduler-3] [KisConnectionPool]
구독 가능한 KIS WebSocket 세션이 없습니다. - stockId: 3249, symbol: 032680

2026-08-21 04:12:31.279 WARN [market-scheduler-1] [KisSubscriptionSynchronizer]
현재 노드의 KIS WebSocket 세션 용량이 보유 종목 수보다 부족합니다.
보유 종목 수: 7384, 세션 용량: 0 (세션: 0개, 세션당 최대: 41)

2026-08-21 04:13:10.604 INFO [market-scheduler-4] [KisCredentialAllocator]
Allocated KIS credential lock
```

새 노드는 이전 노드가 점유하던 Redis Credential 락이 만료되거나 해제되기 전까지 KIS Credential을 할당받지 못한다. 기본 Credential 락 TTL은 60초이고 갱신 주기는 20초다. 위 사례에서는 세션이 0개인 상태에서 구독 동기화가 먼저 실행됐고 약 39초 뒤 Credential 락이 할당됐다.

Credential 할당 지연과 별개로 실시간 구독 대상이 7,384개라는 구조적 용량 문제도 존재한다. 현재 세션당 구독 한도는 41개이며 설정 가능한 두 Credential을 모두 한 노드가 할당받아도 최대 용량은 82개다.

## 현재 구현과 확인된 사실

1. `HoldingStockProjectionRebuildService`는 애플리케이션 시작 시 `amount > 0`인 모든 자산의 distinct `stockId`를 Redis의 `market:holding:stock-ids` projection으로 재구축한다.
2. `KisSubscriptionSynchronizer`는 예약 종목, 전체 보유 종목과 활성 watcher 종목을 합쳐 실시간 구독 후보로 사용한다.
3. 보유 종목은 보호 대상으로 분류되고 quota 제한 없이 Phase 1에서 전부 구독을 시도한다.
4. watcher와 예약 종목에 적용되는 노드 quota는 `활성 종목 수 / 활성 노드 수`와 현재 노드의 세션 용량 중 작은 값으로 계산된다.
5. Credential은 Redis 락으로 노드에 배타 할당되므로 노드 수만 늘려도 전체 구독 가능량은 늘지 않는다. 전체 상한은 유효 Credential 수와 세션당 한도에 의해 결정된다.
6. `KisConnectionPool.subscribe()`는 연결된 여유 세션이 없으면 로그와 메트릭만 남기고 정상 반환한다.
7. 상위 스케줄러는 `subscribe()`가 정상 반환하면 실제 구독 여부를 확인하지 않고 `subscriptionOrder`에 추가하고 성공으로 집계한다.
8. 다음 동기화 주기에 실제 구독 목록과 로컬 순서를 조정하면서 실패 종목을 다시 시도할 수 있어 동일 오류가 반복될 수 있다.
9. 장 상태 계산은 `Asia/Seoul`을 명시적으로 사용한다. 컨테이너 로그가 UTC라면 로그의 `04:12`는 한국 시각 `13:12`로 장중이다.

## 해결해야 하는 문제

1. 전체 KIS WebSocket 용량 안에서 실제로 실시간성이 필요한 종목만 선택해야 한다.
2. 예약, 현재 접속 또는 감시 중인 종목, 보유 종목 사이의 구독 우선순위와 초과 시 제외 정책을 명시해야 한다.
3. 세션이 준비되지 않은 동안 종목별 구독을 대량으로 시도하지 않아야 한다.
4. 세션 부재, 용량 초과, 연결 끊김과 전송 실패를 상위 계층에 명시적으로 전달해야 한다.
5. 실제로 성공하지 않은 구독을 로컬 순서와 분산 소유권에 남기지 않아야 한다.
6. Credential 인계 중 일시적 세션 부재와 지속적인 용량 부족을 로그와 메트릭에서 구분해야 한다.
7. 운영자가 현재 Credential 수, 연결 세션 수, 실제 용량, 구독 수와 탈락 종목 수를 함께 확인할 수 있어야 한다.

## 변경 범위

- market 모듈의 실시간 구독 후보 선정과 우선순위 정책
- 연결된 세션의 실제 용량을 반영한 노드별 quota
- `MarketDataStreamPort` 및 KIS adapter의 구독 성공·실패 계약
- Credential 및 WebSocket 세션 준비 상태에서의 동기화 동작
- 실패 또는 탈락 종목의 분산 소유권과 로컬 상태 정리
- 용량 부족, 준비 중, 연결 실패와 구독 실패 관측 지표 및 로그
- 단일 노드와 다중 노드의 구독 용량·소유권 테스트

## 제외 범위

- 프론트엔드 UI 변경
- 종목 캔들 조회 개선
- 뉴스 수집 및 배치 검증
- 무중단 배포
- KIS Credential 추가 구매 또는 외부 계정 변경
- 운영 자산, 포트폴리오 또는 보유 데이터 삭제
- WebSocket에서 제외된 모든 종목을 위한 대체 현재가 조회 정책의 전면 개편
- KIS 외 다른 실시간 시세 제공자 도입

## 예상되는 위험과 제약

1. 현재 구성으로 동시에 실시간 구독할 수 있는 종목 수는 전체 보유 종목보다 매우 작다.
2. 실시간 구독에서 제외된 종목은 기존 캐시나 REST 조회 결과에 의존하므로 가격 최신성이 낮을 수 있다.
3. 예약 주문과 실제 접속 사용자의 화면 등 실시간성이 높은 기능을 어떤 순서로 보호할지 사용자 결정이 필요하다.
4. 우선순위가 같은 종목이 용량을 초과할 때 FIFO, 최근 활동 또는 고정 정렬 중 어떤 정책을 사용할지 결정해야 한다.
5. Credential 락 인계 시간을 공격적으로 줄이면 이전 노드와 새 노드가 같은 Credential을 동시에 사용할 위험이 증가한다.
6. 다중 노드 환경에서는 로컬 세션 용량뿐 아니라 Credential의 전체 배타 할당과 종목별 소유권을 함께 유지해야 한다.
7. 실패 계약 변경은 mock adapter와 관련 테스트에도 동일하게 반영해야 한다.
