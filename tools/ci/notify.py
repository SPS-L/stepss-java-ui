"""Raise a GitHub issue when a run needs a human.

Nobody watches a cron job, so a run that fails - or that bumps URAMSES and
leaves the README's known-limitation paragraph stale - has to say so somewhere
durable. Deduped on the exact title against open issues carrying the label, so
a component that fails on every tick produces one issue, not one a day.
"""

import json
import subprocess

from . import release

USAGE = "Usage: python3 -m tools.ci notify --title <t> --body <b> [--label <l>]\n"

DEFAULT_LABEL = "release-automation"


def raise_or_update(title, body, label, run=subprocess.run):
    """Create an issue, or comment on a matching open one.

    Dedup is on the exact title among OPEN issues carrying `label`: `gh issue
    list` is asked for at most 100 open issues in that label, and the first
    one whose title matches exactly gets the comment instead of a sibling
    issue.
    """
    listing = run(
        [
            "gh", "issue", "list",
            "--state", "open",
            "--label", label,
            "--json", "number,title",
            "--limit", "100",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    existing = json.loads(listing.stdout or "[]")
    match = next((issue for issue in existing if issue["title"] == title), None)

    if match:
        run(
            ["gh", "issue", "comment", str(match["number"]), "--body", body],
            check=True,
            capture_output=True,
            text=True,
        )
        return "commented"

    run(
        ["gh", "issue", "create", "--title", title, "--body", body, "--label", label],
        check=True,
        capture_output=True,
        text=True,
    )
    return "created"


def main(argv, run=subprocess.run):
    """CLI entry point.

    Option parsing goes through release._required_option/_optional_option
    rather than a hand-rolled `argv[argv.index(name) + 1]` lookup: that
    naive shape has already produced two rounds of fixes elsewhere in this
    codebase (an IndexError when the flag is last, and a flag silently
    swallowing the next flag's name as its own value), and release.py's
    helpers are exactly the guarded, already-tested replacement. `run` is
    accepted here (defaulting to subprocess.run, same as raise_or_update) so
    tests can inject a fake without needing to monkeypatch a default that
    was already bound at import time.
    """
    title = release._required_option(argv, "--title", USAGE)
    if title is None:
        return 2
    body = release._required_option(argv, "--body", USAGE)
    if body is None:
        return 2
    label = release._optional_option(argv, "--label", USAGE)
    if label is release._MISSING:
        return 2
    if label is None:
        label = DEFAULT_LABEL

    print(raise_or_update(title, body, label, run=run))
    return 0
