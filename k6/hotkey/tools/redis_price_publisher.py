#!/usr/bin/env python3
import argparse
import json
import math
import os
import random
import socket
import sys
import time
from bisect import bisect_left
from pathlib import Path


def env(name: str, default=None):
	value = os.getenv(name)
	return value if value not in (None, "") else default


def parse_args():
	parser = argparse.ArgumentParser(description="Direct Redis price-event publisher for websocket hot-key tests")
	parser.add_argument("--host", default=env("REDIS_HOST", "127.0.0.1"))
	parser.add_argument("--port", type=int, default=int(env("REDIS_PORT", "6379")))
	parser.add_argument("--password", default=env("REDIS_PASSWORD"))
	parser.add_argument("--db", type=int, default=int(env("REDIS_DB", "0")))
	parser.add_argument("--channel", default=env("REDIS_CHANNEL", "market:price-updated"))
	parser.add_argument("--channel-partition-count", type=int, default=int(env("REDIS_CHANNEL_PARTITION_COUNT", "1")))
	parser.add_argument("--stock-mode", choices=["single-hot", "multi-stock", "per-stock-steady"], default=env("PUBLISH_STOCK_MODE", "single-hot"))
	parser.add_argument("--traffic-mode", choices=["steady", "burst"], default=env("PUBLISH_TRAFFIC_MODE", "steady"))
	parser.add_argument("--mode", choices=["single-hot", "multi-stock", "zipf", "burst", "per-stock-steady"], default=env("PUBLISH_MODE"))
	parser.add_argument("--rate", type=float, default=float(env("PUBLISH_RATE", "1000")))
	parser.add_argument("--duration-sec", type=int, default=int(env("PUBLISH_DURATION_SEC", "300")))
	parser.add_argument("--hot-stock-id", type=int, default=int(env("HOT_STOCK_ID", "5930")))
	parser.add_argument("--hot-ratio", type=float, default=float(env("PUBLISH_HOT_RATIO", "0.5")))
	parser.add_argument("--zipf-skew", type=float, default=float(env("PUBLISH_ZIPF_SKEW", "1.1")))
	parser.add_argument("--stock-limit", type=int, default=int(env("PUBLISH_STOCK_LIMIT", "0")))
	parser.add_argument("--burst-rate", type=float, default=float(env("PUBLISH_BURST_RATE", "3000")))
	parser.add_argument("--burst-start-sec", type=int, default=int(env("PUBLISH_BURST_START_SEC", "60")))
	parser.add_argument("--burst-duration-sec", type=int, default=int(env("PUBLISH_BURST_DURATION_SEC", "30")))
	parser.add_argument("--stock-pool-file", default=env("PUBLISH_STOCK_POOL_FILE", "./k6/data/ids.json"))
	parser.add_argument("--seed", type=int, default=int(env("PUBLISH_SEED", str(int(time.time())))))
	parser.add_argument("--start-price", type=float, default=float(env("PUBLISH_START_PRICE", "70000")))
	parser.add_argument("--price-volatility", type=float, default=float(env("PUBLISH_PRICE_VOLATILITY", "25")))
	parser.add_argument("--start-volume", type=int, default=int(env("PUBLISH_START_VOLUME", "1000")))
	parser.add_argument("--pipeline-batch-size", type=int, default=int(env("PUBLISH_PIPELINE_BATCH_SIZE", "256")))
	args = parser.parse_args()
	legacy_mode = args.mode
	if legacy_mode:
		if legacy_mode == "single-hot":
			args.stock_mode = "single-hot"
			args.traffic_mode = "steady"
		elif legacy_mode in ("multi-stock", "zipf"):
			args.stock_mode = "multi-stock"
			args.traffic_mode = "steady"
		elif legacy_mode == "per-stock-steady":
			args.stock_mode = "per-stock-steady"
			args.traffic_mode = "steady"
		elif legacy_mode == "burst":
			args.stock_mode = "multi-stock"
			args.traffic_mode = "burst"
	return args


def encode_command(*parts):
	out = [f"*{len(parts)}\r\n".encode()]
	for part in parts:
		if isinstance(part, bytes):
			value = part
		else:
			value = str(part).encode()
		out.append(f"${len(value)}\r\n".encode())
		out.append(value + b"\r\n")
	return b"".join(out)


def read_resp(sock):
	prefix = sock.recv(1)
	if not prefix:
		raise RuntimeError("Redis connection closed")
	line = b""
	while not line.endswith(b"\r\n"):
		chunk = sock.recv(1)
		if not chunk:
			raise RuntimeError("Redis connection closed while reading response")
		line += chunk
	body = line[:-2].decode()
	if prefix == b"+":
		return body
	if prefix == b":":
		return int(body)
	if prefix == b"-":
		raise RuntimeError(f"Redis error: {body}")
	if prefix == b"$":
		length = int(body)
		if length < 0:
			return None
		remaining = length + 2
		data = b""
		while len(data) < remaining:
			data += sock.recv(remaining - len(data))
		return data[:-2].decode()
	if prefix == b"*":
		count = int(body)
		return [read_resp(sock) for _ in range(count)]
	raise RuntimeError(f"Unsupported RESP prefix: {prefix!r}")


def connect_redis(args):
	sock = socket.create_connection((args.host, args.port), timeout=5)
	sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
	if args.password:
		sock.sendall(encode_command("AUTH", args.password))
		read_resp(sock)
	if args.db:
		sock.sendall(encode_command("SELECT", args.db))
		read_resp(sock)
	sock.settimeout(None)
	return sock


def load_stock_pool(path_value, hot_stock_id):
	path = Path(path_value)
	if path.exists():
		data = json.loads(path.read_text())
		stock_ids = data.get("stockIds") if isinstance(data, dict) else None
		if isinstance(stock_ids, list):
			parsed = [int(item) for item in stock_ids if str(item).strip()]
			if parsed:
				return parsed
	return [hot_stock_id]


def resolve_channel(base_channel, stock_id, partition_count):
	if partition_count <= 1:
		return base_channel
	return f"{base_channel}:{stock_id % partition_count}"


def limit_stock_pool(stock_pool, stock_limit, hot_stock_id):
	if stock_limit <= 0 or stock_limit >= len(stock_pool):
		return stock_pool
	limited = stock_pool[:stock_limit]
	if hot_stock_id in stock_pool and hot_stock_id not in limited:
		limited = [hot_stock_id] + limited[:-1]
	return limited


def build_zipf_weights(stock_pool, skew):
	weights = []
	total = 0.0
	for index, _stock_id in enumerate(stock_pool, start=1):
		weight = 1.0 / math.pow(index, skew)
		total += weight
		weights.append(total)
	return [weight / total for weight in weights]


def choose_stock_id(args, stock_pool, zipf_cdf):
	if args.stock_mode == "single-hot":
		return args.hot_stock_id

	if random.random() < args.hot_ratio:
		return args.hot_stock_id
	r = random.random()
	return stock_pool[min(len(stock_pool) - 1, bisect_left(zipf_cdf, r))]


def current_rate(args, elapsed_sec):
	if args.traffic_mode != "burst":
		return args.rate
	burst_end = args.burst_start_sec + args.burst_duration_sec
	if args.burst_start_sec <= elapsed_sec < burst_end:
		return args.burst_rate
	return args.rate


def next_price(previous, volatility):
	delta = random.uniform(-volatility, volatility)
	return max(1.0, previous + delta)


def build_payload(stock_id, prices, volumes, args):
	prev_price = prices.get(stock_id, args.start_price)
	price = next_price(prev_price, args.price_volatility)
	prices[stock_id] = price
	prev_close = max(1.0, price - random.uniform(-max(1.0, args.price_volatility), max(1.0, args.price_volatility)))
	change_pct = ((price - prev_close) / prev_close) * 100.0
	volume = volumes.get(stock_id, args.start_volume) + random.randint(1, 50)
	volumes[stock_id] = volume
	return {
		"stockId": stock_id,
		"close": round(price, 2),
		"prevDayChangePct": round(change_pct, 4),
		"volume": volume,
		"ts": int(time.time() * 1000),
	}


def publish_batch(sock, args, stock_pool, zipf_cdf, prices, volumes, batch_size):
	commands = []
	for _ in range(batch_size):
		stock_id = choose_stock_id(args, stock_pool, zipf_cdf)
		payload = build_payload(stock_id, prices, volumes, args)
		serialized = json.dumps(payload, separators=(",", ":"))
		channel = resolve_channel(args.channel, stock_id, args.channel_partition_count)
		commands.append(encode_command("PUBLISH", channel, serialized))

	sock.sendall(b"".join(commands))
	for _ in range(batch_size):
		read_resp(sock)


def publish_selected_batch(sock, args, selected_stock_ids, prices, volumes):
	commands = []
	for stock_id in selected_stock_ids:
		payload = build_payload(stock_id, prices, volumes, args)
		serialized = json.dumps(payload, separators=(",", ":"))
		channel = resolve_channel(args.channel, stock_id, args.channel_partition_count)
		commands.append(encode_command("PUBLISH", channel, serialized))

	if not commands:
		return 0

	sock.sendall(b"".join(commands))
	for _ in commands:
		read_resp(sock)
	return len(commands)


def publish_per_stock_steady_loop(sock, args, stock_pool):
	prices = {}
	volumes = {}
	start = time.monotonic()
	next_report = start + 1.0
	published = 0
	last_report_published = 0
	next_tick = start

	while True:
		now = time.monotonic()
		elapsed = now - start
		if elapsed >= args.duration_sec:
			break

		if now < next_tick:
			time.sleep(min(0.01, next_tick - now))
			continue

		published += publish_selected_batch(sock, args, stock_pool, prices, volumes)
		next_tick += 1.0

		if now >= next_report:
			interval_count = published - last_report_published
			print(
				f"[publisher] elapsed={elapsed:7.2f}s stock_mode={args.stock_mode} traffic_mode={args.traffic_mode} current_rate={len(stock_pool):.0f}/s published_total={published} published_last_sec={interval_count}",
				flush=True,
			)
			last_report_published = published
			next_report += 1.0

	return published


def publish_loop(sock, args, stock_pool):
	zipf_cdf = build_zipf_weights(stock_pool, args.zipf_skew)
	prices = {}
	volumes = {}
	start = time.monotonic()
	next_report = start + 1.0
	published = 0
	last_report_published = 0

	while True:
		now = time.monotonic()
		elapsed = now - start
		if elapsed >= args.duration_sec:
			break

		rate = current_rate(args, elapsed)
		expected = int(elapsed * rate)
		if published >= expected:
			time.sleep(0.001)
			continue

		remaining = expected - published
		batch_size = min(max(1, args.pipeline_batch_size), remaining)
		publish_batch(sock, args, stock_pool, zipf_cdf, prices, volumes, batch_size)
		published += batch_size

		if now >= next_report:
			interval_count = published - last_report_published
			print(
				f"[publisher] elapsed={elapsed:7.2f}s stock_mode={args.stock_mode} traffic_mode={args.traffic_mode} current_rate={rate:.0f}/s published_total={published} published_last_sec={interval_count}",
				flush=True,
			)
			last_report_published = published
			next_report += 1.0

	return published


def main():
	args = parse_args()
	random.seed(args.seed)
	stock_pool = load_stock_pool(args.stock_pool_file, args.hot_stock_id)
	stock_pool = limit_stock_pool(stock_pool, args.stock_limit, args.hot_stock_id)
	if args.hot_stock_id not in stock_pool:
		stock_pool = [args.hot_stock_id] + stock_pool

	print(
		"\n".join(
			[
				"[publisher] starting direct Redis price publisher",
				f"[publisher] redis={args.host}:{args.port} db={args.db} channel={args.channel}",
				f"[publisher] channel_partition_count={args.channel_partition_count}",
				f"[publisher] stock_mode={args.stock_mode} traffic_mode={args.traffic_mode} duration_sec={args.duration_sec} rate={args.rate}",
				f"[publisher] stock_limit={args.stock_limit}",
				f"[publisher] hot_stock_id={args.hot_stock_id} hot_ratio={args.hot_ratio} zipf_skew={args.zipf_skew}",
				f"[publisher] burst_rate={args.burst_rate} burst_start_sec={args.burst_start_sec} burst_duration_sec={args.burst_duration_sec}",
				f"[publisher] stock_pool_size={len(stock_pool)} seed={args.seed}",
			]
		),
		flush=True,
	)

	try:
		sock = connect_redis(args)
		if args.stock_mode == "per-stock-steady":
			published = publish_per_stock_steady_loop(sock, args, stock_pool)
		else:
			published = publish_loop(sock, args, stock_pool)
		print(f"[publisher] completed published_total={published}", flush=True)
		return 0
	except KeyboardInterrupt:
		print("[publisher] interrupted", flush=True)
		return 130
	except Exception as exc:
		print(f"[publisher] failed: {exc}", file=sys.stderr, flush=True)
		return 1


if __name__ == "__main__":
	sys.exit(main())
