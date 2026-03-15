# Seed Usage

`users` 더미데이터 CSV 생성과 MariaDB 적재 방법입니다.

## 1) CSV 생성

```bash
python3 scripts/seed/generate_users_csv.py \
  --rows 1000000 \
  --output data/users_1000000.csv \
  --seed 42
```

## 2) users.id 타입 확인

```sql
SHOW COLUMNS FROM users LIKE 'id';
```

- `char(36)` 또는 `varchar(36)` 이면 `load_users_char_uuid.sql` 사용
- `binary(16)` 이면 `load_users_binary_uuid.sql` 사용

## 3) LOAD DATA 실행

### CHAR/VARCHAR UUID

```bash
sed "s|__CSV_PATH__|$(pwd)/data/users_1000000.csv|g" \
  scripts/seed/load_users_char_uuid.sql \
  | mysql --local-infile=1 -u finvibe -p finvibe
```

### BINARY UUID

```bash
sed "s|__CSV_PATH__|$(pwd)/data/users_1000000.csv|g" \
  scripts/seed/load_users_binary_uuid.sql \
  | mysql --local-infile=1 -u finvibe -p finvibe
```

## 4) 확인 쿼리

```sql
SELECT COUNT(*) AS user_count FROM users;
```

```sql
SELECT
  table_name,
  ROUND(data_length / 1024 / 1024, 2) AS data_mb,
  ROUND(index_length / 1024 / 1024, 2) AS index_mb,
  ROUND((data_length + index_length) / 1024 / 1024, 2) AS total_mb
FROM information_schema.tables
WHERE table_schema = 'finvibe'
  AND table_name = 'users';
```

## 참고

- `provider_id` null 값은 CSV에서 `\N` 으로 들어갑니다.
- 더미 비밀번호 해시는 고정값을 사용합니다(생성 속도 목적).

---

# 부하 테스트용 더미 데이터 생성 (generate_seed_data.py)

users/wallet 이외 모든 테이블의 더미 데이터를 인터랙티브하게 생성합니다.

## 사전 조건

```bash
pip install pymysql
```

`.env`에 DB 접속 정보 추가 (없으면 기본값 사용):

```
SEED_DB_HOST=localhost
SEED_DB_PORT=3306
SEED_DB_NAME=finvibe
SEED_DB_USER=finvibe
SEED_DB_PASSWORD=finvibe
```

## 실행

```bash
python3 scripts/seed/generate_seed_data.py
```

인터랙티브 메뉴에서 번호를 선택해 테이블별로 생성하거나, `0`으로 전체를 순서대로 생성합니다.

## 생성 순서 (의존성)

```
1  personal_challenge
2  course → lesson_content → lesson
3  interest_stock              (users, stocks 필요)
4  portfolio_group + asset + trade  (users, stocks 필요)
7  user_squad                  (users, squads 필요)
8  user_xp + user_xp_award     (users 필요)
9  badge_ownership             (users 필요)
10 user_metric                 (users 필요)
11 personal_challenge_reward   (users, challenges 필요)
12 user_xp_ranking_snapshot    (user_xp 적재 후 실행)
13 study_metric                (users 필요)
14 course_progress + lesson_complete  (users, courses/lessons 필요)
15 discussion + comment + like (users, news 필요)
16 news_like                   (users, news 필요)
```

## 볼륨 조정

`generate_seed_data.py` 상단의 상수를 수정해 볼륨을 조정할 수 있습니다:

| 상수 | 기본값 | 의미 |
|------|--------|------|
| `PORTFOLIOS_PER_USER` | 3 | 유저당 포트폴리오 수 |
| `ASSETS_PER_PORTFOLIO` | 4 | 포트폴리오당 종목 수 |
| `TRADES_PER_USER` | 40 | 유저당 거래 수 |
| `INTEREST_STOCKS_PER_USER` | 10 | 관심 종목 수 |
| `USERS_PER_SQUAD` | 1000 | 스쿼드당 유저 수 |
| `USER_METRIC_FRACTION` | 0.10 | user_metric 적용 비율 |
