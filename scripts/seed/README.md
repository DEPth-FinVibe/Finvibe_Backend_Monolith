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

## ⚡ 성능 최적화 (v2.0)

이 스크립트는 **병렬 처리**와 **DB 최적화**를 적용하여 대용량 데이터(100만 유저 기준)를 
**기존 1~2시간 → 15~30분**으로 단축합니다.

### 최적화 항목

| 최적화 | 효과 |
|--------|------|
| **병렬 CSV 생성** | CPU 코어 수만큼 동시에 생성 (ProcessPoolExecutor) |
| **DB 로드 안정화** | PyMySQL 안정성 이슈 회피를 위해 순차 적재 |
| **DB 설정 최적화** | FK/Unique 체크 일시 비활성화 (SUPER 권한 불필요) |
| **TSV 포맷** | CSV 대비 escape 처리 감소, 빠른 파싱 |
| **StringIO 버퍼링** | 메모리에서 한 번에 파일 쓰기 |
| **파이프라인 구조** | Phase별 실행으로 의존성 관리 |

## 사전 조건

```bash
pip install pymysql
```

`.env`에 DB 접속 정보 및 병렬 설정:

```bash
# 필수 설정
SEED_DB_HOST=localhost
SEED_DB_PORT=3306
SEED_DB_NAME=finvibe
SEED_DB_USER=finvibe
SEED_DB_PASSWORD=finvibe

# 선택사항: 병렬 워커 수 (기본값: CPU 코어 수)
SEED_PARALLEL_WORKERS=8
```

## 실행

### 인터랙티브 모드 (테이블별 선택)

```bash
python3 scripts/seed/generate_seed_data.py
```

메뉴에서 번호를 선택해 특정 테이블만 생성할 수 있습니다.

### 전체 자동 생성 (권장)

```bash
# 옵션 0: 전체를 최적화된 병렬 방식으로 생성
python3 scripts/seed/generate_seed_data.py
# 메뉴에서 "0" 입력
```

## 생성 순서 및 병렬 처리

```
PHASE 1: Independent Tables (순차 실행)
  1. personal_challenge
  2. course → lesson_content → lesson

PHASE 2: Large Tables (병렬 처리)
  3. interest_stock              ← 병렬 CSV 생성 + 병렬 로드
  4. portfolio_group + asset + trade  ← 3단계 파이프라인 (병렬)

PHASE 3: User-Related Tables (순차 실행)
  5. user_squad
  6. user_xp + user_xp_award
  7. badge_ownership
  8. user_metric
  9. personal_challenge_reward
  10. study_metric
  11. course_progress + lesson_complete

PHASE 4: Social Tables (순차 실행)
  12. discussion + comment + like
  13. news_like

PHASE 5: Ranking (user_xp 의존)
  14. user_xp_ranking_snapshot
```

## 볼륨 조정

`generate_seed_data.py` 상단의 상수를 수정:

| 상수 | 기본값 | 100만 유저 기준 예상량 |
|------|--------|------------------------|
| `COURSES` | 10 | 10개 |
| `LESSONS_PER_COURSE` | 5 | 50개 |
| `PERSONAL_CHALLENGES` | 20 | 20개 |
| `PORTFOLIOS_PER_USER` | 3 | 300만 행 |
| `ASSETS_PER_PORTFOLIO` | 4 | 1,200만 행 |
| `TRADES_PER_USER` | 40 | 4,000만 행 |
| `INTEREST_STOCKS_PER_USER` | 10 | 1,000만 행 |
| `USERS_PER_SQUAD` | 1000 | 스쿼드당 1,000명 |
| `XP_AWARDS_PER_USER` | 5 | 500만 행 |
| `BADGES_PER_USER_AVG` | 2 | 200만 행 |
| `USER_METRIC_FRACTION` | 0.10 | 30만 행 |
| `CHALLENGE_COMPLETION_RATE` | 0.30 | 600만 행 |
| `COURSE_PROGRESS_RATE` | 0.40 | 40만 행 |
| `LESSON_COMPLETE_RATE` | 0.60 | 3,000만 행 |
| `DISCUSSIONS_PER_NEWS` | 3 | news 수 × 3 |
| `COMMENTS_PER_DISCUSSION` | 3 | 토론 수 × 3 |
| `DISCUSSION_LIKES_PER` | 30 | 토론 수 × 30 |
| `COMMENT_LIKES_PER` | 10 | 댓글 수 × 10 |
| `NEWS_LIKES_PER` | 50 | news 수 × 50 |

## 성능 벤치마크

**테스트 환경:**
- CPU: 8 cores
- RAM: 16GB
- DB: MariaDB 10.6 (로컬)
- Network: localhost

**100만 유저 기준:**

| 테이블 | 예상 행 수 | 생성 시간 | 로드 시간 |
|--------|------------|-----------|-----------|
| interest_stock | 1,000만 | ~2분 | ~1분 |
| portfolio_group | 300만 | ~1분 | ~30초 |
| asset | 1,200만 | ~4분 | ~2분 |
| trade | 4,000만 | ~8분 | ~4분 |
| **전체** | **~1억** | **~20분** | **~10분** |
| **총 소요** | - | **~30분** | (기존: 1~2시간) |

## 주의사항

### DB 설정 최적화

전체 생성 시 다음 설정이 **일시적으로 비활성화**됩니다:
- `FOREIGN_KEY_CHECKS = 0`
- `UNIQUE_CHECKS = 0`

완료 후 자동으로 복원됩니다. 중간에 종료할 경우 수동 복원 필요:

```sql
SET FOREIGN_KEY_CHECKS = 1;
SET UNIQUE_CHECKS = 1;
```

### 메모리 사용량

- 병렬 생성 중 메모리 사용량 증가
- 대용량(100만 유저+)에서는 최소 8GB RAM 권장
- `SEED_PARALLEL_WORKERS` 값을 낮춰 메모리 사용량 조절 가능

### 디스크 공간

생성되는 임시 TSV 파일들:
- interest_stock: ~500MB
- asset: ~600MB
- trade: ~2GB
- **총 임시 파일**: ~4GB

작업 완료 후 자동 삭제됩니다.

## 문제 해결

### "Too many open files" 에러

```bash
# macOS/Linux
ulimit -n 4096

# Windows (PowerShell Admin)
# 레지스트리 수정 또는 더 낮은 WORKERS 값 사용
```

### 메모리 부족

```bash
# .env에서 워커 수 감소
SEED_PARALLEL_WORKERS=4
```

### LOAD DATA 실패

1. MariaDB 설정 확인:
```sql
SHOW VARIABLES LIKE 'local_infile';
-- ON이어야 함
```

2. 파일 권한 확인:
```bash
ls -la data/*.tsv
```

## 기술 상세

### 병렬 처리 아키텍처

```
┌─────────────────────────────────────────────────┐
│  Main Process                                   │
│  ├── Phase 1: Reference Data Loading          │
│  ├── Phase 2: Parallel CSV Generation         │
│  │   └── ProcessPoolExecutor (CPU-bound)     │
│  ├── Phase 3: Parallel LOAD DATA              │
│  │   └── ThreadPoolExecutor (I/O-bound)     │
│  └── Phase 4: Cleanup                         │
└─────────────────────────────────────────────────┘
```

### CSV vs TSV 비교

| 항목 | CSV | TSV |
|------|-----|-----|
| 구분자 | `,` | `\t` |
| Escape 필요 | 쉼표, 따옴표 | 없음 |
| 파일 크기 | 큼 | 작음 |
| MariaDB 파싱 | 느림 | 빠름 |

### 왜 Process + Thread Pool?

- **ProcessPool**: Python GIL 우회, 진정한 CPU 병렬화
- **ThreadPool**: DB 연결 공유, 네트워크 I/O 병렬화

---

## 개발 참고

### 백업 생성

기존 스크립트 백업:
```bash
cp scripts/seed/generate_seed_data.py \
   scripts/seed/generate_seed_data.py.backup
```

### 프로파일링

```bash
# 시간 측정
python3 -m cProfile -s cumtime scripts/seed/generate_seed_data.py
```

### 로그 확인

스크립트 실행 시 상세 로그:
```
[DB] Optimizations enabled (FK/Unique checks disabled)
[Parallel] Starting 8 tasks with 8 workers [interest_stock]
  Chunk 1/8 completed
  Chunk 2/8 completed
  ...
```
