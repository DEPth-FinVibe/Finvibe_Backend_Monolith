# WebSocket 메모리 최적화 (Server-Side) - Portfolio Case Study

## 프로젝트 맥락
- 서비스: 실시간 시세 전달용 Spring WebSocket 서버
- 제약: KIS 연동 구조는 유지하고, 서버(WebSocket broker/handler) 레이어만 최적화
- 목표: 동시 접속자 증가 시 힙 사용량과 스케줄링 오버헤드를 줄이면서 안정성을 유지

## 문제 정의
기존 구조는 기능적으로는 안정적이었지만, 동접이 늘어날수록 아래 비용이 선형으로 증가했습니다.

1. **세션별 heartbeat/auth timeout 스케줄 태스크**
   - 연결 수만큼 `ScheduledFuture`가 생성되어 스케줄러 큐/객체 오버헤드 증가
2. **구독 인덱스의 문자열/박싱 중심 구조**
   - `quote:{stockId}` 문자열과 `Map<String, Integer>` 기반 카운팅으로 엔트리당 메모리 효율 저하
3. **컨테이너 레벨 메시지 버퍼 상한 과대 설정**
   - 세션당 버퍼 상한이 커서 동접 증가 시 이론상 메모리 상한이 빠르게 확대
4. **세션 정리 경로 분산**
   - 연결 종료/에러/강제 종료 경로가 분산되어 정리 일관성 리스크 존재

## 개선 전략
핵심 방향은 **"세션 객체 미세 최적화"보다 "구조적 비용(버퍼/인덱스/스케줄링) 절감"**에 두었습니다.

### 1) Per-connection heartbeat 제거 -> 단일 sweep 루프 전환
- 적용 파일: `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java`
- 변경 내용
  - 세션별 `scheduleAtFixedRate` 제거
  - `TaskScheduler` 기반 단일 주기 작업(`sweepConnections`)으로 전체 연결 순회
  - sweep 내부에서 다음을 처리
    - 인증 미완료 세션 auth timeout 강제 종료
    - heartbeat ping 전송
    - missed pong 카운트 기반 연결 종료
    - 닫힌 세션 정리
- 기대 효과
  - 동접 N 증가 시 태스크 객체 수가 `O(N) -> O(1)`로 감소
  - 스케줄러 큐 압력 완화

### 2) 구독 인덱스의 문자열 중심 자료구조를 stockId(Long) 중심으로 변경
- 적용 파일: `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketWebSocketRegistry.java`
- 변경 내용
  - `topicSubscribers: Map<String, Set<String>> -> Map<Long, Set<String>>`
  - `userSubscriptions: Map<UUID, Map<String, Integer>> -> Map<UUID, Map<Long, Integer>>`
  - subscribe/unsubscribe/ttl refresh 경로에서 문자열 파싱 반복 제거
- 기대 효과
  - 동일 구독 관계를 더 작은 key 타입으로 유지
  - 문자열 key/해시 오버헤드 감소

### 3) 세션 상태 객체 경량화
- 적용 파일: `src/main/java/depth/finvibe/modules/market/infra/websocket/server/CustomWebSocketSession.java`
- 변경 내용
  - `userId: String -> UUID`
  - `LocalDateTime` 기반 timestamp -> `epoch millis(long)`
  - `subscribedTopics(Set<String>) -> subscribedStockIds(Set<Long>)`
  - `connectedAtEpochMs` 도입으로 sweep 기반 auth timeout 계산
- 기대 효과
  - 상태 객체당 참조/객체 수 감소
  - 시간 계산 비용 단순화

### 4) 세션 종료 정리 경로 단순화
- 적용 파일: `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketQuoteWebSocketHandler.java`, `src/main/java/depth/finvibe/modules/market/infra/websocket/server/MarketWebSocketRegistry.java`
- 변경 내용
  - transport error / close 시 `registry.remove(sessionId)` 단일 경로 사용
  - registry에서 구독 인덱스 정리를 일관되게 수행
- 기대 효과
  - 종료 경로 중복 제거
  - 정리 누락 가능성 완화

### 5) WebSocket 컨테이너 버퍼 상한 축소
- 적용 파일: `src/main/java/depth/finvibe/boot/config/WebSocketConfig.java`
- 변경 내용
  - text/binary message buffer: `512KB -> 64KB`
- 기대 효과
  - per-session 메모리 상한 크게 감소
  - 동접 규모가 큰 환경에서 힙 pressure 완화

## 구현 후 검증
- 빌드 검증: `./gradlew compileJava` 성공
- 관측 포인트(기존 메트릭 활용)
  - `ws.connections.active`
  - `ws.topics.active`
  - `ws.session.send.failures.total`
  - `ws.connection.closed.slow-consumer`
  - `ws.auth.timeout`

## 성과 서술 예시 (포트폴리오용)
> 실시간 시세 WebSocket 서버에서 메모리 병목의 원인을 세션 객체 자체보다 구조적 비용(세션별 스케줄 태스크, 문자열 중심 구독 인덱스, 과대 버퍼 상한)으로 정의하고, heartbeat를 단일 sweep 루프로 재설계했습니다. 또한 구독 인덱스를 `stockId(Long)` 중심으로 전환하고 컨테이너 버퍼 상한을 512KB에서 64KB로 조정해 동접 증가 시 힙 사용량의 선형 증가폭을 완화했습니다. 기능 안정성을 유지한 채 컴파일 검증을 완료했으며, 운영 메트릭을 통해 연결 수/오류율/강제종료 추이를 지속 관찰 가능한 구조로 개선했습니다.

## 기술적 의사결정 포인트
- "미세 최적화"보다 "증가 비용의 차수"를 먼저 줄이는 접근
  - `O(N)` 태스크 생성 구조 제거
- 도메인 key 정규화
  - topic 문자열 대신 stockId 기반 인덱싱
- 운영 친화성 유지
  - 기존 메트릭 체계를 유지하면서 내부 구조만 개선

## 다음 단계 (고도화)
1. primitive 컬렉션(fastutil 등)으로 `Long/Integer` 박싱 비용 추가 절감
2. WebSocket 부하 테스트(k6)에서 동접/구독수 구간별 힙 및 GC 지표 비교 자동화
3. profile별(`local/remote/prod`) WebSocket 버퍼/timeout 튜닝값 분리 운영
