#!/usr/bin/env sh
CERT_DIR="/certificates"
KEYSTORE="/var/lib/jetty/keystore.jks"
TRUSTSTORE="/var/lib/jetty/truststore.jks"
PASSWORD="changeit"
for CERT in "$CERT_DIR"/*.crt; do
ALIAS=$(basename "$CERT" | sed 's/\.[^.]*$//')
keytool -importcert -noprompt -file "$CERT" -alias "$ALIAS" -keystore "$KEYSTORE" -storepass "$PASSWORD"
keytool -importcert -noprompt -file "$CERT" -alias "$ALIAS" -keystore "$TRUSTSTORE" -storepass "$PASSWORD"
done
chown jetty:jetty $TRUSTSTORE 
chown jetty:jetty $KEYSTORE