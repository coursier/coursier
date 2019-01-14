#!/usr/bin/env bash
set -euv

cd "$(dirname "${BASH_SOURCE[0]}")/.."

sbt web/fastOptJS::webpack

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

echo "Cleaning-up gh-pages"
mkdir .tmp
while read i; do
  git mv "$i" .tmp/
done < <(cat .keep)
git rm -r *
while read i; do
  git mv ".tmp/$i" .
done < <(cat .keep)
rmdir .tmp

echo "Copying new website"
cp -pR ../../doc/website/build/coursier/* .
mkdir demo
cp ../../modules/web/target/scala-2.12/scalajs-bundler/main/web-fastopt-bundle.js demo/
sed 's@\.\./scalajs-bundler/main/@@g' < ../../modules/web/target/scala-2.12/classes/index.html > demo/index.html
cat > demo.html << EOF
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="refresh" content="0; url=demo/" />
</head>
<body>
Redirecting to <a href="demo/">demo/</a>
</body>
</html>
EOF
git add .

MSG="Update website"

# probably not fine with i18n
if git status | grep "nothing to commit" >/dev/null 2>&1; then
  echo "Nothing changed"
else
  git commit -m "$MSG"

  echo "Pushing changes"
  git push origin gh-pages
fi
