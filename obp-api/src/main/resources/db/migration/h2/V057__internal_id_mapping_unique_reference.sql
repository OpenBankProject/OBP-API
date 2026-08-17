-- Add the missing single-column unique index on the plain-text-reference column of the three
-- internal id-mapping tables, and drop the redundant composite index it was mistakenly written as.
--
-- THE DEFECT (pre-existing, inherited verbatim from the Lift entities, not introduced by the
-- Mapper -> Doobie migration). Each entity declared:
--
--     UniqueIndex(mAccountId) :: UniqueIndex(mAccountId, mAccountPlainTextReference)
--
-- Neither constrains the reference column on its own:
--   * mAccountId is a MappedUUID - a fresh random value on every insert, so it never collides.
--   * the composite (mAccountId, reference) is strictly implied by the single-column unique index
--     on mAccountId, so it can never reject a row the first index would have allowed. It is dead
--     weight: zero constraint value, and zero lookup value too, since its leading column is
--     already covered.
--
-- So getOrCreate*Id - a SELECT-then-INSERT with no constraint underneath - lets two concurrent
-- calls for the SAME reference both miss the SELECT, both INSERT, and both succeed, minting two
-- different OBP ids for one underlying bank reference. A later read (LIMIT 1, no ORDER BY) then
-- returns an arbitrary one of them, so data written under one id is invisible under the other.
-- This is on the hot path: Helper.convertToId runs it for every inbound message on the RabbitMQ,
-- gRPC, REST and stored-procedure connectors.
--
-- The providers already contain a "unique-index violation from a concurrent insert - re-fetch the
-- committed row" retry branch, written for exactly the constraint that was never created. That
-- branch is dead code today; this migration is what makes it live and correct. No provider code
-- changes are needed.
--
-- DEDUP: a unique index cannot be created over existing duplicates, so any that the missing
-- constraint already allowed are collapsed first, keeping the LOWEST id per reference - the
-- earliest-inserted row, the one most likely to have downstream data already keyed to it. Rows
-- with a NULL reference are left alone: SQL unique indexes permit multiple NULLs, so they cannot
-- violate the new constraint and must not be deleted.
--
-- CAVEAT for the non-H2 vendor folders when they are created: on PostgreSQL a constraint
-- violation aborts the surrounding transaction, so the providers' catch-and-re-fetch retry only
-- works if the INSERT is not sharing a transaction with later statements. Verify that before
-- porting this file to db/migration/postgres.

DELETE FROM accountidmapping
WHERE maccountplaintextreference IS NOT NULL
  AND id NOT IN (
    SELECT MIN(id) FROM accountidmapping
    WHERE maccountplaintextreference IS NOT NULL
    GROUP BY maccountplaintextreference
  );

DELETE FROM mappedcustomeridmapping
WHERE mcustomerplaintextreference IS NOT NULL
  AND id NOT IN (
    SELECT MIN(id) FROM mappedcustomeridmapping
    WHERE mcustomerplaintextreference IS NOT NULL
    GROUP BY mcustomerplaintextreference
  );

DELETE FROM transactionidmapping
WHERE transactionplaintextreference IS NOT NULL
  AND id NOT IN (
    SELECT MIN(id) FROM transactionidmapping
    WHERE transactionplaintextreference IS NOT NULL
    GROUP BY transactionplaintextreference
  );

DROP INDEX IF EXISTS "PUBLIC"."ACCOUNTIDMAPPING_MACCOUNTID_MACCOUNTPLAINTEXTREFERENCE";
DROP INDEX IF EXISTS "PUBLIC"."MAPPEDCUSTOMERIDMAPPING_MCUSTOMERID_MCUSTOMERPLAINTEXTREFERENCE";
DROP INDEX IF EXISTS "PUBLIC"."TRANSACTIONIDMAPPING_TRANSACTIONID_TRANSACTIONPLAINTEXTREFERENCE";

CREATE UNIQUE INDEX "PUBLIC"."ACCOUNTIDMAPPING_MACCOUNTPLAINTEXTREFERENCE"
    ON "PUBLIC"."ACCOUNTIDMAPPING"("MACCOUNTPLAINTEXTREFERENCE" NULLS FIRST);
CREATE UNIQUE INDEX "PUBLIC"."MAPPEDCUSTOMERIDMAPPING_MCUSTOMERPLAINTEXTREFERENCE"
    ON "PUBLIC"."MAPPEDCUSTOMERIDMAPPING"("MCUSTOMERPLAINTEXTREFERENCE" NULLS FIRST);
CREATE UNIQUE INDEX "PUBLIC"."TRANSACTIONIDMAPPING_TRANSACTIONPLAINTEXTREFERENCE"
    ON "PUBLIC"."TRANSACTIONIDMAPPING"("TRANSACTIONPLAINTEXTREFERENCE" NULLS FIRST);
