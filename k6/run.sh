#!/usr/bin/env bash

if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

VENV_DIR="${K6_VENV_DIR:-.venv}"
VENV_PYTHON="$VENV_DIR/bin/python3"
REQUIREMENTS_FILE="k6/requirements.txt"
REQUIREMENTS_STAMP="$VENV_DIR/.k6_requirements.sha256"

compute_requirements_hash() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$REQUIREMENTS_FILE" | awk '{print $1}'
    return
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$REQUIREMENTS_FILE" | awk '{print $1}'
    return
  fi
  python3 - <<PY
import hashlib
from pathlib import Path
print(hashlib.sha256(Path("$REQUIREMENTS_FILE").read_bytes()).hexdigest())
PY
}

ensure_python_runtime() {
  mode="$1"

  if [ ! -f "$REQUIREMENTS_FILE" ]; then
    echo "⚠️  requirements 파일을 찾을 수 없습니다: $REQUIREMENTS_FILE"
    return 1
  fi

  if [ ! -x "$VENV_PYTHON" ]; then
    echo "📦 Python venv가 없어 새로 생성합니다: $VENV_DIR"
    if ! python3 -m venv "$VENV_DIR"; then
      echo "⚠️  venv 생성에 실패했습니다."
      return 1
    fi
  fi

  case "$mode" in
    ws-watch)
      required_modules="websockets"
      ;;
    report)
      required_modules="google.generativeai dotenv"
      ;;
    *)
      required_modules=""
      ;;
  esac

  missing_modules=""
  if [ -n "$required_modules" ]; then
    for module in $required_modules; do
      if ! "$VENV_PYTHON" -c "import $module" >/dev/null 2>&1; then
        missing_modules="$missing_modules $module"
      fi
    done
  fi

  current_hash="$(compute_requirements_hash)"
  installed_hash=""
  if [ -f "$REQUIREMENTS_STAMP" ]; then
    installed_hash="$(cat "$REQUIREMENTS_STAMP" 2>/dev/null)"
  fi

  if [ -n "$missing_modules" ] || [ "$current_hash" != "$installed_hash" ]; then
    echo "📦 Python 의존성 설치 중... ($REQUIREMENTS_FILE)"
    if [ -n "$missing_modules" ]; then
      echo "   누락 모듈:$missing_modules"
    fi
    if [ "$current_hash" != "$installed_hash" ]; then
      echo "   requirements 변경 감지: 재설치 수행"
    fi
    if ! "$VENV_PYTHON" -m pip install -r "$REQUIREMENTS_FILE"; then
      echo "⚠️  의존성 설치에 실패했습니다."
      return 1
    fi
    printf '%s\n' "$current_hash" > "$REQUIREMENTS_STAMP"
  else
    echo "✓ Python 의존성 확인 완료. 추가 설치 없음."
  fi

  return 0
}

echo "============================================================"
echo "  Finvibe k6 부하테스트 실행기"
echo "============================================================"
echo "1) REST API 테스트"
echo "2) WebSocket 테스트  (Mock Provider 필요: SPRING_PROFILES_ACTIVE=local,mock-market)"
echo "3) WebSocket 실시간 모니터링  (Mock Provider 필요: SPRING_PROFILES_ACTIVE=local,mock-market)"
echo "4) Redis 부하 테스트  (Stage 1)"
echo "5) Redis + WebSocket Mixed 테스트  (direct Redis publisher와 함께 권장)"
echo "6) 현실형 증권 플랫폼 시나리오  (세션당 10/20/30종목)"
echo "============================================================"
read -p "테스트 종류 선택 (1~6): " test_type

case $test_type in
  1)
    echo ""
    echo "------------------------------------------------------------"
    echo "  REST API 프로파일"
    echo "------------------------------------------------------------"
    echo "0) quick    │  3초  │  ~3 RPS"
    echo "   코드 배포 후 서버가 살아있는지 바로 확인할 때 사용합니다."
    echo "   CI 파이프라인에서 자동으로 돌리기 좋습니다."
    echo ""
    echo "1) smoke    │  5분  │  ~5 RPS"
    echo "   낮은 부하로 주요 API가 정상 동작하는지 확인합니다."
    echo "   스트레스 테스트 전 사전 검증 용도로 먼저 실행하세요."
    echo ""
    echo "2) ramp10   │ 10분  │  ~3→13 RPS"
    echo "   부하를 천천히 올리면서 응답 시간 변화를 관찰합니다."
    echo "   병목이 생기기 시작하는 시점을 파악할 때 유용합니다."
    echo ""
    echo "3) baseline │ 25분  │  ~10→21 RPS"
    echo "   실제 운영 수준의 부하를 25분 동안 유지합니다."
    echo "   평상시 성능 기준선(SLA 기준)을 측정할 때 사용합니다."
    echo ""
    echo "4) stepup   │ 25분  │  ~20→48 RPS"
    echo "   baseline의 2배 수준까지 단계적으로 부하를 높입니다."
    echo "   트래픽이 늘어날 때 서버가 버티는지 확인하세요."
    echo ""
    echo "5) stress   │ 30분  │  ~30→130 RPS"
    echo "   서버가 한계에 도달하는 지점을 찾습니다."
    echo "   에러율이 급증하거나 응답 시간이 튀는 구간을 확인하세요."
    echo ""
    echo "6) spike    │ 15분  │  ~5→165→5 RPS"
    echo "   30초 만에 트래픽이 16배로 폭증했다가 빠르게 회복하는 시나리오입니다."
    echo "   갑작스러운 이벤트(이벤트 오픈, 미디어 노출)에 대한 대응력을 봅니다."
    echo "------------------------------------------------------------"
    read -p "프로파일 선택 (0~6): " choice
    case $choice in
      0) ENV_FILE="k6/.env.quick" ;;
      1) ENV_FILE="k6/.env.smoke" ;;
      2) ENV_FILE="k6/.env.ramp10" ;;
      3) ENV_FILE="k6/.env.baseline" ;;
      4) ENV_FILE="k6/.env.stepup" ;;
      5) ENV_FILE="k6/.env.stress" ;;
      6) ENV_FILE="k6/.env.spike" ;;
      *)
        echo "잘못된 선택입니다. 0~6 중 하나를 입력하세요."
        exit 1
        ;;
    esac
    ;;
  2)
    echo ""
    echo "------------------------------------------------------------"
    echo "  WebSocket 프로파일  (VU 수 = 동시 연결 수)"
    echo "------------------------------------------------------------"
    echo "0) ws-connect │ 30초  │   5 VU (고정)"
    echo "   연결·인증·10초 유지·정상 종료를 빠르게 검증합니다."
    echo "   WS 서버가 기동되었는지 가장 빠르게 확인할 때 사용하세요."
    echo ""
    echo "1) ws-smoke  │  5분  │  10 VU (고정)"
    echo "   10개 연결을 유지하며 인증·구독·이벤트 수신이 정상인지 확인합니다."
    echo "   WS 기능이 처음 올바르게 동작하는지 확인할 때 먼저 실행하세요."
    echo ""
    echo "2) ws-ramp   │ 15분  │  10 → 50 → 100 VU"
    echo "   연결 수를 단계적으로 늘리며 delivery lag 추이를 관찰합니다."
    echo "   어느 연결 수부터 지연이 커지는지 파악할 때 사용합니다."
    echo ""
    echo "3) ws-stress │ 20분  │  20 → 100 → 300 → 500 → 800 → 1200 → 1800 → 2400 → 3000 VU"
    echo "   연결 수를 빠르게 3000까지 끌어올려 서버의 한계점을 탐색합니다."
    echo "   연결 실패율과 delivery lag가 무너지기 시작하는 구간을 확인하세요."
    echo ""
    echo "4) ws-fast-stress │ 10분 │  20 → 500 → 1500 → 3000 → 5000 → 7500 → 10000 VU"
    echo "   10분 안에 10000까지 빠르게 상승시켜 조기 병목과 연결 붕괴 지점을 찾습니다."
    echo "   짧은 시간 안에 한계 구간을 스캔할 때 사용하세요."
    echo ""
    echo "5) ws-spike  │ 10분  │  20 → 200 → 20 VU"
    echo "   연결이 30초 만에 10배로 급증했다가 다시 줄어드는 시나리오입니다."
    echo "   급격한 연결 폭증 후 lag가 회복되는지, 연결이 끊기지 않는지 봅니다."
    echo ""
    echo "6) ws-connect-dense-10k │ 약 45초 │ 500 VU × 20 connections (총 10,000 연결)"
    echo "   1개 VU가 여러 WebSocket을 동시에 열어 로컬 k6 pthread 한계를 낮춥니다."
    echo "   연결 수용/인증 병목을 먼저 확인할 때 사용하세요."
    echo "------------------------------------------------------------"
    read -p "프로파일 선택 (0~6): " choice
    case $choice in
      0) ENV_FILE="k6/.env.ws-connect" ;;
      1) ENV_FILE="k6/.env.ws-smoke" ;;
      2) ENV_FILE="k6/.env.ws-ramp" ;;
      3) ENV_FILE="k6/.env.ws-stress" ;;
      4) ENV_FILE="k6/.env.ws-fast-stress" ;;
      5) ENV_FILE="k6/.env.ws-spike" ;;
      6) ENV_FILE="k6/.env.ws-connect-dense-10k" ;;
      *)
        echo "잘못된 선택입니다. 0~6 중 하나를 입력하세요."
        exit 1
        ;;
    esac
    ;;
  3)
    echo ""
    set -a
    # shellcheck source=/dev/null
    if [ -f ".env" ]; then
      . ".env"
    fi
    . "k6/.env.ws-connect"
    set +a

    # .env.ws-connect 의 상대 경로를 repo 루트 기준으로 재정의
    export TOKENS_FILE="k6/data/tokens.json"
    export IDS_FILE="k6/data/ids.json"

    read -p "구독할 종목 수 (default: 10): " ws_count
    export WS_SUBSCRIBE_COUNT="${ws_count:-10}"

    if ! ensure_python_runtime "ws-watch"; then
      exit 1
    fi

    echo ""
    echo "▶ WebSocket 실시간 모니터링 시작 (Ctrl+C 로 종료)"
    echo ""
    "$VENV_PYTHON" k6/ws-watch.py
    exit $?
    ;;
  4)
    echo ""
    echo "------------------------------------------------------------"
    echo "  Redis / Hotkey 프로파일"
    echo "------------------------------------------------------------"
    echo "0) redis-latency-smoke       │  1분   │ 500 rps 고정"
    echo "   stage1 sanity: 넓은 stock pool current-price 조회로 단일 Redis 지연 유발 여부 확인"
    echo ""
    echo "1) redis-latency-ramp        │ 10분   │ 1000 → 1500 → 2000 → 2400 rps"
    echo "   stage1 ramp: 전체 캐시 부하 증가에 따라 단일 Redis latency 변화를 관찰"
    echo ""
    echo "2) redis-latency-hold        │ 10분   │ 1100 rps 고정"
    echo "   stage1 hold: 단일 Redis에 지속 부하를 걸어 지연 누적/회복 여부를 확인"
    echo ""
    echo "3) redis-latency-spike       │ 4.5분  │ 1000 → 1400 → 1600 → 1000 rps"
    echo "   stage1 spike: 단일 Redis에 순간 부하를 밀어 넣어 지연/실패/OOM 전조를 탐색"
    echo ""
    echo "4) redis-ramp-discovery      │  7분   │ 300 → 500 → 700 → 850 → 1000 → 1100 → 900 rps"
    echo "   stage1.5 discovery: 즉시 1000을 꽂지 않고 임계 구간을 단계별로 찾는다"
    echo ""
    echo "5) redis-ramp-narrow         │  7분   │ 700 → 800 → 850 → 900 → 950 → 1000 → 850 rps"
    echo "   stage1.6 narrow: discovery 결과를 바탕으로 실제 안전 상한선을 좁혀서 측정"
    echo ""
    echo "6) redis-step-guarded        │ 5.5분  │ 500 고정 → 700 → 900 → 1000 → 700 rps"
    echo "   stage1.7 guarded: 워밍업 뒤 step 입력을 넣어 순간 충격과 절대 한계를 분리"
    echo ""
    echo "7) redis-ramp-fine           │ 14분   │ 500 → 600 → 650 → 700 → 750 → 800 → 650 rps"
    echo "   stage1.8 fine: safe ceiling 후보 구간을 50~100 rps 단위로 촘촘히 측정"
    echo ""
    echo "8) redis-single-mixed-smoke  │  2분   │ 100 WS 세션 유지 + mixed hotkey/churn"
    echo "   direct Redis publisher와 함께 돌려 listener + Redis 조합 정상성을 확인"
    echo ""
    echo "9) redis-single-mixed-ramp   │ 15분   │ 200 → 1000 → 3000 → 5000 WS"
    echo "   hot stock 집중 + 일부 churn 상태에서 listener/Redis 병목 이동을 관찰"
    echo ""
    echo "10) redis-single-mixed-10k   │ 30분   │ 500 → 3000 → 7000 → 10000 WS"
    echo "    10k 유지형 mixed websocket 시나리오. direct Redis publisher와 함께 최종 검증용"
    echo "------------------------------------------------------------"
    echo "※ redis-single-mixed-* 는 반드시 direct Redis price publisher와 함께 실행 권장"
    echo "------------------------------------------------------------"
    read -p "프로파일 선택 (0~10): " choice
    case $choice in
      0) ENV_FILE="k6/hotkey/.env.redis-latency-smoke" ;;
      1) ENV_FILE="k6/hotkey/.env.redis-latency-ramp" ;;
      2) ENV_FILE="k6/hotkey/.env.redis-latency-hold" ;;
      3) ENV_FILE="k6/hotkey/.env.redis-latency-spike" ;;
      4) ENV_FILE="k6/hotkey/.env.redis-ramp-discovery" ;;
      5) ENV_FILE="k6/hotkey/.env.redis-ramp-narrow" ;;
      6) ENV_FILE="k6/hotkey/.env.redis-step-guarded" ;;
      7) ENV_FILE="k6/hotkey/.env.redis-ramp-fine" ;;
      8) ENV_FILE="k6/hotkey/.env.redis-single-mixed-smoke" ;;
      9) ENV_FILE="k6/hotkey/.env.redis-single-mixed-ramp" ;;
      10) ENV_FILE="k6/hotkey/.env.redis-single-mixed-10k" ;;
      *)
        echo "잘못된 선택입니다. 0~10 중 하나를 입력하세요."
        exit 1
        ;;
    esac
    ;;
  5)
    echo ""
    echo "------------------------------------------------------------"
    echo "  Redis + WebSocket Mixed 프로파일"
    echo "------------------------------------------------------------"
    echo "0) redis-single-mixed-smoke  │  2분   │ 100 WS 세션 유지 + mixed hotkey/churn"
    echo "   direct Redis publisher와 함께 돌려 listener + Redis 조합 정상성을 확인"
    echo ""
    echo "1) redis-single-mixed-ramp   │ 15분   │ 200 → 1000 → 3000 → 5000 WS"
    echo "   hot stock 집중 + 일부 churn 상태에서 listener/Redis 병목 이동을 관찰"
    echo ""
    echo "2) redis-single-mixed-10k    │ 30분   │ 500 → 3000 → 7000 → 10000 WS"
    echo "   10k 유지형 mixed websocket 시나리오. direct Redis publisher와 함께 최종 검증용"
    echo "------------------------------------------------------------"
    echo "※ 이 메뉴는 direct Redis price publisher와 함께 실행하는 것을 권장"
    echo "------------------------------------------------------------"
    read -p "프로파일 선택 (0~2): " choice
    case $choice in
      0) ENV_FILE="k6/hotkey/.env.redis-single-mixed-smoke" ;;
      1) ENV_FILE="k6/hotkey/.env.redis-single-mixed-ramp" ;;
      2) ENV_FILE="k6/hotkey/.env.redis-single-mixed-10k" ;;
      *)
        echo "잘못된 선택입니다. 0~2 중 하나를 입력하세요."
        exit 1
        ;;
    esac
    ;;
  6)
    echo ""
    echo "------------------------------------------------------------"
    echo "  현실형 증권 플랫폼 프로파일"
    echo "------------------------------------------------------------"
    echo "0) realistic-10  │ mixed-ramp │ 세션당 10종목, hot ratio 0.25"
    echo "   현실적인 최소선. active user 이전 단계의 baseline 검증용"
    echo ""
    echo "1) realistic-20  │ mixed-ramp │ 세션당 20종목, hot ratio 0.25"
    echo "   일반적인 active user 가정. Redis watcher / fanout 동시 압박용"
    echo ""
    echo "2) realistic-30  │ mixed-10k  │ 세션당 30종목, hot ratio 0.20"
    echo "   heavy user 가정. 매우 강한 fanout + watcher pressure 검증용"
    echo "------------------------------------------------------------"
    echo "※ publisher는 multi-stock steady 5000/8000/12000 msg/s와 함께 실행 권장"
    echo "------------------------------------------------------------"
    read -p "프로파일 선택 (0~2): " choice
    case $choice in
      0) ENV_FILE="k6/hotkey/.env.redis-realistic-10" ;;
      1) ENV_FILE="k6/hotkey/.env.redis-realistic-20" ;;
      2) ENV_FILE="k6/hotkey/.env.redis-realistic-30" ;;
      *)
        echo "잘못된 선택입니다. 0~2 중 하나를 입력하세요."
        exit 1
        ;;
    esac
    ;;
  *)
    echo "잘못된 선택입니다. 1~6 중 하나를 입력하세요."
    exit 1
    ;;
esac

echo ""
echo "▶ 실행: $ENV_FILE"
echo ""

set -a
# shellcheck source=/dev/null
# 루트 .env에서 GEMINI_API_KEY 등 공통 설정 로드
if [ -f ".env" ]; then
  . ".env"
fi
. "$ENV_FILE"
set +a

# 결과 저장 경로 설정
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
# WS 프로파일은 WS_LOAD_PROFILE, HTTP 프로파일은 LOAD_PROFILE 사용
if [ -n "$HOTKEY_LOAD_PROFILE" ]; then
  PROFILE_NAME="$HOTKEY_LOAD_PROFILE"
  K6_ENTRYPOINT="k6/hotkey/main.js"
  REPORTS_DIR="k6/hotkey/reports"
elif [ -n "$WS_DENSE_PROFILE" ]; then
  PROFILE_NAME="$WS_DENSE_PROFILE"
  K6_ENTRYPOINT="k6/ws-connect-dense.js"
  REPORTS_DIR="k6/reports"
elif [ -n "$WS_LOAD_PROFILE" ]; then
  PROFILE_NAME="$WS_LOAD_PROFILE"
  K6_ENTRYPOINT="k6/ws-main.js"
  REPORTS_DIR="k6/reports"
else
  PROFILE_NAME="${LOAD_PROFILE:-smoke}"
  K6_ENTRYPOINT="k6/main.js"
  REPORTS_DIR="k6/reports"
fi
mkdir -p "$REPORTS_DIR"
SUMMARY_FILE="$REPORTS_DIR/${PROFILE_NAME}_${TIMESTAMP}.json"
export SUMMARY_OUTPUT_FILE="$SUMMARY_FILE"

# k6 실행
k6 run "$K6_ENTRYPOINT"
K6_EXIT=$?

echo ""
echo "==============================="
echo "  k6 종료 (exit code: $K6_EXIT)"
echo "==============================="

if [ -f "$SUMMARY_FILE" ]; then
  if [ -z "$GEMINI_API_KEY" ]; then
    echo ""
    echo "❌ GEMINI_API_KEY가 설정되지 않아 markdown 보고서를 생성할 수 없습니다."
    echo "   정책상 k6 테스트는 JSON summary 이후 AI markdown 보고서 생성까지 완료되어야 합니다."
    echo "   다음 중 하나로 다시 실행하세요:"
    echo "   1) export GEMINI_API_KEY=<your-key>"
    echo "   2) .env 파일에 GEMINI_API_KEY 추가"
    echo "   이후 재실행: bash k6/run.sh"
    exit 1
  else
    echo ""
    if ! ensure_python_runtime "report"; then
      echo "❌ Python 런타임 준비에 실패하여 markdown 보고서를 생성할 수 없습니다."
      exit 1
    fi

    echo "📊 Gemini API로 보고서 생성 중... (python: $VENV_PYTHON)"
    "$VENV_PYTHON" k6/report.py "$SUMMARY_FILE" "$PROFILE_NAME"
    REPORT_EXIT=$?
    if [ $REPORT_EXIT -ne 0 ]; then
      echo ""
      echo "❌ AI markdown 보고서 생성에 실패했습니다. (exit code: $REPORT_EXIT)"
      echo "   직접 재실행:"
      echo "   $VENV_PYTHON k6/report.py \"$SUMMARY_FILE\" \"$PROFILE_NAME\""
      exit $REPORT_EXIT
    fi
  fi
else
  echo ""
  echo "❌ 요약 JSON 파일을 찾을 수 없어 markdown 보고서를 생성할 수 없습니다: $SUMMARY_FILE"
  exit 1
fi

exit $K6_EXIT
