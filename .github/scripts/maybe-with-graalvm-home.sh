#!/usr/bin/env bash
set -e

# GraalVM freezes the image builder's encoding-related system properties into the image it
# produces - file.encoding, sun.jnu.encoding, native.encoding. A builder running under a
# non-UTF-8 locale therefore bakes that encoding in, and the resulting launchers mangle
# non-ASCII arguments and environment variables at run time whatever locale the user has.
# Windows derives those properties from the code page rather than from a locale, so there is
# nothing to pick there.
#
# C.UTF-8 comes first because it is built into glibc; en_US.UTF-8 is the macOS spelling, and
# on glibc it has to be generated first and silently falls back to ASCII when it has not
# been. Hence checking what the locale actually resolves to rather than trusting the name.
if [[ "$OSTYPE" != msys* && "$OSTYPE" != mingw* && "$OSTYPE" != cygwin* ]]; then
  if [ "$(locale charmap 2>/dev/null)" != "UTF-8" ]; then
    for candidate in C.UTF-8 en_US.UTF-8; do
      if [ "$(LC_ALL="$candidate" locale charmap 2>/dev/null)" = "UTF-8" ]; then
        export LANG="$candidate"
        export LC_ALL="$candidate"
        echo "Using LC_ALL=$candidate so the image builder bakes in a UTF-8 encoding"
        break
      fi
    done
  fi

  if [ "$(locale charmap 2>/dev/null)" != "UTF-8" ]; then
    echo "Error: no UTF-8 locale available - launchers built here would mangle non-ASCII values" >&2
    exit 1
  fi
fi

if [[ "$OSTYPE" == msys* || "$OSTYPE" == mingw* || "$OSTYPE" == cygwin* ]]; then
  if [[ ! -e jvm ]]; then
    ./mill.bat -i ci.copyJvm --dest jvm
  fi
  export GRAALVM_HOME="$(pwd -W | sed 's,/,\\,g')\\jvm"
  echo "GRAALVM_HOME=$GRAALVM_HOME"

  ./mill.bat -i "$@"
else
  ./mill -i "$@"
fi
