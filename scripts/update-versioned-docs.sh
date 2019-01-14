#!/usr/bin/env bash
set -ev

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ${TRAVIS_TAG} != v* ]]; then
  echo "Not on a git tag"
  exit 1
fi

source scripts/setup-build-tools.sh
npm install
npm install bower
export PATH="$PATH:$(pwd)/node_modules/bower/bin"

mkdir -p target
cd target
git clone "https://${GH_TOKEN}@github.com/coursier/versioned-docs.git" -b master
cd -
cp -R target/versioned-docs/* doc/website/

git config user.name "Travis-CI"
git config user.email "invalid@travis-ci.com"

VERSION="$(echo "$TRAVIS_TAG" | sed 's@^v@@')"

mill -i all doc.publishLocal doc.mdoc.mdoc
cd doc/website
npm install
yarn run build
yarn run version "$VERSION"
cd -

cp -R doc/website/version* target/versioned-docs/
cd target/versioned-docs
git add version*
git commit -m "Add doc for $VERSION"

git push origin master
