#!/usr/bin/env bash
set -evx

if [ "$TARGET" = "Scala.JS" ]; then
  sbt scala212 \
    js/compile \
    js/test:compile \
    coreJS/fastOptJS \
    cacheJS/fastOptJS \
    testsJS/test:fastOptJS \
    js/test:fastOptJS \
    js/test
elif [ "$TARGET" = "ScalaNative" ]; then
  # travis_setup.sh below does that, but doesn't handle directories fine
  find /usr -name "*libunwind*" -print0 | sudo xargs -0 rm -rf
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
elif [ "$TARGET" = "Website" ]; then
  echo "$PATH"
  ls bin
  which cs || true
  which amm || true
  which amm-runner || true
  amm-runner website.sc generate
else
  if [[ ${SCALA_VERSION} == 2.12* ]]; then
    sudo apt-get install -y nailgun
  fi

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

