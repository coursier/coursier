#!/usr/bin/env bash
set -e

export PATH="$(pwd)/bin:$PATH"

mkdir -p bin
curl -L -o bin/sbt https://raw.githubusercontent.com/paulp/sbt-extras/33b1a535656222810572d36d10afc5711515958e/sbt
chmod +x bin/sbt

curl -Lo bin/mill https://github.com/lihaoyi/mill/releases/download/0.3.5/0.3.5
chmod +x bin/mill
