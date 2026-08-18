-- Restores five unique indexes the earliest migration scripts left out.
--
-- V013's comment records the discovery that FlywayBaselineExport does not emit dbIndexes-declared
-- unique indexes even though Schemifier creates them, so from that script onwards each one was
-- added by hand, read off a booted instance's information_schema.indexes. The five tables migrated
-- before that discovery - V001 atm, V003 comment, V004 tag, V006 transactionimage, V009
-- consentitem - kept only the plain indexes the export did emit. Their entities all declared a
-- UniqueIndex:
--
--   MappedAtm               UniqueIndex(mBankId, mAtmId)
--   MappedComment           UniqueIndex(apiId)
--   MappedTag               UniqueIndex(tagId)
--   MappedTransactionImage  UniqueIndex(imageId)
--   ConsentItem             UniqueIndex(consentItemId)
--
-- An existing database still has them - Schemifier created them before the entity was deleted - so
-- this only bites a database created from the Flyway scripts alone: every CI run, and any new
-- deployment. There the constraint is simply absent and duplicates are accepted silently, which
-- the code does not expect: the readers take one row (LIMIT 1) and assume there is only one.
--
-- Written as a new script rather than an edit to V001/V003/V004/V006/V009 because Flyway checksums
-- what it has applied; editing an applied script fails every existing database with a checksum
-- mismatch.
--
-- Duplicates are collapsed first, keeping the lowest id - the earliest-inserted row, the one most
-- likely to have downstream data keyed to it - because a unique index cannot be created over
-- existing duplicates. On a database that already has the index there is nothing to collapse and
-- IF NOT EXISTS makes the creation a no-op.

DELETE FROM mappedatm
WHERE mbankid IS NOT NULL AND matmid IS NOT NULL
  AND id NOT IN (
    SELECT MIN(id) FROM mappedatm
    WHERE mbankid IS NOT NULL AND matmid IS NOT NULL
    GROUP BY mbankid, matmid
  );

DELETE FROM mappedcomment
WHERE apiid IS NOT NULL
  AND id NOT IN (SELECT MIN(id) FROM mappedcomment WHERE apiid IS NOT NULL GROUP BY apiid);

DELETE FROM mappedtag
WHERE tagid IS NOT NULL
  AND id NOT IN (SELECT MIN(id) FROM mappedtag WHERE tagid IS NOT NULL GROUP BY tagid);

DELETE FROM mappedtransactionimage
WHERE imageid IS NOT NULL
  AND id NOT IN (
    SELECT MIN(id) FROM mappedtransactionimage WHERE imageid IS NOT NULL GROUP BY imageid
  );

DELETE FROM consent_item
WHERE consent_item_id IS NOT NULL
  AND id NOT IN (
    SELECT MIN(id) FROM consent_item WHERE consent_item_id IS NOT NULL GROUP BY consent_item_id
  );

CREATE UNIQUE INDEX IF NOT EXISTS mappedatm_mbankid_matmid
    ON mappedatm(mbankid NULLS FIRST, matmid NULLS FIRST);
CREATE UNIQUE INDEX IF NOT EXISTS mappedcomment_apiid
    ON mappedcomment(apiid NULLS FIRST);
CREATE UNIQUE INDEX IF NOT EXISTS mappedtag_tagid
    ON mappedtag(tagid NULLS FIRST);
CREATE UNIQUE INDEX IF NOT EXISTS mappedtransactionimage_imageid
    ON mappedtransactionimage(imageid NULLS FIRST);
CREATE UNIQUE INDEX IF NOT EXISTS consent_item_consent_item_id
    ON consent_item(consent_item_id NULLS FIRST);
