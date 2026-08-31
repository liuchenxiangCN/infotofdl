#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FDL_HOME="${FDL_HOME:-/mnt/d/FDL-tomcat-win64}"
PLUGIN_VERSION="${PLUGIN_VERSION:-0.5.7}"
PLUGIN_ID="com.fr.plugin.tptj.ivan.jsd19083"
PLUGIN_NAME="fine-plugin-${PLUGIN_ID}-${PLUGIN_VERSION}"
PLUGIN_ZIP="$REPO_ROOT/plugin-src/${PLUGIN_NAME}.zip"
JAVAC="$FDL_HOME/jdk/bin/javac.exe"
JAVA="$FDL_HOME/jdk/bin/java.exe"

if [ ! -f "$PLUGIN_ZIP" ]; then
  echo "plugin zip not found: $PLUGIN_ZIP" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
unzip -q -j "$PLUGIN_ZIP" "${PLUGIN_NAME}.jar" -d "$TMP_DIR"

to_windows_path() {
  if command -v wslpath >/dev/null 2>&1; then
    wslpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

SOURCE_WIN="$(to_windows_path "$SCRIPT_DIR/FdlNativeConditionProbe.java")"
TMP_WIN="$(to_windows_path "$TMP_DIR")"
PLUGIN_JAR_WIN="$(to_windows_path "$TMP_DIR/${PLUGIN_NAME}.jar")"
FDL_LIB_WIN="$(to_windows_path "$FDL_HOME/webapps/webroot/WEB-INF/lib")"
CLASSPATH="${FDL_LIB_WIN}/*;${PLUGIN_JAR_WIN}"

"$JAVAC" -encoding UTF-8 -proc:none -cp "$CLASSPATH" -d "$TMP_WIN" "$SOURCE_WIN"
"$JAVA" -cp "$CLASSPATH;$TMP_WIN" FdlNativeConditionProbe
