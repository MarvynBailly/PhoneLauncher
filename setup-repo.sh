#!/bin/bash
set -e

cd "$(dirname "$0")"

# Set git identity for this repo
git config user.email "marvyn@marvyn.com"
git config user.name "marvynb"

# Init git repo and make first commit
git init
git add -A
git commit -m "Initial commit: minimal productivity launcher"

# Create GitHub repo (private by default) and push
gh.exe repo create PhoneLauncher --private --source=. --push

echo "Done! Repo created and pushed."
