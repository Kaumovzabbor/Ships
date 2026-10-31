#!/usr/bin/env sh

# Small Gradle wrapper bootstrapper. The Gradle distribution is pinned in
# gradle/wrapper/gradle-wrapper.properties (8.9) and cached by Gradle itself.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
GRADLE_VERSION="8.9"
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
GRADLE_HOME="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin/manual"
GRADLE_BIN="$GRADLE_HOME/gradle-${GRADLE_VERSION}/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$GRADLE_HOME"
  ZIP="$GRADLE_HOME/gradle-${GRADLE_VERSION}-bin.zip"
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "curl or wget is required to bootstrap Gradle." >&2
    exit 1
  fi
  unzip -q -o "$ZIP" -d "$GRADLE_HOME"
fi
exec "$GRADLE_BIN" "$@"
