"""Assemble the release: version number, README line, notes.

Pure. Everything here is derived from what the bump already produced.
"""

import json
import re
import sys

from . import pins

README_ANCHOR = re.compile(r"^Current release: \*\*.+\*\*\.$", re.MULTILINE)


def next_version(ramses_version, existing_tags):
    """The next free tag in the RAMSES-version sequence.

    Derived from the tags that exist rather than from a stored counter, so
    there is nothing to drift out of step. A RAMSES bump restarts the sequence
    on its own, because no tag for the new version exists yet.
    """
    base = pins.version_of(ramses_version)
    bare = "v" + base
    counters = []
    taken = set(existing_tags)
    pattern = re.compile(r"^v%s\.(\d+)$" % re.escape(base))
    for tag in taken:
        match = pattern.match(tag)
        if match:
            counters.append(int(match.group(1)))

    if bare not in taken and not counters:
        return bare
    return "v%s.%d" % (base, max(counters) + 1 if counters else 1)


def update_readme(path, version):
    """Rewrites the 'Current release' line, and only that line.

    Anchored to the whole line so it cannot match the version number where it
    appears in prose. The known-limitation paragraph names a pinned URAMSES
    version as part of a claim about that version, and substituting a new
    number there would keep the claim alive while making it look freshly
    checked - so it is left for a human, flagged by tools/ci/notify.py.
    """
    bare = pins.version_of(version)
    with open(path, "r", encoding="utf-8") as handle:
        text = handle.read()

    if not README_ANCHOR.search(text):
        raise ValueError("%s has no 'Current release: **...**.' line" % path)

    replacement = "Current release: **%s**." % bare
    updated = README_ANCHOR.sub(replacement, text)
    if updated == text:
        return False

    with open(path, "w", encoding="utf-8") as handle:
        handle.write(updated)
    return True


def next_version_main(argv):
    if argv:
        sys.stderr.write("Usage: python3 -m tools.ci next-version\n")
        return 2

    import subprocess

    props = pins.load("versions.properties")
    tags = subprocess.run(
        ["git", "tag", "--list"], check=True, capture_output=True, text=True
    ).stdout.split()
    print(next_version(props["ramses.version"], tags))
    return 0


def update_readme_main(argv, path="README.md"):
    version = _required_option(argv, "--version")
    if version is None:
        return 2
    update_readme(path, version)
    return 0


def _required_option(argv, name, usage=None):
    """Returns the value following `name` in argv, or None on misuse.

    Never raises: a missing option and a present-but-valueless option (the
    flag is the last item in argv) both print a usage message to stderr and
    return None, so callers can turn that into main()'s exit-2 convention
    - matching bump.main's style - without an uncaught exception (a raw
    IndexError, or a SystemExit carrying a string, which exits 1 rather
    than 2) ever reaching the caller.

    `usage` defaults to update-readme's own usage line, so the one existing
    caller is unaffected; notes_main (which has three required options, not
    one) passes its own so the message printed actually names the command
    and option the caller got wrong instead of an unrelated one.
    """
    if usage is None:
        usage = "Usage: python3 -m tools.ci update-readme --version <tag>\n"
    if name not in argv:
        sys.stderr.write(usage)
        return None
    index = argv.index(name)
    if index + 1 >= len(argv):
        sys.stderr.write(usage)
        return None
    return argv[index + 1]


DISPLAY_NAMES = {
    "ramses": "RAMSES",
    "helios": "Helios",
    "dyngraph": "DYNGRAPH",
    "codegen": "CODEGEN",
    "uramses": "URAMSES",
}


def compose_notes(version, props, changed, jar_sha256, previous_tag):
    """The release body.

    Four of the five component repos are private, so a link to an upstream
    release is a 404 for most people reading this page. The upstream bodies are
    therefore embedded rather than linked, which is the whole point of the
    exercise: the bundle's provenance has to be readable here or it is not
    readable anywhere.
    """
    lines = ["# STEPSS %s" % version, ""]

    if changed:
        described = ", ".join(
            "**%s %s** (was %s)"
            % (DISPLAY_NAMES[entry["component"]], entry["new_version"],
               entry["old_version"])
            for entry in changed
        )
        lines.append("Rebuilt for %s." % described)
    elif previous_tag:
        lines.append("Manual release. No component changes since %s." % previous_tag)
    else:
        lines.append("Manual release.")
    lines.append("")

    lines.append("## Bundled components")
    lines.append("")
    lines.append("| Component | Version | Upstream release |")
    lines.append("|---|---|---|")
    for component in pins.COMPONENTS:
        lines.append(
            "| %s | %s | %s |"
            % (
                DISPLAY_NAMES[component],
                props["%s.version" % component],
                props["%s.tag" % component],
            )
        )
    lines.append("")

    if changed:
        lines.append("## Upstream release notes")
        lines.append("")
        for entry in changed:
            summary = "<b>%s %s</b>" % (
                DISPLAY_NAMES[entry["component"]],
                entry["new_tag"],
            )
            if entry["title"]:
                summary += " &mdash; %s" % entry["title"]
            lines.append("<details open><summary>%s</summary>" % summary)
            lines.append("")
            lines.append(entry["body"] or "_No release notes upstream._")
            lines.append("")
            lines.append("</details>")
            lines.append("")

    lines.append("## Artifact")
    lines.append("")
    lines.append("`stepss.jar` &mdash; SHA-256 `%s`" % jar_sha256)
    lines.append("")

    return "\n".join(lines)


def notes_main(argv):
    usage = (
        "Usage: python3 -m tools.ci notes --version <tag> --summary <path.json> "
        "--jar <path> [--previous <tag>]\n"
    )
    version = _required_option(argv, "--version", usage)
    if version is None:
        return 2
    summary_path = _required_option(argv, "--summary", usage)
    if summary_path is None:
        return 2
    jar_path = _required_option(argv, "--jar", usage)
    if jar_path is None:
        return 2
    previous = argv[argv.index("--previous") + 1] if "--previous" in argv else None

    from . import upstream

    with open(summary_path, "r", encoding="utf-8") as handle:
        summary = json.load(handle)
    props = pins.load("versions.properties")
    print(
        compose_notes(
            version,
            props,
            summary["changed"],
            upstream.sha256_file(jar_path),
            previous or None,
        )
    )
    return 0
