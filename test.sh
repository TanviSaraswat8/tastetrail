#!/bin/sh
# Compile and run the unit tests (macOS / Linux)
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -encoding UTF-8 -d out -cp "lib/*" $(find src -name "*.java") || exit 1
exec java -cp "out:lib/*" com.tastetrail.CoreTest
