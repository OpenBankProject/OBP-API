-- This script reports how stored amounts are distributed across currency codes, so the asset
-- registry migration (ideas/ASSET_REGISTRY.md, section 7) can be sized against real data before
-- any of it is written. It only reads: everything runs in one transaction that is rolled back at
-- the end, and that transaction is switched to read-only before the first query reads data, so
-- even a mistake in this file cannot change data. The only things it creates are two temporary
-- views, which exist only in this session and are discarded by the rollback.
--
-- Run it against a copy of the database you want to assess (PostgreSQL), for example:
--   psql -h localhost -U obp -d sandbox -f scripts/asset_registry_currency_report.sql
--
-- Background: amounts are stored as whole numbers of minor units, at the precision
-- Helper.currencyDecimalPlaces gives each code today (CZK/JPY/KRW 0, KWD/OMR 3, every other code 2).
-- The migration converts them to exact decimals in the main unit, then corrects each code's
-- precision. Lowering a code's precision is only possible if no stored amount in that code has
-- digits beyond the new precision, and that is what sections 3 and 4 count.

BEGIN;

-- Every stored amount, with the currency it is in and the table and column it came from.
-- bankaccountbalance has no currency of its own; it takes the currency of its account.
CREATE TEMPORARY VIEW stored_amounts AS
          SELECT 'mappedbankaccount.accountbalance'::text AS amount_column, accountcurrency AS currency, accountbalance AS minor_units FROM mappedbankaccount
UNION ALL SELECT 'mappedtransaction.amount',              currency,        amount            FROM mappedtransaction
UNION ALL SELECT 'mappedtransaction.newaccountbalance',   currency,        newaccountbalance FROM mappedtransaction
UNION ALL SELECT 'standingorder.amountvalue',             amountcurrency,  amountvalue       FROM standingorder
UNION ALL SELECT 'bankaccountbalance.balanceamount',      account.accountcurrency, balance.balanceamount
          FROM bankaccountbalance balance
          LEFT JOIN mappedbankaccount account
                 ON account.theaccountid = balance.accountid_ AND account.bank = balance.bankid_;

-- The precision each code is stored at today (Helper.currencyDecimalPlaces). The match there is
-- case-sensitive, so 'jpy' is stored at 2, not 0; this view reproduces that exactly.
CREATE TEMPORARY VIEW stored_amounts_with_precision AS
SELECT amount_column, currency, minor_units,
       CASE WHEN currency IN ('CZK', 'JPY', 'KRW') THEN 0
            WHEN currency IN ('KWD', 'OMR')        THEN 3
            ELSE 2 END AS legacy_decimal_places
FROM stored_amounts;

-- PostgreSQL refuses CREATE (even of a temporary view) in a read-only transaction, so the views
-- above are created first and the transaction is made read-only here, before anything is read.
SET LOCAL transaction_read_only = on;

\echo
\echo '1. Rows per currency code and amount column (every code with stored amounts)'
SELECT currency, amount_column, count(*) AS row_count
FROM stored_amounts
GROUP BY currency, amount_column
ORDER BY currency, amount_column;

\echo
\echo '2. Currency values that are not in upper case, or that are missing'
\echo '   (affected by the rule that currency codes are case-insensitive and stored upper case)'
SELECT coalesce(currency, '<null>') AS currency, amount_column, count(*) AS row_count
FROM stored_amounts
WHERE currency IS NULL OR currency <> upper(currency)
GROUP BY currency, amount_column
ORDER BY currency, amount_column;

\echo
\echo '3. Codes whose ISO precision is 0 but which are stored at 2:'
\echo '   rows whose amount has a fractional part, which would block lowering the precision to 0'
SELECT currency, amount_column,
       count(*) AS row_count,
       count(*) FILTER (WHERE minor_units % 100 <> 0) AS rows_with_fractional_amount
FROM stored_amounts
WHERE currency IN ('BIF', 'CLP', 'DJF', 'GNF', 'ISK', 'KMF', 'PYG', 'RWF', 'UGX', 'UYI',
                   'VND', 'VUV', 'XAF', 'XOF', 'XPF')
GROUP BY currency, amount_column
ORDER BY currency, amount_column;

\echo
\echo '4. lovelace and wei (to be folded into ADA and ETH): rows holding a fractional lovelace or'
\echo '   wei, which is not a real on-chain amount and would be reported, not converted'
SELECT currency, amount_column,
       count(*) AS row_count,
       count(*) FILTER (WHERE minor_units % 100 <> 0) AS rows_with_fractional_unit
FROM stored_amounts
WHERE lower(currency) IN ('lovelace', 'wei')
GROUP BY currency, amount_column
ORDER BY currency, amount_column;

\echo
\echo '5. Conversion check: the largest absolute amount per code, in minor units and in the main unit'
\echo '   (the new column holds up to 20 digits before the decimal point, so every Long fits)'
SELECT currency, legacy_decimal_places,
       max(abs(minor_units)) AS largest_minor_units,
       max(abs(minor_units))::numeric / (10 ^ legacy_decimal_places)::numeric AS largest_main_units
FROM stored_amounts_with_precision
WHERE minor_units IS NOT NULL
GROUP BY currency, legacy_decimal_places
ORDER BY currency;

\echo
\echo '6. Balance records whose account cannot be found (their currency is unknown, so they cannot be converted)'
SELECT count(*) AS orphaned_balance_rows
FROM bankaccountbalance balance
LEFT JOIN mappedbankaccount account
       ON account.theaccountid = balance.accountid_ AND account.bank = balance.bankid_
WHERE account.id IS NULL;

\echo
\echo '7. Amounts already stored as decimals or whole units, outside the five minor-unit columns'
\echo '   productfee.amount is a decimal with 2 places whatever the currency;'
\echo '   mappedtransactiontype.mcustomerfee_amount is a whole number of main units'
SELECT 'productfee.amount' AS amount_column, currency, count(*) AS row_count,
       count(*) FILTER (WHERE amount <> trunc(amount)) AS rows_with_fractional_amount
FROM productfee GROUP BY currency
UNION ALL
SELECT 'mappedtransactiontype.mcustomerfee_amount', mcustomerfee_currency, count(*), 0
FROM mappedtransactiontype GROUP BY mcustomerfee_currency
ORDER BY 1, 2;

ROLLBACK;
