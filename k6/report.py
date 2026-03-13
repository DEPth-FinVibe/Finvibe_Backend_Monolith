#!/usr/bin/env python3
"""
k6 부하테스트 결과를 Gemini API로 분석하여 마크다운 보고서를 생성합니다.
사용법: python3 k6/report.py <summary_json_path> [profile_name]

GEMINI_API_KEY는 프로젝트 루트의 .env 파일 또는 환경변수에서 읽습니다.
"""

import json
import os
import sys
from datetime import datetime
from pathlib import Path


def load_dotenv_from_root():
    """프로젝트 루트 .env에서 환경변수를 로드합니다 (python-dotenv 없어도 동작)."""
    # report.py 위치(k6/)에서 한 단계 위가 프로젝트 루트
    root_env = Path(__file__).parent.parent / ".env"
    if not root_env.exists():
        return
    try:
        from dotenv import load_dotenv
        load_dotenv(root_env, override=False)
    except ImportError:
        # python-dotenv 없으면 직접 파싱
        with open(root_env, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, _, value = line.partition("=")
                key = key.strip()
                value = value.strip().strip('"').strip("'")
                os.environ.setdefault(key, value)


def load_summary(json_path: str) -> dict:
    with open(json_path, "r", encoding="utf-8") as f:
        return json.load(f)


def extract_metric(metrics: dict, name: str) -> dict | None:
    return metrics.get(name)


def fmt_ms(value: float | None) -> str:
    if value is None:
        return "N/A"
    return f"{value:.1f}ms"


def fmt_rate(value: float | None, as_percent: bool = False) -> str:
    if value is None:
        return "N/A"
    if as_percent:
        return f"{value * 100:.2f}%"
    return f"{value:.4f}"


def build_metrics_summary(data: dict) -> str:
    metrics = data.get("metrics", {})
    profile = data.get("profile", "unknown")
    base_url = data.get("baseUrl", "unknown")
    tokens_loaded = data.get("tokensLoaded", 0)
    ids_summary = data.get("idStatsSummary", "unknown")
    thresholds = data.get("thresholds", {})

    lines = []
    lines.append(f"## 테스트 기본 정보")
    lines.append(f"- 프로파일: {profile}")
    lines.append(f"- 대상 서버: {base_url}")
    lines.append(f"- 로드된 토큰 수: {tokens_loaded}")
    lines.append(f"- 로드된 ID 통계: {ids_summary}")
    lines.append("")

    # HTTP 전체 요청 통계
    req_dur = extract_metric(metrics, "http_req_duration")
    req_failed = extract_metric(metrics, "http_req_failed")
    http_reqs = extract_metric(metrics, "http_reqs")

    lines.append("## 전체 HTTP 요청 통계")
    if http_reqs:
        v = http_reqs.get("values", {})
        lines.append(f"- 총 요청 수: {int(v.get('count', 0)):,}")
        lines.append(f"- 초당 요청 수(RPS): {v.get('rate', 0):.2f}")
    if req_failed:
        v = req_failed.get("values", {})
        lines.append(f"- 실패율: {fmt_rate(v.get('rate'), as_percent=True)}")
        lines.append(f"- 실패 건수: {int(v.get('fails', 0)):,}")
        lines.append(f"- 성공 건수: {int(v.get('passes', 0)):,}")
    lines.append("")

    # 응답시간 전체
    lines.append("## 전체 응답 시간 (http_req_duration)")
    if req_dur:
        v = req_dur.get("values", {})
        lines.append(f"- avg: {fmt_ms(v.get('avg'))}")
        lines.append(f"- min: {fmt_ms(v.get('min'))}")
        lines.append(f"- med: {fmt_ms(v.get('med'))}")
        lines.append(f"- p(90): {fmt_ms(v.get('p(90)'))}")
        lines.append(f"- p(95): {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- p(99): {fmt_ms(v.get('p(99)'))}")
        lines.append(f"- max: {fmt_ms(v.get('max'))}")
    lines.append("")

    # scope별 응답시간
    scope_metrics = {
        "scope:public": "공개 API (Public)",
        "scope:auth": "인증 API (Auth)",
        "cost:heavy": "고비용 조회 (Heavy Read)",
    }
    lines.append("## Scope별 응답 시간")
    for tag, label in scope_metrics.items():
        key = f"http_req_duration{{{tag}}}"
        m = extract_metric(metrics, key)
        if m:
            v = m.get("values", {})
            lines.append(f"### {label}")
            lines.append(f"- p(95): {fmt_ms(v.get('p(95)'))}")
            lines.append(f"- p(99): {fmt_ms(v.get('p(99)'))}")
            lines.append(f"- avg: {fmt_ms(v.get('avg'))}")
            lines.append(f"- max: {fmt_ms(v.get('max'))}")
    lines.append("")

    # checks 통계
    lines.append("## Checks 통계")
    checks_keys = [
        ("checks{scope:public}", "공개 API"),
        ("checks{scope:auth}", "인증 API"),
        ("checks{cost:heavy}", "고비용 조회"),
        ("checks", "전체"),
    ]
    for key, label in checks_keys:
        m = extract_metric(metrics, key)
        if m:
            v = m.get("values", {})
            lines.append(f"- {label}: {fmt_rate(v.get('rate'), as_percent=True)} ({int(v.get('passes', 0)):,}통과 / {int(v.get('fails', 0)):,}실패)")
    lines.append("")

    # 에러 관련 커스텀 메트릭
    lines.append("## 에러율 세부 통계")
    error_metrics = [
        ("unauthorized_rate", "401 인증 실패율"),
        ("not_found_rate", "404 미발견율"),
        ("api_business_failure_count", "비즈니스 실패 누적"),
    ]
    for key, label in error_metrics:
        m = extract_metric(metrics, key)
        if m:
            v = m.get("values", {})
            rate_val = v.get("rate") or v.get("count")
            if rate_val is not None:
                if "rate" in (m.get("type") or ""):
                    lines.append(f"- {label}: {fmt_rate(v.get('rate'), as_percent=True)}")
                else:
                    lines.append(f"- {label}: {int(v.get('count', 0)):,}")
    lines.append("")

    # Threshold 결과
    lines.append("## Threshold 통과/실패 현황")
    if thresholds:
        for th_key, th_val in thresholds.items():
            ok = th_val.get("ok", False)
            status = "✅ PASS" if ok else "❌ FAIL"
            lines.append(f"- `{th_key}`: {status}")
    else:
        lines.append("- threshold 데이터 없음")
    lines.append("")

    # 시나리오별 반복 횟수
    lines.append("## 시나리오별 반복 수 (iterations by scenario)")
    scenario_groups = [
        "public_market",
        "public_news",
        "auth_profile",
        "auth_activity",
        "heavy_read",
    ]
    for sg in scenario_groups:
        key = f"iterations{{scenario_group:{sg}}}"
        m = extract_metric(metrics, key)
        if m:
            v = m.get("values", {})
            lines.append(f"- {sg}: {int(v.get('count', 0)):,}회 ({v.get('rate', 0):.2f} iter/s)")
    lines.append("")

    return "\n".join(lines)


def build_prompt(metrics_summary: str, profile: str) -> str:
    return f"""당신은 백엔드 성능 엔지니어입니다. 아래는 Finvibe 서비스에 대한 k6 부하테스트 결과 데이터입니다.
테스트 프로파일은 "{profile}"입니다.

---
{metrics_summary}
---

위 데이터를 바탕으로 한국어로 상세한 부하테스트 분석 보고서를 마크다운 형식으로 작성해주세요.

보고서에 반드시 포함해야 할 항목:
1. **테스트 요약** - 프로파일, 목적, 전체 결과(합격/불합격)
2. **핵심 지표 분석**
   - 전체 RPS, 실패율
   - 응답시간 분포 해석 (p95, p99 의미와 목표 대비 평가)
   - Scope별(Public/Auth/Heavy) 응답시간 차이 분석
3. **Threshold 판정 결과** - 각 임계치 통과/실패 이유 설명
4. **에러 분석**
   - HTTP 실패(5xx, 타임아웃)와 비즈니스 실패(401, 404) 구분 해석
   - 401 인증 실패율과 404 미발견율의 원인 추론
5. **병목 및 위험 구간**
   - 어떤 시나리오/API 그룹에서 성능 저하 가능성이 있는지
   - Heavy Read 시나리오 특이사항
6. **개선 권고사항** - 구체적이고 실행 가능한 3~5가지
7. **종합 평가** - 이 결과가 프로덕션 배포에 적합한지 한 줄 판정

마크다운 헤더(#, ##, ###), 표, 코드블록, 이모지를 적절히 활용하여 가독성 높게 작성하세요.
숫자 비교 시 임계치(p95<700ms for public, p95<900ms for auth, p95<1500ms for heavy)와 대조하여 평가하세요.
"""


def call_gemini(prompt: str) -> str:
    try:
        import google.generativeai as genai
    except ImportError:
        print("[ERROR] google-generativeai 패키지가 설치되지 않았습니다.")
        print("       pip install google-generativeai 를 실행하세요.")
        sys.exit(1)

    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        print("[ERROR] GEMINI_API_KEY 환경변수가 설정되지 않았습니다.")
        sys.exit(1)

    model_name = os.environ.get("GEMINI_HIGH_MODEL_NAME") or os.environ.get("GEMINI_MODEL_NAME", "gemini-2.0-flash")
    genai.configure(api_key=api_key)
    model = genai.GenerativeModel(model_name)

    print("─" * 60)
    chunks = []
    for chunk in model.generate_content(prompt, stream=True):
        text = chunk.text
        print(text, end="", flush=True)
        chunks.append(text)
    print("\n" + "─" * 60)

    return "".join(chunks)


def save_report(content: str, profile: str, reports_dir: Path) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"report_{profile}_{timestamp}.md"
    output_path = reports_dir / filename
    output_path.write_text(content, encoding="utf-8")
    return output_path


def build_report_header(profile: str, json_path: str) -> str:
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    model_name = os.environ.get("GEMINI_HIGH_MODEL_NAME") or os.environ.get("GEMINI_MODEL_NAME", "gemini-2.0-flash")
    return f"""# Finvibe k6 부하테스트 보고서

> **생성일시**: {now}
> **프로파일**: {profile}
> **원본 데이터**: {json_path}
> **분석 모델**: {model_name}

---

"""


def main():
    load_dotenv_from_root()

    if len(sys.argv) < 2:
        print("사용법: python3 k6/report.py <summary_json_path> [profile_name]")
        sys.exit(1)

    json_path = sys.argv[1]
    if not os.path.exists(json_path):
        print(f"[ERROR] 파일을 찾을 수 없습니다: {json_path}")
        sys.exit(1)

    print(f"[1/4] 결과 파일 로드 중... ({json_path})")
    data = load_summary(json_path)

    profile = sys.argv[2] if len(sys.argv) > 2 else data.get("profile", "unknown")

    print("[2/4] 지표 데이터 가공 중...")
    metrics_summary = build_metrics_summary(data)
    prompt = build_prompt(metrics_summary, profile)

    print(f"[3/4] Gemini API로 보고서 생성 중... (model: {os.environ.get('GEMINI_HIGH_MODEL_NAME') or os.environ.get('GEMINI_MODEL_NAME', 'gemini-2.0-flash')})")
    print()
    report_body = call_gemini(prompt)
    print()

    header = build_report_header(profile, json_path)
    full_report = header + report_body

    script_dir = Path(__file__).parent
    reports_dir = script_dir / "reports"
    output_path = save_report(full_report, profile, reports_dir)

    print(f"[4/4] 보고서 저장 완료!")
    print(f"      → {output_path}")


if __name__ == "__main__":
    main()
