#!/usr/bin/env bash
set -euv

sbt web/fastOptJS::webpack

DEST="$WEBSITE_DIR/build/coursier/demo"
mkdir -p "$DEST"

cp modules/web/target/scala-2.12/scalajs-bundler/main/web-fastopt-bundle.js "$DEST/"
cp modules/web/target/scala-2.12/classes/bundle.js "$DEST/"
sed 's@\.\./scalajs-bundler/main/@@g' < modules/web/target/scala-2.12/classes/index.html > "$DEST/index.html"

