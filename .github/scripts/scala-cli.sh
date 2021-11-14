#!/usr/bin/env bash

# This is the launcher script of Scala CLI (https://github.com/VirtusLab/scala-cli).
# This script downloads and runs the Scala CLI version set by VERSION below.
#
# Download the latest version of this script at https://github.com/VirtusLab/scala-cli/raw/master/scala-cli.sh

set -eu

SCALA_CLI_VERSION="0.0.4"

if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" == "Linux" ]; then
  SCALA_CLI_URL="https://github.com/VirtusLab/scala-cli/releases/download/v$SCALA_CLI_VERSION/scala-cli-x86_64-pc-linux.gz"
  CACHE_BASE="$HOME/.cache/coursier/v1"
elif [ "$(uname)" == "Darwin" ]; then
  SCALA_CLI_URL="https://github.com/VirtusLab/scala-cli/releases/download/v$SCALA_CLI_VERSION/scala-cli-x86_64-apple-darwin.gz"
  CACHE_BASE="$HOME/Library/Caches/Coursier/v1"
else
   echo "This standalone scala-cli launcher is supported only in Linux and Darwin OS. If you are using Windows, please use the dedicated launcher scala-cli.bat"
   exit 1
fi

CACHE_DEST="$CACHE_BASE/$(echo "$SCALA_CLI_URL" | sed 's@://@/@')"
SCALA_CLI_BIN_PATH=${CACHE_DEST%.gz}

if [ ! -f "$CACHE_DEST" ]; then
  mkdir -p "$(dirname "$CACHE_DEST")"
  TMP_DEST="$CACHE_DEST.tmp-setup"
  echo "Downloading $SCALA_CLI_URL"
  curl -fLo "$TMP_DEST" "$SCALA_CLI_URL"
  mv "$TMP_DEST" "$CACHE_DEST"
  gunzip -k "$CACHE_DEST"
  chmod +x "$SCALA_CLI_BIN_PATH"
fi

exec "$SCALA_CLI_BIN_PATH" "$@"