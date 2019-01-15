#!/usr/bin/env bash
set -e

export PATH="$(pwd)/bin:$PATH"

mkdir -p bin
curl -Lo bin/sbt https://raw.githubusercontent.com/paulp/sbt-extras/62e7b13b3886cc36c2f3a5a4e9dd161b94657d09/sbt
chmod +x bin/sbt

(echo "#!/usr/bin/env sh" && curl -L https://github.com/lihaoyi/Ammonite/releases/download/1.6.2/2.12-1.6.2) > bin/amm
chmod +x bin/amm

curl -Lo bin/coursier https://github.com/coursier/coursier/releases/download/v1.1.0-M10/coursier
chmod +x bin/coursier
