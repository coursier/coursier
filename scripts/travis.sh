#!/usr/bin/env bash
set -ev

SCALA_VERSION="${SCALA_VERSION:-${TRAVIS_SCALA_VERSION:-2.12.1}}"
PULL_REQUEST="${PULL_REQUEST:-${TRAVIS_PULL_REQUEST:-false}}"
BRANCH="${BRANCH:-${TRAVIS_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}}"
PUBLISH="${PUBLISH:-0}"
SCALA_JS="${SCALA_JS:-0}"

JARJAR_VERSION="${JARJAR_VERSION:-1.0.1-coursier-SNAPSHOT}"

is210() {
  echo "$SCALA_VERSION" | grep -q "^2\.10"
}

is211() {
  echo "$SCALA_VERSION" | grep -q "^2\.11"
}

is212() {
  echo "$SCALA_VERSION" | grep -q "^2\.12"
}

setupCoursierBinDir() {
  mkdir -p bin
  cp coursier bin/
  export PATH="$(pwd)/bin:$PATH"
}

downloadInstallSbtExtras() {
  mkdir -p bin
  curl -L -o bin/sbt https://github.com/paulp/sbt-extras/raw/9ade5fa54914ca8aded44105bf4b9a60966f3ccd/sbt
  chmod +x bin/sbt
}

launchTestRepo() {
  ./scripts/launch-test-repo.sh "$@"
}

setupCsbtLauncher() {
  sbt ++2.12.1 coreJVM/publishLocal cache/publishLocal sbt-launcher/publishLocal
  scripts/generate-sbt-launcher.sh -f
  rm -rf ~/.sbt ~/.ivy2/cache
  mv csbt bin/
}

launchProxyRepos() {
  if [ "$(uname)" != "Darwin" ]; then
    ./scripts/launch-proxies.sh
  fi
}

integrationTestsRequirements() {
  # Required for ~/.ivy2/local repo tests
  csbt ++2.11.11 coreJVM/publishLocal cli/publishLocal

  csbt ++2.12.1 http-server/publishLocal

  # Required for HTTP authentication tests
  launchTestRepo --port 8080 --list-pages

  # Required for missing directory listing tests (no --list-pages)
  launchTestRepo --port 8081
}

isScalaJs() {
  [ "$SCALA_JS" = 1 ]
}

sbtCoursier() {
  [ "$SBT_COURSIER" = 1 ]
}

sbtShading() {
  [ "$SBT_SHADING" = 1 ]
}

runSbtCoursierTests() {
  addPgpKeys
  csbt ++$SCALA_VERSION sbt-plugins/publishLocal
  if [ "$SCALA_VERSION" = "2.10" ]; then
    csbt ++$SCALA_VERSION "sbt-coursier/scripted sbt-coursier/*" "sbt-coursier/scripted sbt-coursier-0.13/*"
  else
    csbt ++$SCALA_VERSION "sbt-coursier/scripted sbt-coursier/simple" # full scripted suite currently taking too long on Travis CI...
  fi
  csbt ++$SCALA_VERSION sbt-pgp-coursier/scripted
}

runSbtShadingTests() {
  csbt ++$SCALA_VERSION coreJVM/publishLocal cache/publishLocal extra/publishLocal sbt-coursier/publishLocal "sbt-shading/scripted sbt-shading/*"
  if [ "$SCALA_VERSION" = "2.10" ]; then
    csbt ++$SCALA_VERSION "sbt-shading/scripted sbt-shading-0.13/*"
  fi
}

jsCompile() {
  csbt ++$SCALA_VERSION js/compile js/test:compile coreJS/fastOptJS fetch-js/fastOptJS testsJS/test:fastOptJS js/test:fastOptJS
}

jvmCompile() {
  csbt ++$SCALA_VERSION jvm/compile jvm/test:compile
}

runJsTests() {
  csbt ++$SCALA_VERSION js/test
}

runJvmTests() {
  if [ "$(uname)" == "Darwin" ]; then
    IT="testsJVM/it:test" # don't run proxy-tests in particular
  else
    IT="jvm/it:test"
  fi

  csbt ++$SCALA_VERSION jvm/test $IT
}

validateReadme() {
  # check that tut runs fine, and that the README doesn't change after a `sbt tut`
  mv README.md README.md.orig

  csbt ++${SCALA_VERSION} tut

  if cmp -s README.md.orig README.md; then
    echo "README.md doesn't change"
  else
    echo "Error: README.md not the same after a \"sbt tut\":"
    diff -u README.md.orig README.md
    exit 1
  fi
}

validateReadme() {
  csbt ++${SCALA_VERSION} tut
}

checkBinaryCompatibility() {
  csbt ++${SCALA_VERSION} coreJVM/mimaReportBinaryIssues cache/mimaReportBinaryIssues
}

testLauncherJava6() {
  csbt ++${SCALA_VERSION} cli/pack
  docker run -it --rm \
    -v $(pwd)/cli/target/pack:/opt/coursier \
    -e CI=true \
    openjdk:6-jre \
      /opt/coursier/bin/coursier fetch org.scalacheck::scalacheck:1.13.4

  docker run -it --rm \
    -v $(pwd)/cli/target/pack:/opt/coursier \
    -e CI=true \
    openjdk:6-jre \
      /opt/coursier/bin/coursier launch --help
}

testSbtCoursierJava6() {
  csbt ++${SCALA_VERSION} coreJVM/publishLocal cache/publishLocal extra/publishLocal sbt-coursier/publishLocal

  git clone https://github.com/alexarchambault/scalacheck-shapeless.git
  cd scalacheck-shapeless
  cd project
  clean_plugin_sbt
  cd project
  clean_plugin_sbt
  cd ../..
  docker run -it --rm \
    -v $HOME/.ivy2/local:/root/.ivy2/local \
    -v $(pwd):/root/project \
    -v $(pwd)/../bin:/root/bin \
    -e CI=true \
    openjdk:6-jre \
      /bin/bash -c "cd /root/project && /root/bin/sbt update"
  cd ..

  # ensuring resolution error doesn't throw NoSuchMethodError
  mkdir -p foo/project
  cd foo
  echo 'libraryDependencies += "foo" % "bar" % "1.0"' >> build.sbt
  echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-SNAPSHOT")' >> project/plugins.sbt
  echo 'sbt.version=0.13.15' >> project/build.properties
  docker run -it --rm \
    -v $HOME/.ivy2/local:/root/.ivy2/local \
    -v $(pwd):/root/project \
    -v $(pwd)/../bin:/root/bin \
    -e CI=true \
    openjdk:6-jre \
      /bin/bash -c "cd /root/project && /root/bin/sbt update || true" | tee -a output
  grep "coursier.ResolutionException: Encountered 1 error" output
  echo "Ok, found ResolutionException in output"
  cd ..
}

clean_plugin_sbt() {
  mv plugins.sbt plugins.sbt0
  grep -v coursier plugins.sbt0 > plugins.sbt || true
  echo '
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-SNAPSHOT")
  ' >> plugins.sbt
}

publish() {
  csbt ++${SCALA_VERSION} publish
}

testBootstrap() {
  if is211; then
    csbt ++${SCALA_VERSION} echo/publishLocal cli/pack
    cli/target/pack/bin/coursier bootstrap -o cs-echo io.get-coursier:echo:1.0.0-SNAPSHOT
    if [ "$(./cs-echo foo)" != foo ]; then
      echo "Error: unexpected output from bootstrapped echo command." 1>&2
      exit 1
    fi
  fi
}

testNativeBootstrap() {
  if is211; then
    sbt ++${SCALA_VERSION} cli/pack
    cli/target/pack/bin/coursier bootstrap -S -o native-test io.get-coursier.scala-native::sandbox_native0.3:0.3.0-coursier-1
    if [ "$(./native-test)" != "Hello, World!" ]; then
      echo "Error: unexpected output from native test bootstrap." 1>&2
      exit 1
    fi
  fi
}

addPgpKeys() {
  for key in b41f2bce 9fa47a44 ae548ced b4493b94 53a97466 36ee59d9 dc426429 3b80305d 69e0a56c fdd5c0cd 35543c27 70173ee5 111557de 39c263a9; do
    gpg --keyserver keyserver.ubuntu.com --recv "$key"
  done
}


# TODO Add coverage once https://github.com/scoverage/sbt-scoverage/issues/111 is fixed

downloadInstallSbtExtras
setupCoursierBinDir

setupCsbtLauncher

if isScalaJs; then
  jsCompile
  runJsTests
else
  testNativeBootstrap

  integrationTestsRequirements
  jvmCompile

  if sbtCoursier; then
    if is210 || is212; then
      runSbtCoursierTests
    fi

    if is210; then
      testSbtCoursierJava6
    fi
  elif sbtShading; then
    if is210 || is212; then
      runSbtShadingTests
    fi
  else
    # Required for the proxy tests (currently CentralNexus2ProxyTests and CentralNexus3ProxyTests)
    launchProxyRepos

    runJvmTests

    testBootstrap

    validateReadme
    checkBinaryCompatibility

    if is211; then
      testLauncherJava6
    fi
  fi

  # Not using a jdk6 matrix entry with Travis as some sources of coursier require Java 7 to compile
  # (even though it won't try to call Java 7 specific methods if it detects it runs under Java 6).
  # The tests here check that coursier is nonetheless fine when run under Java 6.
fi


if [ "$PUBLISH" = 1 -a "$PULL_REQUEST" = false -a "$BRANCH" = master ]; then
  publish

  if is211 && isScalaJs; then
    #"$(dirname "$0")/push-gh-pages.sh" "$SCALA_VERSION"
    :
  fi
fi

