# mTLS topologies: peer vs caller

**Status: §11.2 and §11.3 implemented; §11.4 onward still proposed.** The peer-vs-forwarder rule of
§3 lives in `code.api.util.PeerTrust` and runs for every request. What remains is the
dev-behind-nginx harness (§11.4) and the per-environment rollout (§11.5), which is where any
deployment's behaviour actually changes — the shipped defaults reproduce what every deployment did
before. `docs/MTLS_DEV_MODE.md` is the operator-facing guide; this document is why it looks the way
it does.

## 1. What prompted this

The current production topology is: TPP application → mTLS → nginx, which verifies the client
certificate and forwards it to OBP-API as the `PSD2-CERT` header over a plain HTTP hop. The
proposal under discussion is to *also* run mutual TLS on the nginx → OBP-API hop, so OBP-API
terminates that connection itself and sees a certificate for nginx as well as the forwarded
certificate for the App.

Four deployments are in scope, all of them considered legitimate:

|                | OBP-API is the TLS edge | OBP-API behind nginx |
|----------------|-------------------------|----------------------|
| **development** | supported before this work (`--mtls`) | needs the harness of §11.4 |
| **production**  | possible since §11.3; not scheduled (§11.7) | supported; mTLS on the hop is §11.5 |

At the time of writing, the second column's production cell ran over a plain HTTP hop and the
run-mode gate blocked the first column's.

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

**Corollary: run mode is not a topology axis.** The `Props.mode == Development` gate encoded an
assumption about deployment that all four supported cases falsify. §5.3 records what replaced it.

**Corollary: do not add a topology prop.** An explicit `mtls.topology=edge|behind_proxy` would
reintroduce exactly what this design removes: two pieces of state that can disagree with each
other. The topology is *inferred* from whether the allowlist is empty.

## 4. What the original implementation did, and why it did not generalise

Kept as the rationale for §3; the code described here was replaced in §11.3.

`Http4sMtls.injectClientCertificate` stripped any inbound `PSD2-CERT` and replaced it with the
certificate from the TLS handshake. That is correct for the one deployment it was written for — OBP
as the mTLS edge in development, where an inbound `PSD2-CERT` can only be a spoof — and it was
deliberately confined there by a run-mode gate.

Its role was to **mimic nginx**: terminate the handshake, translate the verified client certificate
into the single representation OBP consumes downstream (a PEM `PSD2-CERT` header), and hand the
request to the identical code path production uses. There was no second authentication mechanism in
development and no second identity — the client certificate simply *was* the App.

That is also why enabling it in production as-is would have been wrong: it would faithfully do its
job and overwrite the App certificate forwarded by nginx with nginx's own handshake certificate.
Every request would arrive as the proxy. The trusted-forwarder rule of §3 generalises the behaviour
instead of special-casing it, and that failure is now pinned by a test
(`CallerCertificateTest`: "keep the App's certificate, not the proxy's").

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

**Decided (2026-07-23): (b), keyed on issuer CA + subject DN.**

Indexed prop pairs, because a DN contains commas and any comma-separated list of DNs is ambiguous
the first time someone writes a real one:

```properties
# Peers whose handshake certificate makes them a forwarder rather than a caller.
# Empty (the default) = OBP is the TLS edge: the handshake certificate IS the caller.
mtls.trusted_proxy.1.issuer=CN=TESOBE Internal CA,O=TESOBE GmbH,C=DE
mtls.trusted_proxy.1.subject=CN=nginx-prod-1,OU=Edge,O=TESOBE GmbH,C=DE

mtls.trusted_proxy.2.issuer=CN=TESOBE Internal CA,O=TESOBE GmbH,C=DE
mtls.trusted_proxy.2.subject=CN=nginx-prod-2,OU=Edge,O=TESOBE GmbH,C=DE
```

Settable from the environment like every OBP prop:
`OBP_MTLS_TRUSTED_PROXY_1_ISSUER`, `OBP_MTLS_TRUSTED_PROXY_1_SUBJECT`.

`subject=*` accepts any subject signed by that issuer. This is the configuration that makes proxy
rotation genuinely free — sign the new proxy with the same CA, change nothing in OBP — and it is
also the configuration where "what else does this CA sign" becomes the entire security argument.
Support it; do not default to it; log a warning at boot when it is in use.

Implementation notes that the format depends on:

- Compare `X500Principal.getName(X500Principal.CANONICAL)` on both sides. It normalises case and
  whitespace, so operators need not match those exactly — but it does **not** reorder RDNs, so
  components written in a different order than the certificate silently fail to match.
- Document the command that prints exactly what to paste:
  `openssl x509 -in nginx-prod-1.crt -noout -issuer -subject -nameopt RFC2253`.
- When a peer is rejected as not-a-forwarder, log its canonical issuer and subject. The failure
  mode is "all TPP traffic through this proxy is suddenly anonymous", and it should be diagnosable
  from one log line rather than a debugging session.

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

That cannot be a flag day. **Decided (2026-07-23):** an explicit
`mtls.trust_forwarded_header_without_tls` prop **defaulting to `true`** — today's behaviour — so the
insecure case is named rather than implicit, and can be switched off per environment as each gains
the mTLS hop. Defaulting it to `false` was rejected: it would require every deployment to set the
prop in the same release, and anything missed fails closed, i.e. TPP traffic stops.

The cost of that choice is shipping a security-relevant prop whose default is the permissive
setting. Mitigate it by logging a warning at boot whenever it resolves to `true`, so the state is
noisy rather than silent, and remove the default entirely once §11.5 has rolled through every
environment.

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
- **Cost of TLS termination in Ember** relative to per-request database work. Expected to be
  negligible, but worth a measurement before it is asked about.
- **What "the nginx → OBP connection is already secured" means concretely** (§11.7 decision 3). If
  that hop already carries TLS without client authentication, OBP terminates TLS but has no peer
  certificate — the fourth row of the §3 table, not the fifth — and
  `mtls.trust_forwarded_header_without_tls`, which keys off *no TLS at all*, would not be the prop
  governing it. Worth pinning down before §11.5, because it decides which row that deployment
  lands on.

Answered on 2026-07-23, kept for the record:

- ~~Is authenticating the hop required in its own right, or is network isolation sufficient?~~ The
  hop is already secured by other means; this work removes the dependence on that, and is not
  urgent remediation of an open exposure.
- ~~Does anything need to authorise on the proxy identity?~~ No — it need only prove "this is our
  proxy", so the two-Consumers question of §2 does not arise.

## 10. Reference

| File | Role |
|---|---|
| `obp-api/src/main/scala/bootstrap/http4s/Http4sMtls.scala` | Props, `SSLContext`/`TLSContext`, the `PSD2-CERT` injection middleware this document generalises |
| `obp-api/src/main/scala/code/api/util/APIUtil.scala` | `getPSD2-CERT` (`:286`), `tppCertificateForStandard` (`:3871`), the PSD2 gate (`:3888`) |
| `obp-api/src/main/scala/code/api/util/ConsentUtil.scala` | Consumer lookup by certificate and the consent/Consumer match — the three normalizations of §5.2 |
| `obp-api/src/main/scala/code/api/util/CertificateUtil.scala` | `normalizePemX509Certificate` (`:233`) |
| `obp-api/src/main/scala/code/api/util/CertificateVerifier.scala` | PKIX chain + CRL validation; currently reached only from the Berlin Group signature path |
| `docs/MTLS_DEV_MODE.md` | The mTLS support that exists today, including the proxy configuration rules |

## 11. Implementation plan

Five changes, sequenced. Each is independently shippable, and PRs 1 and 2 are behaviour-preserving
in every existing deployment — the rollout is entirely in PR 4's configuration.

### 11.1 Two decisions that shape all of it

**The middleware must be wired unconditionally.** `injectClientCertificate` runs only when
`Http4sMtls.enabled` (`Http4sServer.scala:29-40`). In today's production topology — nginx over a
plain HTTP hop — no certificate middleware runs at all and the header passes straight through to
`ConsentUtil`. So this work cannot be done by extending the existing wrapper in place: the
resolution middleware has to wrap `Http4sApp.httpApp` unconditionally, with the TLS branch only
supplying the peer certificate. That refactor is what makes §11.2 and §11.3 possible at all.

**The rule of §3 should be a pure function, not middleware logic.**

```scala
sealed trait Resolution
case class DirectCaller(cert: X509Certificate) extends Resolution
case class ForwardedCaller(cert: X509Certificate, via: X509Certificate) extends Resolution
case class NoCaller(reason: String) extends Resolution
case class Rejected(reason: String) extends Resolution

def resolve(peer: Option[X509Certificate],
            forwarded: Option[String],
            config: TrustConfig): Resolution
```

The decision table in §3 then becomes a table-driven unit test needing no server and no TLS, and the
middleware reduces to a shell that calls `resolve` and rewrites the header. Every later phase gets
cheaper for it.

### 11.2 PR 1 — normalize `PSD2-CERT` on ingress — **implemented**

Implements §5.2. `Psd2CertIngress.canonicalize` runs unconditionally from `Http4sApp.httpApp`;
`CertificateUtil.canonicalizePemX509Certificate` percent-decodes, parses and re-emits canonical PEM
through `CertificateUtil.toPem`, which `Http4sMtls.toPem` now delegates to so the dev injector and
a forwarded certificate produce byte-identical values.

Two deviations from the plan as written, both deliberate:

- **The downstream compensations were kept, not removed.** `ConsentUtil.scala:159-167` looks the
  Consumer up in the database, so only the request side of that comparison can be normalised; the
  stored certificate is whatever was pasted at registration. The `removeBreakLines` comparison in
  the consent check is now *alternative* to `comparePemX509Certificates` rather than replaced by it
  — neither subsumes the other for every stored form, and accepting either keeps the change
  strictly more permissive. Removing them safely needs the stored certificates normalised at write
  time plus a migration, which is its own change.
- **Percent-decoding is hand-rolled rather than `java.net.URLDecoder`.** URLDecoder maps `+` to a
  space, which is correct for form encoding and silently corrupts a base64 payload.

**This is the one phase with a genuine regression path.** A Consumer registered with a
non-canonical PEM currently matches through the raw-value lookup that runs first; normalizing the
header alone would make that comparison fail. The mitigation is to normalize **both sides** — the
incoming header and the stored `clientCertificate` — at comparison time, which is strictly more
permissive than today, so no existing match can break. Moving normalization to ingress and deleting
the fallback is *not* equivalent and must not be done.

Tests: one table covering URL-encoded (nginx), single-line PEM (HAProxy), canonical PEM (the dev
injector) and whitespace-mangled input all reducing to one value, plus a regression test for a
non-canonically stored Consumer.

Small; mostly deletion downstream.

### 11.3 PR 2 — peer-vs-forwarder resolution — **implemented**

Implements §3, §5.1, §5.3, §5.4 and §5.5 — the bulk of the design. Adds `resolve` and its config,
reduces `injectClientCertificate` to a caller of it, and introduces:

| Prop | Default | Meaning |
|---|---|---|
| `mtls.trusted_proxy_issuers` | empty | issuer CN + subject DN pairs treated as forwarders (§5.1) |
| `mtls.trust_forwarded_header_without_tls` | `true` | today's behaviour on a plain hop, now named (§5.4) |

An empty allowlist reproduces current dev-as-edge behaviour exactly; `true` on the legacy prop
reproduces current production behaviour exactly. **Net behaviour change in every existing
deployment: none** — which is what makes this safe to merge well ahead of any rollout.

Also in this PR: the run-mode gate at `Http4sMtls.scala:56` is replaced by the dev-keystore
fingerprint refusal of §5.3, and the resolution is recorded on the `CallContext` for metrics and
audit per §5.5.

Tests: the five-state table as pure unit tests, plus an extension of
`Http4sMtlsHandshakeTest.scala` with a proxy certificate in the allowlist and a forwarded header,
asserting the header survives. That harness already generates certificates on the fly and builds a
real Ember server, so simulating nginx costs one additional generated keypair and no nginx.

Two things found while building it, both now pinned by tests:

- **The empty string is a valid X.500 name.** `X500Principal("")` parses and canonicalises back to
  `""`, so a blank `mtls.trusted_proxy.N.subject` would have become a rule matching any certificate
  with an empty subject rather than a configuration error. `canonicalDn` rejects blanks explicitly.
- **An HTTP header value cannot contain newlines**, so a proxy can only ever forward a single-line
  PEM — canonical multi-line PEM is rejected by the client before it reaches the wire. This is
  what makes §5.2's ingress canonicalisation load-bearing rather than cosmetic, and it is the
  reason PR 1's regression analysis holds: the header side of every stored-certificate comparison
  was always single-line.

Deviation from the plan: the `Rejected` case of the sketched ADT was not implemented. Nothing
produces it — a peer that is not a trusted forwarder is simply the caller, and an unusable
certificate is the authorisation layer's to reject, not this layer's. Three cases, no dead branch.

### 11.4 PR 3 — dev-behind-nginx

Implements §6.1. No production code: a docker-compose with real nginx in front, a make target and
the CI job. Exercises the encoding disagreement, allowlist rotation, spoofing attempts arriving
through the proxy, and the missed-overwrite misconfiguration.

Cheap once PR 2 exists, and it is what gives PRs 4 and 5 a local reproduction. Worth resisting the
temptation to defer: this is the phase that pays for the others.

### 11.5 PR 4 — prod-behind-nginx rollout

Configuration rather than code, ordered per environment: enable the mTLS hop, set that
environment's forwarder allowlist, confirm from the §5.5 logs that requests resolve as
`ForwardedCaller`, and only then set `trust_forwarded_header_without_tls` to `false` there. Roll to
the remaining environments, then delete the legacy default in a small follow-up.

The observability from PR 2 is the gate: do not flip the prop in an environment until its logs show
every request resolving as forwarded.

### 11.6 PR 5 — prod-as-edge — **not scheduled** (§11.7 decision 3)

Kept here because the gap it closes is real and will matter if the deployment assumption ever
changes. Nothing below is planned work.


Enable revocation checking on the TLS context and/or route the handshake certificate through
`CertificateVerifier`, whose CRL machinery and `use_tpp_signature_revocation_list` toggle already
exist (`CertificateVerifier.scala:83-86`) and are simply never reached from this path. Needs a test
with an actually revoked certificate — the piece that turns §6.2 from inference into fact.

Sizing depends on the answers in §11.7, so this is deliberately left unscheduled.

### 11.7 Decisions taken (2026-07-23)

1. **How a forwarder is recognised** → issuer CA + subject DN, in the prop format now written out in
   §5.1. Leaf fingerprints were rejected for the config change they impose on every proxy rotation.
2. **`trust_forwarded_header_without_tls` default** → `true`, preserving current behaviour, with a
   boot warning when it resolves that way. See §5.4 for why a `false` default was rejected.
3. **prod-as-edge** → enumerated for completeness; nobody is asking for it, and the nginx → OBP
   connection is already secured by other means. PR 5 (§11.6) is therefore **not scheduled**, and
   the revocation gap in §6.2 stays a documented gap rather than blocking work. It must be
   reopened before any deployment makes OBP the public TLS edge.
4. **Normalising stored certificates at write time** → not scheduled. PR 1 normalises the request
   side; the compensating fallbacks on the stored side stay, with the comments in `ConsentUtil`
   explaining why they are not redundant. Revisit if certificate-mismatch incidents suggest the
   stored values are the problem.
