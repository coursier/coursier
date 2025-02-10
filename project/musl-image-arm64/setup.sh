#!/usr/bin/env bash
set -e

cd /usr/local/musl/bin

for i in aarch64-unknown-linux-musl-*; do
  dest="$(echo "$i" | sed 's/-unknown//')"
  echo ln -s "$i" "$dest"
  ln -s "$i" "$dest"

  x86dest="x86_64-$(echo "$i" | sed 's/-unknown//' | sed 's/aarch64-//')"
  echo ln -s "$i" "$x86dest"
  ln -s "$i" "$x86dest"
done
