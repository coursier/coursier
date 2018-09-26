#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

./generate-website.sh

./push-website.sh

