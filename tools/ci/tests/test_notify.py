import io
import json
import subprocess
import unittest
from unittest import mock

from tools.ci import notify


class ScriptedRun(object):
    """Replays a queued stdout per call and records the argv it saw."""

    def __init__(self, outputs):
        self.outputs = list(outputs)
        self.calls = []

    def __call__(self, argv, **kwargs):
        self.calls.append(argv)
        stdout = self.outputs.pop(0) if self.outputs else ""
        return subprocess.CompletedProcess(argv, 0, stdout, "")


class RaiseOrUpdateTest(unittest.TestCase):
    def test_creates_when_no_open_issue_matches(self):
        run = ScriptedRun(["[]", ""])
        result = notify.raise_or_update("Release failed", "log link", "ci", run=run)
        self.assertEqual("created", result)
        self.assertIn("create", run.calls[1])

    def test_comments_when_an_open_issue_matches(self):
        existing = json.dumps([{"number": 12, "title": "Release failed"}])
        run = ScriptedRun([existing, ""])
        result = notify.raise_or_update("Release failed", "log link", "ci", run=run)
        self.assertEqual("commented", result)
        self.assertIn("comment", run.calls[1])
        self.assertIn("12", run.calls[1])

    def test_ignores_an_open_issue_with_a_different_title(self):
        existing = json.dumps([{"number": 12, "title": "Something else"}])
        run = ScriptedRun([existing, ""])
        self.assertEqual(
            "created", notify.raise_or_update("Release failed", "b", "ci", run=run)
        )

    def test_searches_by_label(self):
        run = ScriptedRun(["[]", ""])
        notify.raise_or_update("t", "b", "release-automation", run=run)
        self.assertIn("release-automation", run.calls[0])


class NotifyMainTest(unittest.TestCase):
    """CLI coverage for notify.main.

    The brief's own notify.main used a naive `option()` helper that repeats
    a bug already fixed twice elsewhere in this module (see
    release._option_value's docstring): `argv[argv.index(name) + 1]` raises
    IndexError when a flag is last, and silently swallows the next flag's
    name as the value when a flag is followed by another flag. notify.main
    is built on release._required_option/_optional_option instead, so this
    class exists to prove that choice actually holds at the CLI boundary
    rather than only in release.py's own tests - missing CLI coverage is
    exactly how two earlier tracebacks in this plan shipped.
    """

    def test_missing_title_option_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO) as fake_stderr:
            result = notify.main(["--body", "b"])
        self.assertEqual(2, result)
        self.assertIn("notify", fake_stderr.getvalue())

    def test_missing_body_option_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO) as fake_stderr:
            result = notify.main(["--title", "t"])
        self.assertEqual(2, result)
        self.assertIn("notify", fake_stderr.getvalue())

    def test_title_flag_followed_by_another_flag_exits_2_without_raising(self):
        # Regression: --title consuming --body as its own "value" used to
        # leave the body unset - here it must fail as a usage message, not
        # raise or silently set the title to "--body".
        with mock.patch("sys.stderr", new_callable=io.StringIO) as fake_stderr:
            result = notify.main(["--title", "--body", "x"])
        self.assertEqual(2, result)
        self.assertIn("notify", fake_stderr.getvalue())

    def test_label_defaults_when_absent(self):
        # raise_or_update's run=subprocess.run default binds at import time,
        # so mock.patch("subprocess.run", ...) after that point would not be
        # seen by a call that relies on the unpassed default. main() accepts
        # its own injectable `run` and threads it through instead, purely as
        # a testability seam - argv parsing and the real CLI default are
        # unaffected.
        run = ScriptedRun(["[]", ""])
        result = notify.main(["--title", "t", "--body", "b"], run=run)
        self.assertEqual(0, result)
        self.assertIn("release-automation", run.calls[0])


if __name__ == "__main__":
    unittest.main()
