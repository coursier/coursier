#!/usr/bin/env bash
set -e

if [[ "$OSTYPE" == "msys" ]]; then
  ./mill.bat -i ci.copyJvm --dest jvm
  export GRAALVM_HOME="$(pwd -W | sed 's,/,\\,g')\\jvm"
  echo "GRAALVM_HOME=$GRAALVM_HOME"

  ./mill.bat -i "$@"
else
  ./mill -i "$@"
fi
