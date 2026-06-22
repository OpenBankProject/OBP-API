-- Deduplication prerequisite for the UniqueIndex additions in the concurrency-hazard fix.
--
-- Background: MapperAccountHolders.dbIndexes changed from Index to UniqueIndex(user, bank, account).
--             MappedEntitlement.dbIndexes added UniqueIndex(mBankId, mUserId, mRoleName).
--             Lift Schemifier issues CREATE UNIQUE INDEX without a prior duplicate check; if any
--             duplicate rows exist the DDL fails and the application will not start.
--
-- Run this script BEFORE deploying the build that introduced these UniqueIndex additions.
--
-- IMPORTANT: Verify the exact column names against your live schema before running.
--            Use `\d mapperaccountholder` and `\d mappedentitlement` in psql to confirm.
--
-- PostgreSQL syntax. For H2 (dev/test), duplicates are normally absent; skip if not needed.

-- 1. Remove duplicate account-holder rows, keeping the earliest (lowest id) per natural key.
--    Natural key: (user_, accountbankpermalink, accountpermalink)
DELETE FROM mapperaccountholder
WHERE id NOT IN (
  SELECT MIN(id)
  FROM mapperaccountholder
  GROUP BY user_, accountbankpermalink, accountpermalink
);

-- 2. Remove duplicate entitlement rows, keeping the earliest per natural key.
--    Natural key: (mbankid, muserid, mrolename)
DELETE FROM mappedentitlement
WHERE id NOT IN (
  SELECT MIN(id)
  FROM mappedentitlement
  GROUP BY mbankid, muserid, mrolename
);
