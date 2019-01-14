#!/usr/bin/env bash
set -eu

if [[ ${TRAVIS_TAG} != v* ]]; then
  echo "Not on a git tag"
  exit 1
fi

export VERSION="$(echo "$TRAVIS_TAG" | sed 's@^v@@')"

mkdir -p target
cd target

if [ -d gh-pages ]; then
  echo "Removing former gh-pages clone"
  rm -rf gh-pages
fi

echo "Cloning"
git clone "https://${GH_TOKEN}@github.com/coursier/coursier.git" -q -b gh-pages gh-pages
cd gh-pages

git config user.name "Travis-CI"
git config user.email "invalid@travis-ci.com"

curl -Lo coursier "https://github.com/coursier/coursier/releases/download/$TRAVIS_TAG/coursier"
curl -Lo coursier.bat "https://github.com/coursier/coursier/releases/download/$TRAVIS_TAG/coursier.bat"

git add -- coursier coursier.bat

MSG="Add $VERSION launcher"

# probably not fine with i18n
if git status | grep "nothing to commit" >/dev/null 2>&1; then
  echo "Nothing changed"
else
  git commit -m "$MSG"

  echo "Pushing changes"
  git push origin gh-pages
fi
