#!/usr/bin/env bash
set -eu

# adapted fro https://github.com/almond-sh/almond/blob/d9f838f74dbc95965032e8b51568f7c1c7f2e71b/scripts/upload-launcher.sh

# config
REPO="coursier/coursier"
NAME="coursier"
CMD="./scripts/generate-launcher.sh -f" # will work once sync-ed to Maven Central

# initial check with Sonatype releases
cd "$(dirname "${BASH_SOURCE[0]}")"
mkdir -p target/launcher
export OUTPUT="target/launcher/$NAME"
$CMD -r sonatype:releases


# actual script
VERSION="$(git describe --tags --abbrev=0 --always --match 'v*' | sed 's@^v@@')"

RELEASE_ID="$(http "https://api.github.com/repos/$REPO/releases?access_token=$GH_TOKEN" | jq -r '.[] | select(.name == "v'"$VERSION"'") | .id')"

echo "Release ID is $RELEASE_ID"

# wait for sync to Maven Central
ATTEMPT=0
while ! $CMD; do
  if [ "$ATTEMPT" -ge 25 ]; then
    echo "Not synced to Maven Central after $ATTEMPT minutes, exiting"
    exit 1
  else
    echo "Not synced to Maven Central after $ATTEMPT minutes, waiting 1 minute"
    ATTEMPT=$(( $ATTEMPT + 1 ))
    sleep 60
  fi
done

echo "Uploading launcher"

curl \
  --data-binary "@$OUTPUT" \
  -H "Content-Type: application/zip" \
  "https://uploads.github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=$NAME&access_token=$GH_TOKEN"

