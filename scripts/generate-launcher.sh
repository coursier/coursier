#!/bin/bash
set -e

if [ -z ${VERSION+x} ]; then
  VERSION="$(git describe --tags --abbrev=0 --match "v*" | sed 's/^v//')"
fi

OUTPUT="${OUTPUT:-"coursier"}"

if [[ "$DOWNLOAD_LAUNCHER" == true ]]; then
  LAUNCHER="./coursier-$VERSION"
  curl -fLo "$LAUNCHER" "https://github.com/coursier/coursier/releases/download/v$VERSION/coursier"
  chmod +x "$LAUNCHER"
  EXTRA_ARGS="launch -r typesafe:ivy-releases io.get-coursier:coursier-cli_2.12:2.0.0-RC2-6 -E io.get-coursier:coursier-okhttp_2.12 --"
else
  LAUNCHER="$(dirname "$0")/../modules/cli/target/pack/bin/coursier"
  EXTRA_ARGS=""

  if [ ! -f "$LAUNCHER" ]; then
    sbt scala212 cli/pack
  fi
fi

MODULE="${MODULE:-"coursier-cli"}"

"$LAUNCHER" $EXTRA_ARGS bootstrap \
  "io.get-coursier::$MODULE:$VERSION" \
  --no-default \
  -r central \
  -r typesafe:ivy-releases \
  -f -o "$OUTPUT" \
  "$@"
