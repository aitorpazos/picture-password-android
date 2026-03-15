#!/bin/bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
cd /Users/aitor/zeroclaw-agents/personal/workspace/picture-password-android

echo "=== RUNNING TESTS ==="
./gradlew testDebugUnitTest 2>&1
TEST_EXIT=$?
echo "=== TEST EXIT: $TEST_EXIT ==="

if [ $TEST_EXIT -eq 0 ]; then
    echo "=== BUILDING DEBUG APK ==="
    ./gradlew assembleDebug 2>&1
    BUILD_EXIT=$?
    echo "=== BUILD EXIT: $BUILD_EXIT ==="
    
    if [ $BUILD_EXIT -eq 0 ]; then
        APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
        if [ -n "$APK" ]; then
            cp "$APK" /Users/aitor/zeroclaw-agents/personal/workspace/picture-password-debug.apk
            echo "=== APK COPIED ==="
        fi
    fi
fi
echo "=== DONE ==="
