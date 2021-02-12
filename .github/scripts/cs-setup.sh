#!/usr/bin/env bash

#
# coursier launcher setup script
# See https://github.com/coursier/ci-scripts
#


set -e

coursier_version="2.0.6"
dest="cs"

# Adapted from https://github.com/paulp/sbt-extras/blob/ea47026bd55439760f4c5e9b27b002fa64a8b82f/sbt#L427-L435 and around
die() {
  echo "Aborting: $*"
  exit 1
}
process_args() {
  require_arg() {
    local type="$1"
    local opt="$2"
    local arg="$3"

    if [[ -z "$arg" ]] || [[ "${arg:0:1}" == "-" ]]; then
      die "$opt requires <$type> argument"
    fi
  }
  parsing_args="true"
  while [[ $# -gt 0 && "$parsing_args" == "true" ]]; do
    case "$1" in
      --version) require_arg version "$1" "$2" && coursier_version="$2" && shift 2 ;;
      --dest) require_arg path "$1" "$2" && dest="$2" && shift 2 ;;
      *) die "Unexpected argument: '$1'" ;;
    esac
  done
}

process_args "$@"


do_chmod="1"
ext=""

# https://stackoverflow.com/questions/3466166/how-to-check-if-running-in-cygwin-mac-or-linux/17072017#17072017
if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" == "Linux" ]; then
  cs_url="https://github.com/coursier/coursier/releases/download/v$coursier_version/cs-x86_64-pc-linux"
  cache_base="$HOME/.cache/coursier/v1"
elif [ "$(uname)" == "Darwin" ]; then
  cs_url="https://github.com/coursier/coursier/releases/download/v$coursier_version/cs-x86_64-apple-darwin"
  cache_base="$HOME/Library/Caches/Coursier/v1"
else
  # assuming Windows…
  cs_url="https://github.com/coursier/coursier/releases/download/v$coursier_version/cs-x86_64-pc-win32.exe"
  cache_base="$LOCALAPPDATA/Coursier/v1" # TODO Check that
  ext=".exe"
  do_chmod="0"
fi

cache_dest="$cache_base/$(echo "$cs_url" | sed 's@://@/@')"

if [ -f "$cache_dest" ]; then
  echo "Found $cache_dest in cache"
else
  mkdir -p "$(dirname "$cache_dest")"
  tmp_dest="$cache_dest.tmp-setup"
  echo "Downloading $cs_url"
  curl -fLo "$tmp_dest" "$cs_url"
  mv "$tmp_dest" "$cache_dest"
fi

cp "$cache_dest" "$dest$ext"
if [ "$do_chmod" = "1" ]; then
  chmod +x "$dest$ext"
fi

export PATH=".:$PATH"
"$dest" --help
