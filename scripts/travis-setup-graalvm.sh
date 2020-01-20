export JABBA_HOME="$HOME/.jabba"

if [[ "$TRAVIS_OS_NAME" == "windows" ]]; then

  choco install -y windows-sdk-7.1 vcbuildtools kb2519277

  # temporary workaround for https://github.com/oracle/graal/issues/1876
  # (should be fixed in GraalVM 20.0)
  cp "/C/Program Files/Microsoft SDKs/Windows/v7.1/Lib/x64/advapi32.lib" "/C/Program Files/Microsoft SDKs/Windows/v7.1/Lib/x64/stdc++.lib"

  curl -Lo graalvm.zip https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.1/graalvm-ce-java8-windows-amd64-19.3.1.zip
  unzip graalvm.zip
  rm -f graalvm.zip
  JAVA_HOME="$(pwd)/graalvm-ce-java8-19.3.1"

else


  if [[ "$(uname)" == "Darwin" ]]; then
    JDK_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.1/graalvm-ce-java8-darwin-amd64-19.3.1.tar.gz"
  else
    JDK_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.1/graalvm-ce-java8-linux-amd64-19.3.1.tar.gz"
  fi

  curl -sL https://raw.githubusercontent.com/shyiko/jabba/0.11.2/install.sh | bash
  source "$HOME/.jabba/jabba.sh"
  "$JABBA_HOME/bin/jabba" install "graalvm@19.3.0-8=tgz+$JDK_URL"
  JAVA_HOME="$JABBA_HOME/jdk/graalvm@19.3.0-8"

  if [[ "$(uname)" == "Darwin" ]]; then
    JAVA_HOME="$JAVA_HOME/Contents/Home"
  fi

fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
