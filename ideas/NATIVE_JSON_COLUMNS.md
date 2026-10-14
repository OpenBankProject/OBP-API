# Native JSON columns

**Status:** Draft / proposal
**Scope:** OBP-API — storing the JSON we already write in columns the database can read.

---

## The idea

Several of our largest columns hold JSON: `metric.responsebody`, `mappedconsent.mjsonwebtokenpayload`,
`consentrequest.payload`. We declare them as plain text, so the database treats them as opaque blobs.
It can hand the text back; it cannot look inside.

PostgreSQL has a native JSON type (`jsonb`) that can. Same data, same writes, same API responses —
the database simply gains the ability to filter, index and aggregate on what is inside.

We have already made this argument once, for Dynamic Entities
(see [`dynamic_entity_indexing.md`](dynamic_entity_indexing.md)), and the vendor-detection
machinery it calls for already ships as `IndexingCapabilities`. This proposal extends the same
capability to the tables where we are paying to store JSON we cannot read.

The governing principle is inherited unchanged: **DB-native JSON is an optional accelerator behind
capability detection, never a requirement.** The portable path must keep working everywhere.

## The example: `metric.responsebody`

Every API call writes a row to `metric`. For selected endpoints we also store the full JSON
response body. We capture it, we store it, we pay for it — and then we cannot ask a single
question about it without exporting the data and scanning it outside the system.

`/management/metrics` offers around twenty filters. Every one of them is a dedicated column that
somebody added by hand, in its own migration, in its own release.

## What it unlocks, by journey

**A support engineer investigating a customer complaint**
Today: "the call succeeded, status 200" — and to see what the customer was actually served,
raise a request for a log export and wait.
After: search the responses directly, find the exact payload, answer the customer in the same session.

**A compliance officer answering an auditor**
Today: reconstruct from logs by hand, and hope the reconstruction is defensible.
After: one reproducible query showing precisely what was returned, to whom, when.

**A fraud analyst tracing an account**
Today: not answerable in the database at all.
After: find every response that touched the account, across every endpoint, in one query.

**A product manager sizing a new feature**
Today: ask engineering, wait for a schema change and a release to get the number.
After: run the query.

**An engineer adding a filter to the metrics API**
Today: new column, migration, backfill, release.
After: a query. No schema change.

The pattern across all five is the same: **the cost of a new question drops from a release to a query.**
That is the difference between analytics we plan a sprint around and analytics someone runs in an
afternoon.

## What it does not change

No API contract changes. No data is lost or rewritten in meaning. Nothing changes for customers
calling the API. Deployments that do not run PostgreSQL keep the behaviour they have today.

## Next step

Agreement to pilot this on `metric.responsebody` — the highest-value column, write-once and
read-rarely, so the conversion is contained and the pattern is proven end to end before we take it
near the consent tables.
