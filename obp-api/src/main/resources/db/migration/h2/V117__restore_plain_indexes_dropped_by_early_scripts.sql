-- Restores the plain indexes the earliest migration scripts left out, for the same reason V116
-- restored their unique ones.
--
-- FlywayBaselineExport emits the per-field indexes Schemifier creates from dbIndexed_?, but not
-- the ones declared in dbIndexes - V001's MAPPEDATM_MBANKID came through, the composite ones did
-- not. The scripts written before that was understood (V002 narrative, V003 comment, V004 tag,
-- V005 wheretag, V006 transactionimage, V008 connectortrace, V009 consentitem) therefore create no
-- index at all beyond the primary key, while their entities declared:
--
--   MappedNarrative         Index(bank, account, transaction)
--   MappedComment           Index(view, bank, account, transaction)
--   MappedTag               Index(bank, account, transaction, view)
--   MappedWhereTag          Index(bank, account, transaction, view)
--   MappedTransactionImage  Index(bank, account, transaction, view)
--   ConnectorTrace          Index(date)
--   ConsentItem             Index(consentReferenceId), Index(consentReferenceId, bankId)
--
-- Unlike V116 nothing here is a correctness problem - a missing index changes no answer. It
-- changes the cost: the first five are exactly the lookup every transaction-metadata read
-- performs, so without them each one becomes a full scan of a table that grows with transaction
-- volume, and consent items are read per consent check. An existing database still has these
-- indexes; a database built from the scripts alone - every CI run, every new deployment - does not.
--
-- Column names carry Schemifier's reserved-word suffixes: TRANSACTION_C, VIEW_C, DATE_C.
--
-- A new script rather than an edit to the applied ones, and IF NOT EXISTS throughout, so this is a
-- no-op on a database that already has them.

CREATE INDEX IF NOT EXISTS "PUBLIC"."MAPPEDNARRATIVE_BANK_ACCOUNT_TRANSACTION_C"
    ON "PUBLIC"."MAPPEDNARRATIVE"("BANK" NULLS FIRST, "ACCOUNT" NULLS FIRST, "TRANSACTION_C" NULLS FIRST);

CREATE INDEX IF NOT EXISTS "PUBLIC"."MAPPEDCOMMENT_VIEW_C_BANK_ACCOUNT_TRANSACTION_C"
    ON "PUBLIC"."MAPPEDCOMMENT"("VIEW_C" NULLS FIRST, "BANK" NULLS FIRST, "ACCOUNT" NULLS FIRST, "TRANSACTION_C" NULLS FIRST);

CREATE INDEX IF NOT EXISTS "PUBLIC"."MAPPEDTAG_BANK_ACCOUNT_TRANSACTION_C_VIEW_C"
    ON "PUBLIC"."MAPPEDTAG"("BANK" NULLS FIRST, "ACCOUNT" NULLS FIRST, "TRANSACTION_C" NULLS FIRST, "VIEW_C" NULLS FIRST);

CREATE INDEX IF NOT EXISTS "PUBLIC"."MAPPEDWHERETAG_BANK_ACCOUNT_TRANSACTION_C_VIEW_C"
    ON "PUBLIC"."MAPPEDWHERETAG"("BANK" NULLS FIRST, "ACCOUNT" NULLS FIRST, "TRANSACTION_C" NULLS FIRST, "VIEW_C" NULLS FIRST);

CREATE INDEX IF NOT EXISTS "PUBLIC"."MAPPEDTRANSACTIONIMAGE_BANK_ACCOUNT_TRANSACTION_C_VIEW_C"
    ON "PUBLIC"."MAPPEDTRANSACTIONIMAGE"("BANK" NULLS FIRST, "ACCOUNT" NULLS FIRST, "TRANSACTION_C" NULLS FIRST, "VIEW_C" NULLS FIRST);

CREATE INDEX IF NOT EXISTS "PUBLIC"."CONNECTOR_TRACE_DATE_C"
    ON "PUBLIC"."CONNECTOR_TRACE"("DATE_C" NULLS FIRST);

CREATE INDEX IF NOT EXISTS "PUBLIC"."CONSENT_ITEM_CONSENT_REFERENCE_ID"
    ON "PUBLIC"."CONSENT_ITEM"("CONSENT_REFERENCE_ID" NULLS FIRST);

CREATE INDEX IF NOT EXISTS "PUBLIC"."CONSENT_ITEM_CONSENT_REFERENCE_ID_BANK_ID"
    ON "PUBLIC"."CONSENT_ITEM"("CONSENT_REFERENCE_ID" NULLS FIRST, "BANK_ID" NULLS FIRST);
