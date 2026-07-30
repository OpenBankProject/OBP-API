# dev-behind-nginx — OBP-API behind an mTLS reverse proxy

Reproduces the production topology where a reverse proxy terminates the TPP's mutual TLS and
forwards the verified client certificate to OBP-API, which itself terminates a second mTLS hop from
the proxy. See [`docs/MTLS.md`](../../docs/MTLS.md) for the guide and
[`docs/MTLS_TOPOLOGIES.md`](../../docs/MTLS_TOPOLOGIES.md) for the design.

```
TPP client ──mTLS──▶ nginx ──mTLS──▶ OBP-API
  tpp-client cert     verifies client, forwards it as PSD2-CERT,
                      presents proxy-client cert to OBP-API
                                        recognises nginx as a trusted forwarder
                                        (mtls.trusted_proxy.*), reads the header as the caller
```

## The two ways to run it

**Automated (the behavioural check):** `NginxForwarderTest` — real nginx (this same image) in front
of the real caller-resolution middleware. It asserts the four things this topology gets wrong in
practice: the URL-encoded-header decode, the forwarder allowlist, spoof overwrite, and the
missed-overwrite misconfiguration. Requires Docker; skipped without it.

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -pl obp-api -am test \
  -DwildcardSuites=bootstrap.http4s.NginxForwarderTest -DfailIfNoTests=false
```

**Manual (eyeball a full OBP-API through the proxy):** `docker-compose.yml` runs just nginx in front
of a separately-started OBP-API — see the header of that file for the exact commands. In short:
start OBP-API with `mtls.enabled=true` and nginx as a trusted forwarder, `docker compose up`, then
call OBP-API through nginx on port 8443 with the TPP client certificate.

## Files

| File | Role |
|---|---|
| `nginx.conf` | The reference proxy config: client-mTLS termination, `PSD2-CERT` = `$ssl_client_escaped_cert` (overwrite), mTLS to the upstream presenting the forwarder identity. |
| `docker-compose.yml` | Runs nginx from `nginx.conf` against a host-run OBP-API, for manual inspection. |

Both use the committed development certificate set (`obp-api/src/test/resources/cert/`): `obp-server`
as the server identity, `dev-ca` as the client-verification CA, `proxy-client` (`CN=nginx-dev-1`) as
nginx's forwarder identity, `tpp-client` as the calling TPP. Regenerate with
`scripts/generate_dev_certs.sh`.
