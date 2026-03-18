#!/usr/bin/env python3
"""Real-time WebSocket monitor for Finvibe quote events."""

import asyncio
import json
import os
import ssl
import sys
from datetime import datetime

import websockets


def load_env():
	base_url = os.environ.get("BASE_URL", "http://localhost:8080")
	tokens_file = os.environ.get("TOKENS_FILE", "k6/data/tokens.json")
	ids_file = os.environ.get("IDS_FILE", "k6/data/ids.json")
	subscribe_count = int(os.environ.get("WS_SUBSCRIBE_COUNT", "10"))
	return base_url, tokens_file, ids_file, subscribe_count


def load_token(tokens_file):
	try:
		with open(tokens_file) as f:
			data = json.load(f)
		tokens = data.get("tokens", [])
		if not tokens:
			print("토큰 파일이 비어 있습니다. 먼저 k6 테스트를 실행하여 토큰을 생성하세요.", file=sys.stderr)
			sys.exit(1)
		return tokens[0]
	except FileNotFoundError:
		print(f"토큰 파일을 찾을 수 없습니다: {tokens_file}", file=sys.stderr)
		print("k6 테스트를 먼저 실행하여 토큰을 생성하세요.", file=sys.stderr)
		sys.exit(1)
	except (KeyError, IndexError, ValueError) as e:
		print(f"토큰 파일 형식 오류: {e}", file=sys.stderr)
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
	token = load_token(tokens_file)
	topics = load_topics(ids_file, subscribe_count)
	ws_url = base_url.replace("https://", "wss://").replace("http://", "ws://") + "/market/ws"

	try:
		asyncio.run(watch(ws_url, token, topics))
	except KeyboardInterrupt:
		print("\n모니터링 종료.")


if __name__ == "__main__":
	main()
