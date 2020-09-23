#!/usr/bin/env bash
set -euo pipefail

# TODO Migrate that to an Ammonite script

if [ ! -z ${TRAVIS_TAG+x} ] && [ ${TRAVIS_TAG} == v* ]; then
  VERSION="${TRAVIS_TAG#v}"
  GIT_USERNAME="Travis-CI"
  GIT_EMAIL="invalid@travis-ci.com"
elif [ ! -z ${GITHUB_REF+x} ] && [ ${GITHUB_REF} == refs/tags/v* ]; then
  VERSION="${GITHUB_REF#refs/tags/v}"
  GIT_USERNAME="Github Actions"
  GIT_EMAIL="actions@github.com"
else
  echo "Error: could not get current tag" 1>&2
  exit 1
fi

mkdir -p target
cd target

if [ -d homebrew-formulas ]; then
  echo "Removing former homebrew-formulas clone"
  rm -rf homebrew-formulas
fi

echo "Cloning"
git clone "https://${GH_TOKEN}@github.com/coursier/homebrew-formulas.git" -q -b master homebrew-formulas
cd homebrew-formulas

git config user.name "$GIT_USERNAME"
git config user.email "$GIT_EMAIL"

JAR_URL="https://github.com/coursier/coursier/releases/download/v$VERSION/coursier"
curl -fLo jar-launcher "$JAR_URL"

URL="https://github.com/coursier/coursier/releases/download/v$VERSION/cs-x86_64-apple-darwin"
curl -fLo launcher "$URL"

JAR_SHA256="$(openssl dgst -sha256 -binary < jar-launcher | xxd -p -c 256)"
rm -f jar-launcher

SHA256="$(openssl dgst -sha256 -binary < launcher | xxd -p -c 256)"
rm -f launcher

cat "../../scripts/coursier.rb.template" |
  sed 's=@LAUNCHER_VERSION@='"$VERSION"'=g' |
  sed 's=@LAUNCHER_URL@='"$URL"'=g' |
  sed 's=@LAUNCHER_SHA256@='"$SHA256"'=g' |
  sed 's=@JAR_LAUNCHER_URL@='"$JAR_URL"'=g' |
  sed 's=@JAR_LAUNCHER_SHA256@='"$JAR_SHA256"'=g' > coursier.rb

git add -- coursier.rb

MSG="Updates for $VERSION"

# probably not fine with i18n
if git status | grep "nothing to commit" >/dev/null 2>&1; then
  echo "Nothing changed"
else
  git commit -m "$MSG"

  echo "Pushing changes"
  git push origin master
fi
