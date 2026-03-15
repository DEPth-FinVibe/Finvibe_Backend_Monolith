INSERT INTO wallet (created_at, last_modified_at, balance, user_id)
SELECT NOW(6), NOW(6), 10000000, u.id
FROM users u
LEFT JOIN wallet w ON u.id = w.user_id
WHERE w.user_id IS NULL;
