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
