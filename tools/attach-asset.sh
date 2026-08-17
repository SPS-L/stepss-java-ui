#!/usr/bin/env bash
# Attaches files to a release identified by its numeric ID, replacing any asset
# already carrying the same name.
#
# By ID, and never by tag, because the release these run against is a DRAFT and
# a draft has no tag. `gh release upload <tag>` resolves the tag with
# GET /releases/tags/{tag}, which 404s for every draft, and then falls back to
# listing every release in the repository and scanning it for a matching
# tag_name. That listing is a second call on the least reliable endpoint in the
# API, made once per attach, and it is what broke the first draft-based release:
# two legs attached to the draft and the third got "release not found" for a
# draft that demonstrably existed, during a GitHub partial outage.
#
# A release ID is handed down from the job that created the draft, so every call
# here is a direct addressed operation with nothing to resolve.
#
# Usage: attach-asset.sh <release-id> <file> [file ...]
# Requires: GH_TOKEN, GITHUB_REPOSITORY.
set -euo pipefail

if [ "$#" -lt 2 ]; then
    echo "usage: $0 <release-id> <file> [file ...]" >&2
    exit 2
fi

release_id="$1"
shift

: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"

for path in "$@"; do
    if [ ! -f "$path" ]; then
        echo "attach-asset: no such file: $path" >&2
        exit 1
    fi

    # Exported rather than interpolated into the jq program: an asset name is
    # derived from a version string that ultimately comes from an upstream tag,
    # and `env.ASSET_NAME` cannot be closed and escaped the way a spliced
    # literal can.
    ASSET_NAME="$(basename "$path")"
    export ASSET_NAME

    # Replace rather than fail. Re-running a leg after a transient error is the
    # normal way this workflow recovers, and the upload API answers 422 for a
    # name that is already present rather than overwriting it.
    existing="$(gh api "repos/$GITHUB_REPOSITORY/releases/$release_id/assets" \
        --paginate --jq 'map(select(.name == env.ASSET_NAME)) | .[0].id // empty')"
    if [ -n "$existing" ]; then
        echo "attach-asset: replacing existing $ASSET_NAME (asset $existing)"
        gh api --method DELETE \
            "repos/$GITHUB_REPOSITORY/releases/assets/$existing" --silent
    fi

    # Uploads go to uploads.github.com rather than api.github.com, so the full
    # URL is given; gh honours it instead of prefixing its own host.
    gh api --method POST \
        -H "Content-Type: application/octet-stream" \
        "https://uploads.github.com/repos/$GITHUB_REPOSITORY/releases/$release_id/assets?name=$ASSET_NAME" \
        --input "$path" --silent
    echo "attach-asset: attached $ASSET_NAME to release $release_id"
done
