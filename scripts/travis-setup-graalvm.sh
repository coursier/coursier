export JABBA_HOME="$HOME/.jabba" GRAALVM_JDK="graalvm@19.0.2"
if [[ "$TRAVIS_OS_NAME" == "windows" ]]; then
  # doesn't work yet (even locally)

  choco install -y mingw windows-sdk-7.1 vcbuildtools
  # find "/C/Program Files/Microsoft SDKs/Windows/v7.1" | sort
  export PATH="/C/Program Files/Microsoft SDKs/Windows/v7.1/Bin/x64:/C/Program Files/Microsoft SDKs/Windows/v7.1/Bin:$PATH"
  curl -Lo graalvm.zip https://github.com/oracle/graal/releases/download/vm-19.1.1/graalvm-ce-windows-amd64-19.1.1.zip
  unzip graalvm.zip
  GRAALVM_HOME="$(pwd)/graalvm-ce-19.1.1"

  # no JDK on Travis CI Windows, using graal's java too
  export PATH="$GRAALVM_HOME/bin:$PATH"
  export NATIVE_IMAGE="$GRAALVM_HOME/bin/native-image.cmd"
else
  curl -sL https://raw.githubusercontent.com/shyiko/jabba/0.11.2/install.sh | bash
  source "$HOME/.jabba/jabba.sh"
  "$JABBA_HOME/bin/jabba" install "$GRAALVM_JDK"
  GRAALVM_HOME="$JABBA_HOME/jdk/$GRAALVM_JDK"

  if [[ "$(uname)" == "Darwin" ]]; then
    GRAALVM_HOME="$GRAALVM_HOME/Contents/Home"
  fi

  "$GRAALVM_HOME/bin/gu" install native-image
fi

export GRAALVM_HOME
