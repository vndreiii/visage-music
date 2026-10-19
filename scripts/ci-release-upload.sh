#!/usr/bin/env bash
# Adds files to the GitHub release of a tag as soon as a CI job has made them.
# The release is made as a pre-release first, so it does not become "latest" (and updaters do not read it)
# before latest.json exists. The release workflow's last job turns it into a normal release.
# Usage: GH_TOKEN=... ci-release-upload.sh <tag> <file>...
set -euo pipefail
tag="$1"; shift
repo="${GITHUB_REPOSITORY:?}"
for attempt in 1 2 3 4 5; do
  gh release view "$tag" --repo "$repo" >/dev/null 2>&1 && break
  # Several jobs can get here at the same time; the loser of the race just views the release on the next turn.
  gh release create "$tag" --repo "$repo" --title "VIsage $tag" --generate-notes --prerelease --verify-tag >/dev/null 2>&1 || sleep $((attempt * 3))
done
gh release view "$tag" --repo "$repo" >/dev/null
for attempt in 1 2 3; do
  gh release upload "$tag" "$@" --repo "$repo" --clobber && exit 0
  sleep $((attempt * 5))
done
echo "::error::Could not upload to release $tag" >&2
exit 1
