-- users.id type: CHAR(36) or VARCHAR(36)
-- Replace __CSV_PATH__ with your absolute CSV path before running.
-- Example:
-- sed "s|__CSV_PATH__|/Users/me/project/data/users_1000000.csv|g" \
--   scripts/seed/load_users_char_uuid.sql \
--   | mysql --local-infile=1 -u finvibe -p finvibe

LOAD DATA LOCAL INFILE '__CSV_PATH__'
INTO TABLE users
FIELDS TERMINATED BY ','
LINES TERMINATED BY '\n'
(
	id,
	login_id,
	password_hash,
	provider,
	provider_id,
	role,
	phone_number_first_part,
	phone_number_second_part,
	phone_number_third_part,
	birth_date,
	name,
	nickname,
	email,
	is_deleted,
	created_at,
	last_modified_at
);
