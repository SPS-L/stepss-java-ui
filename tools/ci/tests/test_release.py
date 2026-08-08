import os
import tempfile
import unittest

from tools.ci import release

README = """\
# STEPSS

**Static and Transient Electric Power Systems Simulation**

STEPSS is the Java (Swing) desktop GUI.

Current release: **3.55**.

## Features

The pinned v3.55 predates the marker comments, so Compile stops.
"""


def write_readme(text=README):
    handle, path = tempfile.mkstemp(suffix=".md")
    with os.fdopen(handle, "w") as out:
        out.write(text)
    return path


class NextVersionTest(unittest.TestCase):
    def test_first_release_for_a_ramses_version_is_bare(self):
        self.assertEqual("v3.55", release.next_version("3.55", []))

    def test_second_release_gets_a_patch_counter(self):
        self.assertEqual("v3.55.1", release.next_version("3.55", ["v3.55"]))

    def test_continues_from_the_highest_counter(self):
        self.assertEqual(
            "v3.55.4",
            release.next_version("3.55", ["v3.55", "v3.55.1", "v3.55.3"]),
        )

    def test_counter_sorts_numerically_not_lexically(self):
        self.assertEqual(
            "v3.55.11",
            release.next_version("3.55", ["v3.55.9", "v3.55.10"]),
        )

    def test_a_ramses_bump_restarts_the_sequence(self):
        self.assertEqual(
            "v3.56", release.next_version("3.56", ["v3.55", "v3.55.7"])
        )

    def test_ignores_unrelated_tags(self):
        self.assertEqual(
            "v3.55", release.next_version("3.55", ["v1.0", "nightly", "v3.550.2"])
        )

    def test_accepts_a_v_prefixed_ramses_version(self):
        self.assertEqual("v3.55", release.next_version("v3.55", []))


class UpdateReadmeTest(unittest.TestCase):
    def test_rewrites_the_current_release_line(self):
        path = write_readme()
        self.assertTrue(release.update_readme(path, "v3.55.1"))
        self.assertIn("Current release: **3.55.1**.", open(path).read())

    def test_strips_the_v_prefix(self):
        path = write_readme()
        release.update_readme(path, "v3.56")
        self.assertNotIn("**v3.56**", open(path).read())

    def test_accepts_a_bare_version(self):
        path = write_readme()
        release.update_readme(path, "3.56")
        self.assertIn("Current release: **3.56**.", open(path).read())

    def test_leaves_the_known_limitation_paragraph_alone(self):
        path = write_readme()
        release.update_readme(path, "v3.60")
        self.assertIn("The pinned v3.55 predates the marker comments", open(path).read())

    def test_reports_no_change_when_already_current(self):
        path = write_readme()
        self.assertFalse(release.update_readme(path, "v3.55"))

    def test_missing_anchor_raises(self):
        path = write_readme("# STEPSS\n\nNo version line here.\n")
        with self.assertRaises(ValueError):
            release.update_readme(path, "v3.55.1")


if __name__ == "__main__":
    unittest.main()
