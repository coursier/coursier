
if [[ "$TRAVIS_OS_NAME" == "windows" ]]; then
  choco install -y windows-sdk-7.1 vcbuildtools kb2519277
fi

curl -L https://raw.githubusercontent.com/coursier/ci-scripts/master/setup.sh | bash
eval "$(./cs java --env --jvm graalvm-ce-java8:20.1.0)"
rm -f cs cs.exe
