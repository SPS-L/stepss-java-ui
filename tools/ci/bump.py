"""Re-pin the build to the newest component releases.

Detects, downloads and rewrites; it does not build, commit, tag or publish.
All validation happens before the first write, so a run that fails leaves the
working tree exactly as it found it.
"""

import json
import os

from . import pins, upstream as _upstream

PAYLOAD_CACHE = "payload-cache"
TOOLCHAIN = os.path.join("src", "my", "ramses", "platform", "Toolchain.java")
PROPERTIES = "versions.properties"

# component -> owner/repo, read from the pins checked into this repo. Kept as
# a module-level dict (rather than hardcoded literals) so it can never drift
# from versions.properties; computed from this file's own location so it does
# not depend on the caller's working directory.
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_pinned = pins.load(os.path.join(_REPO_ROOT, PROPERTIES))
REPOS = {
    component: _pinned["%s.repo" % component]
    for component in pins.COMPONENTS
    if "%s.repo" % component in _pinned
}
del _pinned


class AssetNotFound(Exception):
    """An expected asset name is absent from the upstream release.

    Never recovered from by matching some other asset: in a pipeline that
    publishes without review, silently picking a different file is the one
    failure that reaches users as a wrong binary.
    """


def run(repo_root, dry_run=False, up=_upstream):
    properties_path = os.path.join(repo_root, PROPERTIES)
    toolchain_path = os.path.join(repo_root, TOOLCHAIN)
    cache = os.path.join(repo_root, PAYLOAD_CACHE)
    props = pins.load(properties_path)

    changed = []
    updates = {}
    renames = {}

    for component in pins.COMPONENTS:
        repo = props.get("%s.repo" % component)
        if repo is None:
            continue
        release = up.latest_release(repo)
        new_version = pins.version_of(release.tag)
        old_version = props["%s.version" % component]
        if new_version == old_version:
            continue

        if component == "uramses":
            _plan_uramses(
                repo_root, cache, props, new_version, updates, renames, up
            )
        else:
            _plan_component(
                component, cache, props, release, old_version, new_version,
                updates, renames, up,
            )

        updates["%s.version" % component] = new_version
        updates["%s.tag" % component] = release.tag
        changed.append(
            {
                "component": component,
                "old_version": old_version,
                "new_version": new_version,
                "old_tag": pins.tag_of(old_version),
                "new_tag": release.tag,
                "title": release.name,
                "body": release.body,
                "url": release.url,
                "published": release.published,
            }
        )

    if changed and not dry_run:
        pins.set_values(properties_path, updates)
        pins.rewrite_toolchain(toolchain_path, renames)

    return {"changed": changed}


def _plan_component(
    component, cache, props, release, old_version, new_version, updates, renames, up
):
    """Validates and downloads one component's assets, recording the rewrites.

    Every expected name is checked against the release's asset list before
    anything is downloaded, so a partial rename upstream fails fast rather
    than after three downloads.
    """
    expected = pins.asset_names(props, component, new_version)
    for platform, name in sorted(expected.items()):
        if name not in release.assets:
            raise AssetNotFound(
                "%s %s has no asset named %s (platform %s).\n"
                "The release contains: %s\n"
                "Either the upstream naming changed - update "
                "%s.%s.asset.pattern in versions.properties - or the release "
                "is incomplete."
                % (
                    component, release.tag, name, platform,
                    ", ".join(release.assets) or "(none)",
                    component, platform,
                )
            )

    old_names = pins.asset_names(props, component, old_version)
    for platform, name in sorted(expected.items()):
        path = up.download_asset(props["%s.repo" % component], release.tag, name, cache)
        updates["%s.%s.asset" % (component, platform)] = name
        updates["%s.%s.sha256" % (component, platform)] = up.sha256_file(path)
        renames[old_names[platform]] = name


def _plan_uramses(repo_root, cache, props, new_version, updates, renames, up):
    """URAMSES is public and pinned on a content manifest, not on the archive.

    See upstream.uramses_manifest_digest for why the archive's own bytes are
    not a usable pin.
    """
    url = pins.uramses_url(props, new_version)
    dest = os.path.join(cache, "stepss-uramses-%s.zip" % new_version)
    up.download_url(url, dest)
    updates["uramses.source.url"] = url
    updates["uramses.manifest.sha256"] = up.uramses_manifest_digest(dest, repo_root)
    old_version = props["uramses.version"]
    renames["uramses-kit-v%s.zip" % old_version] = "uramses-kit-v%s.zip" % new_version


def main(argv):
    dry_run = "--dry-run" in argv
    summary = run(os.getcwd(), dry_run=dry_run)
    print(json.dumps(summary, indent=2))
    return 0
