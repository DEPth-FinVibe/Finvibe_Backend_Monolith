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


def is_ws_profile(profile: str) -> bool:
    return profile.startswith("ws-")


def is_hotkey_profile(profile: str) -> bool:
    return profile.startswith("hotkey-")


def is_hotkey_cache_profile(profile: str) -> bool:
    return profile.startswith("hotkey-cache-")


def is_redis_spike_profile(profile: str) -> bool:
    return profile.startswith("redis-spike-")


def build_http_metrics_summary(data: dict) -> str:
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


def build_ws_metrics_summary(data: dict) -> str:
    metrics = data.get("metrics", {})
    profile = data.get("profile", "unknown")
    base_url = data.get("baseUrl", "unknown")
    tokens_loaded = data.get("tokensLoaded", 0)
    ids_summary = data.get("idStatsSummary", "unknown")

    lines = []
    lines.append("## 테스트 기본 정보")
    lines.append(f"- 프로파일: {profile}")
    lines.append(f"- 대상 서버: {base_url}")
    lines.append(f"- 로드된 토큰 수: {tokens_loaded}")
    lines.append(f"- 로드된 ID 통계: {ids_summary}")
    lines.append("")

    lines.append("## WebSocket 핵심 지표")
    ws_connect_rate = extract_metric(metrics, "ws_connect_rate")
    ws_auth_rate = extract_metric(metrics, "ws_auth_rate")
    ws_connect_fail = extract_metric(metrics, "ws_connect_fail")
    ws_auth_fail_count = extract_metric(metrics, "ws_auth_fail_count")
    ws_events_received = extract_metric(metrics, "ws_events_received")
    ws_sessions = extract_metric(metrics, "ws_sessions")
    ws_active_connections = extract_metric(metrics, "ws_active_connections")
    ws_connecting = extract_metric(metrics, "ws_connecting")
    ws_lag = extract_metric(metrics, "ws_delivery_lag_ms{scenario_group:ws_quote}") or extract_metric(metrics, "ws_delivery_lag_ms")

    if ws_sessions:
        v = ws_sessions.get("values", {})
        lines.append(f"- 총 세션 수: {int(v.get('count', 0)):,}")
    if ws_events_received:
        v = ws_events_received.get("values", {})
        lines.append(f"- 총 수신 이벤트 수: {int(v.get('count', 0)):,}")
        lines.append(f"- 초당 수신 이벤트 수: {v.get('rate', 0):.2f}")
    if ws_connect_rate:
        v = ws_connect_rate.get("values", {})
        lines.append(f"- 연결 성공률: {fmt_rate(v.get('rate'), as_percent=True)}")
    if ws_auth_rate:
        v = ws_auth_rate.get("values", {})
        lines.append(f"- 인증 성공률: {fmt_rate(v.get('rate'), as_percent=True)}")
    if ws_connect_fail:
        v = ws_connect_fail.get("values", {})
        lines.append(f"- 연결 실패 수: {int(v.get('count', 0)):,}")
    if ws_auth_fail_count:
        v = ws_auth_fail_count.get("values", {})
        lines.append(f"- 인증 실패 수: {int(v.get('count', 0)):,}")
    if ws_active_connections:
        v = ws_active_connections.get("values", {})
        lines.append(f"- 활성 연결 수(max): {int(v.get('max', 0)):,}")
    lines.append("")

    lines.append("## 연결 수립 시간 (ws_connecting)")
    if ws_connecting:
        v = ws_connecting.get("values", {})
        lines.append(f"- avg: {fmt_ms(v.get('avg'))}")
        lines.append(f"- med: {fmt_ms(v.get('med'))}")
        lines.append(f"- p(95): {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- p(99): {fmt_ms(v.get('p(99)'))}")
        lines.append(f"- max: {fmt_ms(v.get('max'))}")
    else:
        lines.append("- 데이터 없음")
    lines.append("")

    lines.append("## 이벤트 전달 지연 (ws_delivery_lag_ms)")
    if ws_lag:
        v = ws_lag.get("values", {})
        lines.append(f"- avg: {fmt_ms(v.get('avg'))}")
        lines.append(f"- med: {fmt_ms(v.get('med'))}")
        lines.append(f"- p(95): {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- p(99): {fmt_ms(v.get('p(99)'))}")
        lines.append(f"- max: {fmt_ms(v.get('max'))}")
        if any((v.get(key) or 0) < 0 for key in ("avg", "med", "p(95)", "p(99)", "max", "min")):
            lines.append("- 참고: 음수 지연값이 관찰되면 서버 이벤트 타임스탬프와 k6 실행 환경 시계 차이를 먼저 점검해야 합니다.")
    else:
        lines.append("- 데이터 없음")
    lines.append("")

    lines.append("## Checks 통계")
    checks = extract_metric(metrics, "checks")
    if checks:
        v = checks.get("values", {})
        lines.append(f"- 전체 체크 성공률: {fmt_rate(v.get('rate'), as_percent=True)} ({int(v.get('passes', 0)):,}통과 / {int(v.get('fails', 0)):,}실패)")
    else:
        lines.append("- 데이터 없음")
    lines.append("")

    lines.append("## Threshold 통과/실패 현황")
    threshold_lines = []
    for metric_name, metric in metrics.items():
        thresholds = metric.get("thresholds", {})
        for threshold_name, threshold_result in thresholds.items():
            ok = threshold_result.get("ok", False)
            status = "✅ PASS" if ok else "❌ FAIL"
            threshold_lines.append(f"- `{metric_name}` / `{threshold_name}`: {status}")
    if threshold_lines:
        lines.extend(threshold_lines)
    else:
        lines.append("- threshold 데이터 없음")
    lines.append("")

    return "\n".join(lines)


def build_hotkey_metrics_summary(data: dict) -> str:
    metrics = data.get("metrics", {})
    profile = data.get("profile", "unknown")
    base_url = data.get("baseUrl", "unknown")
    tokens_loaded = data.get("tokensLoaded", 0)
    ids_summary = data.get("idStatsSummary", "unknown")

    connect_rate = extract_metric(metrics, "ws_hotkey_connect_rate")
    auth_rate = extract_metric(metrics, "ws_hotkey_auth_rate")
    connect_fail = extract_metric(metrics, "ws_hotkey_connect_fail_count")
    auth_fail = extract_metric(metrics, "ws_hotkey_auth_fail_count")
    subscribe_fail = extract_metric(metrics, "ws_hotkey_subscribe_fail_count")
    rejected_topics = extract_metric(metrics, "ws_hotkey_rejected_topic_count")
    snapshot_miss = extract_metric(metrics, "ws_hotkey_snapshot_miss_count")
    subscribe_ack = extract_metric(metrics, "ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_hotkey_subscribe}") or extract_metric(metrics, "ws_hotkey_subscribe_ack_latency_ms")
    snapshot_latency = extract_metric(metrics, "ws_hotkey_initial_snapshot_latency_ms{scenario_group:ws_hotkey_subscribe}") or extract_metric(metrics, "ws_hotkey_initial_snapshot_latency_ms")
    sessions = extract_metric(metrics, "ws_sessions")
    disconnects = extract_metric(metrics, "ws_hotkey_disconnect_count")
    ws_connecting = extract_metric(metrics, "ws_connecting")

    lines = []
    lines.append("## 테스트 기본 정보")
    lines.append(f"- 프로파일: {profile}")
    lines.append(f"- 대상 서버: {base_url}")
    lines.append(f"- 로드된 토큰 수: {tokens_loaded}")
    lines.append(f"- 로드된 ID 통계: {ids_summary}")
    lines.append("")

    lines.append("## Hotkey 핵심 지표")
    if sessions:
        v = sessions.get("values", {})
        lines.append(f"- 총 세션 수: {int(v.get('count', 0)):,}")
    if connect_rate:
        v = connect_rate.get("values", {})
        lines.append(f"- 연결 성공률: {fmt_rate(v.get('rate'), as_percent=True)}")
    if auth_rate:
        v = auth_rate.get("values", {})
        lines.append(f"- 인증 성공률: {fmt_rate(v.get('rate'), as_percent=True)}")
    if connect_fail:
        v = connect_fail.get("values", {})
        lines.append(f"- 연결 실패 수: {int(v.get('count', 0)):,}")
    if auth_fail:
        v = auth_fail.get("values", {})
        lines.append(f"- 인증 실패 수: {int(v.get('count', 0)):,}")
    if subscribe_fail:
        v = subscribe_fail.get("values", {})
        lines.append(f"- subscribe 실패 수: {int(v.get('count', 0)):,}")
    if rejected_topics:
        v = rejected_topics.get("values", {})
        lines.append(f"- 거절된 topic 수: {int(v.get('count', 0)):,}")
    if snapshot_miss:
        v = snapshot_miss.get("values", {})
        lines.append(f"- snapshot miss 수: {int(v.get('count', 0)):,}")
    if disconnects:
        v = disconnects.get("values", {})
        lines.append(f"- disconnect 수: {int(v.get('count', 0)):,}")
    lines.append("")

    lines.append("## Subscribe Ack 지연")
    if subscribe_ack:
        v = subscribe_ack.get("values", {})
        lines.append(f"- avg: {fmt_ms(v.get('avg'))}")
        lines.append(f"- p(95): {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- p(99): {fmt_ms(v.get('p(99)'))}")
        lines.append(f"- max: {fmt_ms(v.get('max'))}")
    else:
        lines.append("- 데이터 없음")
    lines.append("")

    lines.append("## Initial Snapshot 지연")
    if snapshot_latency:
        v = snapshot_latency.get("values", {})
        lines.append(f"- avg: {fmt_ms(v.get('avg'))}")
        lines.append(f"- med: {fmt_ms(v.get('med'))}")
        lines.append(f"- p(95): {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- p(99): {fmt_ms(v.get('p(99)'))}")
        lines.append(f"- max: {fmt_ms(v.get('max'))}")
    else:
        lines.append("- 데이터 없음")
    lines.append("")

    lines.append("## 연결 수립 시간 (ws_connecting)")
    if ws_connecting:
        v = ws_connecting.get("values", {})
        lines.append(f"- avg: {fmt_ms(v.get('avg'))}")
        lines.append(f"- p(95): {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- p(99): {fmt_ms(v.get('p(99)'))}")
        lines.append(f"- max: {fmt_ms(v.get('max'))}")
    else:
        lines.append("- 데이터 없음")
    lines.append("")

    lines.append("## Threshold 통과/실패 현황")
    threshold_lines = []
    for metric_name, metric in metrics.items():
        thresholds = metric.get("thresholds", {})
        for threshold_name, threshold_result in thresholds.items():
            ok = threshold_result.get("ok", False)
            status = "✅ PASS" if ok else "❌ FAIL"
            threshold_lines.append(f"- `{metric_name}` / `{threshold_name}`: {status}")
    if threshold_lines:
        lines.extend(threshold_lines)
    else:
        lines.append("- threshold 데이터 없음")
    lines.append("")

    return "\n".join(lines)


def build_hotkey_cache_metrics_summary(data: dict) -> str:
    metrics = data.get("metrics", {})
    profile = data.get("profile", "unknown")
    base_url = data.get("baseUrl", "unknown")
    tokens_loaded = data.get("tokensLoaded", 0)
    ids_summary = data.get("idStatsSummary", "unknown")

    cache_rate = extract_metric(metrics, "hotkey_cache_read_rate")
    cache_fail = extract_metric(metrics, "hotkey_cache_read_fail_count")
    cache_latency = extract_metric(metrics, "hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}") or extract_metric(metrics, "hotkey_cache_read_latency_ms")

    lines = []
    lines.append("## 테스트 기본 정보")
    lines.append(f"- 프로파일: {profile}")
    lines.append(f"- 대상 서버: {base_url}")
    lines.append(f"- 로드된 토큰 수: {tokens_loaded}")
    lines.append(f"- 로드된 ID 통계: {ids_summary}")
    lines.append("")

    lines.append("## Cache-Read 핵심 지표")
    if cache_rate:
        v = cache_rate.get("values", {})
        lines.append(f"- cache-read 성공률: {fmt_rate(v.get('rate'), as_percent=True)}")
    if cache_fail:
        v = cache_fail.get("values", {})
        lines.append(f"- cache-read 실패 수: {int(v.get('count', 0)):,}")
    if cache_latency:
        v = cache_latency.get("values", {})
        lines.append(f"- avg: {fmt_ms(v.get('avg'))}")
        lines.append(f"- med: {fmt_ms(v.get('med'))}")
        lines.append(f"- p(95): {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- p(99): {fmt_ms(v.get('p(99)'))}")
        lines.append(f"- max: {fmt_ms(v.get('max'))}")
    else:
        lines.append("- cache-read latency 데이터 없음")
    lines.append("")

    lines.append("## Threshold 통과/실패 현황")
    threshold_lines = []
    for metric_name, metric in metrics.items():
        thresholds = metric.get("thresholds", {})
        for threshold_name, threshold_result in thresholds.items():
            ok = threshold_result.get("ok", False)
            status = "✅ PASS" if ok else "❌ FAIL"
            threshold_lines.append(f"- `{metric_name}` / `{threshold_name}`: {status}")
    if threshold_lines:
        lines.extend(threshold_lines)
    else:
        lines.append("- threshold 데이터 없음")
    lines.append("")

    return "\n".join(lines)


def build_redis_spike_metrics_summary(data: dict) -> str:
    metrics = data.get("metrics", {})
    profile = data.get("profile", "unknown")
    base_url = data.get("baseUrl", "unknown")
    tokens_loaded = data.get("tokensLoaded", 0)
    ids_summary = data.get("idStatsSummary", "unknown")

    cache_rate = extract_metric(metrics, "hotkey_cache_read_rate")
    cache_fail = extract_metric(metrics, "hotkey_cache_read_fail_count")
    cache_latency = extract_metric(metrics, "hotkey_cache_read_latency_ms{scenario_group:hotkey_cache_read}") or extract_metric(metrics, "hotkey_cache_read_latency_ms")
    connect_rate = extract_metric(metrics, "ws_hotkey_connect_rate")
    auth_rate = extract_metric(metrics, "ws_hotkey_auth_rate")
    subscribe_fail = extract_metric(metrics, "ws_hotkey_subscribe_fail_count")
    snapshot_miss = extract_metric(metrics, "ws_hotkey_snapshot_miss_count")
    subscribe_ack = extract_metric(metrics, "ws_hotkey_subscribe_ack_latency_ms{scenario_group:ws_hotkey_subscribe}") or extract_metric(metrics, "ws_hotkey_subscribe_ack_latency_ms")

    lines = []
    lines.append("## 테스트 기본 정보")
    lines.append(f"- 프로파일: {profile}")
    lines.append(f"- 대상 서버: {base_url}")
    lines.append(f"- 로드된 토큰 수: {tokens_loaded}")
    lines.append(f"- 로드된 ID 통계: {ids_summary}")
    lines.append("")

    lines.append("## Read Pressure (current-price cache-read)")
    if cache_rate:
        v = cache_rate.get("values", {})
        lines.append(f"- read 성공률: {fmt_rate(v.get('rate'), as_percent=True)}")
    if cache_fail:
        v = cache_fail.get("values", {})
        lines.append(f"- read 실패 수: {int(v.get('count', 0)):,}")
    if cache_latency:
        v = cache_latency.get("values", {})
        lines.append(f"- read latency avg: {fmt_ms(v.get('avg'))}")
        lines.append(f"- read latency p95: {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- read latency p99: {fmt_ms(v.get('p(99)'))}")
    lines.append("")

    lines.append("## Write-ish Pressure (websocket churn)")
    if connect_rate:
        v = connect_rate.get("values", {})
        lines.append(f"- ws connect 성공률: {fmt_rate(v.get('rate'), as_percent=True)}")
    if auth_rate:
        v = auth_rate.get("values", {})
        lines.append(f"- ws auth 성공률: {fmt_rate(v.get('rate'), as_percent=True)}")
    if subscribe_fail:
        v = subscribe_fail.get("values", {})
        lines.append(f"- subscribe 실패 수: {int(v.get('count', 0)):,}")
    if snapshot_miss:
        v = snapshot_miss.get("values", {})
        lines.append(f"- first-event miss 수: {int(v.get('count', 0)):,}")
    if subscribe_ack:
        v = subscribe_ack.get("values", {})
        lines.append(f"- subscribe ack p95: {fmt_ms(v.get('p(95)'))}")
        lines.append(f"- subscribe ack p99: {fmt_ms(v.get('p(99)'))}")
    lines.append("")

    lines.append("## Threshold 통과/실패 현황")
    threshold_lines = []
    for metric_name, metric in metrics.items():
        thresholds = metric.get("thresholds", {})
        for threshold_name, threshold_result in thresholds.items():
            ok = threshold_result.get("ok", False)
            status = "✅ PASS" if ok else "❌ FAIL"
            threshold_lines.append(f"- `{metric_name}` / `{threshold_name}`: {status}")
    if threshold_lines:
        lines.extend(threshold_lines)
    else:
        lines.append("- threshold 데이터 없음")
    lines.append("")

    return "\n".join(lines)


def build_prompt(metrics_summary: str, profile: str) -> str:
    if is_redis_spike_profile(profile):
        return f"""당신은 백엔드 성능 엔지니어입니다. 아래는 Finvibe 서비스에 대한 k6 Redis mixed spike 테스트 결과 데이터입니다.
테스트 프로파일은 \"{profile}\"입니다.

---
{metrics_summary}
---

위 데이터를 바탕으로 한국어로 상세한 Redis mixed spike 분석 보고서를 마크다운 형식으로 작성해주세요.

중요:
- 이 보고서는 단일 Redis 서버에 read + write-ish pressure를 동시에 준 테스트입니다.
- read pressure는 `/market/stocks/{{stockId}}/current-price` 반복 조회입니다.
- write-ish pressure는 websocket churn(subscribe/unsubscribe 반복)입니다.
- 분석 시 read latency와 websocket churn 실패/지연을 분리해서 설명하세요.
- OOM/eviction/Redis server distress는 k6 결과만으로 확정하지 말고, 외부 Redis 관측(used_memory, evicted_keys, latency monitor, connected_clients) 필요성을 분명히 쓰세요.

보고서에 반드시 포함해야 할 항목:
1. **테스트 요약** - 프로파일, 목적, 전체 결과(합격/불합격)
2. **Read pressure 분석** - current-price latency / 실패율
3. **Write-ish pressure 분석** - websocket churn connect/auth/subscribe/first-event
4. **Threshold 판정 결과** - 각 임계치 통과/실패 이유 설명
5. **Redis distress 징후 해석** - latency 상승, failure surge, external Redis metrics 필요성
6. **개선 권고사항** - 구체적이고 실행 가능한 3~5가지
7. **종합 평가** - 단일 Redis 서버 spike 관점의 한 줄 판정

마크다운 헤더(#, ##, ###), 표를 적절히 활용해 가독성 높게 작성하세요.
"""

    if is_hotkey_cache_profile(profile):
        return f"""당신은 백엔드 성능 엔지니어입니다. 아래는 Finvibe 서비스에 대한 k6 Redis current-price cache hotkey 테스트 결과 데이터입니다.
테스트 프로파일은 \"{profile}\"입니다.

---
{metrics_summary}
---

위 데이터를 바탕으로 한국어로 상세한 cache-read hotkey 분석 보고서를 마크다운 형식으로 작성해주세요.

중요:
- 이 보고서는 WebSocket subscribe-init 보고서가 아닙니다.
- 분석 대상은 `/market/stocks/{{stockId}}/current-price` 반복 조회에 대한 cache-read latency 입니다.
- `hotkey_cache_read_rate`, `hotkey_cache_read_fail_count`, `hotkey_cache_read_latency_ms`를 중심으로 해석하세요.
- `tokensLoaded` 값만으로 인증 실패를 추론하지 마세요. 이 시나리오는 공개 market endpoint를 대상으로 동작할 수 있습니다.

보고서에 반드시 포함해야 할 항목:
1. **테스트 요약** - 프로파일, 목적, 전체 결과(합격/불합격)
2. **핵심 지표 분석**
   - cache-read 성공률
   - cache-read latency(avg/p95/p99/max)
3. **Threshold 판정 결과** - 각 임계치 통과/실패 이유 설명
4. **에러 및 이상 징후 분석**
   - 실패 수, 지연 tail 상승 원인 추론
5. **병목 및 위험 구간**
   - Redis hot key / current-price lookup 관점에서 설명
6. **개선 권고사항** - 구체적이고 실행 가능한 3~5가지
7. **종합 평가** - cache-read hotkey 관점의 한 줄 판정

마크다운 헤더(#, ##, ###), 표를 적절히 활용해 가독성 높게 작성하세요.
"""

    if is_hotkey_profile(profile):
        return f"""당신은 백엔드 성능 엔지니어입니다. 아래는 Finvibe 서비스에 대한 k6 Hotkey WebSocket 부하테스트 결과 데이터입니다.
테스트 프로파일은 \"{profile}\"입니다.

---
{metrics_summary}
---

위 데이터를 바탕으로 한국어로 상세한 Hotkey WebSocket 부하테스트 분석 보고서를 마크다운 형식으로 작성해주세요.

중요:
- 이 보고서는 HTTP REST API 보고서가 아닙니다.
- 공개/인증/헤비 REST API, 401/404 HTTP 에러, http_req_duration 같은 HTTP 중심 해석은 쓰지 마세요.
- `tokensLoaded` 값만 보고 인증 실패라고 단정하지 마세요. 반드시 `ws_hotkey_auth_rate`, `ws_hotkey_auth_fail_count`, `ws_hotkey_connect_rate`, `ws_hotkey_subscribe_fail_count`를 함께 보고 판단하세요.
- `ws_hotkey_auth_rate.rate`가 1에 가깝고 `ws_hotkey_auth_fail_count`가 0이면 인증 실패로 해석하지 마세요.
- 분석 대상은 `/market/ws`를 통한 hotkey subscribe / initial snapshot 흐름입니다.

보고서에 반드시 포함해야 할 항목:
1. **테스트 요약** - 프로파일, 목적, 전체 결과(합격/불합격)
2. **핵심 지표 분석**
   - 연결 성공률, 인증 성공률
   - subscribe ack latency 해석
   - initial snapshot latency 해석
   - snapshot miss / subscribe fail / rejected topic 유무 해석
3. **Threshold 판정 결과** - 각 임계치 통과/실패 이유 설명
4. **에러 및 이상 징후 분석**
   - 연결 실패와 인증 실패를 구분
   - tail latency가 긴 경우 snapshot 경로 관점에서 설명
5. **병목 및 위험 구간**
   - subscribe 직후 snapshot 경로에서 의심할 수 있는 병목 설명
6. **개선 권고사항** - 구체적이고 실행 가능한 3~5가지
7. **종합 평가** - hotkey subscribe 경로 관점의 한 줄 판정

마크다운 헤더(#, ##, ###), 표를 적절히 활용해 가독성 높게 작성하세요.
"""

    if is_ws_profile(profile):
        return f"""당신은 백엔드 성능 엔지니어입니다. 아래는 Finvibe 서비스에 대한 k6 WebSocket 부하테스트 결과 데이터입니다.
테스트 프로파일은 "{profile}"입니다.

---
{metrics_summary}
---

위 데이터를 바탕으로 한국어로 상세한 WebSocket 부하테스트 분석 보고서를 마크다운 형식으로 작성해주세요.

중요:
- 이 보고서는 HTTP REST API 보고서가 아닙니다.
- 공개/인증/헤비 REST API, 401/404 HTTP 에러, http_req_duration 같은 HTTP 중심 해석은 쓰지 마세요.
- 분석 대상은 `/market/ws`를 통한 주식 quote WebSocket 시나리오입니다.

보고서에 반드시 포함해야 할 항목:
1. **테스트 요약** - 프로파일, 목적, 전체 결과(합격/불합격)
2. **핵심 지표 분석**
   - 연결 성공률, 인증 성공률
   - 연결 수립 시간(ws_connecting) 해석
   - 이벤트 전달 지연(ws_delivery_lag_ms) 해석
   - 총 세션 수, 활성 연결 수, 총 수신 이벤트 수 해석
3. **Threshold 판정 결과** - 각 임계치 통과/실패 이유 설명
4. **에러 및 이상 징후 분석**
   - 연결 실패, 인증 실패, 체크 실패 원인 추론
   - 지연값이 음수인 경우 서버/클라이언트 시계 차이 가능성 설명
5. **병목 및 위험 구간**
   - 동시 연결 수 증가 시 어떤 구간에서 문제가 생길 수 있는지
   - quote 전달 경로에서 의심할 수 있는 병목 설명
6. **개선 권고사항** - 구체적이고 실행 가능한 3~5가지
7. **종합 평가** - WebSocket quote 서비스 관점의 한 줄 판정

마크다운 헤더(#, ##, ###), 표를 적절히 활용해 가독성 높게 작성하세요.
숫자 비교 시 실제 threshold와 대조해 평가하고, threshold가 모두 통과한 경우에도 잔여 리스크를 분리해서 설명하세요.
"""

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
    if is_redis_spike_profile(profile):
        metrics_summary = build_redis_spike_metrics_summary(data)
    elif is_hotkey_cache_profile(profile):
        metrics_summary = build_hotkey_cache_metrics_summary(data)
    elif is_hotkey_profile(profile):
        metrics_summary = build_hotkey_metrics_summary(data)
    elif is_ws_profile(profile):
        metrics_summary = build_ws_metrics_summary(data)
    else:
        metrics_summary = build_http_metrics_summary(data)
    prompt = build_prompt(metrics_summary, profile)

    print(f"[3/4] Gemini API로 보고서 생성 중... (model: {os.environ.get('GEMINI_HIGH_MODEL_NAME') or os.environ.get('GEMINI_MODEL_NAME', 'gemini-2.0-flash')})")
    print()
    report_body = call_gemini(prompt)
    print()

    header = build_report_header(profile, json_path)
    full_report = header + report_body

    summary_path = Path(json_path)
    reports_dir = summary_path.parent
    output_path = save_report(full_report, profile, reports_dir)

    print(f"[4/4] 보고서 저장 완료!")
    print(f"      → {output_path}")


if __name__ == "__main__":
    main()
