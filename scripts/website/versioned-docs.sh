#!/usr/bin/env bash
set -ev

if [ $# = 0 ]; then
  UPDATE=0
elif [ $# = 1 ]; then
  if [ "$1" = --update ]; then
    UPDATE=1
  else
    echo "Unrecognized argument: $1" 1>&2
    exit 1
  fi
else
  echo "Too many arguments passed (expected one or zero arguments)" 1>&2
  exit 1
fi

if [ "$UPDATE" = 1 ]; then
  if [[ ${TRAVIS_TAG} != v* ]]; then
    echo "Not on a git tag"
    exit 1
  fi

  REMOTE="https://${GH_TOKEN}@github.com/$VERSIONED_DOCS_REPO.git"
else
  REMOTE="https://github.com/$VERSIONED_DOCS_REPO.git"
fi

mkdir -p target
git clone "$REMOTE" -b master target/versioned-docs
cp -R \
  target/versioned-docs/versioned_docs \
  target/versioned-docs/versioned_sidebars \
  target/versioned-docs/versions.json \
  "$WEBSITE_DIR/"


if [ "$UPDATE" = 1 ]; then
  git config user.name "Travis-CI"
  git config user.email "invalid@travis-ci.com"

  VERSION="$(echo "$TRAVIS_TAG" | sed 's@^v@@')"

  cd "$WEBSITE_DIR"
  yarn run version "$VERSION"
  cd -

  cp -R "$WEBSITE_DIR"/version* target/versioned-docs/
  cd target/versioned-docs
  git add version*
  git commit -m "Add doc for $VERSION"

  git push origin master
fi
