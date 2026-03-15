#!/usr/bin/env python3
"""
scripts/seed/generate_seed_data.py

Optimized dummy data generator for Finvibe backend load testing.
Uses parallel processing, TSV format, and pipelined loading for maximum speed.

Requirements:
    pip install pymysql

.env configuration:
    SEED_DB_HOST=localhost
    SEED_DB_PORT=3306
    SEED_DB_NAME=finvibe
    SEED_DB_USER=finvibe
    SEED_DB_PASSWORD=finvibe
    SEED_PARALLEL_WORKERS=8  # default: CPU count
"""

from __future__ import annotations

import pathlib
import random
import datetime as dt
import io
import os
import multiprocessing as mp
from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor, as_completed
from typing import Any, Callable, Iterator, List, Tuple, Dict
from dataclasses import dataclass
from functools import partial

try:
    import pymysql
    import pymysql.cursors
except ImportError:
    raise SystemExit("pymysql가 필요합니다. 실행: pip install pymysql")


# ── Configuration ───────────────────────────────────────────────────────────

COURSES = 10
LESSONS_PER_COURSE = 5
PERSONAL_CHALLENGES = 20
PORTFOLIOS_PER_USER = 3
ASSETS_PER_PORTFOLIO = 4
TRADES_PER_USER = 40
INTEREST_STOCKS_PER_USER = 10
USERS_PER_SQUAD = 1000
XP_AWARDS_PER_USER = 5
BADGES_PER_USER_AVG = 2
USER_METRIC_FRACTION = 0.10
CHALLENGE_COMPLETION_RATE = 0.30
COURSE_PROGRESS_RATE = 0.40
LESSON_COMPLETE_RATE = 0.60
DISCUSSIONS_PER_NEWS = 3
COMMENTS_PER_DISCUSSION = 3
DISCUSSION_LIKES_PER = 30
COMMENT_LIKES_PER = 10
NEWS_LIKES_PER = 50
XP_RANKING_TOP_N = 200

BADGES = [
    "FIRST_PROFIT",
    "KNOWLEDGE_SEEKER",
    "DILIGENT_INVESTOR",
    "DIVERSIFICATION_MASTER",
    "BEST_DEBATER",
    "PERFECT_SCORE_QUIZ",
    "CHALLENGE_MARATHONER",
    "TOP_ONE_PERCENT_TRAINER",
]
METRIC_TYPES = [
    "LOGIN_COUNT_PER_DAY",
    "CURRENT_RETURN_RATE",
    "STOCK_BUY_COUNT",
    "STOCK_SELL_COUNT",
    "PORTFOLIO_COUNT_WITH_STOCKS",
    "HOLDING_STOCK_COUNT",
    "NEWS_COMMENT_COUNT",
    "NEWS_LIKE_COUNT",
    "DISCUSSION_POST_COUNT",
    "DISCUSSION_COMMENT_COUNT",
    "DISCUSSION_LIKE_COUNT",
    "AI_CONTENT_COMPLETE_COUNT",
    "CHALLENGE_COMPLETION_COUNT",
    "LOGIN_STREAK_DAYS",
    "LAST_LOGIN_DATETIME",
]
COLLECT_PERIODS = ["ALLTIME", "WEEKLY"]
RANKING_PERIODS = ["DAILY", "WEEKLY", "MONTHLY"]
TRANSACTION_TYPES = ["BUY", "SELL"]
TRADE_TYPES = ["NORMAL", "RESERVED", "CANCELLED", "FAILED"]
COURSE_DIFFICULTIES = ["BEGINNER", "INTERMEDIATE", "ADVANCED"]

DATETIME_FMT = "%Y-%m-%d %H:%M:%S"
DATE_FMT = "%Y-%m-%d"
NOW = dt.datetime.now().replace(microsecond=0)
CREATED_FROM = dt.datetime(2023, 1, 1)
DATA_DIR = pathlib.Path(__file__).resolve().parent.parent.parent / "data"

DISCUSSION_CONTENTS = [
    "이 뉴스에 대해 어떻게 생각하시나요?",
    "금리 인상이 주식 시장에 미치는 영향이 클 것 같습니다.",
    "지금 매수 타이밍인지 고민이 되네요.",
    "장기 투자 관점에서는 좋은 기회라고 생각합니다.",
    "단기 변동성이 심해서 조심해야 할 것 같습니다.",
    "분산 투자가 역시 중요한 것 같아요.",
    "경기 침체 우려가 반영된 것 같습니다.",
    "기관 투자자들의 움직임을 잘 봐야 할 것 같아요.",
]
COMMENT_CONTENTS = [
    "동의합니다.",
    "좋은 분석이네요.",
    "저도 같은 생각입니다.",
    "반대 의견입니다.",
    "더 지켜봐야 할 것 같아요.",
    "감사합니다!",
    "좋은 정보 공유 감사해요.",
    "저는 다르게 생각해요.",
]


# ── Environment & Config ───────────────────────────────────────────────────


def load_env() -> dict[str, str]:
    env_path = pathlib.Path(__file__).resolve().parents[2] / ".env"
    env: dict[str, str] = {}
    if env_path.exists():
        for raw in env_path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if line and not line.startswith("#") and "=" in line:
                k, _, v = line.partition("=")
                env[k.strip()] = v.strip()
    return env


def get_db_config() -> dict:
    env = load_env()
    return {
        "host": env.get("SEED_DB_HOST", "localhost"),
        "port": int(env.get("SEED_DB_PORT", 3306)),
        "database": env.get("SEED_DB_NAME", "finvibe"),
        "user": env.get("SEED_DB_USER", "finvibe"),
        "password": env.get("SEED_DB_PASSWORD", "finvibe"),
    }


def get_parallel_workers() -> int:
    env = load_env()
    return int(env.get("SEED_PARALLEL_WORKERS", mp.cpu_count()))


# ── Database Connection ────────────────────────────────────────────────────


def connect(cfg: dict) -> pymysql.Connection:
    return pymysql.connect(
        host=cfg["host"],
        port=cfg["port"],
        user=cfg["user"],
        password=cfg["password"],
        database=cfg["database"],
        charset="utf8mb4",
        local_infile=True,
        autocommit=False,
        ssl_disabled=True,
    )


def optimize_db_for_import(conn: pymysql.Connection) -> None:
    """Temporarily disable constraints for faster bulk loading."""
    with conn.cursor() as cur:
        cur.execute("SET FOREIGN_KEY_CHECKS = 0")
        cur.execute("SET UNIQUE_CHECKS = 0")
        cur.execute("SET AUTOCOMMIT = 0")
    conn.commit()
    print("  [DB] 최적화 적용 (외래키/고유키 검사 비활성화)")


def restore_db_settings(conn: pymysql.Connection) -> None:
    """Restore normal database settings."""
    with conn.cursor() as cur:
        cur.execute("SET FOREIGN_KEY_CHECKS = 1")
        cur.execute("SET UNIQUE_CHECKS = 1")
    conn.commit()
    print("  [DB] 설정 복원 완료")


# ── Utilities ────────────────────────────────────────────────────────────────


def fmt_dt(d: dt.datetime) -> str:
    return d.strftime(DATETIME_FMT)


def fmt_date(d: dt.date) -> str:
    return d.strftime(DATE_FMT)


def null(v: Any) -> str:
    """None → MariaDB NULL marker."""
    return str(v) if v is not None else r"\N"


# ── TSV Generation with StringIO ────────────────────────────────────────────


def escape_tsv(value: Any) -> str:
    """Escape tab and newline characters for TSV format."""
    if value is None:
        return r"\N"
    s = str(value)
    # Replace tabs and newlines with spaces to avoid TSV parsing issues
    s = s.replace("\t", " ").replace("\n", " ").replace("\r", "")
    return s


@dataclass
class TSVWriter:
    """Buffered TSV writer using StringIO for memory efficiency."""

    buffer: io.StringIO
    delimiter: str = "\t"

    def write_row(self, row: tuple) -> None:
        """Write a single row to buffer."""
        line = self.delimiter.join(escape_tsv(v) for v in row) + "\n"
        self.buffer.write(line)

    def getvalue(self) -> str:
        return self.buffer.getvalue()

    def close(self) -> None:
        self.buffer.close()


def write_tsv_file(
    filepath: pathlib.Path, rows: Iterator[tuple], total: int, label: str = ""
) -> int:
    """Write rows to TSV file with buffered writing for speed."""
    filepath.parent.mkdir(parents=True, exist_ok=True)
    buffer = io.StringIO()
    writer = TSVWriter(buffer)
    written = 0

    for row in rows:
        writer.write_row(row)
        written += 1
        if written % 500_000 == 0 and total > 0:
            pct = written * 100 // total
            print(f"    {written:>12,} / {total:,} ({pct}%) [{label}]", flush=True)

    filepath.write_text(writer.getvalue(), encoding="utf-8")
    writer.close()
    return written


# ── Parallel Processing ────────────────────────────────────────────────────


def parallel_generate_csv(
    task_func: Callable, task_args_list: List[tuple], workers: int, label: str = ""
) -> List[pathlib.Path]:
    """Generate multiple CSV files in parallel using process pool."""
    print(f"  [병렬] {len(task_args_list)}개 작업 시작 ({workers}개 워커) [{label}]")

    filepaths = []
    executor = ProcessPoolExecutor(max_workers=workers)
    try:
        futures = {
            executor.submit(task_func, *args): i
            for i, args in enumerate(task_args_list)
        }

        for future in as_completed(futures):
            idx = futures[future]
            try:
                filepath = future.result()
                filepaths.append(filepath)
                print(f"    청크 {idx + 1}/{len(task_args_list)} 완료")
            except Exception as e:
                print(f"    오류 발생 (청크 {idx + 1}): {e}")
                raise
    except KeyboardInterrupt:
        print(f"\n  [병렬] 중단됨 — 워커 취소 중 [{label}]...")
        executor.shutdown(wait=False, cancel_futures=True)
        cleanup_files(filepaths)
        raise
    except Exception:
        executor.shutdown(wait=False, cancel_futures=True)
        cleanup_files(filepaths)
        raise
    else:
        executor.shutdown(wait=True)

    return filepaths


def _load_one_file(cfg: dict, filepath: pathlib.Path, load_sql_template: str) -> int:
    """Worker: opens its own connection, loads one TSV, commits, closes."""
    conn = connect(cfg)
    try:
        sql = load_sql_template.replace("__FILE__", str(filepath).replace("\\", "/"))
        with conn.cursor() as cur:
            cur.execute(sql)
            affected = cur.rowcount
        conn.commit()
        print(f"    {filepath.name} 로드 완료: {affected:,} 행")
        return affected
    except Exception as e:
        conn.rollback()
        print(f"    오류 발생 ({filepath.name} 로드): {e}")
        raise
    finally:
        conn.close()


def parallel_load_csv(
    cfg: dict,
    filepaths: List[pathlib.Path],
    load_sql_template: str,
    workers: int,
) -> int:
    """Load multiple TSV files in parallel, each with its own DB connection."""
    total_affected = 0
    with ThreadPoolExecutor(max_workers=workers) as executor:
        futs = {
            executor.submit(_load_one_file, cfg, fp, load_sql_template): fp
            for fp in filepaths
        }
        for fut in as_completed(futs):
            total_affected += fut.result()  # re-raises on error
    return total_affected


def cleanup_files(filepaths: List[pathlib.Path]) -> None:
    """Delete temporary CSV files."""
    for fp in filepaths:
        fp.unlink(missing_ok=True)


# ── Reference Data Loading ─────────────────────────────────────────────────


def load_ref(conn: pymysql.Connection) -> dict:
    """Load reference data from DB."""
    ref: dict = {}
    with conn.cursor() as cur:
        print("  레퍼런스 데이터 로딩 중...", end=" ", flush=True)

        cur.execute("SELECT id FROM users")
        ref["users"] = [str(row[0]) for row in cur.fetchall()]

        cur.execute("SELECT id, name FROM stock")
        rows = cur.fetchall()
        ref["stock_ids"] = [r[0] for r in rows]
        ref["stock_name"] = {r[0]: r[1] for r in rows}

        cur.execute("SELECT id FROM squad")
        ref["squad_ids"] = [row[0] for row in cur.fetchall()]

        cur.execute("SELECT id FROM news")
        ref["news_ids"] = [row[0] for row in cur.fetchall()]

        print(
            f"유저: {len(ref['users']):,}, 주식: {len(ref['stock_ids']):,}, "
            f"스쿼드: {len(ref['squad_ids']):,}, 뉴스: {len(ref['news_ids']):,}"
        )

    return ref


# ── Generators ─────────────────────────────────────────────────────────────


class RowGenerator:
    """Base class for row generators."""

    def __init__(self, ref: dict, seed: int = 42):
        self.ref = ref
        self.rng = random.Random(seed)

    def generate(self) -> Iterator[tuple]:
        raise NotImplementedError


class InterestStockGenerator(RowGenerator):
    def __init__(self, ref: dict, users: List[str], seed: int = 42):
        super().__init__(ref, seed)
        self.users = users

    def generate(self) -> Iterator[tuple]:
        stock_ids = self.ref["stock_ids"]
        stock_name = self.ref["stock_name"]
        created_at = fmt_dt(NOW)

        for uid in self.users:
            picks = self.rng.sample(
                stock_ids, min(INTEREST_STOCKS_PER_USER, len(stock_ids))
            )
            for sid in picks:
                yield (uid, sid, stock_name[sid], created_at, created_at)


class PortfolioGroupGenerator(RowGenerator):
    def __init__(self, ref: dict, users: List[str], seed: int = 42):
        super().__init__(ref, seed)
        self.users = users

    def generate(self) -> Iterator[tuple]:
        icon_codes = ["icon_01", "icon_02", "icon_03", "icon_04", "icon_05"]
        now_s = fmt_dt(NOW)

        for uid in self.users:
            for p in range(PORTFOLIOS_PER_USER):
                is_default = 1 if p == 0 else 0
                yield (
                    uid,
                    f"Portfolio {p + 1}",
                    self.rng.choice(icon_codes),
                    is_default,
                    "0.00",
                    "0.00",
                    "0.00",
                    null(None),
                    now_s,
                    now_s,
                )


class AssetGenerator(RowGenerator):
    def __init__(self, ref: dict, pg_data: List[tuple], seed: int = 42):
        super().__init__(ref, seed)
        self.pg_data = pg_data  # (pg_id, user_id)

    def generate(self) -> Iterator[tuple]:
        stock_ids = self.ref["stock_ids"]
        stock_name = self.ref["stock_name"]
        now_s = fmt_dt(NOW)

        for pg_id, uid in self.pg_data:
            picks = self.rng.sample(
                stock_ids, min(ASSETS_PER_PORTFOLIO, len(stock_ids))
            )
            for sid in picks:
                amount = round(self.rng.uniform(1, 100), 2)
                price = self.rng.randint(1000, 500_000)
                total_price = round(amount * price, 2)
                cur_val = round(total_price * self.rng.uniform(0.8, 1.5), 2)
                profit = round(cur_val - total_price, 2)
                ret_rate = round((profit / total_price) * 100, 4) if total_price else 0
                yield (
                    pg_id,
                    uid,
                    sid,
                    stock_name[sid],
                    amount,
                    total_price,
                    "KRW",
                    cur_val,
                    profit,
                    ret_rate,
                    now_s,
                    now_s,
                    now_s,
                )


class TradeGenerator(RowGenerator):
    def __init__(
        self, ref: dict, users: List[str], pg_data: List[tuple], seed: int = 42
    ):
        super().__init__(ref, seed)
        self.users = users
        self.pg_data = pg_data
        self.user_to_pgs: Dict[str, List[int]] = {}
        for pg_id, uid in pg_data:
            self.user_to_pgs.setdefault(uid, []).append(pg_id)

    def generate(self) -> Iterator[tuple]:
        stock_ids = self.ref["stock_ids"]
        stock_name = self.ref["stock_name"]
        now_s = fmt_dt(NOW)

        for uid in self.users:
            pg_ids = self.user_to_pgs.get(uid, [])
            if not pg_ids:
                continue
            for _ in range(TRADES_PER_USER):
                sid = self.rng.choice(stock_ids)
                pg_id = self.rng.choice(pg_ids)
                yield (
                    sid,
                    stock_name[sid],
                    round(self.rng.uniform(1, 50), 2),
                    self.rng.randint(1000, 500_000),
                    pg_id,
                    uid,
                    self.rng.choice(TRANSACTION_TYPES),
                    self.rng.choice(TRADE_TYPES),
                    now_s,
                    now_s,
                )


# ── Chunked Generation Workers ───────────────────────────────────────────


def generate_interest_stock_chunk(
    ref_dict: dict, user_chunk: List[str], seed: int, chunk_idx: int
) -> pathlib.Path:
    """Worker function to generate interest_stock TSV chunk."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    filepath = DATA_DIR / f"interest_stock_{chunk_idx}.tsv"
    generator = InterestStockGenerator(ref_dict, user_chunk, seed + chunk_idx)
    total = len(user_chunk) * INTEREST_STOCKS_PER_USER
    write_tsv_file(filepath, generator.generate(), total, f"interest_stock_{chunk_idx}")
    return filepath


def generate_portfolio_group_chunk(
    ref_dict: dict, user_chunk: List[str], seed: int, chunk_idx: int
) -> pathlib.Path:
    """Worker function to generate portfolio_group TSV chunk."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    filepath = DATA_DIR / f"portfolio_group_{chunk_idx}.tsv"
    generator = PortfolioGroupGenerator(ref_dict, user_chunk, seed + chunk_idx)
    total = len(user_chunk) * PORTFOLIOS_PER_USER
    write_tsv_file(
        filepath, generator.generate(), total, f"portfolio_group_{chunk_idx}"
    )
    return filepath


def generate_asset_chunk(
    ref_dict: dict, pg_chunk: List[tuple], seed: int, chunk_idx: int
) -> pathlib.Path:
    """Worker function to generate asset TSV chunk."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    filepath = DATA_DIR / f"asset_{chunk_idx}.tsv"
    generator = AssetGenerator(ref_dict, pg_chunk, seed + chunk_idx)
    total = len(pg_chunk) * ASSETS_PER_PORTFOLIO
    write_tsv_file(filepath, generator.generate(), total, f"asset_{chunk_idx}")
    return filepath


def generate_trade_chunk(
    ref_dict: dict,
    user_chunk: List[str],
    pg_data: List[tuple],
    seed: int,
    chunk_idx: int,
) -> pathlib.Path:
    """Worker function to generate trade TSV chunk."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    filepath = DATA_DIR / f"trade_{chunk_idx}.tsv"
    generator = TradeGenerator(ref_dict, user_chunk, pg_data, seed + chunk_idx)
    total = len(user_chunk) * TRADES_PER_USER
    write_tsv_file(filepath, generator.generate(), total, f"trade_{chunk_idx}")
    return filepath


def generate_course_progress_chunk(
    user_chunk: List[str],
    course_ids: List[int],
    course_lesson_map: dict,
    seed: int,
    chunk_idx: int,
) -> pathlib.Path:
    """Worker function to generate course_progress TSV chunk."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    filepath = DATA_DIR / f"course_progress_{chunk_idx}.tsv"
    rng = random.Random(seed + chunk_idx)
    now_s = fmt_dt(NOW)

    def rows():
        for uid in user_chunk:
            for cid in course_ids:
                n_total = len(course_lesson_map[cid])
                n_done = rng.randint(0, n_total)
                key = f"{cid}_{uid}"
                yield (cid, uid, n_done, n_total, key, now_s, now_s)

    total = len(user_chunk) * len(course_ids)
    write_tsv_file(filepath, rows(), total, f"course_progress_{chunk_idx}")
    return filepath


def generate_lesson_complete_chunk(
    user_chunk: List[str],
    all_lesson_ids: List[int],
    seed: int,
    chunk_idx: int,
) -> pathlib.Path:
    """Worker function to generate lesson_complete TSV chunk."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    filepath = DATA_DIR / f"lesson_complete_{chunk_idx}.tsv"
    now_s = fmt_dt(NOW)

    def rows():
        for uid in user_chunk:
            for lid in all_lesson_ids:
                key = f"{lid}_{uid}"
                yield (lid, uid, key, now_s, now_s)

    total = len(user_chunk) * len(all_lesson_ids)
    write_tsv_file(filepath, rows(), total, f"lesson_complete_{chunk_idx}")
    return filepath


# ── Load SQL Templates ───────────────────────────────────────────────────

LOAD_SQL_TEMPLATES = {
    "interest_stock": """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE interest_stock
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (user_id, stock_id, stock_name, created_at, last_modified_at)
    """,
    "portfolio_group": """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE portfolio_group
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (user_id, name, icon_code, is_default,
         total_current_value, total_profit_loss, total_return_rate,
         portfolio_valuation_calculated_at, created_at, last_modified_at)
    """,
    "asset": """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE asset
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (portfolio_group_id, user_id, stock_id, name, amount,
         total_price_amount, total_price_currency,
         current_value, profit_loss, return_rate, valuation_calculated_at,
         created_at, last_modified_at)
    """,
    "trade": """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE trade
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (stock_id, stock_name, amount, price, portfolio_id, user_id,
         transaction_type, trade_type, created_at, last_modified_at)
    """,
    "course_progress": """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE course_progress
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (course_id, user_id, completed_lesson_count, total_lesson_count, course_user_id_key, created_at, last_modified_at)
    """,
    "lesson_complete": """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE lesson_complete
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (lesson_id, user_id, lesson_user_id_key, created_at, last_modified_at)
    """,
}


# ── Optimized Main Generators ─────────────────────────────────────────────


def gen_interest_stock_optimized(
    conn: pymysql.Connection, cfg: dict, ref: dict, workers: int, seed: int
) -> int:
    """Generate interest_stock with parallel processing."""
    print("\n  [interest_stock] 병렬 생성 중...")
    users = ref["users"]
    if not ref["stock_ids"]:
        print("    스킵 — 주식 데이터 없음")
        return 0

    # Split users into chunks
    chunk_size = max(1, len(users) // workers)
    chunks = [users[i : i + chunk_size] for i in range(0, len(users), chunk_size)]

    # Prepare tasks
    task_args = [(ref, chunk, seed, i) for i, chunk in enumerate(chunks)]

    # Generate in parallel
    filepaths = parallel_generate_csv(
        generate_interest_stock_chunk, task_args, workers, "interest_stock"
    )

    # Load in parallel (each thread uses its own connection)
    print(f"  파일 {len(filepaths)}개 로드 중...")
    total = parallel_load_csv(
        cfg, filepaths, LOAD_SQL_TEMPLATES["interest_stock"], min(workers, 4)
    )

    cleanup_files(filepaths)
    print(f"  완료: {total:,} 행")
    return total


def gen_portfolio_asset_trade_optimized(
    conn: pymysql.Connection, cfg: dict, ref: dict, workers: int, seed: int
) -> int:
    """Generate portfolio_group, asset, trade with optimized pipeline."""
    total_rows = 0
    users = ref["users"]

    if not ref["stock_ids"]:
        print("    스킵 — 주식 데이터 없음")
        return 0

    # Step 1: Generate portfolio_group
    print("\n  [1/3] portfolio_group - 병렬 생성 중...")
    chunk_size = max(1, len(users) // workers)
    chunks = [users[i : i + chunk_size] for i in range(0, len(users), chunk_size)]
    task_args = [(ref, chunk, seed, i) for i, chunk in enumerate(chunks)]

    pg_filepaths = parallel_generate_csv(
        generate_portfolio_group_chunk, task_args, workers, "portfolio_group"
    )
    total = parallel_load_csv(
        cfg, pg_filepaths, LOAD_SQL_TEMPLATES["portfolio_group"], min(workers, 4)
    )
    cleanup_files(pg_filepaths)
    total_rows += total
    print(f"    완료: {total:,} 행")

    # Step 2: Load portfolio_group IDs for asset FK
    print("\n  portfolio_group ID 로딩 중...")
    with conn.cursor() as cur:
        pg_total = len(users) * PORTFOLIOS_PER_USER
        cur.execute(
            "SELECT id, user_id FROM portfolio_group ORDER BY id ASC LIMIT %s",
            (pg_total,),
        )
        pg_data = cur.fetchall()

    # Convert to list of tuples (pg_id, user_id)
    pg_list = [(row[0], str(row[1])) for row in pg_data]
    print(f"    portfolio_group ID {len(pg_list):,}개 로드 완료")

    # Step 3: Generate and load asset (parallel)
    print("\n  [2/3] asset - 병렬 생성 중...")
    chunk_size = max(1, len(pg_list) // workers)
    pg_chunks = [
        pg_list[i : i + chunk_size] for i in range(0, len(pg_list), chunk_size)
    ]
    asset_task_args = [(ref, chunk, seed, i) for i, chunk in enumerate(pg_chunks)]

    asset_filepaths = parallel_generate_csv(
        generate_asset_chunk, asset_task_args, workers, "asset"
    )
    total = parallel_load_csv(
        cfg, asset_filepaths, LOAD_SQL_TEMPLATES["asset"], min(workers, 4)
    )
    cleanup_files(asset_filepaths)
    total_rows += total
    print(f"    완료: {total:,} 행")

    # Step 4: Generate and load trade (parallel)
    print("\n  [3/3] trade - 병렬 생성 중...")
    chunk_size = max(1, len(users) // workers)
    user_chunks = [users[i : i + chunk_size] for i in range(0, len(users), chunk_size)]
    trade_task_args = [
        (ref, chunk, pg_list, seed, i) for i, chunk in enumerate(user_chunks)
    ]

    trade_filepaths = parallel_generate_csv(
        generate_trade_chunk, trade_task_args, workers, "trade"
    )
    total = parallel_load_csv(
        cfg, trade_filepaths, LOAD_SQL_TEMPLATES["trade"], min(workers, 4)
    )
    cleanup_files(trade_filepaths)
    total_rows += total
    print(f"    완료: {total:,} 행")

    return total_rows


# ── Simple Generators (small tables) ─────────────────────────────────────


def gen_personal_challenge(conn: pymysql.Connection, rng: random.Random) -> list[int]:
    """Small table - direct INSERT."""
    print("\n  [personal_challenge] 직접 INSERT 중...")
    rows = []
    for i in range(PERSONAL_CHALLENGES):
        metric = rng.choice(METRIC_TYPES[:10])
        target = round(rng.uniform(5, 100), 1)
        start = CREATED_FROM.date() + dt.timedelta(days=rng.randint(0, 300))
        end = start + dt.timedelta(days=rng.randint(7, 30))
        reward_xp = rng.choice([50, 100, 200, 300, 500])
        reward_badge = rng.choice(BADGES) if rng.random() < 0.4 else None
        now_s = fmt_dt(NOW)
        rows.append(
            (
                f"Challenge {i + 1}: {metric} achieved",
                f"Dummy challenge description {i + 1}",
                metric,
                target,
                fmt_date(start),
                fmt_date(end),
                reward_xp,
                reward_badge,
                now_s,
                now_s,
            )
        )

    sql = """
        INSERT INTO personal_challenge
            (title, description, metric_type, target_value,
             start_date, end_date, reward_xp, reward_badge,
             created_at, last_modified_at)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """
    with conn.cursor() as cur:
        cur.executemany(sql, rows)
        cur.execute(
            "SELECT id FROM personal_challenge ORDER BY id DESC LIMIT %s",
            (PERSONAL_CHALLENGES,),
        )
        ids = [row[0] for row in cur.fetchall()]
    conn.commit()
    print(f"    완료: {PERSONAL_CHALLENGES} 행")
    return ids


def gen_course_lesson(conn: pymysql.Connection, rng: random.Random) -> dict:
    """Course and lessons - direct INSERT."""
    print("\n  [course/lesson] 직접 INSERT 중...")
    course_rows = []
    for i in range(COURSES):
        diff = COURSE_DIFFICULTIES[i % len(COURSE_DIFFICULTIES)]
        now_s = fmt_dt(NOW)
        course_rows.append(
            (
                f"Dummy Course {i + 1}: Investment Basics {diff}",
                f"This is dummy course {i + 1} for investment learning.",
                diff,
                None,
                False,
                LESSONS_PER_COURSE,
                now_s,
                now_s,
            )
        )

    course_sql = """
        INSERT INTO course
            (title, description, difficulty, owner, is_global, total_lesson_count,
             created_at, last_modified_at)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
    """
    with conn.cursor() as cur:
        cur.executemany(course_sql, course_rows)
        cur.execute("SELECT id FROM course ORDER BY id DESC LIMIT %s", (COURSES,))
        course_ids = list(reversed([row[0] for row in cur.fetchall()]))

    lesson_ids = []
    course_lesson_map: dict[int, list[int]] = {}
    content_sql = "INSERT INTO lesson_content (id, content) VALUES (%s, %s)"
    lesson_sql = "INSERT INTO lesson (course_id, title, description, content_id) VALUES (%s,%s,%s,%s)"

    with conn.cursor() as cur:
        for cid in course_ids:
            course_lesson_map[cid] = []
            for j in range(LESSONS_PER_COURSE):
                cur.execute("SELECT NEXTVAL(lesson_content_seq)")
                result = cur.fetchone()
                if result is None:
                    raise RuntimeError("Failed to get nextval from lesson_content_seq")
                content_id = result[0]
                cur.execute(
                    content_sql,
                    (
                        content_id,
                        f"Dummy lesson content (course={cid}, lesson={j + 1}).",
                    ),
                )
                cur.execute(
                    lesson_sql,
                    (cid, f"Lesson {j + 1}", f"Lesson {j + 1} description", content_id),
                )
                lid = cur.lastrowid
                lesson_ids.append(lid)
                course_lesson_map[cid].append(lid)

    conn.commit()
    print(f"    완료: 코스 {COURSES}개, 레슨 {len(lesson_ids)}개")
    return {
        "course_ids": course_ids,
        "lesson_ids": lesson_ids,
        "course_lesson_map": course_lesson_map,
    }


# ── User Squad, XP, Badge, Metric Generators ───────────────────────────────


def write_tsv_and_load(
    conn: pymysql.Connection,
    filename: str,
    rows_iter,
    total: int,
    load_sql: str,
    label: str = "",
) -> int:
    """Write TSV and load with progress tracking."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    filepath = DATA_DIR / filename
    print(f"  TSV 생성 중: {filepath}")
    written = write_tsv_file(filepath, rows_iter, total, label)
    print(f"  DB 적재 중: {written:,} 행...")
    sql = load_sql.replace("__FILE__", str(filepath).replace("\\", "/"))
    with conn.cursor() as cur:
        cur.execute(sql)
        affected = cur.rowcount
    conn.commit()
    filepath.unlink(missing_ok=True)
    print(f"  완료: {affected:,} 행")
    return affected


def gen_user_squad(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    print("\n  [user_squad] TSV 생성 중...")
    users = ref["users"]
    squad_ids = ref["squad_ids"]
    if not squad_ids:
        print("    스킵 — 스쿼드 데이터 없음")
        return 0

    total_needed = min(len(squad_ids) * USERS_PER_SQUAD, len(users))
    shuffled = list(users[:total_needed])
    rng.shuffle(shuffled)

    def rows():
        idx = 0
        for squad_id in squad_ids:
            for _ in range(USERS_PER_SQUAD):
                if idx >= len(shuffled):
                    return
                yield (shuffled[idx], squad_id)
                idx += 1

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE user_squad
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (user_id, squad_id)
    """
    return write_tsv_and_load(
        conn, "user_squad.tsv", rows(), total_needed, load_sql, "user_squad"
    )


def gen_user_xp_and_awards(
    conn: pymysql.Connection, ref: dict, rng: random.Random
) -> None:
    print("\n  [user_xp + user_xp_award] TSV 생성 중...")
    users = ref["users"]
    xp_reasons = [
        "Lesson completed",
        "Challenge achieved",
        "Discussion participated",
        "First stock purchase",
        "Consecutive login",
        "Quiz passed",
        "News liked",
        "Portfolio profit achieved",
    ]

    def xp_rows():
        for idx, uid in enumerate(users):
            nickname = f"fv{idx:07d}"
            total_xp = rng.randint(0, 50_000)
            weekly_xp = rng.randint(0, min(total_xp, 5_000))
            level = max(1, total_xp // 1_000)
            yield (uid, nickname, total_xp, weekly_xp, level)

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE user_xp
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (user_id, nickname, total_xp, weekly_xp, level)
    """
    write_tsv_and_load(conn, "user_xp.tsv", xp_rows(), len(users), load_sql, "user_xp")

    award_total = len(users) * XP_AWARDS_PER_USER

    def award_rows():
        now_s = fmt_dt(NOW)
        for uid in users:
            for _ in range(XP_AWARDS_PER_USER):
                yield (uid, rng.randint(10, 500), rng.choice(xp_reasons), now_s, now_s)

    award_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE user_xp_award
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (user_id, value, reason, created_at, last_modified_at)
    """
    write_tsv_and_load(
        conn, "user_xp_award.tsv", award_rows(), award_total, award_sql, "user_xp_award"
    )


def gen_badge_ownership(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    print("\n  [badge_ownership] TSV 생성 중...")
    users = ref["users"]

    def rows():
        for uid in users:
            n = rng.randint(1, min(4, len(BADGES)))
            for badge in rng.sample(BADGES, n):
                yield (badge, uid)

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE badge_ownership
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (badge, owner_id)
    """
    return write_tsv_and_load(
        conn,
        "badge_ownership.tsv",
        rows(),
        len(users) * BADGES_PER_USER_AVG,
        load_sql,
        "badge_ownership",
    )


def gen_user_metric(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    print("\n  [user_metric] TSV 생성 중...")
    users = ref["users"]
    sample_size = max(1, int(len(users) * USER_METRIC_FRACTION))
    sampled = rng.sample(users, sample_size)
    total = sample_size * len(METRIC_TYPES) * len(COLLECT_PERIODS)

    def rows():
        for uid in sampled:
            for metric in METRIC_TYPES:
                for period in COLLECT_PERIODS:
                    yield (metric, uid, period, round(rng.uniform(0, 100), 4))

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE user_metric
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (type, user_id, collect_period, value)
    """
    return write_tsv_and_load(
        conn, "user_metric.tsv", rows(), total, load_sql, "user_metric"
    )


def gen_challenge_reward(
    conn: pymysql.Connection, ref: dict, rng: random.Random, challenge_ids: list[int]
) -> int:
    print("\n  [personal_challenge_reward] TSV 생성 중...")
    if not challenge_ids:
        print("    스킵 — 챌린지 ID 없음")
        return 0

    users = ref["users"]
    sample_size = max(1, int(len(users) * CHALLENGE_COMPLETION_RATE))
    sampled = rng.sample(users, sample_size)
    total = sample_size * len(challenge_ids)

    def rows():
        now_s = fmt_dt(NOW)
        for uid in sampled:
            for cid in challenge_ids:
                start = (CREATED_FROM + dt.timedelta(days=rng.randint(0, 300))).date()
                end = start + dt.timedelta(days=rng.randint(7, 30))
                reward_xp = rng.choice([50, 100, 200, 300, 500])
                reward_badge = null(rng.choice(BADGES) if rng.random() < 0.3 else None)
                yield (
                    cid,
                    uid,
                    fmt_date(start),
                    fmt_date(end),
                    reward_xp,
                    reward_badge,
                    now_s,
                    now_s,
                )

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE personal_challenge_reward
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (challenge_id, user_id, start_date, end_date, reward_xp, reward_badge, created_at, last_modified_at)
    """
    return write_tsv_and_load(
        conn,
        "personal_challenge_reward.tsv",
        rows(),
        total,
        load_sql,
        "challenge_reward",
    )


def gen_study_metric(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    print("\n  [study_metric] TSV 생성 중...")
    users = ref["users"]

    def rows():
        base_ts = int(NOW.timestamp())
        for uid in users:
            xp = rng.randint(0, 10_000)
            time_min = rng.randint(0, 3_000)
            last_ping = base_ts - rng.randint(0, 86_400 * 30)
            yield (uid, xp, time_min, last_ping)

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE study_metric
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (user_id, xp_earned, time_spent_minutes, last_ping_at)
    """
    return write_tsv_and_load(
        conn, "study_metric.tsv", rows(), len(users), load_sql, "study_metric"
    )


def gen_course_progress(
    cfg: dict,
    ref: dict,
    rng: random.Random,
    course_data: dict,
    workers: int = 4,
) -> None:
    print("\n  [course_progress] 병렬 생성 중...")
    if not course_data:
        print("    스킵 — 코스 데이터 없음")
        return

    users = ref["users"]
    course_ids = course_data["course_ids"]
    course_lesson_map = course_data["course_lesson_map"]
    seed = rng.randint(0, 2**31)

    cp_sample_size = max(1, int(len(users) * COURSE_PROGRESS_RATE))
    cp_sampled = rng.sample(users, cp_sample_size)

    chunk_size = max(1, len(cp_sampled) // workers)
    cp_chunks = [
        cp_sampled[i : i + chunk_size] for i in range(0, len(cp_sampled), chunk_size)
    ]
    cp_task_args = [
        (chunk, course_ids, course_lesson_map, seed, i)
        for i, chunk in enumerate(cp_chunks)
    ]

    cp_filepaths = parallel_generate_csv(
        generate_course_progress_chunk, cp_task_args, workers, "course_progress"
    )
    total = parallel_load_csv(
        cfg, cp_filepaths, LOAD_SQL_TEMPLATES["course_progress"], min(workers, 4)
    )
    cleanup_files(cp_filepaths)
    print(f"    완료: {total:,} 행")


def gen_lesson_complete(
    cfg: dict,
    ref: dict,
    rng: random.Random,
    course_data: dict,
    workers: int = 4,
) -> None:
    print("\n  [lesson_complete] 병렬 생성 중...")
    if not course_data:
        print("    스킵 — 코스 데이터 없음")
        return

    users = ref["users"]
    all_lesson_ids = course_data["lesson_ids"]
    seed = rng.randint(0, 2**31)

    lc_sample_size = max(1, int(len(users) * LESSON_COMPLETE_RATE))
    lc_sampled = rng.sample(users, lc_sample_size)

    chunk_size = max(1, len(lc_sampled) // workers)
    lc_chunks = [
        lc_sampled[i : i + chunk_size] for i in range(0, len(lc_sampled), chunk_size)
    ]
    lc_task_args = [
        (chunk, all_lesson_ids, seed, i) for i, chunk in enumerate(lc_chunks)
    ]

    lc_filepaths = parallel_generate_csv(
        generate_lesson_complete_chunk, lc_task_args, workers, "lesson_complete"
    )
    total = parallel_load_csv(
        cfg, lc_filepaths, LOAD_SQL_TEMPLATES["lesson_complete"], min(workers, 4)
    )
    cleanup_files(lc_filepaths)
    print(f"    완료: {total:,} 행")


def gen_discussion_social(
    conn: pymysql.Connection, ref: dict, rng: random.Random
) -> None:
    print("\n  [discussion + comment + like] TSV 생성 중...")
    users = ref["users"]
    news_ids = ref["news_ids"]
    if not news_ids:
        print("    스킵 — 뉴스 데이터 없음")
        return

    disc_total = len(news_ids) * DISCUSSIONS_PER_NEWS

    def disc_rows():
        now_s = fmt_dt(NOW)
        for nid in news_ids:
            for _ in range(rng.randint(2, DISCUSSIONS_PER_NEWS)):
                yield (
                    nid,
                    rng.choice(users),
                    rng.choice(DISCUSSION_CONTENTS),
                    0,
                    now_s,
                    now_s,
                )

    disc_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE discussion
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (news_id, user_id, content, is_edited, created_at, last_modified_at)
    """
    write_tsv_and_load(
        conn, "discussion.tsv", disc_rows(), disc_total, disc_load_sql, "discussion"
    )

    with conn.cursor() as cur:
        cur.execute("SELECT id FROM discussion")
        discussion_ids = [row[0] for row in cur.fetchall()]
    print(f"    토론 ID {len(discussion_ids):,}개 로드 완료")

    comment_total = len(discussion_ids) * COMMENTS_PER_DISCUSSION

    def comment_rows():
        now_s = fmt_dt(NOW)
        for did in discussion_ids:
            for _ in range(rng.randint(2, COMMENTS_PER_DISCUSSION)):
                yield (
                    did,
                    rng.choice(users),
                    rng.choice(COMMENT_CONTENTS),
                    0,
                    now_s,
                    now_s,
                )

    comment_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE discussion_comment
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (discussion_id, user_id, content, is_edited, created_at, last_modified_at)
    """
    write_tsv_and_load(
        conn,
        "discussion_comment.tsv",
        comment_rows(),
        comment_total,
        comment_load_sql,
        "disc_comment",
    )

    with conn.cursor() as cur:
        cur.execute("SELECT id FROM discussion_comment")
        comment_ids = [row[0] for row in cur.fetchall()]
    print(f"    댓글 ID {len(comment_ids):,}개 로드 완료")

    dl_total = len(discussion_ids) * DISCUSSION_LIKES_PER

    def dl_rows():
        now_s = fmt_dt(NOW)
        for did in discussion_ids:
            for uid in rng.sample(users, min(DISCUSSION_LIKES_PER, len(users))):
                yield (did, uid, now_s, now_s)

    dl_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE discussion_like
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (discussion_id, user_id, created_at, last_modified_at)
    """
    write_tsv_and_load(
        conn, "discussion_like.tsv", dl_rows(), dl_total, dl_load_sql, "disc_like"
    )

    cl_total = len(comment_ids) * COMMENT_LIKES_PER

    def cl_rows():
        now_s = fmt_dt(NOW)
        for cid in comment_ids:
            for uid in rng.sample(users, min(COMMENT_LIKES_PER, len(users))):
                yield (cid, uid, now_s, now_s)

    cl_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE discussion_comment_like
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (comment_id, user_id, created_at, last_modified_at)
    """
    write_tsv_and_load(
        conn,
        "discussion_comment_like.tsv",
        cl_rows(),
        cl_total,
        cl_load_sql,
        "comment_like",
    )


def gen_news_like(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    print("\n  [news_like] TSV 생성 중...")
    users = ref["users"]
    news_ids = ref["news_ids"]
    if not news_ids:
        print("    스킵 — 뉴스 데이터 없음")
        return 0

    total = len(news_ids) * NEWS_LIKES_PER

    def rows():
        now_s = fmt_dt(NOW)
        for nid in news_ids:
            for uid in rng.sample(users, min(NEWS_LIKES_PER, len(users))):
                yield (nid, uid, now_s, now_s)

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE news_like
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (news_id, user_id, created_at, last_modified_at)
    """
    return write_tsv_and_load(
        conn, "news_like.tsv", rows(), total, load_sql, "news_like"
    )


def gen_xp_ranking_snapshot(conn: pymysql.Connection, rng: random.Random) -> int:
    print("\n  [user_xp_ranking_snapshot] TSV 생성 중...")
    import calendar

    with conn.cursor() as cur:
        cur.execute(
            "SELECT user_id, nickname, total_xp, weekly_xp FROM user_xp ORDER BY total_xp DESC LIMIT %s",
            (XP_RANKING_TOP_N,),
        )
        top_users = cur.fetchall()

    if not top_users:
        print("    스킵 — user_xp 데이터 없음")
        return 0

    today = NOW.date()
    snapshots = []

    for period_type in RANKING_PERIODS:
        if period_type == "DAILY":
            periods = [
                (today - dt.timedelta(days=i), today - dt.timedelta(days=i))
                for i in range(7)
            ]
        elif period_type == "WEEKLY":
            periods = []
            for w in range(4):
                start = today - dt.timedelta(weeks=w + 1)
                start -= dt.timedelta(days=start.weekday())
                periods.append((start, start + dt.timedelta(days=6)))
        else:  # MONTHLY
            periods = []
            for m in range(3):
                ref_d = today.replace(day=1) - dt.timedelta(days=m * 28)
                start = ref_d.replace(day=1)
                end = start.replace(day=calendar.monthrange(start.year, start.month)[1])
                periods.append((start, end))

        for p_start, p_end in periods:
            for rank, (uid_val, nickname, total_xp, weekly_xp) in enumerate(
                top_users, 1
            ):
                uid = str(uid_val)
                period_xp = rng.randint(0, min(total_xp, 5000))
                prev_xp = rng.randint(0, min(total_xp, 5000))
                growth = round(
                    ((period_xp - prev_xp) / prev_xp * 100) if prev_xp else 0, 2
                )
                snapshots.append(
                    (
                        uid,
                        period_type,
                        fmt_date(p_start),
                        fmt_date(p_end),
                        nickname,
                        rank,
                        total_xp,
                        period_xp,
                        prev_xp,
                        growth,
                        fmt_dt(NOW),
                        fmt_dt(NOW),
                        fmt_dt(NOW),
                    )
                )

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE user_xp_ranking_snapshot
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY '\t'
        LINES TERMINATED BY '\n'
        (user_id, period_type, period_start_date, period_end_date, nickname, ranking,
         current_total_xp, period_xp, previous_period_xp, growth_rate, snapshot_at, created_at, last_modified_at)
    """
    return write_tsv_and_load(
        conn,
        "xp_ranking_snapshot.tsv",
        iter(snapshots),
        len(snapshots),
        load_sql,
        "xp_ranking",
    )


# ── Menu & Main ────────────────────────────────────────────────────────────


def print_menu(ref: dict, cfg: dict, workers: int) -> None:
    n_users = len(ref.get("users", []))
    n_stocks = len(ref.get("stock_ids", []))
    n_squads = len(ref.get("squad_ids", []))
    n_news = len(ref.get("news_ids", []))

    print()
    print("╔════════════════════════════════════════════════════════════════╗")
    print("║         Finvibe 더미 데이터 생성기 (최적화 버전)             ║")
    print("╚════════════════════════════════════════════════════════════════╝")
    print(f"  DB: {cfg['user']}@{cfg['host']}:{cfg['port']}/{cfg['database']}")
    print(f"  병렬 워커 수: {workers}")
    print(
        f"  레퍼런스: 유저 {n_users:,} | 주식 {n_stocks:,} | 스쿼드 {n_squads:,} | 뉴스 {n_news:,}"
    )
    print()
    print("  ── 콘텐츠 ─────────────────────────────────────────────────────")
    print(f"   1. personal_challenge                ~{PERSONAL_CHALLENGES} 행")
    print(
        f"   2. course + lesson + lesson_content  {COURSES}개 코스 × {LESSONS_PER_COURSE}개 레슨"
    )
    print()
    print("  ── 유저 활동 (병렬) ─────────────────────────────────────────")
    print(
        f"   3. interest_stock                    ~{n_users * INTEREST_STOCKS_PER_USER:,} 행 (병렬)"
    )
    print(
        f"   4. portfolio_group + asset + trade   ~{n_users * PORTFOLIOS_PER_USER:,} / "
        f"~{n_users * PORTFOLIOS_PER_USER * ASSETS_PER_PORTFOLIO:,} / "
        f"~{n_users * TRADES_PER_USER:,} 행 (병렬)"
    )
    print()
    print("  ── 게이미피케이션 ────────────────────────────────────────────")
    print(f"   5. user_squad                        ~{n_squads * USERS_PER_SQUAD:,} 행")
    print(
        f"   6. user_xp + user_xp_award           ~{n_users:,} + ~{n_users * XP_AWARDS_PER_USER:,} 행"
    )
    print(
        f"   7. badge_ownership                   ~{n_users * BADGES_PER_USER_AVG:,} 행"
    )
    print(
        f"   8. user_metric                       ~{int(n_users * USER_METRIC_FRACTION) * len(METRIC_TYPES) * 2:,} 행"
    )
    print(f"   9. personal_challenge_reward         challenge_id 필요")
    print(f"  10. user_xp_ranking_snapshot          상위 {XP_RANKING_TOP_N}명 × 기간")
    print()
    print("  ── 학습 ──────────────────────────────────────────────────────")
    print(f"  11. study_metric                      ~{n_users:,} 행")
    print(f"  12. course_progress                   course/lesson ID 필요 (병렬)")
    print(f"  13. lesson_complete                   course/lesson ID 필요 (병렬)")
    print()
    print("  ── 소셜 ──────────────────────────────────────────────────────")
    print(
        f"  14. discussion + comment + like      ~{n_news * DISCUSSIONS_PER_NEWS:,} 행"
    )
    print(f"  15. news_like                         ~{n_news * NEWS_LIKES_PER:,} 행")
    print()
    print("   0. 전체 실행 (최적화 병렬)")
    print()
    print("  ── 범위 실행 ─────────────────────────────────────────────────")
    print("   N   N번 스텝만 실행          (예: 5)")
    print("   N~  N번 스텝부터 끝까지 실행  (예: 5~)")
    print()
    print("   q. 종료")
    print()


def confirm(msg: str) -> bool:
    return input(f"  {msg} [y/N] ").strip().lower() == "y"


def run_all_optimized(
    conn: pymysql.Connection, cfg: dict, ref: dict, workers: int, seed: int
) -> None:
    """Run all generators with optimized parallel execution."""
    print("\n" + "=" * 60)
    print("  전체 데이터 생성 시작")
    print("  DB 최적화 적용: 외래키/고유키 검사 비활성화")
    print("  병렬 워커 수:", workers)
    print("=" * 60 + "\n")

    start_time = dt.datetime.now()

    # Apply DB optimizations
    optimize_db_for_import(conn)

    # Phase 1: Independent tables (can run in parallel)
    print("\n" + "=" * 60)
    print("  페이즈 1: 독립 테이블")
    print("=" * 60)

    challenge_ids = gen_personal_challenge(conn, random.Random(seed))
    course_data = gen_course_lesson(conn, random.Random(seed))

    # Phase 2: Large tables with parallel processing
    print("\n" + "=" * 60)
    print("  페이즈 2: 대용량 테이블 (병렬 처리)")
    print("=" * 60)

    gen_interest_stock_optimized(conn, cfg, ref, workers, seed)
    gen_portfolio_asset_trade_optimized(conn, cfg, ref, workers, seed)

    # Phase 3: User-related tables
    print("\n" + "=" * 60)
    print("  페이즈 3: 유저 관련 테이블")
    print("=" * 60)

    gen_user_squad(conn, ref, random.Random(seed))
    gen_user_xp_and_awards(conn, ref, random.Random(seed))
    gen_badge_ownership(conn, ref, random.Random(seed))
    gen_user_metric(conn, ref, random.Random(seed))
    gen_challenge_reward(conn, ref, random.Random(seed), challenge_ids)
    gen_study_metric(conn, ref, random.Random(seed))
    gen_course_progress(cfg, ref, random.Random(seed), course_data, workers)
    gen_lesson_complete(cfg, ref, random.Random(seed), course_data, workers)

    # Phase 4: Social tables
    print("\n" + "=" * 60)
    print("  페이즈 4: 소셜 테이블")
    print("=" * 60)

    gen_discussion_social(conn, ref, random.Random(seed))
    gen_news_like(conn, ref, random.Random(seed))

    # Phase 5: Ranking snapshot (must be after user_xp)
    print("\n" + "=" * 60)
    print("  페이즈 5: 랭킹 스냅샷")
    print("=" * 60)

    gen_xp_ranking_snapshot(conn, random.Random(seed))

    # Restore DB settings
    restore_db_settings(conn)

    elapsed = dt.datetime.now() - start_time
    print("\n" + "=" * 60)
    print(f"  전체 생성 완료")
    print(f"  총 소요 시간: {elapsed}")
    print("=" * 60)


def main() -> None:
    rng = random.Random(42)
    cfg = get_db_config()
    workers = get_parallel_workers()

    print("DB 연결 중...", end=" ", flush=True)
    try:
        conn = connect(cfg)
        print("완료")
    except Exception as e:
        raise SystemExit(f"DB 연결 실패: {e}")

    print("레퍼런스 데이터 로딩 중...")
    ref = load_ref(conn)

    challenge_ids: list[int] = []
    course_data: dict = {}

    def ensure_challenge_ids() -> list[int]:
        nonlocal challenge_ids
        if not challenge_ids:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM personal_challenge")
                challenge_ids = [row[0] for row in cur.fetchall()]
            print(f"    챌린지 ID {len(challenge_ids)}개 DB에서 로드 완료")
        return challenge_ids

    def ensure_course_data() -> dict:
        nonlocal course_data
        if not course_data:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM course")
                cids = [row[0] for row in cur.fetchall()]
                cur.execute("SELECT id, course_id FROM lesson")
                lesson_rows = cur.fetchall()
            clm: dict[int, list[int]] = {}
            for lid, cid in lesson_rows:
                clm.setdefault(cid, []).append(lid)
            course_data = {
                "course_ids": cids,
                "lesson_ids": [r[0] for r in lesson_rows],
                "course_lesson_map": clm,
            }
            print(f"    코스 {len(cids)}개, 레슨 {len(lesson_rows)}개 DB에서 로드 완료")
        return course_data

    # ── Ordered step definitions ───────────────────────────────────────────
    # Each entry: (menu_number, label, callable)
    def step1():
        nonlocal challenge_ids
        challenge_ids = gen_personal_challenge(conn, rng)

    def step2():
        nonlocal course_data
        course_data = gen_course_lesson(conn, rng)

    def step3():
        optimize_db_for_import(conn)
        gen_interest_stock_optimized(conn, cfg, ref, workers, 42)
        restore_db_settings(conn)

    def step4():
        optimize_db_for_import(conn)
        gen_portfolio_asset_trade_optimized(conn, cfg, ref, workers, 42)
        restore_db_settings(conn)

    def step5():
        gen_user_squad(conn, ref, rng)

    def step6():
        gen_user_xp_and_awards(conn, ref, rng)

    def step7():
        gen_badge_ownership(conn, ref, rng)

    def step8():
        gen_user_metric(conn, ref, rng)

    def step9():
        gen_challenge_reward(conn, ref, rng, ensure_challenge_ids())

    def step10():
        gen_xp_ranking_snapshot(conn, rng)

    def step11():
        gen_study_metric(conn, ref, rng)

    def step12():
        gen_course_progress(cfg, ref, rng, ensure_course_data(), workers)

    def step13():
        gen_lesson_complete(cfg, ref, rng, ensure_course_data(), workers)

    def step14():
        gen_discussion_social(conn, ref, rng)

    def step15():
        gen_news_like(conn, ref, rng)

    STEPS: list[tuple[int, str, Callable]] = [
        (1, "personal_challenge", step1),
        (2, "course + lesson + lesson_content", step2),
        (3, "interest_stock (parallel)", step3),
        (4, "portfolio_group + asset + trade (parallel)", step4),
        (5, "user_squad", step5),
        (6, "user_xp + user_xp_award", step6),
        (7, "badge_ownership", step7),
        (8, "user_metric", step8),
        (9, "personal_challenge_reward", step9),
        (10, "user_xp_ranking_snapshot", step10),
        (11, "study_metric", step11),
        (12, "course_progress (parallel)", step12),
        (13, "lesson_complete (parallel)", step13),
        (14, "discussion + comment + like", step14),
        (15, "news_like", step15),
    ]
    step_map = {n: fn for n, _, fn in STEPS}

    def run_steps(start: int, end: int | None = None) -> None:
        """Run steps[start : end] sequentially (1-indexed, end inclusive).

        On KeyboardInterrupt:
          - rolls back the current uncommitted transaction
          - restores DB settings (re-enables FK/unique checks)
          - removes any leftover TSV temp files under DATA_DIR
        """
        subset = [
            (n, label, fn)
            for n, label, fn in STEPS
            if n >= start and (end is None or n <= end)
        ]
        total = len(subset)
        optimize_db_for_import(conn)
        start_time = dt.datetime.now()
        completed = 0
        try:
            for idx, (n, label, fn) in enumerate(subset, 1):
                print(f"\n{'=' * 60}")
                print(f"  스텝 {n}/{STEPS[-1][0]}  [{idx}/{total}]  {label}")
                print(f"  (Ctrl+C 로 나머지 스텝 취소 가능)")
                print(f"{'=' * 60}")
                fn()
                completed += 1
        except KeyboardInterrupt:
            print(
                f"\n\n  스텝 {subset[completed][0] if completed < len(subset) else '?'} 에서 중단되었습니다."
            )
            print(f"  {completed}/{total} 스텝 완료. 현재 트랜잭션 롤백 중...")
            try:
                conn.rollback()
            except Exception:
                pass
            restore_db_settings(conn)
            # clean up any leftover TSV files
            leftover = list(DATA_DIR.glob("*.tsv"))
            if leftover:
                print(f"  임시 파일 {len(leftover)}개 정리 중...")
                cleanup_files(leftover)
            elapsed = dt.datetime.now() - start_time
            print(f"  소요 시간: {elapsed}")
            return
        restore_db_settings(conn)
        elapsed = dt.datetime.now() - start_time
        print(f"\n  {total} 스텝 완료 (소요 시간: {elapsed})")

    try:
        while True:
            print_menu(ref, cfg, workers)
            try:
                choice = (
                    input(
                        "  Select option (N to run step N only, N~ to run from step N to end): "
                    )
                    .strip()
                    .lower()
                )
            except KeyboardInterrupt:
                print("\n  종료합니다...")
                break

            if choice == "q":
                print("종료합니다...")
                break

            elif choice == "0":
                if confirm("전체 데이터를 최적화 병렬 모드로 생성하시겠습니까?"):
                    run_all_optimized(conn, cfg, ref, workers, 42)

            # "N~" — from step N to the end
            elif choice.endswith("~") and choice[:-1].isdigit():
                start = int(choice[:-1])
                if start not in step_map:
                    print(f"  유효하지 않은 스텝: {start}")
                elif confirm(
                    f"{start}번부터 {STEPS[-1][0]}번 스텝까지 순서대로 실행하시겠습니까?"
                ):
                    run_steps(start)

            # plain number — single step
            elif choice.isdigit() and int(choice) in step_map:
                n = int(choice)
                try:
                    optimize_db_for_import(conn)
                    step_map[n]()
                    restore_db_settings(conn)
                except KeyboardInterrupt:
                    print(f"\n\n  스텝 {n} 중단됨. 롤백 중...")
                    try:
                        conn.rollback()
                    except Exception:
                        pass
                    restore_db_settings(conn)
                    leftover = list(DATA_DIR.glob("*.tsv"))
                    if leftover:
                        print(f"  임시 파일 {len(leftover)}개 정리 중...")
                        cleanup_files(leftover)

            else:
                print("  유효하지 않은 입력입니다.")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
