-- Completes what V117 started. V117 restored the indexes it could see by reading the early
-- migration scripts and asking which ones looked short; that found the composite indexes on the
-- transaction-metadata tables and two of the consent-item ones, but it was not an enumeration.
-- Reading each entity's own dbIndexes list instead - the declaration Schemifier acted on - leaves
-- six that no script creates.
--
--   ConnectorTrace  Index(correlationId), Index(connectorName), Index(functionName),
--                   Index(userId), Index(bankId)      -- only Index(date) reached V117
--   ConsentItem     Index(bankId)                     -- the other three are in V116/V117
--
-- As in V117 these change no answer, only the cost of getting it. Both tables are read on hot
-- paths: ConnectorTrace is looked up by correlation id when tracing a single call through the
-- connector, and consent items are read per consent check, which happens on every request that
-- carries a Consent-Id. Without the index each of those is a full scan of a table that grows with
-- traffic. A database that predates Flyway still has all of them - Schemifier created them from
-- the same declarations - so this only affects databases built from the scripts: every CI run and
-- every new deployment.
--
-- A new script rather than an edit to V117, which is already applied and checksummed, and
-- IF NOT EXISTS throughout so this is a no-op where they exist.

CREATE INDEX IF NOT EXISTS connector_trace_correlationid
    ON connector_trace(correlationid NULLS FIRST);

CREATE INDEX IF NOT EXISTS connector_trace_connectorname
    ON connector_trace(connectorname NULLS FIRST);

CREATE INDEX IF NOT EXISTS connector_trace_functionname
    ON connector_trace(functionname NULLS FIRST);

CREATE INDEX IF NOT EXISTS connector_trace_userid
    ON connector_trace(userid NULLS FIRST);

CREATE INDEX IF NOT EXISTS connector_trace_bankid
    ON connector_trace(bankid NULLS FIRST);

CREATE INDEX IF NOT EXISTS consent_item_bank_id
    ON consent_item(bank_id NULLS FIRST);
