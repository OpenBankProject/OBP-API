# Data retention — policies, holds, runs, archive, disposal register

Written 2026-11-03. Phases 1 and 2 are built; phase 3 is described at the end and not built.
Working rules as elsewhere: the user commits, the assistant never does.

## Why

Records must be kept for as long as a records authority, a regulator or the deployment's own rules say,
and no longer (data minimisation). Those periods differ per deployment and change over time, so they are
configuration held per space, never code. Every disposal must be accounted for, nothing may be disposed of
while it is on legal hold, and the destructive steps must be impossible to switch on by accident.

## The model

A **retention policy** belongs to one space: a bank, or `SYS` for the system space (the same meaning of
`SYS` as in `DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md`). It governs one resource type of that space:

| resource_type | live tables | date_basis | notes |
|---|---|---|---|
| `dynamic_entity_record` | `DynamicData` (+ `DynamicDataAccess` rows of the record, the SQL projection row) | `created`, `updated`, `field:<name>` (ISO 8601 date in the record) | needs `entity_name`; condition on the record's own fields |
| `file` | `File`, `FileContent`, `FileAttachment`, `FileAccess` | `created` | |
| `chat_message` | `ChatMessage`, `Reaction` | `created`, `updated` | the room's last-message preview is cleared if it came from a disposed message |
| `chat_room` | `ChatRoom`, `Participant`, and all the room's messages and reactions | `created`, `updated`, `last_activity` | a room holding a message on hold stays whole |
| `customer` | `MappedCustomer`, attributes, addresses, dependants, tax residences, `MappedUserCustomerLink`, `CustomerAccountLink` | `created`, `updated` | bank only, never `SYS` |
| `consent` | `MappedConsent`, `consent_item`, consent auth context | `created`, `updated`, `expires` (default: the token's expiry) | a bank's policy governs Consents whose items name only that bank; `SYS` governs Consents naming no bank. Identified by `consent_reference_id`; the consent id, secret and JWT are never copied |

Fields of a policy: `retain_days` (how long the resource stays live after its trigger date, at least
`retention.minimum_retain_days`), `action`,
`archive_days`, an optional `condition` (`field` = `value`, compared as text; for dynamic entity records on
the record's own fields, for the other types on the fields of the resource's JSON copy), `cascade`,
`requires_approval`, `enabled`, `name`, `description`.

**Actions.**

* **archive** — the resource leaves the live tables and is written to the **retention archive**
  (`RetentionArchive`, a JSON copy; a file's bytes, Base64 in a text column, in `RetentionArchiveContent`). The archive is
  read-only, reachable only through the retention archive endpoints with `CanGetRetentionArchive`, and
  is **not an undo**: there is no restore. After `archive_days` the entry is deleted. `archive_days` is
  required for archive and not allowed for the other actions.
* **delete** — final.
* **anonymise** — customers only. The personal fields (legal name becomes `ANONYMISED`, email, phone,
  date of birth, face image, title, name suffix, relationship status, education, employment, credit) are
  cleared and attributes, addresses, dependants, tax residences and links to Users are deleted; the
  customer id, bank, customer number and links to accounts stay, so references stay valid. An anonymised
  customer is not matched again.

**Cascade.** A disposed record's file attachments always go. With `cascade`, each attached file goes
too, with the record's action (archive stays archive; delete and anonymise delete the file), unless it is
still attached to another record or is on hold. A dynamic entity record's files are attached with the
entity name as `record_type`; a customer's with `customer`. (No endpoint writes `FileAttachment` yet, so
this is the convention the file endpoints must follow.)

**Legal holds** (`RetentionHold`) name one resource (`resource_type` + `resource_id`, optionally
`entity_name`), or every record of one dynamic entity (`entity_name` alone). Every run skips what is held
and counts it; an archived resource on hold is not deleted from the archive. Releasing a hold keeps the
row with who released it and when.

**Runs** (`RetentionRun`) have a mode, `preview` or `execute`, and a status: `pending` (waiting for a
second user), `running`, `done`, `refused` (props off, rejected by the approver, or policy deleted) or
`failed` (some resources failed and were left in place; see `message`). One execution disposes of at
most `retention.run.max_items` resources, oldest row first, then deletes from the archive what that
policy archived whose archive period has ended. The counts include cascaded files and a room's messages.

**Four eyes.** With `requires_approval`, an execution is created as pending; a different user holding
`CanApproveRetentionRun` approves (it executes then) or rejects it. The requester can never decide their
own run. A run the scheduler requested may be approved by anyone holding the Role.

**Disposal register** (`RetentionRunItem`): one row per resource archived, anonymised or deleted, and per
archive entry deleted (`delete_from_archive`): ids, owner, action, time, parent, archive id — never
content, so it outlives the resources.

No webhook is sent for a disposal: a retention run is not a write through the endpoints.

## Safety switches (props, documented in `sample.props.template`)

| prop | default | effect |
|---|---|---|
| `retention.archive.enabled` | false | an execution of an `archive` policy is refused (400, recorded as a `refused` run) |
| `retention.delete.enabled` | false | an execution of a `delete` or `anonymise` policy is refused; archived entries past their period are kept |
| `retention.minimum_retain_days` | 1825 | a policy with a shorter `retain_days` is refused; an existing one runs as though its `retain_days` were this (five years by default) |
| `retention.scheduler.enabled` | false | start `RetentionScheduler` |
| `retention.scheduler.interval_in_seconds` | 3593 | seconds between scheduler runs |
| `retention.run.max_items` | 1000 | most resources one run disposes of |

A preview always works. The scheduler, modelled on `MetricsArchiveScheduler` (a `JobScheduler` lock row),
executes the enabled policies that do not require approval and have something due (skipping, with a log
line, those the props switch off), creates one pending run for each enabled policy that requires approval
and has something due, and, with `retention.delete.enabled`, deletes every archive entry whose period has
ended — whichever policy archived it, so deleting a policy never strands its archive.

## Endpoints (v7.0.0; BANK_ID is a bank id or `SYS`)

| verb | path | Role(s), held at BANK_ID |
|---|---|---|
| POST | `/management/banks/BANK_ID/retention-policies` | CanManageRetentionPolicy |
| GET | `/management/banks/BANK_ID/retention-policies` | CanGetRetentionPolicies or CanManageRetentionPolicy |
| GET | `/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID` | CanGetRetentionPolicies or CanManageRetentionPolicy |
| PUT | `/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID` | CanManageRetentionPolicy |
| DELETE | `/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID` | CanManageRetentionPolicy |
| GET | `/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID/preview` | CanGetRetentionPolicies, CanRunRetentionPolicy or CanManageRetentionPolicy |
| POST | `/management/banks/BANK_ID/retention-policies/RETENTION_POLICY_ID/runs` | CanRunRetentionPolicy |
| GET | `/management/banks/BANK_ID/retention-runs` | CanGetRetentionRuns |
| GET | `/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID` | CanGetRetentionRuns |
| PUT | `/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID/approval` | CanApproveRetentionRun |
| GET | `/management/banks/BANK_ID/retention-runs/RETENTION_RUN_ID/items` | CanGetRetentionRuns |
| POST | `/management/banks/BANK_ID/retention-holds` | CanManageRetentionHold |
| GET | `/management/banks/BANK_ID/retention-holds` | CanManageRetentionHold or CanGetRetentionPolicies |
| DELETE | `/management/banks/BANK_ID/retention-holds/RETENTION_HOLD_ID` | CanManageRetentionHold (release) |
| GET | `/management/banks/BANK_ID/retention-archive` | CanGetRetentionArchive |
| GET | `/management/banks/BANK_ID/retention-archive/RETENTION_ARCHIVE_ID` | CanGetRetentionArchive |

All seven Roles are single names with `requiresBankId = true`, granted at a bank or at `SYS`, per
`ANY_BANK_ROLE_REMOVAL_PLAN.md`: a Role targets one space. Everything is looked up within the space of the
URL. Code: `code.retention` (tables, engine, one handler per resource type), `code.scheduler.RetentionScheduler`,
`code.api.v7_0_0.Http4s700Retention` and `JSONFactory700Retention`; errors OBP-30609 to OBP-30623.
Tests: `code.api.v7_0_0.RetentionTest`.

## Tables (all new; no column added to an existing table)

`RetentionPolicy`, `RetentionArchive`, `RetentionArchiveContent`, `RetentionHold`, `RetentionRun`,
`RetentionRunItem`. Created by Schemifier from `ToSchemify.models`; no migration is needed. Their user id
columns are in `UserReference` as audit columns (`UseAuthenticatedUserId`); the owner columns are copies
of the disposed resource's owner, never the caller.

## Known limits

* An HTTP-started run executes inside the request's transaction, so a large run holds one transaction;
  keep `retention.run.max_items` moderate. Scheduler runs use auto-commit statements, like the metrics
  archive.
* `FileContent` keeps bytes in a `MappedBinary`, which Lift maps to a one-byte `BINARY` on H2, so on H2 (the
  test databases) a file of more than one byte cannot be stored at all; the retention archive avoids the
  problem by storing Base64 text. Postgres is unaffected.
* A preview counts at most 100000 resources (`count_is_capped`).
* The `field:<name>` date basis and conditions scan the entity's records in pages; there is no index on
  JSON fields.
* Consents are matched in memory against their items; a Consent naming several banks is governed by no
  bank's policy (and not by `SYS`).

## Phase 3 — Users (not built)

Users are the identity other records point at, so they are **anonymised only**, never archived or deleted:
`resource_type: user`, action `anonymise` only, at `SYS` (a User is not owned by a bank). It would clear the
personal fields of `ResourceUser` and `AuthUser` (name, email, username replaced by a generated
placeholder, phone), revoke tokens and Consents, delete the User's attributes, auth contexts and
user-customer links, lock the login, and keep the `user_id` so every reference stays valid. Trigger dates:
`created`, `last_login`, or `field:` equivalents on user attributes. Holds and the register work unchanged.
It needs its own decisions first: what counts as inactivity, how it interacts with consent users
(`ON_BEHALF_OF_USER_ID_PLAN.md`), and whether metrics rows keep the user id.
