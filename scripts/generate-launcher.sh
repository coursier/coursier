#!/bin/bash

VERSION=1.0.3-SNAPSHOT
CACHE_VERSION=v1

SBTPACK_LAUNCHER="$(dirname "$0")/../cli/target/pack/bin/coursier"

if [ ! -f "$SBTPACK_LAUNCHER" ]; then
  sbt ++2.12.4 "project cli" pack
fi

"$SBTPACK_LAUNCHER" bootstrap \
  --intransitive io.get-coursier::coursier-cli:$VERSION \
  --classifier standalone \
  -J "-noverify" \
  --no-default \
  -r file:$HOME/.m2/repository \
  -r central \
  -r sonatype:releases \
  -f -o coursier-standalone \
  "$@"

"$SBTPACK_LAUNCHER" shoestrap \
  --intransitive io.get-coursier::coursier-cli:$VERSION \
  -J "-noverify" \
  --no-default \
  -r file:$HOME/.m2/repository \
  -r central \
  -r sonatype:releases \
  -f -o coursier \
  "$@"
