# OBP Roadmap

_Last reviewed: 10 July 2026_

This is the living roadmap for OBP-API and closely related OBP projects. It is organised by **theme** and **horizon** (Active / Next / Later), not by feature-and-date, because OBP's priorities are steered — deliberately — by three forces:

1. **Client demand** — banks and organisations using OBP in production steer what gets built next.
2. **Regulation** — external regulatory deadlines (PSD2/PSD3/PSR, FIDA, CDR, UK Open Banking) are the only items on this roadmap with dates, because those dates are set by regulators, not by us.
3. **Technology shifts** — when something like MCP/agentic AI arrives, we re-prioritise fast. We consider this a feature of the project, not a failure of planning.

**Horizons:**

- **Active** — in active development.
- **Next** — committed direction, expected to start when current work lands or a client engagement triggers it.
- **Later** — on our radar; will be pulled forward by client demand or regulation.

For what has already shipped, see the various API catalogs, glossary and README — those are the authoritative record of API functionality. Architectural milestones that change no API signature (and so don't appear in the catalogs) are logged in [completed_developments.md](completed_developments.md).

If you have a requirement, want to comment, or want to pull an item forward, [get in touch](https://www.openbankproject.com/contact).

---

## Regulatory & standards commitments (the only dated track)

| Item                   | External date                                              | Our status                                                                               |
| ---------------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| PSD3 / PSR             | Final texts agreed Apr 2026; application expected ~2027–28 | Tracking; gap analysis against final RTS when published                                  |
| FIDA (EU open finance) | Still in trilogue; operational ~2029 (est.)                | Watching; OBP dynamic entities/endpoints already support FIDA-style non-payment datasets |

---

## Theme 1: Standards & regulatory coverage

_The standard is an adapter, not the architecture — one core serves many standards._

- **Active:** UK Open Banking v4.x refresh (AIS first, then PIS).
- **Next:** PSD3/PSR alignment work as final technical standards emerge.
- **Later:** FIDA data-sharing schemes; further Berlin Group version updates as published.

## Theme 2: AI & agentic access

- **Active:** OBP MCP server — agents (Claude, others) driving OBP APIs: account information, payments with consent, service discovery. Consent scoping, audit trail and human-in-the-loop controls for agent-initiated actions.
- **Next:** Hardening the agent consent/security model; agent-friendly API metadata (schemas, glossary, resource docs as agent context).
- **Later:** Agent-to-agent flows; agentic commerce standards as they stabilise.

## Theme 3: Core platform & architecture

_The Lift Web → http4s re-platform is **complete**: the entire API surface, every version, now runs on http4s and Cats IO with no Lift Web and no Jetty — done in place, with zero API signature changes. See [completed_developments.md](completed_developments.md)._

- **Active:** **OBP-Dispatch** — lightweight router directing requests between OBP-API and OBP-Trading instances, using Resource Docs for discovery.
- **Next:** Replace Lift Mapper — the last remaining Lift dependency — with a modern persistence layer; OBP Scala Library adoption.
- **Later:** Scala 2.13 / 3.x upgrade (unblocked by the Lift Web removal; gated on the persistence work).

## Theme 4: Bank-side integration

- **Active:** Connector/adapter maintenance for production client banks (REST, Kafka, MQ, stored-procedure connectors).
- **Next:** Integration patterns for coexistence with incumbent API gateways (e.g. MuleSoft, Apigee) — documentation and reference architecture.
- **Later:** Additional core-banking adapter templates driven by client engagements.

## Theme 5: Developer experience

- **Active:** API Explorer, developer portal, sandbox data tooling.
- **Next:** Second-generation SDKs; improved onboarding for TPP developers.
- **Later:** —

## Theme 6: Security, operations & certification

- **Active:** Rate limiting enhancements; ongoing security hardening.
- **Next:** FAPI conformance review; certification targets per standard (e.g. OBIE functional conformance) as client demand requires.
- **Later:** —

---

## How this roadmap is maintained

- Reviewed and re-stamped (see date at top) at least **quarterly**, and whenever a major re-prioritisation happens.
- Items move between horizons freely; that is the system working, not the system failing.
- When an organisation asks us for "the roadmap", we send a dated snapshot of this file — we do not maintain separate per-organisation roadmap documents.
- Shipped work leaves this file. API-visible work needs no separate record — it appears in the Resource Docs, API Explorer and glossary automatically. Architectural milestones that change no API signature (re-platforms, dependency removals, performance work) move to [completed_developments.md](completed_developments.md), which is a curated log of exactly that category — not a complete history.

## History

This document supersedes the previous roadmap.md content (last updated 2019) and the Roadmap page on the GitHub wiki (2018), which now points here.
