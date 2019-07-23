#!/usr/bin/env bash
set -eu

BASE="$(dirname "${BASH_SOURCE[0]}")"

TRANSIENT_ASSEMBLY=false

if [[ -z "${ASSEMBLY:-}" ]]; then
  # TRANSIENT_ASSEMBLY=true
  ASSEMBLY="coursier-assembly"

  # Set that one too once it's published
  # MODULE="coursier-cli-graalvm"
  OUTPUT="$ASSEMBLY" "$BASE/generate-launcher.sh" --assembly
fi

cleanup() {
  [[ "$TRANSIENT_ASSEMBLY" == false ]] || rm -f "$ASSEMBLY"
}

trap cleanup EXIT INT TERM


if [[ "${TEST_RUN:-false}" == true ]]; then
  echo "Test run, not generating graalvm image" 1>&2
  exit 0
fi

OUTPUT="${OUTPUT:-"coursier-graalvm"}"

NATIVE_IMAGE="${NATIVE_IMAGE:-"$GRAALVM_HOME/bin/native-image"}"

JAVA_SECURITY_PROPS="$BASE/java.security.overrides"

if [[ ! -f "$JAVA_SECURITY_PROPS" ]]; then
  echo "$JAVA_SECURITY_PROPS not found." 1>&2
  exit 1
fi

"$NATIVE_IMAGE" \
  --no-server \
  --enable-http \
  --enable-https \
  --no-fallback \
  -H:EnableURLProtocols=http,https \
  --enable-all-security-services \
  -H:ReflectionConfigurationFiles="$BASE/reflection.json" \
  -H:+ReportExceptionStackTraces \
  --initialize-at-build-time=scala.Symbol \
  --initialize-at-build-time=scala.Function2 \
  --initialize-at-build-time=scala.runtime.StructuralCallSite \
  --initialize-at-build-time=scala.runtime.EmptyMethodCache \
  --initialize-at-build-time=coursier.util.Properties\$ \
  --initialize-at-build-time=org.jline.utils.InputStreamReader \
  --initialize-at-build-time=org.jline.utils.InfoCmp \
  --initialize-at-build-time=coursier.cli.CoursierGraalvm \
  --allow-incomplete-classpath \
  -J-D"java.security.properties=$JAVA_SECURITY_PROPS" \
  -D"java.security.properties=$JAVA_SECURITY_PROPS" \
  -jar "$ASSEMBLY" \
  "$OUTPUT"

if [ -e "$OUTPUT.exe" -a ! -e "$OUTPUT" ]; then
  mv "$OUTPUT.exe" "$OUTPUT"
fi

echo "Generate graalvm native image $OUTPUT."
