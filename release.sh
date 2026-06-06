#!/bin/bash
set -e

cd "$(dirname "$0")"

# Get version from argument or auto-increment
VERSION=${1:-"v$(date +%Y%m%d.%H%M)"}
VERSION_NAME="${VERSION#v}"   # versionName shown in Obtainium (strip leading "v")

# Commit BEFORE building so the commit count (which drives versionCode) bumps.
# --allow-empty guarantees a unique, higher versionCode even with no file changes,
# so Obtainium/Android always accept the new APK as an upgrade.
git add -A
git commit --allow-empty -m "Release $VERSION"
git push

echo "Building APK ($VERSION_NAME)..."
./gradlew assembleDebug --quiet -PversionName="$VERSION_NAME"

APK="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
    echo "Build failed - APK not found"
    exit 1
fi

echo "Creating release $VERSION..."
gh.exe release create "$VERSION" "$APK" \
    --title "Launcher $VERSION" \
    --notes "Download app-debug.apk and install on your phone."

echo ""
echo "Done! Download link:"
gh.exe release view "$VERSION" --json url -q .url
