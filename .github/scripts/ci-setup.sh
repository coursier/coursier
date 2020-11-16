#!/usr/bin/env bash
set -e

./.github/scripts/cs-setup.sh
mkdir -p bin
./cs bootstrap -o bin/sbt sbt-launcher:1.2.22 io.get-coursier:coursier_2.12:2.0.0-RC6-25
./cs bootstrap -o bin/amm ammonite:2.1.4-11-307f3d8 --scala 2.12.12
./cs install --install-dir bin cs coursier
./cs install --install-dir bin --contrib amm-runner
export PATH="$(pwd)/bin:$PATH"
echo "$(pwd)/bin" >> "$GITHUB_PATH"

if [ "$1" == "--jvm" ]; then
  JVM="$2"
  eval "$(./cs java --env --jvm "$JVM")"
  echo "JAVA_HOME=$JAVA_HOME" >> "$GITHUB_ENV"
  echo "$JAVA_HOME/bin" >> "$GITHUB_PATH"
  if [ "$(uname)" == "Darwin" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

rm -f cs cs.exe
