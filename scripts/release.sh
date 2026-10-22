#!/usr/bin/env bash
# Cut a Convo release. Signing happens in GitHub Actions (.github/workflows/release.yml);
# this script only bumps the version, commits, tags, and pushes.
#
# Usage:
#   scripts/release.sh 1.0.10          # interactive confirm
#   scripts/release.sh 1.0.10 --yes    # no prompt
set -euo pipefail

VERSION="${1:-}"
ASSUME_YES="${2:-}"

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "usage: $(basename "$0") <major.minor.patch> [--yes]" >&2
    exit 64
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
GRADLE_FILE="app/build.gradle.kts"
TAG="v$VERSION"

# --- preflight -------------------------------------------------------------
if [[ -n "$(git status --porcelain)" ]]; then
    echo "error: working tree is dirty — commit or stash first." >&2
    git status --short >&2
    exit 1
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$BRANCH" != "main" ]]; then
    echo "error: on '$BRANCH', releases are cut from 'main'." >&2
    exit 1
fi

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    echo "error: tag $TAG already exists." >&2
    exit 1
fi

CURRENT_CODE="$(sed -n 's/^ *versionCode *= *\([0-9]\+\).*/\1/p' "$GRADLE_FILE" | head -1)"
CURRENT_NAME="$(sed -n 's/^ *versionName *= *"\(.*\)".*/\1/p' "$GRADLE_FILE" | head -1)"
if [[ -z "$CURRENT_CODE" || -z "$CURRENT_NAME" ]]; then
    echo "error: could not read versionCode/versionName from $GRADLE_FILE" >&2
    exit 1
fi
NEXT_CODE=$((CURRENT_CODE + 1))

echo "Bumping $CURRENT_NAME ($CURRENT_CODE) -> $VERSION ($NEXT_CODE)"

sed -i "s/^\( *versionCode *= *\)[0-9]\+/\1$NEXT_CODE/" "$GRADLE_FILE"
sed -i "s/^\( *versionName *= *\)\"[^\"]*\"/\1\"$VERSION\"/" "$GRADLE_FILE"

echo
git --no-pager diff -- "$GRADLE_FILE"
echo

if [[ "$ASSUME_YES" != "--yes" ]]; then
    read -r -p "Commit, tag $TAG, and push? [y/N] " reply
    [[ "$reply" =~ ^[Yy]$ ]] || { git checkout -- "$GRADLE_FILE"; echo "aborted."; exit 1; }
fi

# --- release ---------------------------------------------------------------
git add "$GRADLE_FILE"
git commit -m "Release $TAG"
git tag -a "$TAG" -m "Convo $VERSION"
git push origin main
git push origin "$TAG"

echo
echo "Pushed $TAG. The workflow builds, signs, and publishes the APK:"
echo "  gh run list -R tedwester/convo -L 1"
echo "  gh run watch -R tedwester/convo"
