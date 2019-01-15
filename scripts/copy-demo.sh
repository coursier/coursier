#!/usr/bin/env bash
set -euv

sbt web/fastOptJS::webpack
mkdir -p "$WEBSITE_DIR/build/coursier/demo"
cp modules/web/target/scala-2.12/scalajs-bundler/main/web-fastopt-bundle.js "$WEBSITE_DIR/build/coursier/demo/"
sed 's@\.\./scalajs-bundler/main/@@g' < modules/web/target/scala-2.12/classes/index.html > doc/website/build/coursier/demo/index.html

