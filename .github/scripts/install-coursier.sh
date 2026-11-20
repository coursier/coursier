#!/bin/bash

# download cs binary launcher for posix shell environments

set -o nounset errexit

case `uname` in
Darwin*)
  set -x
  case `uname -m` in
  arm64*)
    # Apple Silicon (M1, M2, ...):
    curl -fL https://github.com/VirtusLab/coursier-m1/releases/latest/download/cs-aarch64-apple-darwin.gz | gzip -d > cs
    ;;
  x86_64*)
    curl -fL https://github.com/coursier/launchers/raw/master/cs-x86_64-apple-darwin.gz | gzip -d > cs
    ;;
  esac
  chmod +x cs
  cs setup
  ;;
Linux*)
  if [ `uname -r | grep WSL` ]; then
    # might need to disable ipv6 for this to work in WSL
    if [ 1 -eq 2 ]; then
      # disable ipv6
      sudo sysctl net.ipv6.conf.all.autoconf=0
      sudo sysctl net.ipv6.conf.all.disable_ipv6=1
      sudo sysctl net.ipv6.conf.default.accept_ra=0
    fi
  fi
  set -x
  curl -fL https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz | gzip -d > cs
  chmod +x cs
  ./cs setup
  ;;
*)
  # shell environment: download Windows cs.exe
  if [[ "${OS-}" == "Windows_NT" ]]; then
    TARBALL="cs-x86_64-pc-win32.zip"
    CSx64="cs-x86_64-pc-win32.exe"
    set -x
    curl -fLo "$TARBALL" "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.zip"
    jar -xf "$TARBALL"
    rm "$TARBALL"
    mv "$CSx64" "cs.exe"
    ./cs setup
  else
    echo "unrecognized environment" 1>&2
    echo "see https://get-coursier.io/docs/cli-installation"
    exit 1
  fi
  ;;
esac

