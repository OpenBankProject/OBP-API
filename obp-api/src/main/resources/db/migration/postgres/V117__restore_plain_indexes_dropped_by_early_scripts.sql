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

CREATE INDEX IF NOT EXISTS mappednarrative_bank_account_transaction_c
    ON mappednarrative(bank NULLS FIRST, account NULLS FIRST, transaction_c NULLS FIRST);

CREATE INDEX IF NOT EXISTS mappedcomment_view_c_bank_account_transaction_c
    ON mappedcomment(view_c NULLS FIRST, bank NULLS FIRST, account NULLS FIRST, transaction_c NULLS FIRST);

CREATE INDEX IF NOT EXISTS mappedtag_bank_account_transaction_c_view_c
    ON mappedtag(bank NULLS FIRST, account NULLS FIRST, transaction_c NULLS FIRST, view_c NULLS FIRST);

CREATE INDEX IF NOT EXISTS mappedwheretag_bank_account_transaction_c_view_c
    ON mappedwheretag(bank NULLS FIRST, account NULLS FIRST, transaction_c NULLS FIRST, view_c NULLS FIRST);

CREATE INDEX IF NOT EXISTS mappedtransactionimage_bank_account_transaction_c_view_c
    ON mappedtransactionimage(bank NULLS FIRST, account NULLS FIRST, transaction_c NULLS FIRST, view_c NULLS FIRST);

CREATE INDEX IF NOT EXISTS connector_trace_date_c
    ON connector_trace(date_c NULLS FIRST);

CREATE INDEX IF NOT EXISTS consent_item_consent_reference_id
    ON consent_item(consent_reference_id NULLS FIRST);

CREATE INDEX IF NOT EXISTS consent_item_consent_reference_id_bank_id
    ON consent_item(consent_reference_id NULLS FIRST, bank_id NULLS FIRST);
