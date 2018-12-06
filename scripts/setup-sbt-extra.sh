#!/usr/bin/env bash
set -e

export PATH="$(pwd)/bin:$PATH"

mkdir -p bin
curl -L -o bin/sbt https://raw.githubusercontent.com/paulp/sbt-extras/33b1a535656222810572d36d10afc5711515958e/sbt
chmod +x bin/sbt

