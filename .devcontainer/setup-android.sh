#!/bin/bash

set -e

ANDROID_SDK_ROOT=/opt/android-sdk

sudo mkdir -p "$ANDROID_SDK_ROOT"
sudo chown -R vscode:vscode "$ANDROID_SDK_ROOT"

cd /tmp

# Download Android command-line tools
wget -q https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip

mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

unzip -q commandlinetools-linux-13114758_latest.zip \
    -d "$ANDROID_SDK_ROOT/cmdline-tools"

mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" \
   "$ANDROID_SDK_ROOT/cmdline-tools/latest"

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Accept Android licenses
yes | sdkmanager --licenses >/dev/null || true

# Install required Android packages
sdkmanager \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0"

echo ""
echo "========================================="
echo "Android SDK installation complete"
echo "========================================="

java -version
adb --version
sdkmanager --version