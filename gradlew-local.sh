#!/bin/bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
exec "$(dirname "$0")/gradlew" "$@"
