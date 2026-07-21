# Running OBP-API in mTLS Mode (Dev Feature)

OBP-API can terminate **mutual TLS (mTLS) in-process** for local development: the http4s (Ember)
server itself does the TLS handshake, requires a client certificate, and hands the verified
certificate to the application as the `PSD2-CERT` request header. No reverse proxy needed.

> **Dev only.** The feature is honoured **only when `run.mode=development`**. In any other run
> mode `mtls.enabled=true` is ignored with a boot warning. In production, terminate mTLS at a
> reverse proxy (nginx/HAProxy/Apache) that forwards the verified client certificate as the
> `PSD2-CERT` header — see [Production deployments](#production-deployments) below.

This is the http4s successor of the old `RunMTLSWebApp.scala` launcher, which was removed with
the Lift/Jetty teardown. Instead of a separate launcher, it is a props toggle on the normal
server (`bootstrap.http4s.Http4sServer`).

## How it works

```
curl --cert client.crt ── TLS handshake ──► Ember server (mtls.enabled=true)
                                             │  verifies client cert against mtls.truststore
                                             │  exposes it via ServerRequestKeys.SecureSession
                                             ▼
                                            Http4sMtls.injectClientCertificate middleware
                                             │  strips any client-supplied PSD2-CERT header (anti-spoofing)
                                             │  injects the verified cert as PSD2-CERT (PEM)
                                             ▼
                                            OBP application layer (unchanged)
                                             • consumer lookup by certificate
                                             • consent pinning (consumer_validation_method_for_consent)
                                             • GET /my/mtls/certificate/current
```

Everything downstream of the header is the pre-existing OBP machinery — the same code path a
production reverse proxy feeds. Implementation: `bootstrap/http4s/Http4sMtls.scala`, wired in
`bootstrap/http4s/Http4sServer.scala`.

## Quick start

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

> There is also a keystore pair checked into the repo
> (`obp-api/src/test/resources/cert/server.jks` + `server.trust.jks`, password `123456`),
> but its truststore contains no client certificate you own a private key for, and its server
> certificate has no `localhost` SAN — generating fresh certificates as above is the smoother
> path.

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

`run.mode` must be `development` (it is with the standard local run scripts).

### 3. Run

```sh
./flushall_build_and_run.sh --mtls        # or: ./flushall_fast_build_and_run.sh --mtls
./flushall_build_and_run.sh               # props-driven, if you configured them above
```

Boot log confirms the mode:

```
mTLS termination is ENABLED (dev-only): serving HTTPS on port 8080, client_auth=need, keystore=..., truststore=...
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

Note that any `PSD2-CERT` header you send yourself is discarded — the middleware always replaces
it with the certificate from the TLS handshake.

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
| `mtls.enabled` | `false` | Master switch. Only honoured when `run.mode=development`. The one genuinely required prop. |
| `mtls.keystore.path` | `obp-api/src/test/resources/cert/server.jks` | Store with the server's private key + certificate. `.p12`/`.pfx` are read as PKCS12, anything else as JKS. |
| `mtls.keystore.password` | `123456` | Password for keystore and key. |
| `mtls.truststore.path` | `obp-api/src/test/resources/cert/server.trust.jks` | Store with client certificates / CAs the server accepts. Same type detection as the keystore. |
| `mtls.truststore.password` | `123456` | Truststore password. |
| `mtls.client_auth` | `need` | `need` rejects certless handshakes; `want` makes the client certificate optional. |

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
| Boot warning `mtls.enabled=true is ignored` | `run.mode` is not `development`. |
| Boot fails with `points at '…', which does not exist` | The store file isn't there. If you relied on the defaults, the server was launched from somewhere other than the repo root — the message prints the resolved path and the working directory. Use `--mtls` (absolute paths) or set the props absolutely. |
| Logins that worked over plain HTTP now fail | `hostname` changed scheme without `local_identity_provider` being pinned — see the note under "Props reference". |
| `curl: (35)` / `alert certificate required` | No (or untrusted) client certificate in `need` mode — check the cert is in `server.trust.jks`. |
| `curl: (60) SSL certificate problem` | curl doesn't trust the server cert — pass `--cacert server.crt` (or `-k` for a quick look). |
| Client rejects the server cert with "no alternative certificate subject name matches" | The default `server.jks` leaf has **no SAN**. curl/OpenSSL and Java's `DefaultHostnameVerifier` fall back to CN (`localhost`) so both accept it, but stricter clients won't. Point `mtls.keystore.path` at `obp-api/src/test/resources/cert/localhost_san_dns_ip.pfx` (password `123456`), which carries `DNS:localhost, IP:127.0.0.1`. |
| Plain `http://` requests hang or error | The port serves HTTPS when mTLS is enabled — use `https://`. |
| Cert reaches OBP but consumer lookup fails | The Consumer's Client Certificate field doesn't contain the client cert's PEM (lookup is by PEM match, with a whitespace-normalized fallback). |
| DirectLogin returns `OBP-20073: The user email has not been validated` | A freshly created user must validate their email first. In local dev, either click the link from the validation email (see the `mail.*` props) or set the flag directly in the DB: `update authuser set validated=true where username='YOUR_USER';` |

## Production deployments

Do **not** use this feature in production (it is disabled outside development mode by design).
Terminate mTLS at a reverse proxy and forward the verified certificate as the `PSD2-CERT`
header. Two important rules for any proxy config:

1. The header value must be **plain PEM** — OBP does not URL-decode. nginx's
   `$ssl_client_escaped_cert` is URL-encoded and needs decoding (e.g. via njs) before
   forwarding; HAProxy can rebuild a single-line PEM directly:
   `http-request set-header PSD2-CERT "-----BEGIN CERTIFICATE-----%[ssl_c_der,base64]-----END CERTIFICATE-----"`.
2. The proxy must **overwrite** any client-supplied `PSD2-CERT` header, and the OBP port must
   not be reachable except through the proxy — otherwise the header can be spoofed.

## Implementation & tests

| File | Role |
|---|---|
| `obp-api/src/main/scala/bootstrap/http4s/Http4sMtls.scala` | Props, SSLContext/TLSContext construction, `PSD2-CERT` injection middleware. |
| `obp-api/src/main/scala/bootstrap/http4s/Http4sServer.scala` | `mtls.enabled` branch on the Ember builder. |
| `scripts/mtls_env.sh` | The `OBP_MTLS_*` environment overrides behind the `--mtls` flag of both `flushall_*build_and_run.sh` scripts. Sourceable on its own. |
| `scripts/java_env.sh` | Selects a JDK >= 17 for the run scripts. The build compiles with `-release 17`; on a default JDK 11 it otherwise fails with the misleading `'17' is not a valid choice for '-release'`. |
| `obp-api/src/test/scala/bootstrap/http4s/Http4sMtlsTest.scala` | Unit tests: PEM encoding, header injection/stripping, SSLContext from the checked-in keystores. |
| `obp-api/src/test/scala/bootstrap/http4s/Http4sMtlsHandshakeTest.scala` | End-to-end: real Ember server + real mTLS handshake; proves the verified client cert surfaces as `PSD2-CERT` and certless handshakes are rejected. |

Run the tests:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -pl obp-api -am test \
  -DwildcardSuites=bootstrap.http4s.Http4sMtlsTest,bootstrap.http4s.Http4sMtlsHandshakeTest \
  -DfailIfNoTests=false
```
