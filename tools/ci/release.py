"""Assemble the release: version number, README line, notes.

Pure. Everything here is derived from what the bump already produced.
"""

import json
import os
import re

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
    import subprocess

    props = pins.load("versions.properties")
    tags = subprocess.run(
        ["git", "tag", "--list"], check=True, capture_output=True, text=True
    ).stdout.split()
    print(next_version(props["ramses.version"], tags))
    return 0


def update_readme_main(argv):
    version = _required_option(argv, "--version")
    update_readme("README.md", version)
    return 0


def _required_option(argv, name):
    if name not in argv:
        raise SystemExit("Missing required option: " + name)
    return argv[argv.index(name) + 1]
