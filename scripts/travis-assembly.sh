#!/usr/bin/env bash
set -e

export DOWNLOAD_LAUNCHER=true

export REPO="coursier/coursier"
export NAME="coursier.jar"
export CMD="./scripts/generate-launcher.sh --assembly"
export HAS_BAT=false
scripts/upload-launcher/upload-gh-release.sh
