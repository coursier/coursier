#!/usr/bin/env bash
set -e

export PATH="$(pwd)/bin:$PATH"

mkdir -p bin
curl -L -o bin/sbt https://raw.githubusercontent.com/paulp/sbt-extras/62e7b13b3886cc36c2f3a5a4e9dd161b94657d09/sbt
chmod +x bin/sbt

curl -Lo bin/mill https://github.com/lihaoyi/mill/releases/download/0.3.5/0.3.5
chmod +x bin/mill
