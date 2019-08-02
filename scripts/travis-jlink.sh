#!/usr/bin/env bash
set -e

#### DEBUG

GPG_OPTS="--batch=true --yes"
if [[ "$TRAVIS_OS_NAME" == "osx" || "$TRAVIS_OS_NAME" == "windows" ]]; then
  GPG_OPTS="$GPG_OPTS --pinentry-mode loopback"
fi

echo "Import GPG key"
echo "$PGP_SECRET" | base64 --decode | gpg $GPG_OPTS --import

# test run
gpg $GPG_OPTS --passphrase "$PGP_PASSPHRASE" --detach-sign --armor --use-agent --output ".travis.yml.asc" ".travis.yml"

####

export JABBA_HOME="$HOME/.jabba" JABBA_JDK="openjdk@1.11.0-2"

if [[ "$TRAVIS_OS_NAME" == "windows" ]]; then
  curl -Lo jdk.zip https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.4%2B11/OpenJDK11U-jdk_x64_windows_hotspot_11.0.4_11.zip
  unzip jdk.zip
  rm -f jdk.zip
  JAVA_HOME="$(pwd)/jdk-11.0.4+11"

  export PATH="$JAVA_HOME/bin:$PATH"
else
  curl -sL https://raw.githubusercontent.com/shyiko/jabba/0.11.2/install.sh | bash
  source "$HOME/.jabba/jabba.sh"
  "$JABBA_HOME/bin/jabba" install "$JABBA_JDK"
  JAVA_HOME="$JABBA_HOME/jdk/$JABBA_JDK"

  if [[ "$(uname)" == "Darwin" ]]; then
    JAVA_HOME="$JAVA_HOME/Contents/Home"
  fi
fi

export JAVA_HOME

sbt cli/universal:packageBin

export REPO="coursier/coursier"
export NAME="standalone-${LAUNCHER_OS}.zip"
export CMD="cp modules/cli/target/universal/standalone.zip \$OUTPUT #"
export HAS_BAT=false
scripts/upload-launcher/upload-gh-release.sh

# try to find zombie / daemons (must be killed for the job to exit)
ps -ef
