#!/bin/bash

VENV_DIR="${K6_VENV_DIR:-.venv}"
VENV_PYTHON="$VENV_DIR/bin/python3"
REQUIREMENTS_FILE="k6/requirements.txt"

echo "============================================================"
echo "  Finvibe k6 부하테스트 실행기"
echo "============================================================"
echo "0) quick    |  3초   | ~3 RPS    | 연결 확인 전용 (CI 스모크)"
echo "1) smoke    |  5분   | ~5 RPS    | 저부하 사전 검증, 기능 이상 없는지 확인"
echo "2) ramp10   | 10분   | ~13 RPS   | 중간 부하, 완만한 ramp-up"
echo "3) baseline | 25분   | ~21 RPS   | 기준 부하, 일반 운영 수준 재현"
echo "4) stepup   | 25분   | ~48 RPS   | 상향 부하, baseline 2배 수준 점진 증가"
echo "5) stress   | 30분   | ~130 RPS  | 한계점 탐색, stepup 3배 수준까지 공격적 가속"
echo "6) spike    | 15분   | ~165 RPS  | 폭증 대응, 30초 만에 16배 급증 후 회복 관찰"
echo "============================================================"
read -p "프로필 선택 (0~6): " choice

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

echo ""
echo "▶ 실행: $ENV_FILE"
echo ""

set -a
# shellcheck source=/dev/null
# 루트 .env에서 GEMINI_API_KEY 등 공통 설정 로드
if [ -f ".env" ]; then
  source ".env"
fi
source "$ENV_FILE"
set +a

# 결과 저장 경로 설정
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PROFILE_NAME="${LOAD_PROFILE:-smoke}"
mkdir -p k6/reports
SUMMARY_FILE="k6/reports/${PROFILE_NAME}_${TIMESTAMP}.json"
export SUMMARY_OUTPUT_FILE="$SUMMARY_FILE"

# k6 실행
k6 run k6/main.js
K6_EXIT=$?

echo ""
echo "==============================="
echo "  k6 종료 (exit code: $K6_EXIT)"
echo "==============================="

# Gemini 보고서 생성
if [ -f "$SUMMARY_FILE" ]; then
  if [ -z "$GEMINI_API_KEY" ]; then
    echo ""
    echo "⚠️  GEMINI_API_KEY가 설정되지 않아 보고서 생성을 건너뜁니다."
    echo "   보고서를 생성하려면 다음을 실행하세요:"
    echo "   GEMINI_API_KEY=<your-key> $VENV_PYTHON k6/report.py $SUMMARY_FILE $PROFILE_NAME"
  else
    echo ""
    if [ ! -x "$VENV_PYTHON" ]; then
      echo "⚠️  venv python 실행 파일을 찾을 수 없습니다: $VENV_PYTHON"
      echo "   아래 명령으로 venv를 생성/설치한 뒤 다시 실행하세요:"
      echo "   python3 -m venv $VENV_DIR && $VENV_PYTHON -m pip install -r k6/requirements.txt"
      exit $K6_EXIT
    fi

    if [ ! -f "$REQUIREMENTS_FILE" ]; then
      echo "⚠️  requirements 파일을 찾을 수 없습니다: $REQUIREMENTS_FILE"
      echo "   보고서 생성을 건너뜁니다."
      exit $K6_EXIT
    fi

    echo "📦 Python 의존성 설치 중... ($REQUIREMENTS_FILE)"
    if ! "$VENV_PYTHON" -m pip install -r "$REQUIREMENTS_FILE"; then
      echo "⚠️  의존성 설치에 실패하여 보고서 생성을 건너뜁니다."
      exit $K6_EXIT
    fi

    echo "📊 Gemini API로 보고서 생성 중... (python: $VENV_PYTHON)"
    "$VENV_PYTHON" k6/report.py "$SUMMARY_FILE" "$PROFILE_NAME"
  fi
else
  echo ""
  echo "⚠️  요약 파일을 찾을 수 없어 보고서 생성을 건너뜁니다: $SUMMARY_FILE"
fi

exit $K6_EXIT
