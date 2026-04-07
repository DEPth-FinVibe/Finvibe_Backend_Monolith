# Observability — Metrics & Logging

## 스택 구성

```
[메트릭] Spring Boot → Micrometer → /actuator/prometheus ← Prometheus → Grafana
[로그]   Spring Boot(JSON file) → Grafana Alloy → Loki → Grafana
```

---

## Actuator 엔드포인트

| 엔드포인트 | URL | 설명 |
|---|---|---|
| Health | `GET /actuator/health` | 애플리케이션 상태 (liveness/readiness 포함) |
| Prometheus | `GET /actuator/prometheus` | Prometheus 수집용 메트릭 |
| Info | `GET /actuator/info` | 애플리케이션 정보 |

### Health 상세

```
GET /actuator/health
GET /actuator/health/liveness   # liveness probe
GET /actuator/health/readiness  # readiness probe
```

`show-details: never` — 외부에 내부 상태 노출 안 함.

---

## 메트릭 설정

### 공통 태그

모든 메트릭에 아래 태그가 자동 부착됩니다.

| 태그 | 값 |
|---|---|
| `application` | `finvibe-backend-monolith` |
| `environment` | `SPRING_PROFILES_ACTIVE` 환경변수 (기본값: `local`) |

### HTTP 요청 메트릭

퍼센타일 히스토그램이 활성화되어 있어 Prometheus에서 p50/p95/p99 집계가 가능합니다.

```promql
# 전체 요청 수
http_server_requests_seconds_count{application="finvibe-backend-monolith"}

# p99 응답시간
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# 엔드포인트별 에러율
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
  / rate(http_server_requests_seconds_count[5m])
```

---

## Prometheus 설정 (scrape config)

```yaml
scrape_configs:
  - job_name: 'finvibe-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['finvibe-backend:8080']  # Docker 네트워크 내 서비스명
```

---

## Grafana Alloy 설정 (config.alloy)

운영에서는 JSON 로그 파일을 수집해 Loki로 전달합니다.
세부 절차는 `docs/server-log-to-loki-manual.md`를 참고하세요.

```hcl
discovery.docker "containers" {
  host = "unix:///var/run/docker.sock"
}

loki.source.docker "finvibe" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.docker.containers.targets
  forward_to = [loki.write.default.receiver]
  relabeling_rules = loki.relabel.docker.rules
}

loki.relabel "docker" {
  rule {
    source_labels = ["__meta_docker_container_name"]
    target_label  = "container"
  }
}

loki.write "default" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}
```

---

## 로그 포맷

| 프로파일 | 포맷 | 용도 |
|---|---|---|
| `local` | 콘솔 텍스트 | 개발 시 가독성 |
| `prod` | 콘솔 텍스트 + JSON 파일(Logstash 포맷, `app:"finvibe-backend"` 포함) | 콘솔 가독성 + Alloy 파싱 |

### Loki 쿼리 예시

```logql
# finvibe-backend 컨테이너 전체 로그
{container="finvibe-backend"}

# ERROR 레벨만
{container="finvibe-backend"} | json | level="ERROR"

# 특정 키워드 필터
{container="finvibe-backend"} |= "NullPointerException"
```

---

## 검증 체크리스트

- [ ] `GET /actuator/prometheus` → Prometheus 포맷 메트릭 응답
- [ ] `GET /actuator/health` → `{"status":"UP"}` 응답
- [ ] HTTP 요청 발생 후 `http_server_requests_seconds_count` 메트릭 존재
- [ ] Prometheus UI → Targets → `finvibe-backend` job `UP` 상태
- [ ] Grafana → Explore → Loki → `{container="finvibe-backend"}` 로그 조회
- [ ] `prod` 프로파일 실행 시 `/app/logs/application-json.log`(호스트 마운트 포함) 생성 확인

---

## Current-Price Cache 대시보드 해석 기준

### result 라벨 의미

`market.current_price.cache.requests`는 Prometheus에서 보통 아래처럼 보입니다.

- `market_current_price_cache_requests_total{result="hit"}`
- `market_current_price_cache_requests_total{result="miss"}`
- `market_current_price_cache_requests_total{result="market_closed"}`
- `market_current_price_cache_requests_total{result="market_closed_empty"}`

해석 기준:

- `hit`: Redis current-price cache에서 바로 반환
- `miss`: cache에 없어 fallback 경로 사용
- `market_closed`: 장 마감 상태에서 closing price 경로 사용
- `market_closed_empty`: 장 마감 상태인데 closing price도 없음

### 주의

- `market_closed*` 결과가 섞인 상태에서는 **pure cache-hit/miss 테스트가 아닙니다.**
- hit/miss 비율 패널의 분모에 `market_closed*`를 포함하면 해석이 왜곡됩니다.

### 권장 PromQL

#### hit rate

```promql
sum(rate(market_current_price_cache_requests_total{result="hit"}[5m]))
/
sum(rate(market_current_price_cache_requests_total{result=~"hit|miss"}[5m]))
```

#### miss rate

```promql
sum(rate(market_current_price_cache_requests_total{result="miss"}[5m]))
/
sum(rate(market_current_price_cache_requests_total{result=~"hit|miss"}[5m]))
```

#### result별 요청량

```promql
sum by (result) (
  rate(market_current_price_cache_requests_total[5m])
)
```

### latency 해석

#### hit 평균 latency

```promql
sum(rate(market_current_price_cache_read_duration_seconds_sum{result="hit"}[5m]))
/
sum(rate(market_current_price_cache_read_duration_seconds_count{result="hit"}[5m]))
```

#### miss 평균 latency

```promql
sum(rate(market_current_price_cache_read_duration_seconds_sum{result="miss"}[5m]))
/
sum(rate(market_current_price_cache_read_duration_seconds_count{result="miss"}[5m]))
```

### Redis memory usage % 패널 주의

다음 쿼리는 **`redis_memory_max_bytes`가 존재하고 0보다 클 때만** 의미가 있습니다.

```promql
redis_memory_used_bytes / redis_memory_max_bytes * 100
```

만약:

- `redis_memory_max_bytes`가 없거나
- 값이 0이면

퍼센트 패널은 신뢰할 수 없습니다. 이 경우엔 아래 절대값 패널만 사용하세요.

```promql
redis_memory_used_bytes
```

### 운영 해석 원칙

- **요청 성공**과 **cache-hit 검증 성공**은 다릅니다.
- `hit=0`인데 요청이 200으로 끝났다고 해서 cache 전략이 성공했다고 보면 안 됩니다.
- `market_closed*`가 존재하면 장중 current-price cache 실험 결과와 분리해서 해석해야 합니다.
