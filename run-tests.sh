#!/bin/bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
cd /Users/aitor/zeroclaw-agents/personal/workspace/picture-password-android
./gradlew testDebugUnitTest --stacktrace 2>&1
echo "---EXIT: $?---"
