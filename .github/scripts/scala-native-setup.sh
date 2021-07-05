#!/usr/bin/env bash
set -e

find /usr -name "*libunwind*" -print0 | sudo xargs -0 rm -rf
curl -f https://raw.githubusercontent.com/scala-native/scala-native/master/scripts/travis_setup.sh | bash -x
