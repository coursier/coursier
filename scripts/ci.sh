#!/usr/bin/env bash
set -evx

if [ "$TEST_TARGET" = "Scala.JS" ]; then
  sbt scala212 \
    js/compile \
    js/test:compile \
    coreJS/fastOptJS \
    cacheJS/fastOptJS \
    testsJS/test:fastOptJS \
    js/test:fastOptJS \
    js/test
elif [ "$TEST_TARGET" = "ScalaNative" ]; then
  curl -f https://raw.githubusercontent.com/scala-native/scala-native/master/scripts/travis_setup.sh | bash -x

  sbt scala212 \
    launcher-native_03/publishLocal \
    launcher-native_040M2/publishLocal \
    cli/pack

  modules/cli/target/pack/bin/coursier bootstrap \
    -S \
    -o native-echo \
    io.get-coursier:echo_native0.3_2.11:1.0.1

  if [ "$(./native-echo -n foo a)" != "foo a" ]; then
    echo "Error: unexpected output from native test bootstrap." 1>&2
    exit 1
  fi
elif [ "$TEST_TARGET" = "Website" ]; then
  amm website.sc generate
else
  sbt scalaFromEnv \
    jvmProjects/compile \
    jvmProjects/test:compile

  if [ "$(uname)" == "Darwin" ]; then
    IT="testsJVM/it:test" # doesn't run proxy-tests in particular
  else
    IT="jvmProjects/it:test"
  fi

  ./scripts/with-redirect-server.sh \
    ./modules/tests/handmade-metadata/scripts/with-test-repo.sh \
    sbt scalaFromEnv jvmProjects/test "$IT"

  sbt scalaFromEnv evictionCheck compatibilityCheck
fi

