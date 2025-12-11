# User ID Index Migrations

## Overview

This migration adds database indexes to improve data integrity and query performance for user-related operations.

## What This Migration Does

### 1. Unique Index on `resourceuser.userid_`

**File:** `MigrationOfUserIdIndexes.scala` - `addUniqueIndexOnResourceUserUserId()`

**Purpose:** Enforces uniqueness of the `user_id` field at the database level.

**SQL Generated:**
```sql
CREATE UNIQUE INDEX IF NOT EXISTS resourceuser_userid_unique ON resourceuser(userid_);
```

**Why This Is Important:**
- The OBP API specification states that `user_id` **MUST be unique** across the OBP instance
- The application code assumes uniqueness (uses `.find()` not `.findAll()`)
- Previously, uniqueness was only enforced by UUID generation (probabilistic)
- This migration adds a hard constraint at the database level

**Risk Assessment:**
- **Low Risk**: The `userid_` field is generated as a UUID, which has astronomically low collision probability
- If duplicate `user_id` values exist (extremely unlikely), the migration will fail and require manual cleanup

### 2. Index on `Metric.userid`

**File:** `MigrationOfUserIdIndexes.scala` - `addIndexOnMappedMetricUserId()`

**Purpose:** Improves query performance when searching metrics by user ID.

**SQL Generated:**
```sql
CREATE INDEX IF NOT EXISTS metric_userid_idx ON Metric(userid);
```

**Note:** The table name is `Metric` (capital M), not `mappedmetric`. This was changed from the old table name for consistency.

**Why This Is Important:**
- The metrics table tracks every API call and can grow very large
- New v6.0.0 feature: `last_activity_date` queries metrics by `user_id`
- Without an index, queries do a full table scan (slow!)
- With an index, queries are fast even on millions of rows

**Use Cases:**
- Getting a user's last API activity date
- Filtering metrics by user for analytics
- User activity reporting

## How to Run

### Automatic Execution

The migrations will run automatically on application startup if either:

1. **Execute all migrations:**
   ```properties
   migration_scripts.execute_all=true
   ```

2. **Execute specific migrations:**
   ```properties
   list_of_migration_scripts_to_execute=addUniqueIndexOnResourceUserUserId,addIndexOnMappedMetricUserId
   ```

### Migration Properties

Set in your `props/default.props` file:

```properties
# Enable migrations
migration_scripts=true

# Execute specific migrations (comma-separated list)
list_of_migration_scripts_to_execute=addUniqueIndexOnResourceUserUserId,addIndexOnMappedMetricUserId

# OR execute all migrations
# migration_scripts.execute_all=true
```

### Manual Execution (if needed)

If you need to run the SQL manually:

#### PostgreSQL:
```sql
CREATE UNIQUE INDEX IF NOT EXISTS resourceuser_userid_unique ON resourceuser(userid_);
CREATE INDEX IF NOT EXISTS metric_userid_idx ON Metric(userid);
```

#### MySQL:
```sql
CREATE UNIQUE INDEX resourceuser_userid_unique ON resourceuser(userid_);
CREATE INDEX metric_userid_idx ON Metric(userid);
```

#### SQL Server:
```sql
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'resourceuser_userid_unique' AND object_id = OBJECT_ID('resourceuser'))
BEGIN
    CREATE UNIQUE INDEX resourceuser_userid_unique ON resourceuser(userid_);
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'metric_userid_idx' AND object_id = OBJECT_ID('Metric'))
BEGIN
    CREATE INDEX metric_userid_idx ON Metric(userid);
END
```

## Verification

After running the migrations, verify they succeeded:

### Check Migration Logs

```sql
SELECT * FROM migrationscriptlog 
WHERE name IN ('addUniqueIndexOnResourceUserUserId', 'addIndexOnMappedMetricUserId')
ORDER BY executiondate DESC;
```

### Check Indexes Exist

#### PostgreSQL:
```sql
-- Check unique index on resourceuser
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'resourceuser' AND indexname = 'resourceuser_userid_unique';

-- Check index on Metric (note: lowercase 'metric' in pg_indexes)
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'metric' AND indexname = 'metric_userid_idx';
```

#### MySQL:
```sql
-- Check unique index on resourceuser
SHOW INDEX FROM resourceuser WHERE Key_name = 'resourceuser_userid_unique';

-- Check index on Metric
SHOW INDEX FROM Metric WHERE Key_name = 'metric_userid_idx';
```

#### SQL Server:
```sql
-- Check unique index on resourceuser
SELECT name, type_desc, is_unique
FROM sys.indexes 
WHERE object_id = OBJECT_ID('resourceuser') AND name = 'resourceuser_userid_unique';

-- Check index on Metric
SELECT name, type_desc, is_unique
FROM sys.indexes 
WHERE object_id = OBJECT_ID('Metric') AND name = 'metric_userid_idx';
```

## Rollback (if needed)

If you need to remove these indexes:

```sql
-- PostgreSQL / H2
DROP INDEX IF EXISTS resourceuser_userid_unique;
DROP INDEX IF EXISTS metric_userid_idx;

-- MySQL
DROP INDEX resourceuser_userid_unique ON resourceuser;
DROP INDEX metric_userid_idx ON Metric;

-- SQL Server
DROP INDEX resourceuser.resourceuser_userid_unique;
DROP INDEX Metric.metric_userid_idx;
```

Then delete the migration log entries:
```sql
DELETE FROM migrationscriptlog 
WHERE name IN ('addUniqueIndexOnResourceUserUserId', 'addIndexOnMappedMetricUserId');
```

## Performance Impact

### During Migration
- **resourceuser table:** Minimal impact (small table, UUID values already unique)
- **Metric table:** May take several minutes on large tables (millions of rows)
- **Recommendation:** Run during low-traffic period if metrics table is very large

### After Migration
- **Query Performance:** ✅ Significantly faster for user_id lookups in metrics
- **Insert Performance:** Negligible impact (indexes are maintained automatically)
- **Storage:** Minimal increase (indexes are lightweight)

## Related Features

This migration supports:
- **v6.0.0 API Enhancement:** `last_activity_date` field in User responses
- **Data Integrity:** Enforces user_id uniqueness per specification
- **Performance Optimization:** Fast user activity queries

## Troubleshooting

### Migration Fails: Duplicate user_id Found

**Extremely unlikely** (UUID collision probability: ~1 in 10^36), but if it happens:

1. Find duplicates:
   ```sql
   SELECT userid_, COUNT(*) 
   FROM resourceuser 
   GROUP BY userid_ 
   HAVING COUNT(*) > 1;
   ```

2. Investigate and resolve manually (contact OBP support)

3. Re-run migration after cleanup

### Index Already Exists

The migration is idempotent - it checks if indexes exist before creating them.
If the index already exists, the migration will succeed with a log message.

## Support

For issues or questions:
- Open an issue on GitHub: https://github.com/OpenBankProject/OBP-API
- Contact: contact@openbankproject.com

---

**Migration Version:** 1.0  
**Date:** January 2025  
**Author:** OBP Development Team