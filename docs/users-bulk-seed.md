# Users Bulk Seed Guide

This guide generates 1,000,000 dummy `users` rows as CSV and imports them with MariaDB `LOAD DATA`.

## 1) Generate CSV

```bash
python3 scripts/seed/generate_users_csv.py \
  --rows 1000000 \
  --output data/users_1000000.csv \
  --seed 42
```

The generated CSV has no header and matches these columns:

```text
id,login_id,password_hash,provider,provider_id,role,phone_number_first_part,phone_number_second_part,phone_number_third_part,birth_date,name,nickname,email,is_deleted,created_at,last_modified_at
```

## 2) Check `users.id` column type

```sql
SHOW COLUMNS FROM users LIKE 'id';
```

- If type is `char(36)` or `varchar(36)`, use `load_users_char_uuid.sql`
- If type is `binary(16)`, use `load_users_binary_uuid.sql`

## 3) Import CSV with `LOAD DATA LOCAL INFILE`

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

## 4) Verify row count and storage

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

## Notes

- `LOAD DATA LOCAL INFILE` must be enabled on both client and server.
- The CSV uses `\N` for nullable `provider_id`.
- Default dummy password hash is fixed for all rows for generation speed.
