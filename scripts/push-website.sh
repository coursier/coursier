#!/usr/bin/env bash
set -eu

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [ ! -e doc/generated/index.html ]; then
  echo "Generated website not found under doc/generated"
  exit 1
fi

if echo "$OSTYPE" | grep -q darwin; then
  GREP="ggrep"
else
  GREP="grep"
fi

VERSION="$("$GREP" -oP '(?<=")[^"]*(?<!")' version.sbt)"
echo "Current version is $VERSION"

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

UPDATE="0"

if [ -d "$VERSION" ]; then
  echo "Cleaning-up $VERSION directory"
  git rm -r "$VERSION"
  UPDATE="1"
fi

mkdir -p "$VERSION"
echo "Copying new website"
cp -pR ../../doc/generated/* "$VERSION/"
git add "$VERSION"

DIRS=()

if [[ ${VERSION} = *-SNAPSHOT || ${VERSION} = *+* ]]; then
  DIRS=("snapshot")
elif [[ ${VERSION} = *-M* || ${VERSION} = *-RC* ]]; then
  DIRS=("snapshot" "latest")
else
  DIRS=("snapshot" "latest" "stable")
fi

for dir in "${DIRS[@]}"; do
  rm -f "$dir"
  ln -s "$VERSION" "$dir"
  git add "$dir"
done

if [ "$UPDATE" = 1 ]; then
  MSG="Update doc for version $VERSION"
else
  MSG="Add doc for version $VERSION"
fi

# probably not fine with i18n
if git status | grep "nothing to commit" >/dev/null 2>&1; then
  echo "Nothing changed"
else
  git commit -m "$MSG"

  echo "Pushing changes"
  git push origin gh-pages
fi
