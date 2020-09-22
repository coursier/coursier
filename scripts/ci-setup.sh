#!/usr/bin/env bash
set -e

./scripts/cs-setup.sh
mkdir -p bin
./cs bootstrap -o bin/sbt sbt-launcher io.get-coursier:coursier_2.12:2.0.0-RC6-25
./cs install --install-dir bin cs coursier
./cs install --install-dir bin --contrib amm-runner
rm -f cs cs.exe

export PATH="$(pwd)/bin:$PATH"
echo "::add-path::$(pwd)/bin"
