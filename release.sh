#!/bin/bash
set -e

cd "$(dirname "$0")"

# Get version from argument or auto-increment
VERSION=${1:-"v$(date +%Y%m%d.%H%M)"}

echo "Building APK..."
./gradlew assembleDebug --quiet

APK="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
    echo "Build failed - APK not found"
    exit 1
fi

# Commit any changes
if [ -n "$(git status --porcelain)" ]; then
    git add -A
    git commit -m "Release $VERSION"
    git push
fi

echo "Creating release $VERSION..."
gh.exe release create "$VERSION" "$APK" \
    --title "Launcher $VERSION" \
    --notes "Download app-debug.apk and install on your phone."

echo ""
echo "Done! Download link:"
gh.exe release view "$VERSION" --json url -q .url
