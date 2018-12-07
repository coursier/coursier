#!/bin/bash

# grepped and updated during releases
VERSION=1.1.0-M9
ACTUAL_VERSION="${ACTUAL_VERSION:-"$VERSION"}"
CACHE_VERSION=v1

OUTPUT="${OUTPUT:-"coursier"}"

SBTPACK_LAUNCHER="$(dirname "$0")/../modules/cli/target/pack/bin/coursier"

if [ ! -f "$SBTPACK_LAUNCHER" ]; then
  sbt scala212 "project cli" pack
fi

"$SBTPACK_LAUNCHER" bootstrap \
  --intransitive "io.get-coursier::coursier-cli:$ACTUAL_VERSION" \
  --classifier standalone \
  -A jar \
  -J "-noverify" \
  --no-default \
  -r central \
  -r sonatype:releases \
  -f -o "$OUTPUT" \
  "$@"
