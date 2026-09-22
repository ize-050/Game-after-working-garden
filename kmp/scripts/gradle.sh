#!/bin/bash
set -euo pipefail
FARM_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Project-local launch environment: never changes the shell profile or system JDK.
FARM_JDK="${LITTLEFARM_JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
if [ ! -x "$FARM_JDK/bin/java" ]; then
  FARM_JDK="${JAVA_HOME:-}"
fi
if [ ! -x "$FARM_JDK/bin/java" ]; then
  echo 'Set LITTLEFARM_JAVA_HOME to a JDK 21 directory, or install Android Studio with its bundled JBR.' >&2
  exit 1
fi
cd "$FARM_ROOT"
FARM_NATIVE=false
case "${SDK_NAME:-}" in
  iphone*) set -- -Plittlefarm.iosOnly=true "$@"; FARM_NATIVE=true ;;
esac
for FARM_ARG in "$@"; do
  case "$FARM_ARG" in
    *link*Ios*|*compileKotlinIos*|*embedAndSignAppleFrameworkForXcode|build|assemble|allTests) FARM_NATIVE=true ;;
  esac
done
if [ "$FARM_NATIVE" = true ]; then
  FARM_FREE_KB="$(df -Pk "$FARM_ROOT" | awk 'NR == 2 {print $4}')"
  if [ "$FARM_FREE_KB" -lt 5242880 ] && [ "${LITTLEFARM_ALLOW_LOW_DISK:-0}" != 1 ]; then
    echo 'Native build paused: less than 5 GiB free. Free disk space first (10–20 GB recommended for SDK/runtime/build caches).' >&2
    echo 'No native toolchain download was started. See README.md.' >&2
    exit 2
  fi
fi
exec env JAVA_HOME="$FARM_JDK" \
  GRADLE_USER_HOME="${LITTLEFARM_GRADLE_HOME:-$FARM_ROOT/.tooling/gradle-home}" \
  KONAN_DATA_DIR="${LITTLEFARM_KONAN_HOME:-$FARM_ROOT/.tooling/konan}" \
  "$FARM_ROOT/gradlew" "$@"
