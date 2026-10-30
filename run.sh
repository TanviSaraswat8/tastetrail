#!/bin/sh
# TasteTrail - compile and start the server on http://localhost:8080 (macOS / Linux)
cd "$(dirname "$0")" || exit 1
mkdir -p out data
javac -encoding UTF-8 -d out -cp "lib/*" $(find src/main/java -name "*.java") || exit 1
exec java -cp "out:lib/*" com.tastetrail.Main "$@"
