# OBP-API mutual TLS

OBP-API can terminate **mutual TLS (mTLS) in-process**: the http4s (Ember) server itself does the
TLS handshake, requires a client certificate, and identifies the caller from it — no reverse proxy
needed. This is the usual way to develop against mTLS locally, and it is also a supported production
topology. This document is the practical guide and the concrete trust picture; the design rationale
for *who the certificate identifies* lives in [`MTLS_TOPOLOGIES.md`](MTLS_TOPOLOGIES.md).

## Overview: the flow, grounded in what's checked in

### The cast

Two independent apps, one shared trust root.

- **OBP-API** — the bank's API. Terminates TLS, so it's the **server**. Owns the CA.
- **OBP-Hola** — a TPP (third-party provider) that calls OBP-API. Presents a client certificate, so
  it's the **client**. (Separate repository; its cert files are noted below for the full picture.)
- **OBP Dev CA** — one development CA, minted by OBP-API's `scripts/generate_dev_certs.sh`. Its
  private key (`dev-ca.key`) is committed in OBP-API precisely so a counterpart app can sign against
  it.

Everything hangs off that single CA. OBP-API owns the truststore, so it owns the trust root, and the
counterpart signs against it — mirroring the real world, where the TPP holds its own key and the
bank's CA signs the certificate.

### What each side holds

```
OBP-API  (obp-api/src/test/resources/cert/)
  dev-ca.crt / dev-ca.key   the CA — signs everything, trusts nothing above it
  obp-server.p12            server identity: CN=localhost, SAN localhost+127.0.0.1, EKU serverAuth
  dev-truststore.p12        the CA alone — "these are the clients I accept"
  tpp-client.p12            a local test client (CN=test-tpp, O=Example TPP Ltd, EKU clientAuth)
  proxy-client.p12          a reverse-proxy identity, for the behind-a-proxy topology
  expired-tpp.p12           expired on purpose, for negative tests

OBP-Hola  (src/main/resources/cert/)
  hola-client.p12           client identity: CN=obp-hola, O=Hola Ltd, EKU clientAuth
  hola-truststore.p12       the CA alone — "this is the server I trust"
```

Note the symmetry: **both truststores contain only the CA**, nothing pinned. That's deliberate —
either side can regenerate its leaf without breaking the other, because trust flows through the CA,
not through a pinned copy of the peer's certificate.

### How the counterpart's certificate comes to exist

The cross-repo step. OBP-Hola's `scripts/generate_dev_certs.sh`:

1. Finds an OBP-API checkout (via `OBP_API_HOME` or a sibling path) — it needs `dev-ca.key`.
2. Generates **its own** keypair. The private key never leaves the Hola repo.
3. Has OBP-API's CA sign a CSR → `hola-client.crt` (`CN=obp-hola`).
4. Imports OBP-API's `dev-ca.crt` into `hola-truststore.p12`.

So the CA key is *read* to sign, but the client's key is generated where the client lives — the
"TPP holds its own key" property. OBP-API's own `tpp-client` identity is generated the same way,
in-repo, by `scripts/generate_dev_certs.sh` here.

### The handshake, request by request

```
Client (Hola)                                    OBP-API (mtls.enabled, client_auth=need)
 │──── ClientHello ───────────────────────────────────────▶
 ◀──── obp-server cert (CN=localhost) ─────────────────────│
 │  client verifies it against its truststore:             │
 │  chain to OBP Dev CA ✓, SAN matches "localhost" ✓        │
 │──── hola-client cert (CN=obp-hola) ─────────────────────▶│  server verifies against
 │                                                          │  dev-truststore: chain to OBP Dev CA ✓,
 │                                                          │  not expired ✓, client_auth=need ✓
 │◀════════════ mutual TLS established ═════════════════════▶│
```

Both directions authenticate. A certless handshake is rejected in `need` mode (curl 56); an expired
client certificate fails validity (also rejected). Both are covered by `DevCertificateSetTest`.

### After the handshake: who is the caller

Terminating TLS does not by itself say *who called* — that is decided per request by
`code/api/util/PeerTrust.scala`, run for every request whether or not TLS terminates here:

- **No `mtls.trusted_proxy.*` configured** (the default): OBP-API is the **edge**, so the handshake
  peer *is* the caller. Its certificate becomes the `PSD2-CERT` the rest of OBP consumes; any
  inbound `PSD2-CERT` header is stripped as a spoof.
- **A trusted proxy is configured**: the handshake peer is the proxy, and the *forwarded*
  `PSD2-CERT` header names the caller instead.

Both are one rule with a different allowlist — see [`MTLS_TOPOLOGIES.md`](MTLS_TOPOLOGIES.md).

```
TLS handshake ─► Ember server (mtls.enabled=true)
                 │  verifies client cert against mtls.truststore
                 │  exposes it via ServerRequestKeys.SecureSession
                 ▼
                Psd2CertIngress + CallerCertificate middleware
                 │  canonicalises any forwarded PSD2-CERT header
                 │  peer is not a trusted proxy, so it IS the caller:
                 │  strips that header (anti-spoofing) and injects
                 │  the verified handshake cert as PSD2-CERT (PEM)
                 ▼
                OBP application layer (unchanged)
                 • consumer lookup by certificate
                 • consent pinning (consumer_validation_method_for_consent)
                 • the PSD2 gate (passesPsd2Aisp / passesPsd2Pisp)
                 • GET /my/mtls/certificate/current
```

Everything downstream of the header is the pre-existing OBP machinery — the same code path a
production reverse proxy feeds. Implementation: `bootstrap/http4s/Http4sMtls.scala` (TLS context and
stores, wired in `bootstrap/http4s/Http4sServer.scala`) plus `code/api/util/PeerTrust.scala` and
`code/api/util/http4s/CallerCertificate.scala`.

## Which certificate identifies the TPP (per standard)

When `requirePsd2Certificates=ONLINE`, the PSD2 gate identifies the TPP from a certificate — but
**which** certificate depends on the API standard, because the standards disagree on where the TPP's
certificate travels:

| Standard | URL prefix | Header read | Which certificate |
|---|---|---|---|
| Berlin Group (NextGenPSD2) | `/berlin-group/` | `TPP-Signature-Certificate` | the TPP's QSEAL signing certificate |
| UK Open Banking (OBIE) | `/open-banking/` | `PSD2-CERT` | the mTLS transport certificate (QWAC) |
| OBP-native | `/obp/` | `PSD2-CERT` | the mTLS transport certificate (QWAC) |

`TPP-Signature-Certificate` is a **Berlin Group trademark header** and is read on Berlin Group URLs
only. UK Open Banking and OBP's own endpoints identify the TPP by the mTLS transport certificate
(`PSD2-CERT`) — the one this document is about. So a plain mTLS client (like OBP-Hola) reaches UK and
OBP endpoints without ever sending a Berlin Group header. The rule lives in
`APIUtil.tppCertificateForStandard`; whichever certificate it resolves feeds the same
regulated-entity lookup, which matches on issuer CN + serial number.

## Quick start

### 0. The 30-second path — no certificates to generate

`obp-api/src/test/resources/cert` carries a development set, one file per role, all signed by one
CA and all with the password `123456`:

| File | Subject | Role |
|---|---|---|
| `dev-ca.crt` + `dev-ca.key` | `CN=OBP Dev CA, O=TESOBE GmbH, C=DE` | signs the rest; the only entry in the truststore. The key is committed so a counterpart app (OBP-Hola) can sign its own client certificate against it — see the overview. |
| `dev-truststore.p12` | — | what the server accepts: the CA, nothing else |
| `obp-server.p12` | `CN=localhost, OU=OBP Server, O=TESOBE GmbH` | what OBP presents (`serverAuth`, SAN `DNS:localhost, IP:127.0.0.1`) |
| `tpp-client.p12` (+ `.crt`/`.key`) | `CN=test-tpp, OU=TPP, O=Example TPP Ltd` | the calling App (`clientAuth`) |
| `proxy-client.p12` | `CN=nginx-dev-1, OU=Edge Proxy, O=TESOBE GmbH` | a reverse proxy forwarding someone else's certificate |
| `expired-tpp.p12` | `CN=expired-tpp, OU=TPP, O=Example TPP Ltd` | expired on purpose, for negative tests |

Regenerate with `./scripts/generate_dev_certs.sh`. `DevCertificateSetTest` asserts the properties
above still hold, so a regenerated set that loses a SAN or an EKU fails the build.

> **The CA private key is committed on purpose.** OBP-API owns the truststore, so it owns the trust
> root, and a counterpart application has to be able to obtain a certificate this server will accept
> — which is also the real arrangement, where the TPP holds its own key and the bank's CA signs it.
> OBP-Hola's `scripts/generate_dev_certs.sh` signs against this key. It is safe only because it can
> never matter: it is named `OBP Dev CA` wherever it appears, and `Http4sMtls` refuses to boot a
> Production server on these stores.

```sh
# Start the server on the role-named set
CERT=$PWD/obp-api/src/test/resources/cert
OBP_MTLS_KEYSTORE_PATH=$CERT/obp-server.p12 \
OBP_MTLS_TRUSTSTORE_PATH=$CERT/dev-truststore.p12 \
  ./flushall_fast_build_and_run.sh --mtls

# 200 — and the server certificate verifies properly, no -k needed
curl --cacert $CERT/dev-ca.crt --cert $CERT/tpp-client.crt --key $CERT/tpp-client.key \
  https://localhost:8080/obp/v5.1.0/root

# 56 — a handshake with no client certificate is rejected under client_auth=need
curl --cacert $CERT/dev-ca.crt https://localhost:8080/obp/v5.1.0/root
```

The props still default to the older `server.jks` / `server.trust.jks` pair, which also works —
`server.trust.jks` trusts `CN=TESOBE CA` (alias `mykey`), and `localhost_san_dns_ip.pfx` is a client
identity signed by it. Its drawbacks are why the set above exists: the server leaf has no SAN (so
curl needs `-k`), the client identity is called `CN=localhost` rather than named for a TPP, and the
truststore additionally holds five expired certificates and two public web CAs.

Generate your own instead when you need a specific subject that a Consumer or regulated-entity
lookup matches on.

### 1. Generate certificates

You need: a server keypair (JKS), a truststore containing the client certificate (JKS), and a
client certificate + private key in PEM form for curl.

```sh
mkdir -p ~/obp-mtls && cd ~/obp-mtls

# Server keypair (CN + SAN localhost so curl can verify it without -k)
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 365 \
  -dname "CN=localhost" -ext "SAN=DNS:localhost,IP:127.0.0.1" \
  -keystore server.jks -storepass 123456 -keypass 123456

# Export the server certificate — curl's --cacert
keytool -exportcert -rfc -alias server -keystore server.jks -storepass 123456 -file server.crt

# Client ("TPP") keypair + self-signed certificate in PEM
openssl req -newkey rsa:2048 -nodes -keyout client.key -out client.csr -subj "/CN=test-tpp"
openssl x509 -req -in client.csr -signkey client.key -days 365 -out client.crt

# Truststore: the client certificates (or CAs) the server accepts
keytool -importcert -noprompt -alias test-tpp -file client.crt \
  -keystore server.trust.jks -storepass 123456
```

To accept a whole CA instead of individual client certs, import the CA certificate into
`server.trust.jks` and sign client certs with that CA.

> The pair checked into the repo (`obp-api/src/test/resources/cert/server.jks` +
> `server.trust.jks`, password `123456`) already works for a full handshake — see step 0. Its one
> limitation is that the server certificate has no `localhost` SAN, so clients that do not fall
> back to CN need `-k`, or `mtls.keystore.path` pointed at `localhost_san_dns_ip.pfx`, which
> carries `DNS:localhost, IP:127.0.0.1`.

### 2. Configure props

**The short way — no props edit at all:**

```sh
./flushall_fast_build_and_run.sh --mtls
```

The flag sources `scripts/mtls_env.sh`, which exports the `OBP_MTLS_*` environment overrides
(every OBP prop is settable as `OBP_<NAME>` with dots replaced by underscores —
`APIUtil.getPropsValue` reads the environment ahead of the props file) pointing at the dev
keystore pair checked into the repo. It also flips `hostname` to `https://` **and** pins
`local_identity_provider` to the pre-toggle hostname — see the note under "Props reference"
for why that pinning matters. Anything you pre-export wins, so
`OBP_MTLS_CLIENT_AUTH=want ./flushall_fast_build_and_run.sh --mtls` works.

To make the mode permanent instead, put it in your props file
(e.g. `obp-api/src/main/resources/props/default.props`):

```properties
mtls.enabled=true
mtls.keystore.path=/home/YOU/obp-mtls/server.jks
mtls.keystore.password=123456
mtls.truststore.path=/home/YOU/obp-mtls/server.trust.jks
mtls.truststore.password=123456
# need = reject handshakes without a client certificate (default)
# want = client certificate optional (mixed mode; requests without one simply carry no PSD2-CERT header)
mtls.client_auth=need

# so generated links match the TLS listener
hostname=https://localhost:8080

# pin consents to the consumer's certificate (this is the default)
consumer_validation_method_for_consent=CONSUMER_CERTIFICATE
```

Only `mtls.enabled` is actually required. The four store props default to the checked-in dev
pair (`obp-api/src/test/resources/cert/server.jks` / `server.trust.jks`, password `123456`),
resolved relative to the working directory — so they work when the server is launched from the
repo root, which is what the run scripts do. Each fallback is logged at WARN naming the resolved
absolute file, and a missing store fails at startup with the resolved path and working directory
in the message rather than a bare `FileNotFoundException`.

Any `run.mode` works. A Production server refuses to boot on the development certificates checked
into this repository — they are recognised by digest wherever they are copied to, since the private
key is public and the password is in the source.

### 3. Run

```sh
./flushall_build_and_run.sh --mtls        # or: ./flushall_fast_build_and_run.sh --mtls
./flushall_build_and_run.sh               # props-driven, if you configured them above
```

Boot log confirms the mode:

```
mTLS termination is ENABLED: serving HTTPS on port 8080, client_auth=need, keystore=..., truststore=...
No mtls.trusted_proxy.N.issuer configured: OBP treats its TLS peer as the caller (it is the edge).
```

`dev.port` (default 8080) now speaks **HTTPS only** — plain `http://localhost:8080` requests
will fail.

### 4. Smoke test

```sh
curl --cacert server.crt --cert client.crt --key client.key \
  https://localhost:8080/obp/v5.1.0/root
```

| Flag | Meaning |
|---|---|
| `--cacert server.crt` | trust the dev server certificate (instead of the system CA bundle) |
| `--cert client.crt` | the client certificate presented in the handshake |
| `--key client.key` | proves ownership of `client.crt` |

A handshake **without** a client certificate must fail in `need` mode:

```sh
curl --cacert server.crt https://localhost:8080/obp/v5.1.0/root
# curl: (56) ... alert certificate required (or similar handshake error)
```

To see exactly what certificate OBP received, use the diagnostic endpoint (requires a logged-in
user, see next step):

```sh
curl --cacert server.crt --cert client.crt --key client.key \
  https://localhost:8080/obp/v5.1.0/my/mtls/certificate/current \
  -H "Authorization: DirectLogin token=$TOKEN"
# → subject CN=test-tpp, issuer, validity dates...
```

Note that any `PSD2-CERT` header you send yourself is discarded — with no trusted proxies
configured, OBP is the edge, so your TLS peer is the caller and a forwarded certificate can only be
a spoofing attempt. Configure `mtls.trusted_proxy.*` and the header is honoured instead.

### 5. Pin a Consumer to the certificate

mTLS identifies the **application** (Consumer); users still authenticate normally (DirectLogin,
OAuth, ...). To make consumer-by-certificate lookup and consent pinning work, the Consumer
record's *Client Certificate* field must contain the PEM of `client.crt`:

* register via API Explorer's consumer registration page (`<api-explorer-url>/consumers/register`)
  and paste the contents of `client.crt` into the **Client Certificate** field, or
* update an existing consumer via the API (`PUT .../management/consumers/CONSUMER_ID`
  consumer-certificate endpoints), or
* for full PSD2-style onboarding, `POST /obp/v5.1.0/dynamic-registration/consumers`.

Then get a user token over the same mTLS connection:

```sh
TOKEN=$(curl -s --cacert server.crt --cert client.crt --key client.key \
  -X POST https://localhost:8080/my/logins/direct \
  -H "Authorization: DirectLogin username=\"YOUR_USER\", password=\"YOUR_PASSWORD\", consumer_key=\"YOUR_CONSUMER_KEY\"" \
  | jq -r .token)
```

With `consumer_validation_method_for_consent=CONSUMER_CERTIFICATE`, every consent-authenticated
call now requires the handshake certificate to match the certificate stored on the consent's
Consumer — presenting a different client certificate is rejected.

## Props reference

| Prop | Default | Meaning |
|---|---|---|
| `mtls.enabled` | `false` | Master switch, honoured in every run mode. The one genuinely required prop. |
| `mtls.keystore.path` | `obp-api/src/test/resources/cert/server.jks` | Store with the server's private key + certificate. `.p12`/`.pfx` are read as PKCS12, anything else as JKS. |
| `mtls.keystore.password` | `123456` | Password for keystore and key. |
| `mtls.truststore.path` | `obp-api/src/test/resources/cert/server.trust.jks` | Store with client certificates / CAs the server accepts. Same type detection as the keystore. |
| `mtls.truststore.password` | `123456` | Truststore password. |
| `mtls.client_auth` | `need` | `need` rejects certless handshakes; `want` makes the client certificate optional. |
| `mtls.trusted_proxy.N.issuer` | — | Issuer DN of a peer allowed to forward someone else's certificate. Indexed from 1; scanning stops at the first missing index. Empty (the default) means OBP is the edge. |
| `mtls.trusted_proxy.N.subject` | any | Subject DN of that peer. `*` or unset accepts any subject the issuer signed — free proxy rotation, but only as tight as that CA. |
| `mtls.trust_forwarded_header_without_tls` | `true` | Whether a `PSD2-CERT` header is trusted when the sender presented no client certificate. `true` is the pre-existing behaviour of a plain proxy hop; set it to `false` once the proxy authenticates itself. **Ignored (treated as `false`) when `mtls.enabled=true` and no trusted proxies are configured** — OBP is then the TLS edge, so a header from a certless peer (possible under `client_auth=want`) can only be a spoofing attempt and is stripped. |

DNs are compared in canonical form, so case and spacing do not matter — but **RDN order does**.
Print the exact values to paste with
`openssl x509 -in proxy.crt -noout -issuer -subject -nameopt RFC2253`.

Each of these is also settable from the environment as `OBP_MTLS_ENABLED`,
`OBP_MTLS_KEYSTORE_PATH`, … which is what `--mtls` uses.

> **`hostname` and `local_identity_provider` move together.** Turning mTLS on means generated
> links should say `https://`, so `hostname` changes. But `hostname` is also the default for
> `local_identity_provider` (`code/api/constant/constant.scala`), which is the `provider` column
> every `AuthUser` / `ResourceUser` row is keyed on. Changing `hostname` alone gives your existing
> local users a different provider string, orphaning them — logins start failing after a toggle.
> `scripts/mtls_env.sh` handles this by pinning `local_identity_provider` to the pre-toggle
> hostname. If you configure mTLS through props instead, set `local_identity_provider` explicitly
> to whatever `hostname` was before you added the `https://`.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Boot fails with `which is one of the development stores checked into the OBP-API repository` | `run.mode=production` with the repo's dev keystore or truststore. Supply your own certificates. |
| Every request logs `none: PSD2-CERT was sent over a hop with no client certificate` | `mtls.trust_forwarded_header_without_tls=false` but the proxy is not presenting a client certificate. |
| A TPP behind the proxy is suddenly anonymous | The proxy's certificate is not matching `mtls.trusted_proxy.N.*`, so it is being treated as the caller and its forwarded header discarded. The rejection is logged with the peer's canonical issuer and subject — paste those into the props. |
| `OBP-20306: PEM Encoded Certificate cannot be found at request header` on a Berlin Group endpoint | The Berlin Group standard reads `TPP-Signature-Certificate`, not the mTLS `PSD2-CERT` — the client must sign the request and send that header. UK and OBP-native endpoints do *not* need it (see "Which certificate identifies the TPP"). |
| `OBP-34102: Regulated Entity cannot be found by provided certificate` | `requirePsd2Certificates=ONLINE` and the presenting certificate's issuer CN + serial do not match any registered regulated entity. Register the entity (with `CERTIFICATE_CA_NAME` and `CERTIFICATE_SERIAL_NUMBER` attributes), or set `requirePsd2Certificates=NONE` for local work. |
| Boot fails with `points at '…', which does not exist` | The store file isn't there. If you relied on the defaults, the server was launched from somewhere other than the repo root — the message prints the resolved path and the working directory. Use `--mtls` (absolute paths) or set the props absolutely. |
| Logins that worked over plain HTTP now fail | `hostname` changed scheme without `local_identity_provider` being pinned — see the note under "Props reference". |
| `curl: (35)` / `alert certificate required` | No (or untrusted) client certificate in `need` mode — check the cert is in the truststore. |
| `curl: (60) SSL certificate problem` | curl doesn't trust the server cert — pass `--cacert` the CA (or `-k` for a quick look). |
| Client rejects the server cert with "no alternative certificate subject name matches" | The default `server.jks` leaf has **no SAN**. curl/OpenSSL and Java's `DefaultHostnameVerifier` fall back to CN (`localhost`) so both accept it, but stricter clients won't. Point `mtls.keystore.path` at `obp-api/src/test/resources/cert/localhost_san_dns_ip.pfx` (password `123456`), or use `obp-server.p12` from the role-named set — both carry `DNS:localhost, IP:127.0.0.1`. |
| Plain `http://` requests hang or error | The port serves HTTPS when mTLS is enabled — use `https://`. |
| Cert reaches OBP but consumer lookup fails | The Consumer's Client Certificate field doesn't contain the client cert's PEM (lookup is by PEM match, with a whitespace-normalized fallback). |
| DirectLogin returns `OBP-20073: The user email has not been validated` | A freshly created user must validate their email first. In local dev, either click the link from the validation email (see the `mail.*` props) or set the flag directly in the DB: `update authuser set validated=true where username='YOUR_USER';` |

## Production deployments

Two supported shapes, both keyed off `mtls.trusted_proxy.*`:

1. **Reverse proxy terminates mTLS.** The proxy verifies the client certificate and forwards it as
   the `PSD2-CERT` header; OBP does not terminate TLS itself. This is the long-standing setup.
2. **OBP terminates mTLS itself** (`mtls.enabled=true` with your own certificates). Supported in any
   run mode — the only hard guard is that OBP refuses to boot on the development stores checked into
   this repository (recognised by digest).

Use your own certificates in production — never the committed dev set. For a reverse proxy, two
rules matter:

1. The header value may be PEM (multi-line or single-line), nginx's percent-encoded
   `$ssl_client_escaped_cert`, or bare base64 — all of them are decoded and rewritten to one
   canonical PEM on ingress (`Psd2CertIngress`), so no njs decoding step is needed. HAProxy can
   also rebuild a single-line PEM directly:
   `http-request set-header PSD2-CERT "-----BEGIN CERTIFICATE-----%[ssl_c_der,base64]-----END CERTIFICATE-----"`.
   A value that is not a parseable certificate is passed through untouched and rejected later by
   the authorisation code, not by this layer.
2. The proxy must **overwrite** any client-supplied `PSD2-CERT` header, and — unless the proxy
   authenticates itself to OBP over mTLS and is listed in `mtls.trusted_proxy.*` — the OBP port
   must not be reachable except through the proxy, otherwise the header can be spoofed.

Rule 2 is why [`MTLS_TOPOLOGIES.md`](MTLS_TOPOLOGIES.md) exists: running mutual TLS on the proxy → OBP
hop lets OBP trust the forwarded header *because an authenticated peer sent it*, rather than because
of network isolation. That document is the design reference; parts of it are proposal, parts
implemented — it says which.

## Implementation & tests

| File | Role |
|---|---|
| `obp-api/src/main/scala/bootstrap/http4s/Http4sMtls.scala` | Props, SSLContext/TLSContext construction, dev-store digest guard. |
| `obp-api/src/main/scala/bootstrap/http4s/Http4sServer.scala` | `mtls.enabled` branch on the Ember builder. |
| `obp-api/src/main/scala/code/api/util/PeerTrust.scala` | The peer-vs-forwarder rule: who the caller is. |
| `obp-api/src/main/scala/code/api/util/http4s/CallerCertificate.scala` | Applies that rule per request; sets `PSD2-CERT`. |
| `obp-api/src/main/scala/code/api/util/http4s/Psd2CertIngress.scala` | Canonicalises any inbound `PSD2-CERT` encoding to one PEM form. |
| `code/api/util/APIUtil.scala` (`tppCertificateForStandard`) | Per-standard certificate source: Berlin Group → `TPP-Signature-Certificate`, UK / OBP → `PSD2-CERT`. |
| `scripts/generate_dev_certs.sh` | Regenerates the role-named development certificate set (CA, server, TPP, proxy, expired fixture). |
| `scripts/mtls_env.sh` | The `OBP_MTLS_*` environment overrides behind the `--mtls` flag of both `flushall_*build_and_run.sh` scripts. Sourceable on its own. |
| `scripts/java_env.sh` | Selects the project's JDK (pom.xml `<java.version>`) for the build, run and test scripts, and aborts if it is absent. Without it a stale `java`/`mvn` silently builds on a JDK that CI never used, and one too old for `-release` fails with the misleading `'NN' is not a valid choice for '-release'`. |
| `obp-api/src/test/scala/bootstrap/http4s/Http4sMtlsTest.scala` | Unit tests: PEM encoding, SSLContext from the checked-in keystores, the dev-store digest guard. |
| `obp-api/src/test/scala/bootstrap/http4s/DevCertificateSetTest.scala` | Guards the role-named set over a real handshake: names, EKUs, SAN, CA-only truststore, expired certificate rejected. |
| `obp-api/src/test/scala/bootstrap/http4s/Http4sMtlsHandshakeTest.scala` | End-to-end: real Ember server + real mTLS handshake; proves the verified client cert surfaces as `PSD2-CERT` and certless handshakes are rejected. |
| [`docs/MTLS_TOPOLOGIES.md`](MTLS_TOPOLOGIES.md) | Design reference for the peer-vs-forwarder model and the rollout phases. |

Run the tests:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -pl obp-api -am test \
  -DwildcardSuites=bootstrap.http4s.Http4sMtlsTest,bootstrap.http4s.Http4sMtlsHandshakeTest,bootstrap.http4s.DevCertificateSetTest \
  -DfailIfNoTests=false
```
