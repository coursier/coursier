#!/usr/bin/env bash
set -e


COURSIER_VERSION="2.0.0-RC5-6"
AMMONITE_VERSION="2.0.4"

SBT_LAUNCHER_VERSION="1.2.17"
SBT_EXTRAS_COMMIT="150686c"


export PATH="$(pwd)/bin:$PATH"
mkdir -p bin

if [[ "$TRAVIS_OS_NAME" == "windows" ]]; then

  rm -f sbt
  curl -Lo bin/sbt "https://github.com/coursier/sbt-launcher/releases/download/v$SBT_LAUNCHER_VERSION/csbt"
  curl -Lo bin/sbt.bat "https://github.com/coursier/sbt-launcher/releases/download/v$SBT_LAUNCHER_VERSION/csbt.bat"
  
  curl -Lo bin/amm.bat "https://github.com/lihaoyi/Ammonite/releases/download/$AMMONITE_VERSION/2.12-$AMMONITE_VERSION"
  
  curl -Lo bin/coursier "https://github.com/coursier/coursier/releases/download/v$COURSIER_VERSION/coursier"
  curl -Lo bin/coursier.bat "https://github.com/coursier/coursier/releases/download/v$COURSIER_VERSION/coursier.bat"

else

  curl -Lo bin/sbt "https://raw.githubusercontent.com/coursier/sbt-extras/$SBT_EXTRAS_COMMIT/sbt"
  chmod +x bin/sbt

  (echo "#!/usr/bin/env sh" && curl -L "https://github.com/lihaoyi/Ammonite/releases/download/$AMMONITE_VERSION/2.12-$AMMONITE_VERSION") > bin/amm
  chmod +x bin/amm

  curl -Lo bin/coursier "https://github.com/coursier/coursier/releases/download/v$COURSIER_VERSION/coursier"
  chmod +x bin/coursier

fi
