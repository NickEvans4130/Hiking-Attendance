#!/usr/bin/env bash
# Build the app and install it on a connected phone over adb, then launch it.
#
# Usage: scripts/install.sh [--release] [--serial <device>] [--no-launch]
#   --release     build and install the signed release APK (needs keystore.properties)
#   --serial      device serial when more than one is connected (or set ANDROID_SERIAL)
#   --no-launch   install only
set -euo pipefail

PACKAGE="com.rhulhiking.attendance"
VARIANT="debug"
LAUNCH=1

while [[ $# -gt 0 ]]; do
    case "$1" in
        --release) VARIANT="release" ;;
        --serial) export ANDROID_SERIAL="${2:?--serial needs a device serial}"; shift ;;
        --no-launch) LAUNCH=0 ;;
        -h|--help) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
    shift
done

cd "$(dirname "$0")/.."

command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools." >&2; exit 1; }

# Pick the device: honour ANDROID_SERIAL, otherwise require exactly one.
mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    if [[ ${#DEVICES[@]} -eq 0 ]]; then
        echo "No device found. Plug the phone in, enable USB debugging and accept the prompt." >&2
        adb devices >&2
        exit 1
    elif [[ ${#DEVICES[@]} -gt 1 ]]; then
        echo "More than one device connected. Pick one with --serial:" >&2
        printf '  %s\n' "${DEVICES[@]}" >&2
        exit 1
    fi
    export ANDROID_SERIAL="${DEVICES[0]}"
fi
MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
echo "Device: $MODEL ($ANDROID_SERIAL)"

if [[ "$VARIANT" == "release" ]]; then
    [[ -f keystore.properties ]] || { echo "keystore.properties missing; see README (Build and sideload)." >&2; exit 1; }
    ./gradlew --console=plain -q assembleRelease
    APK="app/build/outputs/apk/release/app-release.apk"
else
    ./gradlew --console=plain -q assembleDebug
    APK="app/build/outputs/apk/debug/app-debug.apk"
fi

echo "Installing $APK"
if ! adb install -r "$APK"; then
    # Usually a signature mismatch (debug vs release build already installed).
    read -r -p "Install failed. Uninstall the existing app (clears its data) and retry? [y/N] " ans
    [[ "$ans" =~ ^[Yy]$ ]] || exit 1
    adb uninstall "$PACKAGE" >/dev/null
    adb install "$APK"
fi

if [[ $LAUNCH -eq 1 ]]; then
    adb shell am start -n "$PACKAGE/.MainActivity" >/dev/null
    echo "Launched $PACKAGE"
fi
