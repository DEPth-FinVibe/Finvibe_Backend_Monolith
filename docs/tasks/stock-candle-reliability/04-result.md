# 종목 캔들 조회·실시간 분봉 신뢰성 개선 Result

## 연결 항목

- Issue: [#9 fix: 종목 캔들 조회 신뢰성 개선](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/9)
- Branch: `fix/9-stock-candle-reliability`
- Overview: `docs/tasks/stock-candle-reliability/01-overview.md`
- Plan: `docs/tasks/stock-candle-reliability/02-plan.md`
- Decisions: `docs/tasks/stock-candle-reliability/03-decisions.md`

## 구현 결과

### 캔들 조회 장애 격리

- KIS 캔들 조회 결과를 COMPLETE, PARTIAL, FAILED로 구분한다.
- 제공자 실패 또는 비정상 빈 응답을 신규 `isMissing=true`로 저장하지 않는다.
- 실제 캐시가 있으면 반환하고 캐시가 없을 때 `MARKET_CANDLE_TEMPORARILY_UNAVAILABLE` 503을 반환한다.
- 분봉 예상 시각을 평일과 KST 정규장 09:00~15:30으로 제한하며 확인된 휴장일을 제외한다.
- 기존 `isMissing=true`는 coverage로 인정하지 않아 실제 캔들로 복구할 수 있다.

### 실시간 분봉 적재

- KIS WebSocket 체결 이벤트에 개별 `CNTG_VOL`을 전달한다.
- 현재 체결가와 개별 체결량으로 분 단위 open, high, low, close, volume과 value를 집계한다.
- KIS 이벤트의 당일 OHLC와 당일 누적 거래량·거래대금은 분봉 값으로 사용하지 않는다.
- 메모리에서 집계한 dirty 분봉을 기본 5초 주기로 MariaDB에 upsert한다.
- 다음 분의 첫 체결에서 직전 분봉을 즉시 flush하고 정상 종료 시 남은 분봉을 flush한다.
- 오래된 역전 이벤트, 장외 이벤트와 유효하지 않은 체결은 저장하지 않고 사유별 메트릭을 남긴다.
- 저장 실패 시 현재 분봉의 dirty 상태를 유지해 다음 주기에 재시도한다.

### 완료 분봉 REST 보정

- KIS MINUTE REST 조회 상한을 현재 시각의 마지막 완료 분으로 제한한다.
- 현재 진행 중인 분은 WebSocket 집계가 소유해 REST 스냅샷과의 저장 경합을 피한다.
- 완료된 요청 구간은 KIS REST 결과를 다시 조회·저장해 WebSocket 메시지 유실과 재시작 공백을 보정한다.
- REST 결과는 같은 시각의 기존 WebSocket 집계 분봉을 교체하고 API 응답에서도 우선한다.
- KIS 장애 시에는 기존 WebSocket/REST 캐시를 반환하는 fallback을 유지한다.

### 과거 mock 오염 정리

- `docs/operations/stock-candle-rebuild.sql`에 전체 건수 dry-run과 10,000행 단위 삭제 SQL을 제공한다.
- `docs/operations/stock-candle-rebuild.md`에 백업, 부하 관찰, 중단, 재적재와 4971 검증 절차를 제공한다.
- 자동 배포 마이그레이션과 파괴적인 관리자 API는 추가하지 않았다.
- 운영 DB 삭제 SQL은 실행하지 않았다.

## 관측 항목

- `market.candle.realtime.trades`
- `market.candle.realtime.dropped{reason=invalid_trade|outside_session|out_of_order}`
- `market.candle.realtime.flush{result=success|failure,trigger=periodic|minute_boundary}`
- `market.candle.provider.failures{timeframe=...}`
- `market.candle.provider.partial{timeframe=...}`
- `market.candle.cache.fallback{timeframe=...,reason=provider|lock}`
- `market.candle.unavailable{timeframe=...,reason=provider|lock}`

## 검증 결과

통과:

```bash
./gradlew compileJava compileTestJava
./gradlew test --tests "depth.finvibe.modules.market.application.RealtimeMinuteCandleServiceTest" \
  --tests "depth.finvibe.modules.market.application.MarketQueryServiceCandleTest"
./gradlew test
```

검증한 주요 시나리오:

- 동일 분의 여러 체결에 대한 OHLCV와 거래대금 집계
- 다음 분 첫 체결 시 직전 분봉 즉시 저장
- 주기 저장 실패 후 다음 주기 재시도
- KIS 전체 실패·부분 성공·빈 응답과 캐시 fallback
- 기존 결측 마커 복구
- 휴장일 및 장 운영 시간 필터
- 현재 진행 중인 분의 REST 조회 제외
- 완료 분봉의 KIS REST 교체
- 캔들 전용 오류의 HTTP 503 매핑과 500 비노출

## 남은 운영 검증

- 로컬 MariaDB가 실행 중이지 않아 native `insert ... on duplicate key update`의 실제 DB 통합 실행은 수행하지 못했다. 배포 전 스테이징 또는 운영 승인된 검증 환경에서 한 건 upsert와 동일 분 재갱신을 확인해야 한다.
- 변경 버전이 아직 배포되지 않았으므로 `https://finvibe.space`의 MINUTE API와 장중 WebSocket 적재는 검증하지 않았다.
- 운영 SQL 실행 전 DB 백업과 dry-run 결과를 사용자에게 확인받아야 한다.
- D7 B 선택에 따라 전체 캔들 삭제 후 KIS가 제공하지 않는 오래된 분봉은 복구되지 않을 수 있다.

## 제외 및 미수행

- 프론트 UI와 종목명 `로딩 중...` 문제는 변경하지 않았다.
- Batch와 뉴스 수집은 변경·검증하지 않았다.
- 무중단 배포는 구현하지 않았다.
- 배포, 운영 DB 삭제와 원격 PR merge는 수행하지 않았다.
