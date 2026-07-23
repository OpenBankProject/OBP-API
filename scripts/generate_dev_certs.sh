#!/bin/bash
################################################################################
# OBP-API development certificate set
#
# Regenerates the role-named certificates under obp-api/src/test/resources/cert.
# Everything it writes is DEVELOPMENT-ONLY: the private keys are committed to a
# public repository and the password is in this file. bootstrap.http4s.Http4sMtls
# refuses to boot a Production server on them.
#
# Four roles, so that a deployment can be described rather than guessed at:
#
#   dev-ca         signs the other three; the only entry in the truststore
#   obp-server     what OBP presents (CN=localhost + SAN, serverAuth)
#   tpp-client     the calling App                       (clientAuth)
#   proxy-client   a reverse proxy that forwards someone else's certificate
#   expired-tpp    a deliberately expired client, for negative tests
#
# The older server.jks / server.trust.jks / localhost_san_dns_ip.pfx are left
# alone; they are still what the props default to.
#
#   ./scripts/generate_dev_certs.sh [output-dir]
################################################################################

set -euo pipefail

OUT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/obp-api/src/test/resources/cert}"
PASSWORD=123456
DAYS=3650

mkdir -p "$OUT"
cd "$OUT"

# Distinguished names. Role is in the DN, not only the filename, because with
# mtls.trusted_proxy.N.subject a DN is now a configuration value that an operator
# reads and types (see docs/MTLS_TOPOLOGIES.md §5.1).
CA_DN="/C=DE/O=TESOBE GmbH/CN=OBP Dev CA"
SERVER_DN="/C=DE/O=TESOBE GmbH/OU=OBP Server/CN=localhost"
TPP_DN="/C=DE/O=Example TPP Ltd/OU=TPP/CN=test-tpp"
PROXY_DN="/C=DE/O=TESOBE GmbH/OU=Edge Proxy/CN=nginx-dev-1"
EXPIRED_DN="/C=DE/O=Example TPP Ltd/OU=TPP/CN=expired-tpp"

echo ">>> Writing development certificates to $OUT"

# --- the CA ------------------------------------------------------------------
openssl req -x509 -newkey rsa:2048 -nodes -days "$DAYS" \
    -keyout dev-ca.key -out dev-ca.crt -subj "$CA_DN" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null

# Signs a CSR with the CA. $3 is the extension block: the whole point of the
# separation is that a client certificate gets clientAuth and a server one does
# not, so a mistake shows up as a handshake failure rather than working anyway.
sign() {
    local name="$1" dn="$2" ext="$3"
    openssl req -newkey rsa:2048 -nodes -keyout "$name.key" -out "$name.csr" -subj "$dn" 2>/dev/null
    printf '%s\n' "$ext" > "$name.ext"
    openssl x509 -req -in "$name.csr" -CA dev-ca.crt -CAkey dev-ca.key -CAcreateserial \
        -days "$DAYS" -extfile "$name.ext" -out "$name.crt" 2>/dev/null
    rm -f "$name.csr" "$name.ext"
}

sign obp-server "$SERVER_DN" \
"basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost,IP:127.0.0.1"

sign tpp-client "$TPP_DN" \
"basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=clientAuth"

sign proxy-client "$PROXY_DN" \
"basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=clientAuth"

# --- the expired fixture -----------------------------------------------------
# `openssl x509` gained -not_before/-not_after only in 3.2; this has to work on
# 3.0, so the one certificate needing explicit dates goes through `openssl ca`.
CADIR="$(mktemp -d)"
trap 'rm -rf "$CADIR"' EXIT
mkdir -p "$CADIR/newcerts"
: > "$CADIR/index.txt"
echo 1000 > "$CADIR/serial"
cat > "$CADIR/openssl.cnf" <<CONF
[ ca ]
default_ca = dev
[ dev ]
dir = $CADIR
database = \$dir/index.txt
serial = \$dir/serial
new_certs_dir = \$dir/newcerts
certificate = $OUT/dev-ca.crt
private_key = $OUT/dev-ca.key
default_md = sha256
policy = anything
email_in_dn = no
rand_serial = no
unique_subject = no
[ anything ]
countryName = optional
stateOrProvinceName = optional
organizationName = optional
organizationalUnitName = optional
commonName = supplied
emailAddress = optional
[ client_ext ]
basicConstraints = CA:FALSE
keyUsage = critical,digitalSignature
extendedKeyUsage = clientAuth
CONF

openssl req -newkey rsa:2048 -nodes -keyout expired-tpp.key -out "$CADIR/expired.csr" \
    -subj "$EXPIRED_DN" 2>/dev/null
openssl ca -config "$CADIR/openssl.cnf" -batch -notext \
    -startdate 20200101000000Z -enddate 20210101000000Z \
    -extensions client_ext -in "$CADIR/expired.csr" -out expired-tpp.crt 2>/dev/null

# --- bundle ------------------------------------------------------------------
# PKCS12 throughout: JKS is proprietary and keytool warns about it on every use.
bundle() {
    local name="$1"
    openssl pkcs12 -export -inkey "$name.key" -in "$name.crt" -certfile dev-ca.crt \
        -name "$name" -out "$name.p12" -passout "pass:$PASSWORD"
}
bundle obp-server
bundle tpp-client
bundle proxy-client
bundle expired-tpp

# The truststore holds the CA and nothing else. Not a grab-bag of pinned leaves
# and public web CAs: a client-auth truststore containing a public CA would let
# anything that CA ever signed authenticate as a caller.
rm -f dev-truststore.p12
keytool -importcert -noprompt -alias dev-ca -file dev-ca.crt \
    -keystore dev-truststore.p12 -storetype PKCS12 -storepass "$PASSWORD" 2>/dev/null

# The CA key is deliberately NOT kept: everything is reproducible from this
# script, and a signing key is the one thing worth not committing even in a set
# that is public by design.
rm -f dev-ca.key dev-ca.srl

# The .crt/.key PEM pairs are kept alongside the .p12 bundles on purpose: Java wants PKCS12, while
# curl and nginx want PEM, and making the reader convert first is friction in both directions. The
# proxy pair in particular is what an nginx container in front of OBP will be handed.

echo
echo ">>> Done. Password for every store: $PASSWORD"
for f in dev-ca.crt dev-truststore.p12 obp-server.p12 tpp-client.p12 tpp-client.crt \
         tpp-client.key proxy-client.p12 expired-tpp.p12; do
    [ -f "$f" ] && echo "      $f"
done
