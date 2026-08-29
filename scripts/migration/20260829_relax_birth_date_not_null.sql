-- 회원가입에서 생년월일·휴대폰 번호를 더 이상 수집하지 않는다.
-- users.birth_date 는 NOT NULL 로 만들어져 있어, 애플리케이션만 배포하면
-- 신규 가입 INSERT 가 DB 제약에 걸려 실패한다.
--
-- 실행 순서가 중요하다: 반드시 백엔드 배포 "전에" 적용할 것.
--   1) 이 스크립트 적용 (기존 배포 코드는 여전히 값을 채우므로 영향 없음)
--   2) 백엔드 배포
--
-- ddl-auto: update 는 기존 컬럼의 NOT NULL 제약을 완화하지 않으므로 수동 적용이 필요하다.
-- MariaDB 기준. 운영 반영 전 백업하고 스테이징에서 먼저 검증할 것.

ALTER TABLE users
    MODIFY COLUMN birth_date DATE NULL;

-- 휴대폰 번호 컬럼(phone_number_first_part / second / third)은
-- 애초에 NOT NULL 로 생성되지 않으므로 별도 변경이 필요 없다.
-- 다만 운영 스키마가 다를 수 있으니 아래로 확인한다.
--
--   SELECT COLUMN_NAME, IS_NULLABLE
--   FROM INFORMATION_SCHEMA.COLUMNS
--   WHERE TABLE_NAME = 'users'
--     AND COLUMN_NAME IN ('birth_date',
--                         'phone_number_first_part',
--                         'phone_number_second_part',
--                         'phone_number_third_part');
--
-- NOT NULL 로 나오는 컬럼이 있으면 아래를 함께 실행한다.
--
--   ALTER TABLE users
--       MODIFY COLUMN phone_number_first_part VARCHAR(255) NULL,
--       MODIFY COLUMN phone_number_second_part VARCHAR(255) NULL,
--       MODIFY COLUMN phone_number_third_part VARCHAR(255) NULL;
