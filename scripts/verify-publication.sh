#!/usr/bin/env bash
# Verifies the Maven Central publication setup without credentials: publishes to a throwaway
# local Maven repository, checks the POMs, and compiles a tiny consumer project against the
# published artifacts only (this is what catches `implementation` vs `api` scope mistakes).
set -euo pipefail
cd "$(dirname "$0")/.."

REPO="$(mktemp -d)"
CONSUMER="$(mktemp -d)"
trap 'rm -rf "$REPO" "$CONSUMER"' EXIT
VERSION="$(./gradlew -q properties --no-configuration-cache | sed -n 's/^version: //p' | head -1)"

./gradlew clean publishToMavenLocal --no-configuration-cache -Dmaven.repo.local="$REPO" -q

for m in ktriz-core ktriz-render-kuml ktriz-script ktriz-mcp; do
  pom="$REPO/dev/ktriz/$m/$VERSION/$m-$VERSION.pom"
  [ -f "$pom" ] || { echo "missing POM: $pom"; exit 1; }
done

mcp_pom="$REPO/dev/ktriz/ktriz-mcp/$VERSION/ktriz-mcp-$VERSION.pom"
if grep -q "slf4j-simple" "$mcp_pom"; then
  echo "ktriz-mcp POM must not carry a SLF4J backend"; exit 1
fi
# api dependencies must be compile scope in the POM.
python3 - "$mcp_pom" <<'P'
import sys, re
x = open(sys.argv[1]).read()
for art in ("ktriz-core", "kotlin-sdk-server-jvm", "kotlinx-serialization-json-jvm"):
    m = re.search(r"<artifactId>%s</artifactId>.*?<scope>(\w+)</scope>" % re.escape(art), x, re.S)
    if not m or m.group(1) != "compile":
        sys.exit("ktriz-mcp POM: %s is not compile scope (%s)" % (art, m and m.group(1)))
P

cat > "$CONSUMER/settings.gradle.kts" <<S
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositories { maven(url = uri("$REPO")); mavenCentral() }
}
rootProject.name = "consumer"
S
KOTLIN_VERSION="$(sed -n 's/^kotlin *= *"\(.*\)"/\1/p' gradle/libs.versions.toml | head -1)"
cat > "$CONSUMER/build.gradle.kts" <<B
plugins { kotlin("jvm") version "$KOTLIN_VERSION" }
kotlin { jvmToolchain(21) }
dependencies {
    implementation("dev.ktriz:ktriz-mcp:$VERSION")
    implementation("dev.ktriz:ktriz-script:$VERSION")
}
B
mkdir -p "$CONSUMER/src/main/kotlin"
cat > "$CONSUMER/src/main/kotlin/Consumer.kt" <<'K'
import dev.ktriz.mcp.buildServer
import io.modelcontextprotocol.kotlin.sdk.server.Server

fun main() {
    val server: Server = buildServer()
    println(server)
}
K
cp gradlew "$CONSUMER/"; cp -r gradle "$CONSUMER/"
(cd "$CONSUMER" && ./gradlew compileKotlin --no-daemon -q)
echo "Publication verification OK"
