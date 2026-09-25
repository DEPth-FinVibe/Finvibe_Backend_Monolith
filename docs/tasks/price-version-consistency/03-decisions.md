# 시세 버전 기반 현재가·수익률 정합성 확보 Decisions

## D1. `priceVersion` 계산 방식

- 결정일: 2026-09-25
- 검토한 선택지: 인메모리 순번 / Redis Lua에서 순번 발급(키 유지) / Redis Lua에서 순번 발급(hash tag 키 이전) / Redis `INCR` 카운터
- 선택: **Redis Lua에서 순번 발급, 기존 키 이름 유지** (사용자 선택)

### 내용

- `priceVersion = KIS 체결시각(epoch 초) × 10^6 + 같은 초 안 순번`
- 순번은 현재가 저장과 같은 Lua 스크립트에서 발급한다.
  - 저장된 버전의 초보다 새 틱의 초가 크면 순번 0
  - 같은 초면 저장된 버전 + 1
- 발급한 버전을 저장 현재가 JSON과 Pub/Sub·Kafka 이벤트에 똑같이 싣는다.

### 이유

- 재시작하거나 구독 소유 노드가 바뀌어도 단조성이 유지된다.
- 버전에 시각 의미가 남아서 D8의 지연을 초 단위로 읽을 수 있다.
- 키 이름이 그대로라 리스너 스냅샷 경로(`CurrentPriceSnapshotRedisRepository`)에 영향이 없다.

### 정정 (구현 착수 중 확인)

- Plan D1에는 "현재가 키 두 개가 다른 slot이라 Lua로 묶으려면 키 이전이 필요하다"고 적었다. 하지만 실제 키는 이미 `market:current-price:{stock:<id>}`, `market:current-price-updated-at:{stock:<id>}` 형식의 hash tag를 쓰고 있어 같은 slot이다.
- 따라서 키 이름을 유지하면서 두 키와 버전 키 `market:current-price-version:{stock:<id>}`를 Lua 하나로 처리한다. 기존 SET 두 번(왕복 2회)은 Lua 1회로 줄어든다.
- 버전은 JSON을 파싱하지 않고 별도 버전 키로 비교한다. 저장 JSON은 Java가 직렬화한 객체 끝의 `}` 앞에 `,"priceVersion":<v>`를 붙여 완성한다. 가격(BigDecimal) 표현을 Lua `cjson`으로 다시 인코딩하지 않기 위해서다.
- Lua의 숫자는 double이다. 버전(약 1.8×10^15)은 2^53 미만이라 정확하다. 다만 문자열로 바꿀 때는 `tostring` 대신 `string.format('%.0f')`를 쓴다(지수 표기 방지).
