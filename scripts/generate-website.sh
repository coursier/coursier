#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

if echo "$OSTYPE" | grep -q darwin; then
  GREP="ggrep"
else
  GREP="grep"
fi

VERSION="$("$GREP" -oP '(?<=")[^"]*(?<!")' ../version.sbt)"
echo "Current version is $VERSION"

# website generation based on https://github.com/szeiger/ornate

java -Dversion="$VERSION" -jar ../coursier launch com.novocode:ornate_2.11:0.5 -- ../doc/ornate.conf

DIR="$(cd ../doc/generated; pwd)"

echo
echo "Generated website available under $DIR"
