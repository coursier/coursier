#!/usr/bin/env bash
set -e

export PATH="$(pwd)/bin:$PATH"

mkdir -p bin
curl -L -o bin/sbt https://github.com/paulp/sbt-extras/raw/1d8ee2c0a75374afa1cb687f450aeb095180882b/sbt
chmod +x bin/sbt

