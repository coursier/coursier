#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

SSL_PASSWORD="${SSL_PASSWORD:-ssl-pass}"

openssl genrsa -out server.key 2048
openssl req -new -out server.csr -key server.key
openssl x509 -req -days 365 -in server.csr -signkey server.key -out server.crt
openssl pkcs12 -export -in server.crt -inkey server.key -out server.p12 -name server -CAfile server.crt -caname root

keytool -importkeystore -deststorepass "$SSL_PASSWORD" -destkeypass "$SSL_PASSWORD" -destkeystore server.keystore -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass "" -alias server

rm -f server.crt server.key server.csr server.p12
