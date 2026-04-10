# OpenCode Prompt: 운영 서버 우선 수정 항목

## 목적

운영 서버 기준으로, 현재 Redis current-price cache / hotkey 테스트 해석을 왜곡시키는 문제들 중 **운영에서 먼저 고쳐야 할 것만** 우선 수정하도록 지시하는 프롬프트입니다.

---

## 프롬프트

```text
이 프로젝트에서 운영 서버 기준으로 먼저 고쳐야 할 문제만 우선 수정해줘.

컨텍스트:
- current-price cache 관련 Micrometer 메트릭을 추가해둠:
  - market.current_price.cache.requests
  - market.current_price.cache.read.duration
- Grafana에서 다음 문제가 보임:
  - cache hit rate 0%
  - miss / market_closed_empty 결과가 섞임
  - Redis memory usage % 패널 값이 비정상적으로 큼
- k6 hotkey-cache 리포트는 현재 요청 성공/latency만 보고 “PASS”처럼 해석하는 경향이 있어 실제 cache hit 검증 상태와 어긋남
- 장마감에도 mock provider로 테스트 가능한 구조 일부는 이미 들어감

중요:
- 운영 서버에서 먼저 해야 할 수정만 해줘
- 로컬/테스트 전용 개선은 우선순위 뒤로 미뤄도 됨
- 대규모 리팩터링 하지 말고, 운영 해석 정확도와 관측 정확도를 우선해줘

우선 수정 대상(반드시 포함):

1. Grafana / Prometheus 기준으로 hit/miss 해석이 왜곡되지 않도록 정리
   - `market_closed`, `market_closed_empty`가 섞인 상태에서 hit/miss rate를 오해하지 않게 해야 함
   - 문서 또는 운영 가이드에 result별 해석 기준을 명확히 남겨줘

2. Redis memory usage % 계산 문제 해결
   - 현재 패널 값이 비정상적임
   - `redis_memory_max_bytes`가 없거나 0일 때 어떻게 처리할지 포함해서 Grafana 쿼리/가이드를 수정해줘

3. report.py / hotkey 보고서 해석 수정
   - `hit=0`인데도 성공처럼 요약하지 않게 수정
   - `market_closed*` 결과가 섞이면 pure cache-hit 검증이 아니라고 명시하게 수정
   - “요청 성공”과 “cache hit 검증 성공”을 분리해서 서술하게 수정

4. 운영 관측 문서 추가/수정
   - Grafana에서 어떤 패널을 먼저 봐야 하는지
   - hit/miss/market_closed 결과를 어떻게 읽어야 하는지
   - 어떤 경우에 테스트가 “실행 성공”이고, 어떤 경우에 “cache 검증 성공”인지
   - Redis exporter metric이 없는 경우/있는 경우 각각 어떻게 해석해야 하는지

운영 우선순위에서 지금 굳이 안 해도 되는 것:
- 로컬 테스트 provider 확장
- seed script 추가
- 새로운 테스트 시나리오 대규모 확장
- 대시보드 미려화

원하는 산출물:
1. 운영 문서 (docs 또는 적절한 경로)
2. 필요하면 Grafana 쿼리 수정안
3. report.py 수정
4. 변경 파일 목록
5. 운영자가 가장 먼저 봐야 할 패널 3개

작업 방식:
- 이 레포 기준 실제 메트릭 이름만 사용
- Micrometer -> Prometheus naming convention 반영
- 실제 운영 해석 오류를 줄이는 쪽으로 최소 변경
- 수정 후 무엇이 왜 바뀌었는지 간단히 설명
```

---

## 이 프롬프트의 의도

이 프롬프트는 아래 문제를 **운영 관점에서 먼저 해결**하도록 유도합니다.

- hit/miss/result 해석 오류
- Redis memory % 패널 오작동
- AI 보고서의 과도한 성공 판정
- Grafana 관측 기준 부재

즉, **테스트 추가 개발보다 운영 해석 정확도부터 올리는 것**이 목적입니다.
