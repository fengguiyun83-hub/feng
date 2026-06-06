#!/bin/sh
# ----------------------------------------------------------------------------
# Lightweight Maven Wrapper for this project. It downloads the pinned Maven
# distribution declared in .mvn/wrapper/maven-wrapper.properties when needed.
# ----------------------------------------------------------------------------

set -e

BASE_DIR="${MAVEN_BASEDIR:-$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)}"
MAVEN_USER_HOME="${MAVEN_USER_HOME:-"$HOME/.m2"}"
MAVEN_VERSION="3.9.9"
WRAPPER_PROPS="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_HOME="$MAVEN_USER_HOME/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -x "$MAVEN_BIN" ]; then
  DISTRIBUTION_URL=$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS" | sed 's/\\:/:/g')
  ZIP="$MAVEN_USER_HOME/wrapper/dists/apache-maven-$MAVEN_VERSION.zip"
  TMP="$MAVEN_USER_HOME/wrapper/dists/apache-maven-$MAVEN_VERSION-tmp"

  mkdir -p "$(dirname "$ZIP")" "$MAVEN_HOME"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DISTRIBUTION_URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$DISTRIBUTION_URL" -O "$ZIP"
  else
    echo "Neither curl nor wget is available to download Maven." >&2
    exit 1
  fi

  rm -rf "$TMP"
  mkdir -p "$TMP"
  unzip -q "$ZIP" -d "$TMP"
  cp -R "$TMP"/apache-maven-"$MAVEN_VERSION"/* "$MAVEN_HOME"/
  rm -rf "$TMP"
fi

exec "$MAVEN_BIN" "$@"
