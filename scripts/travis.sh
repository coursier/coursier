#!/usr/bin/env bash
set -evx

setupCoursierBinDir() {
  mkdir -p bin
  cp coursier bin/
  export PATH="$(pwd)/bin:$PATH"
}

integrationTestsRequirements() {
  # Required for ~/.ivy2/local repo tests
  sbt 'set version in ThisBuild := "0.1.2-publish-local"' scala212 utilJVM/publishLocal coreJVM/publishLocal cli/publishLocal
}

isScalaJs() {
  [ "$SCALA_JS" = 1 ]
}

isScalaNative() {
  [ "$NATIVE" = 1 ]
}

bootstrap() {
  [ "$BOOTSTRAP" = 1 ]
}

jsCompile() {
  sbt scalaFromEnv js/compile js/test:compile coreJS/fastOptJS cacheJS/fastOptJS testsJS/test:fastOptJS js/test:fastOptJS
}

jvmCompile() {
  sbt scalaFromEnv jvm/compile jvm/test:compile
}

runJsTests() {
  sbt scalaFromEnv js/test
}

runJvmTests() {
  if [ "$(uname)" == "Darwin" ]; then
    IT="testsJVM/it:test" # don't run proxy-tests in particular
  else
    IT="jvm/it:test"
  fi

  ./scripts/with-redirect-server.sh \
    ./modules/tests/handmade-metadata/scripts/with-test-repo.sh \
    sbt scalaFromEnv jvm/test $IT
}

checkBinaryCompatibility() {
  sbt scalaFromEnv coreJVM/mimaReportBinaryIssues cacheJVM/mimaReportBinaryIssues
}

testBootstrap() {
  scripts/test-bootstrap.sh
}

testNativeBootstrap() {
  sbt scalaFromEnv cli-native_03/publishLocal cli-native_040M2/publishLocal cli/pack
  modules/cli/target/pack/bin/coursier bootstrap -S -o native-echo io.get-coursier:echo_native0.3_2.11:1.0.1
  if [ "$(./native-echo -n foo a)" != "foo a" ]; then
    echo "Error: unexpected output from native test bootstrap." 1>&2
    exit 1
  fi
}

setupCoursierBinDir

if isScalaJs; then
  jsCompile
  runJsTests
elif isScalaNative; then
  testNativeBootstrap
elif bootstrap; then
  testBootstrap
else
  integrationTestsRequirements
  jvmCompile

  runJvmTests

  checkBinaryCompatibility
fi

