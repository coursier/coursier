#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [ "$TRAVIS_BRANCH" != "" ]; then
  source scripts/setup-sbt-extra.sh
fi

sbt scala212 coreJVM/publishLocal cacheJVM/publishLocal

./scripts/generate-website.sh

if [ "${PUSH_WEBSITE:-""}" = 1 ]; then
  ./scripts/push-website.sh
fi

