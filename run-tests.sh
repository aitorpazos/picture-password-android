#!/bin/bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
cd "$(dirname "$0")"
./gradlew testDebugUnitTest 2>&1 | tail -30
