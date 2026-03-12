#!/usr/bin/env python3
"""
Generate dummy users CSV for bulk loading into MariaDB.

Output columns (no header):
id,login_id,password_hash,provider,provider_id,role,
phone_number_first_part,phone_number_second_part,phone_number_third_part,
birth_date,name,nickname,email,is_deleted,created_at,last_modified_at
"""

from __future__ import annotations

import argparse
import datetime as dt
import pathlib
import random
import uuid

DEFAULT_PASSWORD_HASH = (
    "$2a$10$7EqJtq98hPqEX7fNZaFWoO5Y6QnqJ6TP2PzefRg2fzGEylh4G6dkK"
)
SOCIAL_PROVIDERS = ("GOOGLE", "KAKAO", "NAVER")
NAME_POOL = (
    "Minsu",
    "Jiwoo",
    "Seoyeon",
    "Jiwon",
    "Sujin",
    "Hyunjin",
    "Yejun",
    "Jisoo",
    "Haneul",
    "Yuna",
    "Doyun",
    "Sihyun",
    "Taeyang",
    "Hamin",
    "Ara",
)
DATETIME_FORMAT = "%Y-%m-%d %H:%M:%S"


def parse_datetime(value: str) -> dt.datetime:
    try:
        return dt.datetime.strptime(value, "%Y-%m-%dT%H:%M:%S")
    except ValueError as exc:
        raise argparse.ArgumentTypeError(
            f"Invalid datetime '{value}'. Use YYYY-MM-DDTHH:MM:SS"
        ) from exc


def validate_ratio(name: str, value: float) -> None:
    if not (0.0 <= value <= 1.0):
        raise ValueError(f"{name} must be between 0 and 1: {value}")


def random_date(rng: random.Random, start: dt.date, end: dt.date) -> dt.date:
    day_span = (end - start).days
    return start + dt.timedelta(days=rng.randint(0, day_span))


def random_datetime(
    rng: random.Random, start: dt.datetime, end: dt.datetime
) -> dt.datetime:
    second_span = int((end - start).total_seconds())
    return start + dt.timedelta(seconds=rng.randint(0, second_span))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate users CSV file for MariaDB LOAD DATA."
    )
    parser.add_argument(
        "--rows",
        type=int,
        default=1_000_000,
        help="Number of rows to generate (default: 1000000).",
    )
    parser.add_argument(
        "--output",
        type=pathlib.Path,
        default=pathlib.Path("data/users_1000000.csv"),
        help="Output CSV path (default: data/users_1000000.csv).",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for deterministic data (default: 42).",
    )
    parser.add_argument(
        "--social-ratio",
        type=float,
        default=0.30,
        help="Share of social users (default: 0.30).",
    )
    parser.add_argument(
        "--deleted-ratio",
        type=float,
        default=0.01,
        help="Share of deleted users (default: 0.01).",
    )
    parser.add_argument(
        "--admin-ratio",
        type=float,
        default=0.001,
        help="Share of admin users (default: 0.001).",
    )
    parser.add_argument(
        "--created-from",
        type=parse_datetime,
        default=parse_datetime("2023-01-01T00:00:00"),
        help="Start datetime (inclusive), format YYYY-MM-DDTHH:MM:SS.",
    )
    parser.add_argument(
        "--created-to",
        type=parse_datetime,
        default=dt.datetime.now().replace(microsecond=0),
        help="End datetime (inclusive), format YYYY-MM-DDTHH:MM:SS.",
    )
    parser.add_argument(
        "--password-hash",
        default=DEFAULT_PASSWORD_HASH,
        help="Fixed password hash to use for all rows.",
    )
    parser.add_argument(
        "--progress-every",
        type=int,
        default=100_000,
        help="Print progress every N rows (default: 100000, 0 to disable).",
    )
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    if args.rows <= 0:
        raise ValueError("--rows must be > 0")

    validate_ratio("social-ratio", args.social_ratio)
    validate_ratio("deleted-ratio", args.deleted_ratio)
    validate_ratio("admin-ratio", args.admin_ratio)

    if args.created_from > args.created_to:
        raise ValueError("--created-from must be <= --created-to")

    birth_date_start = dt.date(1960, 1, 1)
    birth_date_end = dt.date(2005, 12, 31)

    rng = random.Random(args.seed)
    args.output.parent.mkdir(parents=True, exist_ok=True)

    total = args.rows
    with args.output.open("w", encoding="utf-8", newline="\n") as handle:
        for idx in range(total):
            user_id = str(uuid.UUID(int=rng.getrandbits(128)))
            login_id = f"user{idx:07d}"
            email = f"user{idx:07d}@example.com"
            nickname = f"fv{idx:07d}"
            name = rng.choice(NAME_POOL)

            phone_first = "010"
            phone_second = f"{rng.randint(0, 9999):04d}"
            phone_third = f"{rng.randint(0, 9999):04d}"
            birth_date = random_date(rng, birth_date_start, birth_date_end).isoformat()

            if rng.random() < args.social_ratio:
                provider = rng.choice(SOCIAL_PROVIDERS)
                provider_id = f"{provider.lower()}_{idx:07d}"
            else:
                provider = "LOCAL"
                provider_id = r"\N"

            role = "ADMIN" if rng.random() < args.admin_ratio else "USER"
            is_deleted = "1" if rng.random() < args.deleted_ratio else "0"

            created_at = random_datetime(rng, args.created_from, args.created_to)
            last_modified_at = random_datetime(rng, created_at, args.created_to)

            row = ",".join(
                [
                    user_id,
                    login_id,
                    args.password_hash,
                    provider,
                    provider_id,
                    role,
                    phone_first,
                    phone_second,
                    phone_third,
                    birth_date,
                    name,
                    nickname,
                    email,
                    is_deleted,
                    created_at.strftime(DATETIME_FORMAT),
                    last_modified_at.strftime(DATETIME_FORMAT),
                ]
            )
            handle.write(row)
            handle.write("\n")

            if args.progress_every > 0 and (idx + 1) % args.progress_every == 0:
                print(f"Generated {idx + 1:,}/{total:,} rows...")

    print(f"CSV generated: {args.output.resolve()}")
    print(f"Rows: {total:,}")
    print("Tip: Use LOAD DATA LOCAL INFILE for fast import.")


if __name__ == "__main__":
    main()
