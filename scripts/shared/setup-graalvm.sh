#!/usr/bin/env bash
set -e

if [ "$(expr substr "$(uname -s)" 1 5 2>/dev/null)" != "Linux" -a "$(uname)" != "Darwin" ]; then
  choco install -y windows-sdk-7.1 vcbuildtools kb2519277
fi

curl -L https://raw.githubusercontent.com/coursier/ci-scripts/master/setup.sh | bash
eval "$(./cs java --env --jvm graalvm-ce-java8:19.3.1)"
rm -f cs cs.exe
echo "::set-env name=JAVA_HOME::$JAVA_HOME"
echo "::add-path::$JAVA_HOME/bin"
