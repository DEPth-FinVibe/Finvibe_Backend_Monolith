#!/usr/bin/env python3
"""
scripts/seed/generate_seed_data.py

Interactive dummy data generator for Finvibe backend load testing.

Requirements:
    pip install pymysql

.env에 아래 항목 추가 (없으면 기본값 사용):
    SEED_DB_HOST=localhost
    SEED_DB_PORT=3306
    SEED_DB_NAME=finvibe
    SEED_DB_USER=finvibe
    SEED_DB_PASSWORD=finvibe
"""
from __future__ import annotations

import pathlib
import random
import datetime as dt
from typing import Any

try:
    import pymysql
    import pymysql.cursors
except ImportError:
    raise SystemExit("pymysql가 설치되지 않았습니다. 실행: pip install pymysql")


# ── 볼륨 상수 (필요하면 여기서 조정) ─────────────────────────────────────────

COURSES = 10
LESSONS_PER_COURSE = 5
PERSONAL_CHALLENGES = 20
PORTFOLIOS_PER_USER = 3
ASSETS_PER_PORTFOLIO = 4
TRADES_PER_USER = 40
INTEREST_STOCKS_PER_USER = 10
USERS_PER_SQUAD = 1000
XP_AWARDS_PER_USER = 5
BADGES_PER_USER_AVG = 2            # 유저당 평균 뱃지 수 (1~4 랜덤)
USER_METRIC_FRACTION = 0.10        # user_metric 적용 유저 비율
CHALLENGE_COMPLETION_RATE = 0.30   # personal_challenge_reward 참여율
COURSE_PROGRESS_RATE = 0.40        # course_progress 참여율
LESSON_COMPLETE_RATE = 0.60        # lesson_complete 참여율
DISCUSSIONS_PER_NEWS = 3           # 뉴스 1개당 토론 수 (2~3)
COMMENTS_PER_DISCUSSION = 3        # 토론 1개당 댓글 수 (2~3)
DISCUSSION_LIKES_PER = 30          # 토론 1개당 좋아요 수
COMMENT_LIKES_PER = 10             # 댓글 1개당 좋아요 수
NEWS_LIKES_PER = 50                # 뉴스 1개당 좋아요 수
XP_RANKING_TOP_N = 200             # user_xp_ranking_snapshot 상위 N명

# ── 열거형 상수 ────────────────────────────────────────────────────────────────

BADGES = [
    "FIRST_PROFIT", "KNOWLEDGE_SEEKER", "DILIGENT_INVESTOR",
    "DIVERSIFICATION_MASTER", "BEST_DEBATER", "PERFECT_SCORE_QUIZ",
    "CHALLENGE_MARATHONER", "TOP_ONE_PERCENT_TRAINER",
]
METRIC_TYPES = [
    "LOGIN_COUNT_PER_DAY", "CURRENT_RETURN_RATE", "STOCK_BUY_COUNT",
    "STOCK_SELL_COUNT", "PORTFOLIO_COUNT_WITH_STOCKS", "HOLDING_STOCK_COUNT",
    "NEWS_COMMENT_COUNT", "NEWS_LIKE_COUNT", "DISCUSSION_POST_COUNT",
    "DISCUSSION_COMMENT_COUNT", "DISCUSSION_LIKE_COUNT",
    "AI_CONTENT_COMPLETE_COUNT", "CHALLENGE_COMPLETION_COUNT",
    "LOGIN_STREAK_DAYS", "LAST_LOGIN_DATETIME",
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


# ── .env 파싱 ──────────────────────────────────────────────────────────────────

def load_env() -> dict[str, str]:
    env_path = pathlib.Path(__file__).resolve().parents[2] / ".env"
    env: dict[str, str] = {}
    if env_path.exists():
        for raw in env_path.read_text().splitlines():
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


# ── DB 연결 ────────────────────────────────────────────────────────────────────

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


# ── 유틸 ───────────────────────────────────────────────────────────────────────

def fmt_dt(d: dt.datetime) -> str:
    return d.strftime(DATETIME_FMT)


def fmt_date(d: dt.date) -> str:
    return d.strftime(DATE_FMT)


def null(v: Any) -> str:
    """None → MariaDB NULL 마커"""
    return str(v) if v is not None else r"\N"


def progress(cur: int, total: int, label: str = "", every: int = 200_000) -> None:
    if total and cur > 0 and cur % every == 0:
        pct = cur * 100 // total
        suffix = f"  [{label}]" if label else ""
        print(f"  {cur:>12,} / {total:,}  ({pct}%){suffix}", flush=True)


def write_csv_and_load(
    conn: pymysql.Connection,
    filename: str,
    rows_iter,
    total: int,
    load_sql: str,
    label: str = "",
) -> int:
    """CSV를 data/ 디렉터리에 쓰고 LOAD DATA LOCAL INFILE로 적재. 완료 후 파일 삭제."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    csv_path = DATA_DIR / filename
    print(f"  CSV 생성 중: {csv_path}")
    written = 0
    with csv_path.open("w", encoding="utf-8", newline="\n") as f:
        for row in rows_iter:
            f.write(",".join(str(c) for c in row) + "\n")
            written += 1
            progress(written, total, label)
    print(f"  CSV 완료: {written:,}행  →  LOAD DATA 시작...")
    sql = load_sql.replace("__FILE__", str(csv_path).replace("\\", "/"))
    with conn.cursor() as cur:
        cur.execute(sql)
        affected = cur.rowcount
    conn.commit()
    csv_path.unlink(missing_ok=True)
    print(f"  적재 완료: {affected:,}행")
    return affected


# ── 참조 데이터 로딩 ───────────────────────────────────────────────────────────

def load_ref(conn: pymysql.Connection) -> dict:
    """DB에서 참조 데이터(users, stocks, squads, news IDs)를 읽어옴."""
    ref: dict = {}
    with conn.cursor() as cur:
        print("  user IDs 로딩...", end=" ", flush=True)
        # bit(1) 타입 비교 이슈로 is_deleted 필터 없이 전체 조회
        cur.execute("SELECT id FROM users")
        # MariaDB uuid 타입 → PyMySQL이 str로 반환 (예: "550e8400-e29b-41d4-a716-446655440000")
        ref["users"] = [str(row[0]) for row in cur.fetchall()]
        print(f"{len(ref['users']):,}명")

        print("  stock IDs 로딩...", end=" ", flush=True)
        cur.execute("SELECT id, name FROM stock")
        rows = cur.fetchall()
        ref["stock_ids"] = [r[0] for r in rows]
        ref["stock_name"] = {r[0]: r[1] for r in rows}
        print(f"{len(ref['stock_ids']):,}종목")

        print("  squad IDs 로딩...", end=" ", flush=True)
        cur.execute("SELECT id FROM squad")
        ref["squad_ids"] = [row[0] for row in cur.fetchall()]
        print(f"{len(ref['squad_ids']):,}개")

        print("  news IDs 로딩...", end=" ", flush=True)
        cur.execute("SELECT id FROM news")
        ref["news_ids"] = [row[0] for row in cur.fetchall()]
        print(f"{len(ref['news_ids']):,}건")

    return ref


# ── 생성기: personal_challenge (소량, 직접 INSERT) ───────────────────────────

def gen_personal_challenge(conn: pymysql.Connection, rng: random.Random) -> list[int]:
    """personal_challenge 더미 데이터 삽입. 생성된 ID 목록 반환."""
    rows = []
    for i in range(PERSONAL_CHALLENGES):
        metric = rng.choice(METRIC_TYPES[:10])
        target = round(rng.uniform(5, 100), 1)
        start = CREATED_FROM.date() + dt.timedelta(days=rng.randint(0, 300))
        end = start + dt.timedelta(days=rng.randint(7, 30))
        reward_xp = rng.choice([50, 100, 200, 300, 500])
        reward_badge = rng.choice(BADGES) if rng.random() < 0.4 else None  # None → SQL NULL
        now_s = fmt_dt(NOW)
        rows.append((
            f"챌린지 {i + 1}: {metric} 달성",
            f"더미 챌린지 설명 {i + 1}",
            metric, target,
            fmt_date(start), fmt_date(end),
            reward_xp, reward_badge,
            now_s, now_s,
        ))

    sql = """
        INSERT INTO personal_challenge
            (title, description, metric_type, target_value,
             start_date, end_date, reward_xp, reward_badge,
             created_at, last_modified_at)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """
    with conn.cursor() as cur:
        cur.executemany(sql, rows)
        cur.execute("SELECT id FROM personal_challenge ORDER BY id DESC LIMIT %s", (PERSONAL_CHALLENGES,))
        ids = [row[0] for row in cur.fetchall()]
    conn.commit()
    print(f"  personal_challenge {PERSONAL_CHALLENGES}건 삽입 완료")
    return ids


# ── 생성기: course + lesson_content + lesson (소량, 직접 INSERT) ──────────────

def gen_course_lesson(conn: pymysql.Connection, rng: random.Random) -> dict:
    """course, lesson_content, lesson 삽입."""
    course_rows = []
    for i in range(COURSES):
        diff = COURSE_DIFFICULTIES[i % len(COURSE_DIFFICULTIES)]
        now_s = fmt_dt(NOW)
        course_rows.append((
            f"더미 코스 {i + 1}: 투자 기초 {diff}",
            f"이 코스는 더미 데이터입니다. 투자 관련 학습 내용 {i + 1}.",
            diff, None, False, LESSONS_PER_COURSE,
            now_s, now_s,
        ))

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

    lesson_ids: list[int] = []
    course_lesson_map: dict[int, list[int]] = {}

    # lesson_content.id는 AUTO_INCREMENT 없이 MariaDB 시퀀스(lesson_content_seq)로 생성됨
    content_sql = "INSERT INTO lesson_content (id, content) VALUES (%s, %s)"
    lesson_sql = """
        INSERT INTO lesson (course_id, title, description, content_id)
        VALUES (%s,%s,%s,%s)
    """
    with conn.cursor() as cur:
        for cid in course_ids:
            course_lesson_map[cid] = []
            for j in range(LESSONS_PER_COURSE):
                cur.execute("SELECT NEXTVAL(lesson_content_seq)")
                content_id = cur.fetchone()[0]
                cur.execute(content_sql, (content_id, f"더미 레슨 콘텐츠 (course={cid}, lesson={j + 1}). 투자 학습 내용."))
                cur.execute(lesson_sql, (cid, f"레슨 {j + 1}", f"레슨 {j + 1} 설명", content_id))
                lid = cur.lastrowid
                lesson_ids.append(lid)
                course_lesson_map[cid].append(lid)

    conn.commit()
    print(f"  course {COURSES}개, lesson_content+lesson {COURSES * LESSONS_PER_COURSE}개 삽입 완료")
    return {
        "course_ids": course_ids,
        "lesson_ids": lesson_ids,
        "course_lesson_map": course_lesson_map,
    }


# ── 생성기: interest_stock ────────────────────────────────────────────────────

def gen_interest_stock(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    users = ref["users"]
    stock_ids = ref["stock_ids"]
    stock_name = ref["stock_name"]
    if not stock_ids:
        print("  stock 데이터 없음 - 건너뜀")
        return 0

    total = len(users) * INTEREST_STOCKS_PER_USER

    def rows():
        created_at = fmt_dt(NOW)
        for uid in users:
            picks = rng.sample(stock_ids, min(INTEREST_STOCKS_PER_USER, len(stock_ids)))
            for sid in picks:
                yield (uid, sid, stock_name[sid], created_at, created_at)

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE interest_stock
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (user_id, stock_id, stock_name, created_at, last_modified_at)
    """
    return write_csv_and_load(conn, "interest_stock.csv", rows(), total, load_sql, "interest_stock")


# ── 생성기: portfolio_group + asset + trade ───────────────────────────────────

def gen_portfolio_asset_trade(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    users = ref["users"]
    stock_ids = ref["stock_ids"]
    stock_name = ref["stock_name"]
    if not stock_ids:
        print("  stock 데이터 없음 - 건너뜀")
        return 0

    icon_codes = ["icon_01", "icon_02", "icon_03", "icon_04", "icon_05"]
    pg_total = len(users) * PORTFOLIOS_PER_USER

    def pg_rows_iter():
        now_s = fmt_dt(NOW)
        for uid in users:
            for p in range(PORTFOLIOS_PER_USER):
                is_default = 1 if p == 0 else 0
                yield (
                    uid,
                    f"포트폴리오 {p + 1}",
                    rng.choice(icon_codes),
                    is_default,
                    "0.00", "0.00", "0.00",
                    null(None),   # portfolio_valuation_calculated_at
                    now_s, now_s,
                )

    pg_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE portfolio_group
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (user_id, name, icon_code, is_default,
         total_current_value, total_profit_loss, total_return_rate,
         portfolio_valuation_calculated_at,
         created_at, last_modified_at)
    """
    print("  [1/3] portfolio_group 생성...")
    write_csv_and_load(conn, "portfolio_group.csv", pg_rows_iter(), pg_total, pg_load_sql, "portfolio_group")

    # portfolio_group ID 조회 (asset FK에 필요)
    print("  portfolio_group ID 조회 중...", end=" ", flush=True)
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, user_id FROM portfolio_group ORDER BY id ASC LIMIT %s",
            (pg_total,)
        )
        pg_data = cur.fetchall()  # (id, user_id as str)
    print(f"{len(pg_data):,}건")

    user_to_pg_ids: dict[str, list[int]] = {}
    for pg_id, uid_val in pg_data:
        uid_str = str(uid_val)
        user_to_pg_ids.setdefault(uid_str, []).append(pg_id)

    # ─ asset ─
    asset_total = pg_total * ASSETS_PER_PORTFOLIO
    print("  [2/3] asset 생성...")

    def asset_rows_iter():
        now_s = fmt_dt(NOW)
        for uid, pg_ids in user_to_pg_ids.items():
            for pg_id in pg_ids:
                picks = rng.sample(stock_ids, min(ASSETS_PER_PORTFOLIO, len(stock_ids)))
                for sid in picks:
                    amount = round(rng.uniform(1, 100), 2)
                    price = rng.randint(1000, 500_000)
                    total_price = round(amount * price, 2)
                    cur_val = round(total_price * rng.uniform(0.8, 1.5), 2)
                    profit = round(cur_val - total_price, 2)
                    ret_rate = round((profit / total_price) * 100, 4) if total_price else 0
                    yield (
                        pg_id, uid, sid, stock_name[sid], amount,
                        total_price, "KRW",
                        cur_val, profit, ret_rate,
                        now_s,
                        now_s, now_s,
                    )

    asset_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE asset
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (portfolio_group_id, user_id, stock_id, name, amount,
         total_price_amount, total_price_currency,
         current_value, profit_loss, return_rate, valuation_calculated_at,
         created_at, last_modified_at)
    """
    write_csv_and_load(conn, "asset.csv", asset_rows_iter(), asset_total, asset_load_sql, "asset")

    # ─ trade ─
    trade_total = len(users) * TRADES_PER_USER
    print("  [3/3] trade 생성...")

    def trade_rows_iter():
        now_s = fmt_dt(NOW)
        for uid, pg_ids in user_to_pg_ids.items():
            for _ in range(TRADES_PER_USER):
                sid = rng.choice(stock_ids)
                pg_id = rng.choice(pg_ids)
                yield (
                    sid, stock_name[sid],
                    round(rng.uniform(1, 50), 2),
                    rng.randint(1000, 500_000),
                    pg_id, uid,
                    rng.choice(TRANSACTION_TYPES),
                    rng.choice(TRADE_TYPES),
                    now_s, now_s,
                )

    trade_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE trade
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (stock_id, stock_name, amount, price, portfolio_id, user_id,
         transaction_type, trade_type, created_at, last_modified_at)
    """
    write_csv_and_load(conn, "trade.csv", trade_rows_iter(), trade_total, trade_load_sql, "trade")
    return trade_total


# ── 생성기: user_squad ────────────────────────────────────────────────────────

def gen_user_squad(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    users = ref["users"]
    squad_ids = ref["squad_ids"]
    if not squad_ids:
        print("  squad 데이터 없음 - 건너뜀")
        return 0

    total_needed = len(squad_ids) * USERS_PER_SQUAD
    if total_needed > len(users):
        print(f"  경고: 필요 유저({total_needed:,}) > 보유 유저({len(users):,}). 가능한 범위로 축소.")
        total_needed = len(users)

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
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (user_id, squad_id)
    """
    return write_csv_and_load(conn, "user_squad.csv", rows(), total_needed, load_sql, "user_squad")


# ── 생성기: user_xp + user_xp_award ──────────────────────────────────────────

def gen_user_xp_and_awards(conn: pymysql.Connection, ref: dict, rng: random.Random) -> None:
    users = ref["users"]
    xp_reasons = [
        "레슨 완료", "챌린지 달성", "토론 참여", "주식 첫 매수", "연속 접속",
        "퀴즈 통과", "뉴스 좋아요", "포트폴리오 수익 달성",
    ]

    def xp_rows():
        for idx, uid in enumerate(users):
            nickname = f"fv{idx:07d}"
            total_xp = rng.randint(0, 50_000)
            weekly_xp = rng.randint(0, min(total_xp, 5_000))
            level = max(1, total_xp // 1_000)
            yield (uid, nickname, total_xp, weekly_xp, level)

    user_xp_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE user_xp
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (user_id, nickname, total_xp, weekly_xp, level)
    """
    print("  [1/2] user_xp 생성...")
    write_csv_and_load(conn, "user_xp.csv", xp_rows(), len(users), user_xp_sql, "user_xp")

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
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (user_id, value, reason, created_at, last_modified_at)
    """
    print("  [2/2] user_xp_award 생성...")
    write_csv_and_load(conn, "user_xp_award.csv", award_rows(), award_total, award_sql, "user_xp_award")


# ── 생성기: badge_ownership ───────────────────────────────────────────────────

def gen_badge_ownership(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
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
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (badge, owner_id)
    """
    return write_csv_and_load(
        conn, "badge_ownership.csv", rows(), len(users) * BADGES_PER_USER_AVG, load_sql, "badge_ownership"
    )


# ── 생성기: user_metric ───────────────────────────────────────────────────────

def gen_user_metric(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
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
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (type, user_id, collect_period, value)
    """
    return write_csv_and_load(conn, "user_metric.csv", rows(), total, load_sql, "user_metric")


# ── 생성기: personal_challenge_reward ────────────────────────────────────────

def gen_challenge_reward(
    conn: pymysql.Connection, ref: dict, rng: random.Random, challenge_ids: list[int]
) -> int:
    if not challenge_ids:
        print("  challenge_id 없음 - personal_challenge를 먼저 생성하세요")
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
                yield (cid, uid, fmt_date(start), fmt_date(end), reward_xp, reward_badge, now_s, now_s)

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE personal_challenge_reward
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (challenge_id, user_id, start_date, end_date, reward_xp, reward_badge,
         created_at, last_modified_at)
    """
    return write_csv_and_load(
        conn, "personal_challenge_reward.csv", rows(), total, load_sql, "challenge_reward"
    )


# ── 생성기: study_metric ──────────────────────────────────────────────────────

def gen_study_metric(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
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
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (user_id, xp_earned, time_spent_minutes, last_ping_at)
    """
    return write_csv_and_load(conn, "study_metric.csv", rows(), len(users), load_sql, "study_metric")


# ── 생성기: course_progress + lesson_complete ─────────────────────────────────

def gen_course_progress_and_lesson_complete(
    conn: pymysql.Connection, ref: dict, rng: random.Random, course_data: dict,
) -> None:
    if not course_data:
        print("  course 데이터 없음 - course를 먼저 생성하세요")
        return

    users = ref["users"]
    course_ids = course_data["course_ids"]
    course_lesson_map = course_data["course_lesson_map"]

    sample_size = max(1, int(len(users) * COURSE_PROGRESS_RATE))
    cp_total = sample_size * len(course_ids)

    def cp_rows():
        now_s = fmt_dt(NOW)
        for uid in rng.sample(users, sample_size):
            for cid in course_ids:
                n_total = len(course_lesson_map[cid])
                n_done = rng.randint(0, n_total)
                key = f"{cid}_{uid}"
                yield (cid, uid, n_done, n_total, key, now_s, now_s)

    cp_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE course_progress
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (course_id, user_id, completed_lesson_count, total_lesson_count,
         course_user_id_key, created_at, last_modified_at)
    """
    print("  [1/2] course_progress 생성...")
    write_csv_and_load(conn, "course_progress.csv", cp_rows(), cp_total, cp_load_sql, "course_progress")

    all_lesson_ids = course_data["lesson_ids"]
    lc_sample_size = max(1, int(len(users) * LESSON_COMPLETE_RATE))
    lc_total = lc_sample_size * len(all_lesson_ids)

    def lc_rows():
        now_s = fmt_dt(NOW)
        for uid in rng.sample(users, lc_sample_size):
            for lid in all_lesson_ids:
                key = f"{lid}_{uid}"
                yield (lid, uid, key, now_s, now_s)

    lc_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE lesson_complete
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (lesson_id, user_id, lesson_user_id_key, created_at, last_modified_at)
    """
    print("  [2/2] lesson_complete 생성...")
    write_csv_and_load(conn, "lesson_complete.csv", lc_rows(), lc_total, lc_load_sql, "lesson_complete")


# ── 생성기: discussion + comment + likes ──────────────────────────────────────

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
    "동의합니다.", "좋은 분석이네요.", "저도 같은 생각입니다.",
    "반대 의견입니다.", "더 지켜봐야 할 것 같아요.", "감사합니다!",
    "좋은 정보 공유 감사해요.", "저는 다르게 생각해요.",
]


def gen_discussion_social(conn: pymysql.Connection, ref: dict, rng: random.Random) -> None:
    users = ref["users"]
    news_ids = ref["news_ids"]
    if not news_ids:
        print("  news 데이터 없음 - 건너뜀")
        return

    disc_total = len(news_ids) * DISCUSSIONS_PER_NEWS

    def disc_rows():
        now_s = fmt_dt(NOW)
        for nid in news_ids:
            for _ in range(rng.randint(2, DISCUSSIONS_PER_NEWS)):
                yield (nid, rng.choice(users), rng.choice(DISCUSSION_CONTENTS), 0, now_s, now_s)

    disc_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE discussion
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (news_id, user_id, content, is_edited, created_at, last_modified_at)
    """
    print("  [1/4] discussion 생성...")
    write_csv_and_load(conn, "discussion.csv", disc_rows(), disc_total, disc_load_sql, "discussion")

    print("  discussion ID 조회 중...", end=" ", flush=True)
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM discussion")
        discussion_ids = [row[0] for row in cur.fetchall()]
    print(f"{len(discussion_ids):,}건")

    comment_total = len(discussion_ids) * COMMENTS_PER_DISCUSSION

    def comment_rows():
        now_s = fmt_dt(NOW)
        for did in discussion_ids:
            for _ in range(rng.randint(2, COMMENTS_PER_DISCUSSION)):
                yield (did, rng.choice(users), rng.choice(COMMENT_CONTENTS), 0, now_s, now_s)

    comment_load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE discussion_comment
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (discussion_id, user_id, content, is_edited, created_at, last_modified_at)
    """
    print("  [2/4] discussion_comment 생성...")
    write_csv_and_load(
        conn, "discussion_comment.csv", comment_rows(), comment_total, comment_load_sql, "disc_comment"
    )

    print("  discussion_comment ID 조회 중...", end=" ", flush=True)
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM discussion_comment")
        comment_ids = [row[0] for row in cur.fetchall()]
    print(f"{len(comment_ids):,}건")

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
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (discussion_id, user_id, created_at, last_modified_at)
    """
    print("  [3/4] discussion_like 생성...")
    write_csv_and_load(conn, "discussion_like.csv", dl_rows(), dl_total, dl_load_sql, "disc_like")

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
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (comment_id, user_id, created_at, last_modified_at)
    """
    print("  [4/4] discussion_comment_like 생성...")
    write_csv_and_load(
        conn, "discussion_comment_like.csv", cl_rows(), cl_total, cl_load_sql, "comment_like"
    )


# ── 생성기: news_like ─────────────────────────────────────────────────────────

def gen_news_like(conn: pymysql.Connection, ref: dict, rng: random.Random) -> int:
    users = ref["users"]
    news_ids = ref["news_ids"]
    if not news_ids:
        print("  news 데이터 없음 - 건너뜀")
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
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (news_id, user_id, created_at, last_modified_at)
    """
    return write_csv_and_load(conn, "news_like.csv", rows(), total, load_sql, "news_like")


# ── 생성기: user_xp_ranking_snapshot (상위 N명) ───────────────────────────────

def gen_xp_ranking_snapshot(conn: pymysql.Connection, rng: random.Random) -> int:
    import calendar

    with conn.cursor() as cur:
        cur.execute(
            "SELECT user_id, nickname, total_xp, weekly_xp FROM user_xp ORDER BY total_xp DESC LIMIT %s",
            (XP_RANKING_TOP_N,),
        )
        top_users = cur.fetchall()

    if not top_users:
        print("  user_xp 데이터 없음 - user_xp를 먼저 생성하세요")
        return 0

    today = NOW.date()
    snapshots = []

    for period_type in RANKING_PERIODS:
        if period_type == "DAILY":
            periods = [(today - dt.timedelta(days=i), today - dt.timedelta(days=i)) for i in range(7)]
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
            for rank, (uid_val, nickname, total_xp, weekly_xp) in enumerate(top_users, 1):
                uid = str(uid_val)
                period_xp = rng.randint(0, min(total_xp, 5000))
                prev_xp = rng.randint(0, min(total_xp, 5000))
                growth = round(((period_xp - prev_xp) / prev_xp * 100) if prev_xp else 0, 2)
                snapshots.append((
                    uid, period_type,
                    fmt_date(p_start), fmt_date(p_end),
                    nickname, rank, total_xp, period_xp, prev_xp, growth,
                    fmt_dt(NOW), fmt_dt(NOW), fmt_dt(NOW),
                ))

    load_sql = """
        LOAD DATA LOCAL INFILE '__FILE__'
        INTO TABLE user_xp_ranking_snapshot
        CHARACTER SET utf8mb4
        FIELDS TERMINATED BY ','
        LINES TERMINATED BY '\\n'
        (user_id, period_type, period_start_date, period_end_date,
         nickname, ranking, current_total_xp, period_xp, previous_period_xp, growth_rate,
         snapshot_at, created_at, last_modified_at)
    """
    return write_csv_and_load(
        conn, "xp_ranking_snapshot.csv", iter(snapshots), len(snapshots), load_sql, "xp_ranking"
    )


# ── 메뉴 ───────────────────────────────────────────────────────────────────────

def print_menu(ref: dict, cfg: dict) -> None:
    n_users = len(ref.get("users", []))
    n_stocks = len(ref.get("stock_ids", []))
    n_squads = len(ref.get("squad_ids", []))
    n_news = len(ref.get("news_ids", []))

    print()
    print("╔══════════════════════════════════════════════════════════════╗")
    print("║         Finvibe Dummy Data Generator                        ║")
    print("╚══════════════════════════════════════════════════════════════╝")
    print(f"  DB : {cfg['user']}@{cfg['host']}:{cfg['port']}/{cfg['database']}")
    print(f"  참조: users {n_users:,}  |  stocks {n_stocks:,}  |  squads {n_squads:,}  |  news {n_news:,}")
    print()
    print("  ── Content ────────────────────────────────────────────────────")
    print(f"   1. personal_challenge                ~{PERSONAL_CHALLENGES}행")
    print(f"   2. course + lesson + lesson_content  {COURSES}코스 × {LESSONS_PER_COURSE}레슨")
    print()
    print("  ── User Activity ──────────────────────────────────────────────")
    print(f"   3. interest_stock                    ~{n_users * INTEREST_STOCKS_PER_USER:,}행")
    print(f"   4. portfolio_group + asset + trade   ~{n_users * PORTFOLIOS_PER_USER:,} / "
          f"~{n_users * PORTFOLIOS_PER_USER * ASSETS_PER_PORTFOLIO:,} / "
          f"~{n_users * TRADES_PER_USER:,}행")
    print()
    print("  ── Gamification ───────────────────────────────────────────────")
    print(f"   5. user_squad                        ~{n_squads * USERS_PER_SQUAD:,}행")
    print(f"   6. user_xp + user_xp_award           ~{n_users:,} + ~{n_users * XP_AWARDS_PER_USER:,}행")
    print(f"   7. badge_ownership                   ~{n_users * BADGES_PER_USER_AVG:,}행")
    print(f"   8. user_metric                       ~{int(n_users * USER_METRIC_FRACTION) * len(METRIC_TYPES) * 2:,}행 (유저 {USER_METRIC_FRACTION:.0%})")
    print(f"   9. personal_challenge_reward         challenge_id 필요")
    print(f"  10. user_xp_ranking_snapshot          상위 {XP_RANKING_TOP_N}명 × 기간")
    print()
    print("  ── Study ──────────────────────────────────────────────────────")
    print(f"  11. study_metric                      ~{n_users:,}행")
    print(f"  12. course_progress + lesson_complete course_id/lesson_id 필요")
    print()
    print("  ── Social ─────────────────────────────────────────────────────")
    print(f"  13. discussion + comment + like       ~{n_news * DISCUSSIONS_PER_NEWS:,}행~")
    print(f"  14. news_like                         ~{n_news * NEWS_LIKES_PER:,}행")
    print()
    print("   0. ALL 순서대로 생성")
    print("   q. 종료")
    print()


def confirm(msg: str) -> bool:
    return input(f"  {msg} [y/N] ").strip().lower() == "y"


def run_all(conn: pymysql.Connection, ref: dict, rng: random.Random) -> None:
    print("\n[1/14] personal_challenge")
    challenge_ids = gen_personal_challenge(conn, rng)

    print("\n[2/14] course + lesson + lesson_content")
    course_data = gen_course_lesson(conn, rng)

    print("\n[3/14] interest_stock")
    gen_interest_stock(conn, ref, rng)

    print("\n[4/14] portfolio_group + asset + trade")
    gen_portfolio_asset_trade(conn, ref, rng)

    print("\n[5/14] user_squad")
    gen_user_squad(conn, ref, rng)

    print("\n[6/14] user_xp + user_xp_award")
    gen_user_xp_and_awards(conn, ref, rng)

    print("\n[7/14] badge_ownership")
    gen_badge_ownership(conn, ref, rng)

    print("\n[8/14] user_metric")
    gen_user_metric(conn, ref, rng)

    print("\n[9/14] personal_challenge_reward")
    gen_challenge_reward(conn, ref, rng, challenge_ids)

    print("\n[10/14] user_xp_ranking_snapshot")
    gen_xp_ranking_snapshot(conn, rng)

    print("\n[11/14] study_metric")
    gen_study_metric(conn, ref, rng)

    print("\n[12/14] course_progress + lesson_complete")
    gen_course_progress_and_lesson_complete(conn, ref, rng, course_data)

    print("\n[13/14] discussion + comment + like")
    gen_discussion_social(conn, ref, rng)

    print("\n[14/14] news_like")
    gen_news_like(conn, ref, rng)

    print("\n✓ 전체 생성 완료")


# ── 메인 ───────────────────────────────────────────────────────────────────────

def main() -> None:
    rng = random.Random(42)
    cfg = get_db_config()

    print("DB 연결 중...", end=" ", flush=True)
    try:
        conn = connect(cfg)
        print("OK")
    except Exception as e:
        raise SystemExit(f"DB 연결 실패: {e}")

    print("참조 데이터 로딩 중...")
    ref = load_ref(conn)

    challenge_ids: list[int] = []
    course_data: dict = {}

    while True:
        print_menu(ref, cfg)
        choice = input("  번호 입력: ").strip().lower()

        if choice == "q":
            print("종료")
            break

        elif choice == "0":
            if confirm("전체 생성하시겠습니까? (시간이 많이 걸릴 수 있습니다)"):
                run_all(conn, ref, rng)

        elif choice == "1":
            challenge_ids = gen_personal_challenge(conn, rng)

        elif choice == "2":
            course_data = gen_course_lesson(conn, rng)

        elif choice == "3":
            gen_interest_stock(conn, ref, rng)

        elif choice == "4":
            gen_portfolio_asset_trade(conn, ref, rng)

        elif choice == "5":
            gen_user_squad(conn, ref, rng)

        elif choice == "6":
            gen_user_xp_and_awards(conn, ref, rng)

        elif choice == "7":
            gen_badge_ownership(conn, ref, rng)

        elif choice == "8":
            gen_user_metric(conn, ref, rng)

        elif choice == "9":
            if not challenge_ids:
                with conn.cursor() as cur:
                    cur.execute("SELECT id FROM personal_challenge")
                    challenge_ids = [row[0] for row in cur.fetchall()]
                print(f"  DB에서 challenge_id {len(challenge_ids)}개 로드")
            gen_challenge_reward(conn, ref, rng, challenge_ids)

        elif choice == "10":
            gen_xp_ranking_snapshot(conn, rng)

        elif choice == "11":
            gen_study_metric(conn, ref, rng)

        elif choice == "12":
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
                print(f"  DB에서 course {len(cids)}개, lesson {len(lesson_rows)}개 로드")
            gen_course_progress_and_lesson_complete(conn, ref, rng, course_data)

        elif choice == "13":
            gen_discussion_social(conn, ref, rng)

        elif choice == "14":
            gen_news_like(conn, ref, rng)

        else:
            print("  올바른 번호를 입력하세요")

    conn.close()


if __name__ == "__main__":
    main()
