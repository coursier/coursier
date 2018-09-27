#!/usr/bin/env bash
set -e

coursier() {
  "$(cd $(dirname "${BASH_SOURCE[0]}"); pwd)/../coursier" "$@"
}

coursier launch com.lihaoyi:ammonite_2.12.6:1.2.1 -M ammonite.Main -- \
  "$(dirname "${BASH_SOURCE[0]}")/relativize.sc" "$@"
