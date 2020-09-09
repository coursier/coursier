#!/usr/bin/env bash
set -evx

isScalaJs() {
  [ "$SCALA_JS" = 1 ]
}

isScalaNative() {
  [ "$NATIVE" = 1 ]
}

jsCompile() {
  sbt scalaFromEnv js/compile js/test:compile coreJS/fastOptJS cacheJS/fastOptJS testsJS/test:fastOptJS js/test:fastOptJS
}

jvmCompile() {
  sbt scalaFromEnv jvmProjects/compile jvmProjects/test:compile
}

runJsTests() {
  sbt scalaFromEnv js/test
}

runJvmTests() {
  if [ "$(uname)" == "Darwin" ]; then
    IT="testsJVM/it:test" # don't run proxy-tests in particular
  else
    IT="jvmProjects/it:test"
  fi

  ./scripts/with-redirect-server.sh \
    ./modules/tests/handmade-metadata/scripts/with-test-repo.sh \
    sbt scalaFromEnv jvmProjects/test $IT
}

checkBinaryCompatibility() {
  sbt scalaFromEnv evictionCheck compatibilityCheck
}

testNativeBootstrap() {
  sbt scalaFromEnv launcher-native_03/publishLocal launcher-native_040M2/publishLocal cli/pack
  modules/cli/target/pack/bin/coursier bootstrap -S -o native-echo io.get-coursier:echo_native0.3_2.11:1.0.1
  if [ "$(./native-echo -n foo a)" != "foo a" ]; then
    echo "Error: unexpected output from native test bootstrap." 1>&2
    exit 1
  fi
}

if isScalaJs; then
  jsCompile
  runJsTests
elif isScalaNative; then
  testNativeBootstrap
else
  jvmCompile

  runJvmTests

  checkBinaryCompatibility
fi

