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

echo "Processing Markdown files"

# first processing md files via https://github.com/olafurpg/mdoc
# requires the cache modules and its dependencies to have been published locally
# with
#   sbt coreJVM/publishLocal cacheJVM/publishLocal
../coursier launch \
  com.geirsson:mdoc_2.12.6:0.4.5 \
  "io.get-coursier:coursier-cache_2.12:$VERSION" \
  -- \
    --in ../doc/docs \
    --out ../doc/processed-docs

# website generation based on https://github.com/szeiger/ornate

echo "Generating website"

java -Dversion="$VERSION" -jar ../coursier launch com.novocode:ornate_2.11:0.5 -- ../doc/ornate.conf

DIR="$(cd ../doc/generated; pwd)"

echo
echo "Generated website available under $DIR"


if echo "$OSTYPE" | grep -q darwin; then
  OPEN_CMD="open"
else
  OPEN_CMD="xdg-open"
fi

cat << EOF
Open the generated website with

  $OPEN_CMD doc/generated/index.html

EOF
