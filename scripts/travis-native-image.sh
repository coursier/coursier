#!/usr/bin/env bash
set -e

source scripts/travis-setup-graalvm.sh

export CONTENT_TYPE="application/octet-stream"
export REPO="coursier/coursier"
export NAME="cs-${LAUNCHER_OS}"
export CMD="./scripts/generate-graalvm-launcher.sh"
export HAS_BAT=false
scripts/upload-launcher/upload-gh-release.sh
