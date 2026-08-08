"""Re-pin the build to the newest component releases.

Detects, downloads and rewrites; it does not build, commit, tag or publish.
All validation happens before the first write, so a run that fails leaves the
working tree exactly as it found it. That includes the write itself: with two
files to keep in sync (versions.properties and Toolchain.java), a failure
partway through writing the first would leave the second stale, so
Toolchain.java's rewrite is validated - via pins.validate_toolchain - before
either file is touched.
"""

import json
import os
import sys

from . import pins, upstream as _upstream

PAYLOAD_CACHE = "payload-cache"
TOOLCHAIN = os.path.join("src", "my", "ramses", "platform", "Toolchain.java")
PROPERTIES = "versions.properties"


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
    skipped = []
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

        reason = _refusal(old_version, new_version)
        if reason is not None:
            skipped.append(
                {
                    "component": component,
                    "pinned_version": old_version,
                    "upstream_version": new_version,
                    "reason": reason,
                }
            )
            continue

        if component == "uramses":
            _plan_uramses(
                repo_root, cache, props, new_version, updates, renames, up, dry_run,
            )
        else:
            _plan_component(
                component, cache, props, release, old_version, new_version,
                updates, renames, up, dry_run,
            )

        changed.append(
            {
                "component": component,
                "old_version": old_version,
                "new_version": new_version,
                "old_tag": props["%s.tag" % component],
                "new_tag": release.tag,
                "title": release.name,
                "body": release.body,
                "url": release.url,
                "published": release.published,
            }
        )
        if not dry_run:
            updates["%s.version" % component] = new_version
            updates["%s.tag" % component] = release.tag

    if changed and not dry_run:
        pins.validate_toolchain(toolchain_path, renames)
        pins.set_values(properties_path, updates)
        pins.rewrite_toolchain(toolchain_path, renames)

    return {"changed": changed, "skipped": skipped}


def _refusal(old_version, new_version):
    """Why this bump must not happen, or None if it may.

    A difference from the pinned version is not by itself an upgrade. GitHub's
    "latest release" is the most recently *created* non-draft non-prerelease
    release, not the highest-numbered one, so a backport cut after a newer
    release - v3.54.1 published the day after v3.55 - is what `gh release view`
    answers with. Bumping on that re-pins the build *down*, and the workflow
    then publishes the result with `--latest`, demoting the genuinely newest
    STEPSS release. Nothing downstream of here would notice, so the comparison
    has to happen here.

    Refusing is always the safe answer: the pins stay where a human last left
    them, and the summary's "skipped" list makes the run say so out loud.
    """
    old_key = pins.version_key(old_version)
    new_key = pins.version_key(new_version)
    if old_key is None or new_key is None:
        return (
            "cannot order %r against the pinned %r - a version segment is not "
            "an integer, so the comparison is indeterminate and the pin is "
            "left alone" % (new_version, old_version)
        )
    if new_key < old_key:
        return (
            "upstream %s sorts below the pinned %s - GitHub's latest release "
            "is the most recently created one, so this looks like a backport "
            "rather than an upgrade" % (new_version, old_version)
        )
    return None


def _plan_component(
    component, cache, props, release, old_version, new_version, updates, renames, up,
    dry_run,
):
    """Validates and, unless dry_run, downloads one component's assets.

    Every expected name is checked against the release's asset list before
    anything is downloaded, so a partial rename upstream fails fast rather
    than after three downloads. In dry_run that validation is the whole job:
    the summary carries no digest fields, so nothing is fetched or hashed.
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

    if dry_run:
        return

    # The old names come from versions.properties' own record of what it last
    # pinned (<component>.<platform>.asset), not from re-expanding the pattern
    # at old_version: those two would usually agree, but only the recorded
    # value is guaranteed to be the exact string Toolchain.java names.
    old_names = {
        platform: props["%s.%s.asset" % (component, platform)]
        for platform in pins.PLATFORMS
    }
    for platform, name in sorted(expected.items()):
        path = up.download_asset(props["%s.repo" % component], release.tag, name, cache)
        updates["%s.%s.asset" % (component, platform)] = name
        updates["%s.%s.sha256" % (component, platform)] = up.sha256_file(path)
        renames[old_names[platform]] = name


def _plan_uramses(repo_root, cache, props, new_version, updates, renames, up, dry_run):
    """URAMSES is public and pinned on a content manifest, not on the archive.

    See upstream.uramses_manifest_digest for why the archive's own bytes are
    not a usable pin. URAMSES has no per-platform assets to validate (see
    pins.asset_names), so in dry_run there is nothing to check and this is a
    no-op.

    The archive downloads to a temporary name in the same directory and is
    renamed into place only once complete: build.xml's fetch-uramses target
    reuses payload-cache/stepss-uramses-<version>.zip on mere existence, so an
    interrupted download must never leave a truncated file under the name a
    later build will trust without checking.
    """
    if dry_run:
        return

    url = pins.uramses_url(props, new_version)
    dest = os.path.join(cache, "stepss-uramses-%s.zip" % new_version)
    tmp_dest = dest + ".part"
    up.download_url(url, tmp_dest)
    os.replace(tmp_dest, dest)

    updates["uramses.source.url"] = url
    updates["uramses.manifest.sha256"] = up.uramses_manifest_digest(dest, repo_root)
    old_version = props["uramses.version"]
    renames["uramses-kit-v%s.zip" % old_version] = "uramses-kit-v%s.zip" % new_version


def main(argv):
    if argv not in ([], ["--dry-run"]):
        sys.stderr.write("Usage: python3 -m tools.ci bump [--dry-run]\n")
        return 2
    summary = run(os.getcwd(), dry_run=(argv == ["--dry-run"]))
    print(json.dumps(summary, indent=2))
    return 0
