#!/usr/bin/env python3
"""Real-time WebSocket monitor for Finvibe quote events."""

import asyncio
import json
import os
import ssl
import sys
import urllib.error
import urllib.request
from datetime import datetime

import websockets


def load_env():
	base_url = os.environ.get("BASE_URL", "http://localhost:8080")
	tokens_file = os.environ.get("TOKENS_FILE", "k6/data/tokens.json")
	ids_file = os.environ.get("IDS_FILE", "k6/data/ids.json")
	subscribe_count = int(os.environ.get("WS_SUBSCRIBE_COUNT", "10"))
	return base_url, tokens_file, ids_file, subscribe_count


def load_credentials(tokens_file):
	try:
		with open(tokens_file) as f:
			data = json.load(f)
		credentials = data.get("credentials", [])
		if not credentials:
			print("credentials 파일이 비어 있습니다. loginId/password를 추가하세요.", file=sys.stderr)
			sys.exit(1)
		first = credentials[0]
		login_id = str(first.get("loginId", "")).strip()
		password = str(first.get("password", "")).strip()
		if not login_id or not password:
			print("credentials[0]의 loginId/password가 비어 있습니다.", file=sys.stderr)
			sys.exit(1)
		return {"loginId": login_id, "password": password}
	except FileNotFoundError:
		print(f"credentials 파일을 찾을 수 없습니다: {tokens_file}", file=sys.stderr)
		sys.exit(1)
	except (KeyError, IndexError, ValueError) as e:
		print(f"credentials 파일 형식 오류: {e}", file=sys.stderr)
		sys.exit(1)


def issue_token(base_url, credential):
	login_url = base_url.rstrip("/") + "/auth/login"
	payload = json.dumps(credential).encode("utf-8")
	request = urllib.request.Request(
		login_url,
		data=payload,
		headers={"Content-Type": "application/json"},
		method="POST",
	)
	try:
		with urllib.request.urlopen(request, timeout=10) as response:
			body = response.read().decode("utf-8")
			data = json.loads(body)
			token = data.get("accessToken")
			if not token:
				print("로그인 응답에 accessToken이 없습니다.", file=sys.stderr)
				sys.exit(1)
			return token
	except urllib.error.HTTPError as e:
		body = e.read().decode("utf-8", errors="replace")
		print(f"로그인 실패: HTTP {e.code} {body}", file=sys.stderr)
		sys.exit(1)
	except urllib.error.URLError as e:
		print(f"로그인 요청 실패: {e}", file=sys.stderr)
		sys.exit(1)


def load_topics(ids_file, count):
	try:
		with open(ids_file) as f:
			data = json.load(f)
		stock_ids = data.get("stockIds", [])
		if not stock_ids:
			print(f"종목 ID가 없습니다: {ids_file}", file=sys.stderr)
			sys.exit(1)
		selected = stock_ids[:count]
		return [f"quote:{sid}" for sid in selected]
	except FileNotFoundError:
		print(f"종목 ID 파일을 찾을 수 없습니다: {ids_file}", file=sys.stderr)
		sys.exit(1)
	except (KeyError, ValueError) as e:
		print(f"종목 ID 파일 형식 오류: {e}", file=sys.stderr)
		sys.exit(1)


def format_event(msg):
	topic = msg.get("topic", "unknown")
	data = msg.get("data", {})
	price = data.get("price", 0) or 0
	change_pct = float(data.get("prevDayChangePct", 0) or 0)
	volume = data.get("volume", 0) or 0
	is_initial = data.get("initial", False)

	now = datetime.now()
	ts = now.strftime("%H:%M:%S") + f".{now.microsecond // 1000:03d}"
	sign = "+" if change_pct >= 0 else ""
	initial_tag = "  [INITIAL]" if is_initial else ""

	try:
		price_int = int(price)
		volume_int = int(volume)
	except (TypeError, ValueError):
		price_int = 0
		volume_int = 0

	return f"[{ts}] {topic:<12} price={price_int:>10,}  {sign}{change_pct:.1f}%  vol={volume_int:>12,}{initial_tag}"


async def watch(ws_url, token, topics):
	print(f"연결 중: {ws_url}")
	print(f"구독 종목: {len(topics)}개 — {', '.join(topics)}")
	print("=" * 70)

	# HTTP/2 ALPN 협상 방지: WebSocket 업그레이드는 HTTP/1.1만 지원
	ssl_context = None
	if ws_url.startswith("wss://"):
		ssl_context = ssl.create_default_context()
		ssl_context.set_alpn_protocols(["http/1.1"])

	try:
		async with websockets.connect(ws_url, ssl=ssl_context) as ws:
			# Send auth
			await ws.send(json.dumps({"type": "auth", "token": token}))

			# Wait for auth ack
			raw = await ws.recv()
			auth_resp = json.loads(raw)
			if auth_resp.get("type") == "error" or auth_resp.get("ok") is False:
				code = auth_resp.get("code", "")
				if code == "UNAUTHORIZED":
					print("인증 실패: 토큰이 유효하지 않거나 만료되었습니다.", file=sys.stderr)
				else:
					print(f"인증 실패: {auth_resp}", file=sys.stderr)
				sys.exit(1)

			# Send subscribe
			await ws.send(json.dumps({"type": "subscribe", "request_id": "watch-1", "topics": topics}))

			# Receive loop
			async for raw_msg in ws:
				msg = json.loads(raw_msg)
				msg_type = msg.get("type")

				if msg_type == "event":
					print(format_event(msg))
					sys.stdout.flush()
				elif msg_type == "ping":
					await ws.send(json.dumps({"type": "pong"}))
				elif msg_type == "error":
					print(f"서버 오류: {msg}", file=sys.stderr)
					sys.exit(1)
				# subscribe ack, auth ack 등은 무시

	except websockets.exceptions.ConnectionClosed as e:
		print(f"\n연결이 끊겼습니다: {e}")
		sys.exit(1)


def main():
	base_url, tokens_file, ids_file, subscribe_count = load_env()
	credential = load_credentials(tokens_file)
	token = issue_token(base_url, credential)
	topics = load_topics(ids_file, subscribe_count)
	ws_url = base_url.replace("https://", "wss://").replace("http://", "ws://") + "/market/ws"

	try:
		asyncio.run(watch(ws_url, token, topics))
	except KeyboardInterrupt:
		print("\n모니터링 종료.")


if __name__ == "__main__":
	main()
