#!/usr/bin/env bash

# cs launcher script
# This script lives at https://github.com/coursier/coursier/blob/main/cs.sh
# (Raw version for curl: https://github.com/coursier/coursier/raw/main/cs.sh)
#
# A copy of it is kept at https://github.com/coursier/ci-scripts/blob/master/cs.sh

# Originally adapted from https://github.com/VirtusLab/scala-cli/blob/b754d2afdda114e97febfb0090773cc582bafd19/scala-cli.sh

set -eu

CS_VERSION="2.1.25-M26"

GH_ORG="coursier"
GH_NAME="coursier"

if [ "$CS_VERSION" == "nightly" ]; then
  # nightly launchers live in a release of their own, whose tag has no "v" prefix
  TAG="nightly"
else
  TAG="v$CS_VERSION"
fi

IS_WINDOWS=false
if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" == "MINGW" ]; then
  IS_WINDOWS=true
fi

if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" == "Linux" ]; then
  arch="$(uname -m)"
  if [ "$arch" == "aarch64" ] || [ "$arch" == "x86_64" ]; then
    CS_URL="https://github.com/$GH_ORG/$GH_NAME/releases/download/$TAG/cs-$arch-pc-linux.gz"
    CACHE_BASE="$HOME/.cache/coursier/v1"
  else
    echo "No native coursier launcher available for architecture $arch on Linux" 1>&2
    exit 1
  fi
elif [ "$(uname)" == "Darwin" ]; then
  arch="$(uname -m)"
  CACHE_BASE="$HOME/Library/Caches/Coursier/v1"
  if [ "$arch" == "x86_64" ]; then
    CS_URL="https://github.com/$GH_ORG/$GH_NAME/releases/download/$TAG/cs-x86_64-apple-darwin.gz"
  elif [[ "$arch" == "arm64" ]]; then
    CS_URL="https://github.com/$GH_ORG/$GH_NAME/releases/download/$TAG/cs-aarch64-apple-darwin.gz"
  else
    echo "No native coursier launcher available for architecture $arch on macOS" 1>&2
    exit 1
  fi
elif [ "$IS_WINDOWS" == true ]; then
  CS_URL="https://github.com/$GH_ORG/$GH_NAME/releases/download/$TAG/cs-x86_64-pc-win32.zip"
  CACHE_BASE="$LOCALAPPDATA/Coursier/cache/v1"
else
   echo "This standalone cs launcher supports only Linux and macOS." 1>&2
   exit 1
fi

CACHE_DEST="$CACHE_BASE/$(echo "$CS_URL" | sed 's@://@/@')"

if [ "$IS_WINDOWS" == true ]; then
  CS_BIN_PATH="${CACHE_DEST%.zip}.exe"
else
  CS_BIN_PATH=${CACHE_DEST%.gz}
fi

if [ ! -f "$CACHE_DEST" ]; then
  mkdir -p "$(dirname "$CACHE_DEST")"
  TMP_DEST="$CACHE_DEST.tmp-setup"
  echo "Downloading $CS_URL" 1>&2
  curl -fLo "$TMP_DEST" "$CS_URL"
  mv "$TMP_DEST" "$CACHE_DEST"
fi

if [ ! -f "$CS_BIN_PATH" ]; then
  if [ "$IS_WINDOWS" == true ]; then
    # PowerShell is always available on Windows, unlike unzip, that MinGit doesn't ship.
    # The archive only contains cs-x86_64-pc-win32.exe, which is what CS_BIN_PATH points at.
    CS_ARCHIVE="$CACHE_DEST" CS_ARCHIVE_DEST="$(dirname "$CACHE_DEST")" \
      powershell -NoProfile -NonInteractive -Command \
      'Expand-Archive -LiteralPath $env:CS_ARCHIVE -DestinationPath $env:CS_ARCHIVE_DEST -Force'
  else
    gunzip -k "$CACHE_DEST"
  fi
fi

if [ "$IS_WINDOWS" != true ]; then
  if [ ! -x "$CS_BIN_PATH" ]; then
    chmod +x "$CS_BIN_PATH"
  fi
fi

exec "$CS_BIN_PATH" "$@"
