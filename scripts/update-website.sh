#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [ "$TRAVIS_BRANCH" != "" ]; then
  source scripts/setup-build-tools.sh
fi

mill -i all doc.publishLocal doc.docusaurus.postProcess

if [ "${PUSH_WEBSITE:-""}" = 1 ]; then
  ./scripts/push-website.sh
fi

