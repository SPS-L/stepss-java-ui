import io
import os
import tempfile
import unittest
from unittest import mock

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


class UpdateReadmeMainTest(unittest.TestCase):
    def test_missing_version_option_exits_2_without_raising(self):
        # A raise SystemExit("...") here would surface as exit code 1, and a
        # bug that indexes past argv would surface as an uncaught IndexError
        # (a traceback) rather than as this assertion failing - both are the
        # regressions this test guards against.
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(2, release.update_readme_main([]))

    def test_version_flag_with_no_value_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(2, release.update_readme_main(["--version"]))

    def test_happy_path_updates_the_given_readme(self):
        path = write_readme()
        self.assertEqual(
            0, release.update_readme_main(["--version", "v3.55.1"], path=path)
        )
        self.assertIn("Current release: **3.55.1**.", open(path).read())


class NextVersionMainTest(unittest.TestCase):
    def test_rejects_unrecognised_arguments(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(2, release.next_version_main(["--bogus-flag"]))

    def test_happy_path_prints_the_next_version(self):
        fake_result = mock.Mock(stdout="v3.55\n")
        with mock.patch.object(
            release.pins, "load", return_value={"ramses.version": "3.55"}
        ), mock.patch(
            "subprocess.run", return_value=fake_result
        ), mock.patch(
            "sys.stdout", new_callable=io.StringIO
        ) as fake_stdout:
            result = release.next_version_main([])
        self.assertEqual(0, result)
        self.assertEqual("v3.55.1\n", fake_stdout.getvalue())


if __name__ == "__main__":
    unittest.main()
