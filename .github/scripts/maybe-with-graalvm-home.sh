#!/usr/bin/env bash
set -e

if [[ "$OSTYPE" == "msys" ]]; then
  ./mill.bat -i ci.copyJvm --dest jvm
  export JAVA_HOME="$(pwd -W | sed 's,/,\\,g')\\jvm"
  export GRAALVM_HOME="$JAVA_HOME"
  export PATH="$(pwd)/jvm/bin:$PATH"
  echo "PATH=$PATH"

  ./mill.bat -i "$@"
else
  ./mill -i "$@"
fi
