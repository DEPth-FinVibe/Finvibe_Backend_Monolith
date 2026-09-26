# Dual User ID Migration Runbook

## Purpose

Finvibe is moving to a dual user identifier model:

- `users.id`: existing UUID, preserved as the external/public identity.
- `users.internal_user_id`: new Long identifier for internal relational and batch persistence paths.

This phase does **not** physically swap the `users` primary key. Public JWT/API contracts continue to expose UUID user IDs.

## Deployment Order

1. Back up the production database.
2. Confirm live DDL:
   - `SHOW CREATE TABLE users;`
   - `SHOW CREATE TABLE user_profit_snapshot_daily;`
   - `SHOW CREATE TABLE portfolio_performance_snapshot_daily;`
3. Adapt the migration scripts if live UUID columns are `CHAR(36)`/`VARCHAR(36)` instead of `BINARY(16)`.
4. Apply `scripts/migration/20260513_add_internal_user_id.sql`.
5. Deploy Monolith and Batch code containing `User.internalUserId` and resolver support.
6. Apply/validate snapshot backfill using `scripts/migration/20260513_backfill_snapshot_user_ids.sql`.
7. Deploy Batch with the migrated snapshot writers.
8. Trigger both snapshot jobs manually and verify logs/rows.
9. Verify Prometheus scrapes `backend-batch` on `/actuator/prometheus`.

## Public UUID Contract

Keep these UUID-based during this phase:

- JWT subject and user ID claims.
- `Requester.userId`.
- User-facing DTO fields named `userId`.
- External controller path variables accepting UUID user IDs.

New internal code should use explicit names such as `internalUserId` and `externalUserId` to avoid semantic confusion.

## Internal Long ID

`internal_user_id` is a generated, unique Long column used by the migrated batch snapshot tables. The UUID -> Long resolver reads from the `users` table and writes an immutable Redis cache entry under `user:id-map:{externalUuid}`.

Redis is not authoritative. Cache misses must fall back to the database.

## Backfill Validation

Before swapping or altering snapshot tables, validate:

```sql
SELECT COUNT(*) FROM users WHERE internal_user_id IS NULL;
SELECT internal_user_id, COUNT(*) FROM users GROUP BY internal_user_id HAVING COUNT(*) > 1;

SELECT COUNT(*) FROM user_profit_snapshot_daily;
SELECT COUNT(*) FROM user_profit_snapshot_daily_long;
SELECT COUNT(*) FROM portfolio_performance_snapshot_daily;
SELECT COUNT(*) FROM portfolio_performance_snapshot_daily_long;
```

Any unmapped snapshot rows must be reported and investigated. Do not silently discard historical financial data.

## Rollback

If the application deployment fails before snapshot table swap:

1. Roll back the application image/tag.
2. Leave `users.internal_user_id` in place; it is additive and does not break UUID public flows.
3. Clear `user:id-map:*` cache keys only if stale mappings are suspected.

If snapshot table swap has already occurred:

1. Stop Batch to prevent additional writes.
2. Restore snapshot tables from backup or the pre-swap copy.
3. Roll back the application image/tag.
4. Re-run validation before enabling Batch again.

## Operational Verification

- Batch build: `./gradlew clean build --no-daemon`
- Monolith build: `./gradlew clean build --no-daemon`
- Batch metrics: `curl -fsS http://backend-batch/actuator/prometheus`
- Batch logs: confirm no `Data too long for column 'user_id'` after both snapshot jobs run.
