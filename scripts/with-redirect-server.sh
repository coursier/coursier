#!/usr/bin/env bash
set -eu

DIR="$(dirname "${BASH_SOURCE[0]}")"

export SERVER_HOST="${SERVER_HOST:-"localhost"}"
export SERVER_PORT="${SERVER_PORT:-10002}"
export REDIRECT_TO="${REDIRECT_TO:-"https://repo1.maven.org/maven2"}"

export TEST_REDIRECT_REPOSITORY="http://$SERVER_HOST:$SERVER_PORT"


SERVER_PID=""

cleanup() {
  if [ ! -z "$SERVER_PID" ]; then
    echo "Terminating background HTTP redirect server"
    kill -15 "$SERVER_PID"
    while kill -0 "$SERVER_PID" >/dev/null 2>&1; do
      echo "Redirect server still running"
      sleep 1
      kill -15 "$SERVER_PID" >/dev/null 2>&1 || true
    done
    echo "Redirect server terminated"
  fi
}

trap cleanup EXIT INT TERM


# see https://unix.stackexchange.com/questions/90244/bash-run-command-in-background-and-capture-pid
runServerBg() {
  coursier launch \
    com.lihaoyi:ammonite_2.12.7:1.4.4 \
    -M ammonite.Main \
    -- \
      "$DIR/redirect-server.sc" &
  SERVER_PID="$!"
}

runServerBg

"$@"
