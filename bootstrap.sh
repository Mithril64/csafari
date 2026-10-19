#!/usr/bin/env bash
# Generates the real Gradle wrapper (including gradle-wrapper.jar) without
# requiring Gradle to be installed. Run once, then use ./gradlew as normal.
set -euo pipefail

GRADLE_VERSION="9.4.1"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
CACHE_DIR="${HOME}/.gradle-bootstrap"
GRADLE_HOME="${CACHE_DIR}/gradle-${GRADLE_VERSION}"

cd "$(dirname "$0")"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  mkdir -p "${CACHE_DIR}"
  ZIP="${CACHE_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --progress-bar -o "${ZIP}" "${DIST_URL}"
  elif command -v wget >/dev/null 2>&1; then
    wget -q --show-progress -O "${ZIP}" "${DIST_URL}"
  else
    echo "Need curl or wget." >&2
    exit 1
  fi
  unzip -q -o "${ZIP}" -d "${CACHE_DIR}"
  rm -f "${ZIP}"
fi

echo "Generating wrapper..."
"${GRADLE_HOME}/bin/gradle" wrapper --gradle-version "${GRADLE_VERSION}"

echo
echo "Done. Now run:  ./gradlew build"
echo "The jar will be in build/libs/"
