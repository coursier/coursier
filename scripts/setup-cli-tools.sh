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
  curl -fLo bin/sbt "https://github.com/coursier/sbt-launcher/releases/download/v$SBT_LAUNCHER_VERSION/csbt"
  curl -fLo bin/sbt.bat "https://github.com/coursier/sbt-launcher/releases/download/v$SBT_LAUNCHER_VERSION/csbt.bat"
  
  curl -fLo bin/amm.bat "https://github.com/lihaoyi/Ammonite/releases/download/$AMMONITE_VERSION/2.12-$AMMONITE_VERSION"
  
  curl -fLo bin/coursier "https://github.com/coursier/coursier/releases/download/v$COURSIER_VERSION/coursier"
  curl -fLo bin/coursier.bat "https://github.com/coursier/coursier/releases/download/v$COURSIER_VERSION/coursier.bat"

else

  curl -fLo bin/sbt "https://raw.githubusercontent.com/coursier/sbt-extras/$SBT_EXTRAS_COMMIT/sbt"
  chmod +x bin/sbt

  (echo "#!/usr/bin/env sh" && curl -fL "https://github.com/lihaoyi/Ammonite/releases/download/$AMMONITE_VERSION/2.12-$AMMONITE_VERSION") > bin/amm
  chmod +x bin/amm

  curl -fLo bin/coursier "https://github.com/coursier/coursier/releases/download/v$COURSIER_VERSION/coursier"
  chmod +x bin/coursier

fi
