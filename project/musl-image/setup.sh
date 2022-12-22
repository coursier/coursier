#!/usr/bin/env bash
set -e

cd /usr/local/musl/bin

for i in x86_64-unknown-linux-musl-*; do
  dest="$(echo "$i" | sed 's/-unknown//')"
  ln -s "$i" "$dest"
done
