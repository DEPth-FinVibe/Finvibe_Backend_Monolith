# 서버 작업 매뉴얼: 콘솔 텍스트 + Loki용 JSON 로그 분리

## 1) 변경 목표
- 애플리케이션 콘솔(stdout): 사람이 읽기 쉬운 텍스트 로그 유지
- Loki 전송 경로(Alloy): JSON 파일 로그만 수집

현재 애플리케이션은 `prod`에서 아래처럼 동작하도록 변경되어 있습니다.
- 콘솔 appender: 텍스트 패턴
- 파일 appender: JSON (`LOG_JSON_PATH`, 기본 `/app/logs/application-json.log`)

CD 워크플로우도 아래를 수행하도록 변경되어 있습니다.
- 호스트 로그 디렉터리(`LOG_DIR_HOST`, 기본 `/var/log/finvibe-backend`) 생성
- 컨테이너 볼륨 마운트: `${LOG_DIR_HOST}:/app/logs`
- `LOG_JSON_PATH=/app/logs/application-json.log` 주입

## 2) 서버에서 해야 할 일

### 2-1. GitHub Actions 변수 확인
Repository/Environment Variables에 아래 값이 필요합니다.
- `LOG_DIR_HOST`: JSON 로그 파일을 저장할 서버 경로
  - 권장: `/var/log/finvibe-backend`

값을 지정하지 않으면 CD 기본값(`/var/log/finvibe-backend`)이 사용됩니다.

### 2-2. 서버 디렉터리/권한 준비
배포 대상 서버에서 아래 실행:

```bash
sudo mkdir -p /var/log/finvibe-backend
sudo chown root:root /var/log/finvibe-backend
sudo chmod 755 /var/log/finvibe-backend
```

참고:
- 컨테이너는 root로 실행 중이면 추가 권한 작업 없이 파일 생성 가능
- 컨테이너를 non-root로 바꿀 경우, 해당 UID/GID에 맞게 소유권 조정 필요

### 2-3. Alloy가 파일 로그를 읽도록 설정 변경
기존 `loki.source.docker` 대신(또는 병행 시 finvibe 대상은 파일 소스로 전환) 아래 설정 사용:

```hcl
targets = [{
  __path__   = "/var/log/finvibe-backend/application-json.log",
  container  = "finvibe-backend",
  job        = "finvibe-backend"
}]

loki.source.file "finvibe_json_file" {
  targets    = targets
  forward_to = [loki.process.finvibe.receiver]
}

loki.process "finvibe" {
  stage.json {
    expressions = {
      level = "level",
      logger = "logger_name",
      thread = "thread_name",
      app = "app"
    }
  }

  stage.labels {
    values = {
      level = "",
      app = ""
    }
  }

  forward_to = [loki.write.default.receiver]
}

loki.write "default" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}
```

주의:
- `logger`, `thread`는 카디널리티 증가 위험이 있어 label로 올리지 않고 본문 필드로만 유지 권장
- Alloy 실행 위치가 호스트가 아닌 컨테이너라면, Alloy 컨테이너에도 `/var/log/finvibe-backend`를 동일 경로로 마운트해야 함

### 2-4. Alloy 재시작
실행 방식에 맞춰 재시작:

```bash
# systemd 예시
sudo systemctl restart alloy
sudo systemctl status alloy --no-pager
```

또는 docker compose 사용 시 해당 스택 기준으로 Alloy 컨테이너 재기동.

## 3) 배포 후 검증

### 3-1. 서버 파일 생성 확인
```bash
ls -al /var/log/finvibe-backend
tail -n 5 /var/log/finvibe-backend/application-json.log
```

정상이라면 JSON 한 줄 로그가 출력됩니다.

### 3-2. 애플리케이션 콘솔 로그 형식 확인
```bash
docker logs finvibe-backend --tail 20
```

정상이라면 텍스트 패턴 로그(사람이 읽기 쉬운 형식)로 보입니다.

### 3-3. Loki/Grafana 확인
Grafana Explore (Loki)에서:

```logql
{job="finvibe-backend"}
```

에러만 보고 싶다면:

```logql
{job="finvibe-backend", level="ERROR"}
```

## 4) 트러블슈팅
- 파일이 안 생김:
  - `docker inspect finvibe-backend`에서 Mounts 확인
  - 컨테이너 env에 `LOG_JSON_PATH=/app/logs/application-json.log` 존재 확인
- Alloy가 로그를 못 읽음:
  - Alloy 프로세스/컨테이너에서 파일 경로 접근 가능한지 확인
  - Alloy config 문법/재시작 상태 확인
- Loki에 레벨 라벨 없음:
  - `stage.json` 필드명(`level`)과 실제 JSON 키 일치 여부 확인
