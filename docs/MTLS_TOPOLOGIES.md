# mTLS topologies: peer vs caller

**Status: proposal.** Nothing here is implemented. `docs/MTLS_DEV_MODE.md` documents the mTLS
support that exists today; this document proposes generalising it so that OBP-API can terminate
mutual TLS in production as well as development, behind a proxy or as the edge, without a separate
code path per deployment.

## 1. What prompted this

The current production topology is: TPP application → mTLS → nginx, which verifies the client
certificate and forwards it to OBP-API as the `PSD2-CERT` header over a plain HTTP hop. The
proposal under discussion is to *also* run mutual TLS on the nginx → OBP-API hop, so OBP-API
terminates that connection itself and sees a certificate for nginx as well as the forwarded
certificate for the App.

Four deployments are in scope, all of them considered legitimate:

|                | OBP-API is the TLS edge | OBP-API behind nginx |
|----------------|-------------------------|----------------------|
| **development** | supported today (`--mtls`) | **not currently possible** |
| **production**  | blocked by the run-mode gate | supported today (plain HTTP hop) |

## 2. Is the proxy a second Consumer?

No. The question arises naturally — OBP ends up holding two certificates per request — but the two
certificates answer different questions and only one of them is an identity that OBP authorises on.

| | App / TPP certificate | nginx certificate |
|---|---|---|
| Answers | *who is calling* | *is this our proxy* |
| Varies per | request | never — one certificate for the platform |
| Drives | Consumer lookup, consent binding, the PSD2 regulated-entity gate and AISP/PISP roles, rate limiting, metrics | nothing |

Consumer is the key that rate limiting, metering, entitlements and consent binding are all keyed
on. Modelling the proxy as a Consumer would make every request in the system appear to originate
from one Consumer, so this is not a neutral modelling choice — it would break those features.

The proxy certificate should instead be a **trust decision** that is made once, at the edge of the
request, and then discarded. It never becomes an identity.

## 3. The unifying rule

Dev-as-edge and prod-behind-nginx look like different modes only if the question is "which
deployment am I". They are the same problem under a different question:

> Is my TLS peer the caller, or a forwarder I trust to tell me who the caller is?

This is the `X-Forwarded-For` trust model applied to certificates. It yields one rule:

- **Peer is in the trusted-forwarder set** → the peer is *not* the caller. Take the caller identity
  from the forwarded `PSD2-CERT` header, which is trustworthy precisely *because* the forwarder
  authenticated itself in the handshake.
- **Peer is not a trusted forwarder** → the peer *is* the caller. Its handshake certificate becomes
  the caller identity, and any inbound `PSD2-CERT` header is a spoofing attempt and is stripped.

### Per-request decision table

| TLS peer | `PSD2-CERT` inbound | Caller identity | Notes |
|---|---|---|---|
| trusted forwarder | present | header certificate | production behind nginx |
| trusted forwarder | absent | none | legitimate — most OBP endpoints need no certificate |
| not a forwarder | present | handshake certificate, header stripped | dev today; a spoof attempt in production |
| not a forwarder | absent | handshake certificate | edge deployment with `client_auth=want` |
| no TLS at all | present | **policy decision — see §5.4** | where production lives today |

### Why this satisfies the uniformity objective

All four deployments in §1 are spanned by this one rule, distinguished by exactly one axis:

- forwarder allowlist **empty** → OBP is the edge (dev-as-edge, prod-as-edge)
- forwarder allowlist **non-empty** → peer is the proxy (dev-behind-nginx, prod-behind-nginx)

Development and production then differ only in keystore paths and in how strict the fail-closed
defaults are. There is no topology mode, no run-mode branch, and no second middleware to keep in
step with the first.

**Corollary: run mode is not a topology axis.** The existing `Props.mode == Development` gate
(`Http4sMtls.scala:56`) encodes an assumption about deployment that all four supported cases
falsify. §5.3 proposes what should replace it.

**Corollary: do not add a topology prop.** An explicit `mtls.topology=edge|behind_proxy` would
reintroduce exactly what this design removes: two pieces of state that can disagree with each
other. The topology is *inferred* from whether the allowlist is empty.

## 4. What the current implementation does, and why it does not generalise

`Http4sMtls.injectClientCertificate` (`Http4sMtls.scala:159`) strips any inbound `PSD2-CERT` and
replaces it with the certificate from the TLS handshake. That is correct for the one deployment it
was written for — OBP as the mTLS edge in development, where an inbound `PSD2-CERT` can only be a
spoof — and it is deliberately confined there (`Http4sMtls.scala:56-64`).

Its role is to **mimic nginx**: terminate the handshake, translate the verified client certificate
into the single representation OBP consumes downstream (a PEM `PSD2-CERT` header,
`Http4sMtls.scala:148-151`), and hand the request to the identical code path production uses. There
is no second authentication mechanism in development, and no second identity — the client
certificate simply *is* the App.

That is also why enabling it in production as-is would be wrong: it would faithfully do its job and
overwrite the App certificate forwarded by nginx with nginx's own handshake certificate. Every
request would arrive as the proxy. The trusted-forwarder rule in §3 is what generalises the
behaviour rather than special-casing it.

## 5. Design choices

### 5.1 How a forwarder is recognised

| Option | Trade-off |
|---|---|
| (a) Second truststore — peers verified against `mtls.proxy.truststore` are forwarders, against `mtls.truststore` are callers | Semantically cleanest; doubles the ops surface (two stores to distribute and rotate) |
| (b) One truststore plus an allowlist of subject DNs or SHA-256 fingerprints | One store; explicit, auditable, greppable during an incident |
| (c) Certificate extension or OU convention | Fragile, implicit, hard to audit |

**Recommendation: (b).**

A sub-choice within (b) has real operational consequences:

- **leaf fingerprint** — tightest, but every proxy certificate rotation requires an OBP config change;
- **issuer CA + subject DN** — rotation under the internal CA is free, at the cost that anything
  that CA signs can act as a forwarder.

**Recommendation: issuer CA + subject DN** for an internal CA, stated explicitly in the operator
documentation so the implication is not a surprise.

### 5.2 Normalize the certificate on ingress

This is the largest uniformity win available and it is independent of everything else in this
document.

Today the same logical certificate arrives in at least three encodings — canonical 64-column PEM
from the dev injector, URL-encoded from nginx's `$ssl_client_escaped_cert`, single-line PEM from
HAProxy — and downstream code copes with that in three different ad-hoc ways:

- `ConsentUtil.scala:154-163` — Consumer lookup on the raw header value;
- `ConsentUtil.scala:164-167` — a retry via `CertificateUtil.normalizePemX509Certificate`
  (`CertificateUtil.scala:233`);
- `ConsentUtil.scala:207` + `removeBreakLines` (`ConsentUtil.scala:182`) — a third comparison in
  the consent/Consumer match.

Parsing the certificate once in the middleware and re-emitting one canonical form makes every one
of those an exact comparison, and removes the entire "worked in dev, failed in production" class of
encoding bugs. The helper already exists; it is simply applied late and inconsistently.

### 5.3 What replaces the run-mode gate

Dropping the `Development` gate is required by §3. But the gate is guarding something real — it is
just not the feature. The concrete risk is the **checked-in development keystore reaching
production** (`obp-api/src/test/resources/cert/server.jks`, password `123456`, now also the silent
fallback for the store props).

**Recommendation:** replace the gate with a fingerprint check that refuses to boot in Production run
mode when the configured keystore is the repository's dev pair. Narrower than the current gate, and
it catches the failure that would actually cause harm.

### 5.4 Migration for the existing plain-HTTP hop

Existing production deployments run nginx → OBP over plain HTTP and trust the `PSD2-CERT` header
unconditionally. The only thing protecting that today is the network rule in
`docs/MTLS_DEV_MODE.md` ("the OBP port must not be reachable except through the proxy"): anything
that can route to the port can currently forge any TPP identity. Removing that exposure is the
central security argument for this work — it converts *trust the network* into *trust an
authenticated peer*.

That cannot be a flag day. **Recommendation:** an explicit
`mtls.trust_forwarded_header_without_tls` prop, defaulting to today's behaviour, so the insecure
case is named and opt-in rather than implicit, and can be switched off per environment as each
gains the mTLS hop.

### 5.5 Observability

Record, per request, which branch of §3 resolved (direct caller vs forwarded) and the peer subject,
and carry it on the `CallContext` so metrics can record it. Without this, diagnosing "why is this
TPP suddenly anonymous" is guesswork and there is no audit trail for the trust decision.

## 6. Deployment-specific considerations

### 6.1 dev-behind-nginx — the cell that is missing, and why it is worth building

It costs almost nothing once the forwarder rule exists (it is the same code with a non-empty
allowlist), and it is what makes the production cells testable. Today the production path has no
local reproduction at all. With it, the following become reproducible in a docker-compose:

- the header encoding disagreement — nginx sends URL-encoded, the dev injector sends canonical PEM,
  and today nobody finds out they differ until production;
- forwarder allowlist behaviour, including a proxy certificate rotating out of the allowlist;
- header-spoofing attempts arriving *through* the proxy;
- the "nginx failed to overwrite `PSD2-CERT`" misconfiguration.

### 6.2 prod-as-edge — needs genuinely new security work

This is not "dev-as-edge with different certificates". When nginx is the edge, `ssl_verify_client`
performs chain validation and can perform CRL/OCSP checks against the TPP's CA. If OBP is the
public edge, that responsibility moves to OBP — and the `PSD2-CERT` path does not do it today.

Chain and revocation validation (`CertificateVerifier`, PKIX with CRL checking toggled by
`use_tpp_signature_revocation_list`, `CertificateVerifier.scala:83-86`) is invoked from exactly one
place: `BerlinGroupSigning.scala:193`, on the Berlin Group **signature** path. The `PSD2-CERT` path
does none of it — the PSD2 gate goes straight to the regulated-entity lookup on issuer CN + serial
(`APIUtil.scala:3871`, `APIUtil.scala:3888`), and the Consumer lookup is a PEM string match. JSSE
performs chain validation during the handshake against the configured truststore, but revocation
checking is disabled there by default unless explicitly enabled.

**Consequence: in a prod-as-edge deployment a revoked but unexpired TPP certificate would currently
be accepted.** In a regulated deployment that is the difference between "OBP terminates mTLS" being
a configuration change and being a compliance question. The remedy is not large — enable revocation
on the TLS context and/or run the handshake certificate through `CertificateVerifier` — but it
should be scoped in from the start. dev-as-edge does not care, which is why it has not surfaced.

Remaining prod-as-edge concerns are ordinary operations rather than security gaps: a publicly
trusted server certificate and its rotation; cipher and protocol policy that used to be nginx's;
load-balancer health probes that cannot present a client certificate (`want` vs `need`, or a
separate probe port); and the denial-of-service surface of terminating public TLS in the JVM.

## 7. Rollout

Each phase is independently shippable and backwards compatible.

1. **Normalize `PSD2-CERT` on ingress** (§5.2). No behaviour change; removes the ad-hoc downstream
   normalizations.
2. **Add peer-vs-forwarder resolution** (§3), with `mtls.trust_forwarded_header_without_tls`
   defaulting to current behaviour (§5.4), and the dev-keystore boot check replacing the run-mode
   gate (§5.3). Still no behaviour change in any existing deployment.
3. **dev-behind-nginx** (§6.1) — same code, non-empty allowlist; lands the compose setup and the
   test matrix in CI.
4. **prod-behind-nginx** — enable the mTLS hop in one environment, set its forwarder allowlist, turn
   `trust_forwarded_header_without_tls` off there; then roll to the rest and remove the legacy
   default.
5. **prod-as-edge** — separately, once the revocation question in §6.2 has an answer.

Building phase 3 before phase 4 means production-behind-nginx ships with a local reproduction
already in CI.

## 8. Testing

The four deployments × the five peer/header states in §3 form the matrix. The existing end-to-end
harness (`obp-api/src/test/scala/bootstrap/http4s/Http4sMtlsHandshakeTest.scala`) already starts a
real Ember server and performs a real handshake, so it extends naturally: add a client that presents
a certificate *in* the forwarder allowlist together with a forwarded `PSD2-CERT`, which simulates
nginx without needing nginx in the unit tier. Cases that must be covered explicitly:

- forwarded header preserved when the peer is a trusted forwarder;
- forwarded header **stripped** when the peer is not;
- fail closed when the peer presents no certificate under `client_auth=need`;
- every accepted header encoding normalizing to one canonical form (§5.2);
- a proxy certificate outside the allowlist being rejected rather than silently treated as a caller.

## 9. Open questions

- **Proxy certificate rotation without an OBP restart** — argues for CA-based allowlisting (§5.1) or
  a reloadable truststore.
- **Which certificate nginx forwards under eIDAS.** A TPP holds a QWAC (used for TLS, so it is what
  the handshake and `PSD2-CERT` carry) and a QSEAL (used for signing, so it is what
  `TPP-Signature-Certificate` carries). They have different serial numbers and often different
  issuing CAs, and the regulated-entity lookup matches on issuer CN + serial — the same split
  addressed for UK Open Banking in commit `bc08fc098`.
- **Is authenticating the hop required in its own right** (audit, regulator, zero-trust posture), or
  is network isolation currently considered sufficient? This materially changes the priority of
  phases 4 and 5.
- **Does anything need to authorise on the proxy identity**, or only to prove "this is our proxy"?
  If the latter — as §2 assumes — the two-Consumers question does not arise.
- **Cost of TLS termination in Ember** relative to per-request database work. Expected to be
  negligible, but worth a measurement before it is asked about.

## 10. Reference

| File | Role |
|---|---|
| `obp-api/src/main/scala/bootstrap/http4s/Http4sMtls.scala` | Props, `SSLContext`/`TLSContext`, the `PSD2-CERT` injection middleware this document generalises |
| `obp-api/src/main/scala/code/api/util/APIUtil.scala` | `getPSD2-CERT` (`:286`), `tppCertificateForStandard` (`:3871`), the PSD2 gate (`:3888`) |
| `obp-api/src/main/scala/code/api/util/ConsentUtil.scala` | Consumer lookup by certificate and the consent/Consumer match — the three normalizations of §5.2 |
| `obp-api/src/main/scala/code/api/util/CertificateUtil.scala` | `normalizePemX509Certificate` (`:233`) |
| `obp-api/src/main/scala/code/api/util/CertificateVerifier.scala` | PKIX chain + CRL validation; currently reached only from the Berlin Group signature path |
| `docs/MTLS_DEV_MODE.md` | The mTLS support that exists today, including the proxy configuration rules |
