#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

sbt scala212 coreJVM/publishLocal cacheJVM/publishLocal

./generate-website.sh

./push-website.sh

