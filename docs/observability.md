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
