#!/bin/bash
set -e

SRC_DIR="src"
BUILD_DIR="build"
PLUGIN_ID="com.fr.plugin.tptj.ivan.jsd19083"
PLUGIN_VERSION="${PLUGIN_VERSION:-0.5.5}"
PLUGIN_NAME="fine-plugin-${PLUGIN_ID}-${PLUGIN_VERSION}"
FDL_HOME="${FDL_HOME:-/mnt/d/FDL-tomcat-win64}"
SOURCE_PLUGIN_VERSION="${SOURCE_PLUGIN_VERSION:-0.3.0}"
SOURCE_PLUGIN_NAME="${SOURCE_PLUGIN_NAME:-fine-plugin-${PLUGIN_ID}-${SOURCE_PLUGIN_VERSION}}"
INSTALLED_PLUGIN_DIR="${INSTALLED_PLUGIN_DIR:-$FDL_HOME/webapps/webroot/WEB-INF/plugins/plugin-${PLUGIN_ID}-${SOURCE_PLUGIN_VERSION}}"
JDK_BIN="${JDK_BIN:-$FDL_HOME/jdk/bin}"
JAVAC="${JAVAC:-$JDK_BIN/javac.exe}"
JAR="${JAR:-$JDK_BIN/jar.exe}"

cd "$(dirname "$0")"

to_windows_path() {
  if command -v wslpath >/dev/null 2>&1 && [[ "$1" == /mnt/* ]]; then
    wslpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

if [ ! -x "$JAVAC" ]; then
  echo "javac not found: $JAVAC"
  exit 1
fi

if [ ! -x "$JAR" ]; then
  echo "jar not found: $JAR"
  exit 1
fi

if [ ! -f "pluginkit-10.0-20231122.jar" ] && [ -f "$INSTALLED_PLUGIN_DIR/pluginkit-10.0-20231122.jar" ]; then
  cp "$INSTALLED_PLUGIN_DIR/pluginkit-10.0-20231122.jar" .
fi

if [ ! -d "jar-contents" ] && [ -f "$INSTALLED_PLUGIN_DIR/$SOURCE_PLUGIN_NAME.jar" ]; then
  mkdir -p jar-contents
  unzip -q "$INSTALLED_PLUGIN_DIR/$SOURCE_PLUGIN_NAME.jar" -d jar-contents
fi

if [ ! -d "jar-contents" ]; then
  echo "jar-contents not found. Set INSTALLED_PLUGIN_DIR or extract the original plugin jar first."
  exit 1
fi

if [ ! -f "pluginkit-10.0-20231122.jar" ]; then
  echo "pluginkit-10.0-20231122.jar not found. Set INSTALLED_PLUGIN_DIR or copy it into plugin-src."
  exit 1
fi

# Build classpath using relative paths (javac on Windows needs this)
CP="$BUILD_DIR/classes;$BUILD_DIR/original-plugin.jar"
for jar in lib/*.jar; do
  if [ -e "$jar" ]; then
    CP="$CP;$jar"
  fi
done
CP="$CP;pluginkit-10.0-20231122.jar"

if [ -d "$FDL_HOME/webapps/webroot/WEB-INF/lib" ]; then
  CP="$CP;$(to_windows_path "$FDL_HOME/webapps/webroot/WEB-INF/lib")/*"
fi

if [ -d "$FDL_HOME/lib" ]; then
  CP="$CP;$(to_windows_path "$FDL_HOME/lib")/*"
fi

# Step 1: Create original JAR reference (from jar-contents)
if [ ! -f "$BUILD_DIR/original-plugin.jar" ]; then
  mkdir -p "$BUILD_DIR"
  "$JAR" cf "$BUILD_DIR/original-plugin.jar" -C jar-contents .
fi

mkdir -p "$BUILD_DIR/classes"

# Step 2: Compile modified sources
# Core parsing files (no servlet API dependency)
echo "Compiling core parsing files..."
"$JAVAC" -encoding UTF-8 -cp "$CP" -d "$BUILD_DIR/classes" \
  src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/GroupSQLInfo.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/WriterTargetSchemaPolicy.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/WorkFlowParser.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/util/XmlParserUtil.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/util/DBFieldTransferUtils.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/util/FineUtils.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/LifecycleMonitorEK.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/service/FDLService.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/AbstractParseNodeBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/constant/PluginConstants.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/Link.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/FdlWriteConfigHelper.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/InfaScopedParameterResolver.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/ConditionNodeBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/InvokeTaskNodeBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/VirtualNodeBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/SyncTaskNodeBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/TransformSyncTaskNodeBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/ApiSyncTaskNodeBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/ParamAssignNodeBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/ConnectionMappingInfo.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/DBNodeToInfoBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/DataFlowTransformInfo.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/ExpressionFieldInfo.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/FilterFieldInfo.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/FieldLineageInfo.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/LookupFieldInfo.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/TargetWriteInfo.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/api/ApiNodeFromInfoBean.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/udf/InfaSoapHttpStatusUdf.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/udf/InfaRowApiUdfProvider.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/parse/DataSyncParseNodeBean.java

# Service layer files (may need servlet API - continue on failure)
echo "Compiling service files..."
SERVICE_SOURCES=()
for service_source in \
  src/com/fr/plugin/tptj/ivan/jsd19083/service/impl/AbstractPluginServiceImpl.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/service/impl/PluginServiceImpl.java \
  src/com/fr/plugin/tptj/ivan/jsd19083/service/impl/XMLFileUploadService.java
do
  if [ -f "$service_source" ]; then
    SERVICE_SOURCES+=("$service_source")
  fi
done

if [ ${#SERVICE_SOURCES[@]} -gt 0 ]; then
  "$JAVAC" -encoding UTF-8 -cp "$CP" -d "$BUILD_DIR/classes" "${SERVICE_SOURCES[@]}" \
    2>/dev/null || echo "  (service compilation skipped - using existing class files)"
else
  echo "  (no service source files present - using existing class files)"
fi

if [ $? -ne 0 ]; then
  echo "Compilation failed"
  exit 1
fi

# Step 3: Update JAR with new class files
cp "$BUILD_DIR/original-plugin.jar" "$BUILD_DIR/$PLUGIN_NAME.jar"
"$JAR" uf "$BUILD_DIR/$PLUGIN_NAME.jar" -C "$BUILD_DIR/classes" .

# Step 4: Package final ZIP
cp plugin.xml "$BUILD_DIR/"
cd "$BUILD_DIR"
"$JAR" cfM "../$PLUGIN_NAME.zip" plugin.xml "$PLUGIN_NAME.jar" ../pluginkit-10.0-20231122.jar
cd ..
echo "Built: $PLUGIN_NAME.zip"
