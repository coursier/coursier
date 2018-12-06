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

SCALA_VERSION="$(grep scala212 ../project/ScalaVersion.scala | "$GREP" -oP '(?<=")[^"]*(?<!")')"

echo "Processing Markdown files"

# first processing md files via https://github.com/olafurpg/mdoc
# requires the cache modules and its dependencies to have been published locally
# with
#   sbt coreJVM/publishLocal cacheJVM/publishLocal
../coursier launch \
  -r sonatype:releases \
  "com.geirsson:mdoc_$SCALA_VERSION:0.7.0" \
  "io.get-coursier:coursier-cache_2.12:$VERSION" \
  -- \
    --in ../doc/docs \
    --out ../doc/processed-docs \
    --site.VERSION "$VERSION" \
    --site.PLUGIN_VERSION "$VERSION" \
    --site.SCALA_VERSION "$SCALA_VERSION"

echo "Generating website"

cd ../doc/website
npm install
yarn run build
cd -

../scripts/relativize.sh ../doc/website/build

DIR="$(cd ../doc/website/build/coursier; pwd)"

echo
echo "Generated website available under $DIR"


cat << EOF
Open the generated website with

  npx http-server doc/website/build/coursier

EOF
