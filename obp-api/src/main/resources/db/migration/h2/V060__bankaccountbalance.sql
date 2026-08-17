-- Per-account balances (sixty-third table off Lift Mapper), backing the v5.1.0
-- bank-account-balance CRUD endpoints. balanceamount is stored in the smallest currency unit
-- (cents/pence/...) as a BIGINT; the account's currency, needed to convert it back to a decimal,
-- is not on this table and is looked up from mappedbankaccount.
--
-- NO primary key and NO indexes - deliberately. The entity is a KeyedMapper[String, _] whose
-- primaryKeyField is BalanceId_, but Schemifier emitted a bare CREATE TABLE for it: no PK
-- constraint, no unique index, not even the implicit id column the LongKeyedMapper tables get.
-- Verified against a booted instance: information_schema.indexes returns nothing at all for this
-- table. Reproduced as-is. Adding the primary key that the entity declares would be a schema
-- change beyond this migration's remit, and on an existing database it could fail outright if
-- duplicate balanceid_ values were already allowed in by its absence.

CREATE TABLE "PUBLIC"."BANKACCOUNTBALANCE"(
    "UPDATEDAT" TIMESTAMP,
    "CREATEDAT" TIMESTAMP,
    "ACCOUNTID_" CHARACTER VARYING(36),
    "BALANCEID_" CHARACTER VARYING(36),
    "BANKID_" CHARACTER VARYING(36),
    "BALANCETYPE" CHARACTER VARYING(255),
    "BALANCEAMOUNT" BIGINT,
    "REFERENCEDATE" DATE
);
