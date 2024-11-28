#!/usr/bin/env bash

# This is the launcher script of Scala CLI (https://github.com/VirtusLab/scala-cli).
# This script downloads and runs the Scala CLI version set by SCALA_CLI_VERSION below.
#
# Download the latest version of this script at https://github.com/VirtusLab/scala-cli/raw/main/scala-cli.sh

set -eu

SCALA_CLI_VERSION="1.5.4"

GH_ORG="VirtusLab"
GH_NAME="scala-cli"

if [ "$SCALA_CLI_VERSION" == "nightly" ]; then
  TAG="nightly"
else
  TAG="v$SCALA_CLI_VERSION"
fi

if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" == "Linux" ]; then
  arch=$(uname -m)
  if [[ "$arch" == "aarch64" ]] || [[ "$arch" == "x86_64" ]]; then
    SCALA_CLI_URL="https://github.com/$GH_ORG/$GH_NAME/releases/download/$TAG/scala-cli-${arch}-pc-linux.gz"
  else
    echoerr "scala-cli is not supported on $arch"
    exit 2
  fi
  CACHE_BASE="$HOME/.cache/coursier/v1"
elif [ "$(uname)" == "Darwin" ]; then
  arch=$(uname -m)
  CACHE_BASE="$HOME/Library/Caches/Coursier/v1"
  if [[ "$arch" == "x86_64" ]]; then
    SCALA_CLI_URL="https://github.com/$GH_ORG/$GH_NAME/releases/download/$TAG/scala-cli-x86_64-apple-darwin.gz"
  elif [[ "$arch" == "arm64" ]]; then
    SCALA_CLI_URL="https://github.com/$GH_ORG/$GH_NAME/releases/download/$TAG/scala-cli-aarch64-apple-darwin.gz"
  else
    echoerr "scala-cli is not supported on $arch"
    exit 2
  fi
else
  echo "This standalone scala-cli launcher is supported only in Linux and macOS. If you are using Windows, please use the dedicated launcher scala-cli.bat"
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
fi

if [ ! -f "$SCALA_CLI_BIN_PATH" ]; then
  gunzip -k "$CACHE_DEST"
fi

if [ ! -x "$SCALA_CLI_BIN_PATH" ]; then
  chmod +x "$SCALA_CLI_BIN_PATH"
fi

exec "$SCALA_CLI_BIN_PATH" "$@"
