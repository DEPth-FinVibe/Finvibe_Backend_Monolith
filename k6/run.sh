#!/bin/bash

VENV_DIR="${K6_VENV_DIR:-.venv}"
VENV_PYTHON="$VENV_DIR/bin/python3"
REQUIREMENTS_FILE="k6/requirements.txt"

echo "==============================="
echo "  Finvibe k6 부하테스트 실행기"
echo "==============================="
echo "0) quick    - 3초, 연결 확인용"
echo "1) smoke    - 5분, 저부하 사전 검증"
echo "2) ramp10   - 10분, 중간 부하"
echo "3) baseline - 25분, 기준 부하"
echo "4) stepup   - 25분, 상향 부하"
echo "==============================="
read -p "프로필 선택 (0/1/2/3/4): " choice

case $choice in
  0) ENV_FILE="k6/.env.quick" ;;
  1) ENV_FILE="k6/.env.smoke" ;;
  2) ENV_FILE="k6/.env.ramp10" ;;
  3) ENV_FILE="k6/.env.baseline" ;;
  4) ENV_FILE="k6/.env.stepup" ;;
  *)
    echo "잘못된 선택입니다. 0, 1, 2, 3, 4 중 하나를 입력하세요."
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
