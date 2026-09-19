#!/usr/bin/env bash
# Compiles and runs the pipeline against fixtures/corpus-a.jsonl.
# Needs a JDK 21 and nothing else - no network, no database, no Gradle.
set -euo pipefail
cd "$(dirname "$0")"

echo "==> compiling"
rm -rf build/selfcheck && mkdir -p build/selfcheck
# The Mongo adapter is compiled and tested by Gradle with its declared driver.
# Keep this smoke path dependency-free so it also works offline.
javac -d build/selfcheck $(find src/main/java -name '*.java' ! -name 'MongoDocumentStore.java' ! -name 'App.java')

echo
echo "==> running"
java -cp build/selfcheck in.simplifymoney.ledgersync.SelfCheck "$@"
