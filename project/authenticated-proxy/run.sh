#!/usr/bin/env sh
set -e

# inspired by https://github.com/bahamat/docker-authenticated-proxy/blob/71901e9e13a3cce7bcf025e30eed71b0e3fdc71c/Dockerfile

mkdir -p /run/apache2
apk add apache2 apache2-ctl apache2-proxy apache2-utils

mkdir -p /etc/apache2/conf.d
cat > /etc/apache2/conf.d/proxy.conf << EOF
LoadModule proxy_module modules/mod_proxy.so
LoadModule proxy_connect_module modules/mod_proxy_connect.so
LoadModule proxy_ftp_module modules/mod_proxy_ftp.so
LoadModule proxy_http_module modules/mod_proxy_http.so

<IfModule mod_proxy.c>

    ProxyRequests On

    <Proxy *>
        Order deny,allow
        Allow from none
        Deny from all
        AuthType Basic
        AuthName "Just A Test"
        AuthUserFile /etc/apache2/htpasswd
        Require valid-user
        Satisfy Any
    </Proxy>

</IfModule>
EOF

echo 'jack:$apr1$S0H.8F5a$dTQ70PRSs6uJK0CFA9qFC/' > /etc/apache2/htpasswd

echo "Starting server"

exec /usr/sbin/apachectl -D FOREGROUND
