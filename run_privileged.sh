#!/bin/bash
#
# Adapted from github.com/android/appfunctions (agent/run_privileged.sh, startAppFunctionTestingAgent).
# Copyright 2026 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
#
# Builds and installs the NanoBot debug APK, then starts it through ShellIdentityInstrumentation,
# so NanoBot holds android.permission.EXECUTE_APP_FUNCTIONS.
#
# Usage: ./run_privileged.sh [-b|--build] [-s|--serial SERIAL]

set -e

PACKAGE_NAME="com.hrithikvish.nanobot"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
REBUILD=false
SERIAL=""

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -b|--build) REBUILD=true ;;
        -s|--serial) SERIAL="$2"; shift ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

ADB="adb"
[[ -n "$SERIAL" ]] && ADB="adb -s $SERIAL"

if [[ "$REBUILD" = true || ! -f "$APK_PATH" ]]; then
    ./gradlew :app:assembleDebug
fi
$ADB install -r "$APK_PATH"

SDK_VERSION=$($ADB shell getprop ro.build.version.sdk | tr -d '\r')
if [[ $SDK_VERSION -ge 37 ]]; then
    # Android 17+ executes AppFunctions only for allowlisted callers.
    FINGERPRINT=$($ADB shell dumpsys package $PACKAGE_NAME | grep PackageSignatures | awk -F 'signatures:\\[' '{print $2}' | awk -F '\\]' '{print $1}' | tr '[:upper:]' '[:lower:]')
    if [[ -z "$FINGERPRINT" ]]; then
        echo "Could not read the signing fingerprint of $PACKAGE_NAME."
        exit 1
    fi
    if $ADB shell cmd app_function help 2>&1 | grep -q "purge-allowlist-cache"; then
        $ADB shell cmd app_function purge-allowlist-cache
    fi
    if $ADB shell cmd allowlist help 2>&1 | grep -q "add-package-with-metadata-multimap"; then
        $ADB shell cmd allowlist add-package-with-metadata-multimap 2 ${PACKAGE_NAME}:${FINGERPRINT} android.allowlist.metadata.key.APP_FUNCTION_TYPE_DOWNLOADED:boolean:true "'*'" ignore:string:default
    else
        $ADB shell cmd allowlist add-package-multimap 2 ${PACKAGE_NAME}:${FINGERPRINT} "'*'"
    fi
    if $ADB shell cmd app_function help 2>&1 | grep -q "set-temporary-caller"; then
        $ADB shell cmd app_function flush-allowlist-changes
    fi
fi

$ADB shell am force-stop "$PACKAGE_NAME"

# The shell delegates its permissions to one instrumentation at a time. Stop the others,
# for example the AppFunctions sample agent.
for OTHER in $($ADB shell dumpsys activity processes | grep -o 'ActiveInstrumentation{[^}]*{[^/]*' | sed 's/.*{//' | sort -u); do
    if [[ "$OTHER" != "$PACKAGE_NAME" ]]; then
        echo "Stopping $OTHER, which holds the shell permissions."
        $ADB shell am force-stop "$OTHER"
    fi
done

echo "Starting NanoBot with the shell identity. Keep this terminal open; Ctrl+C stops the privileged session."
$ADB shell am instrument -w "${PACKAGE_NAME}/.ShellIdentityInstrumentation"
