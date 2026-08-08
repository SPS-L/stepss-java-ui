import io
import json
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


PROPS = {
    "ramses.version": "3.55",
    "ramses.tag": "v3.55",
    "helios.version": "1.4.1",
    "helios.tag": "v1.4.1",
    "dyngraph.version": "1.2.0",
    "dyngraph.tag": "v1.2.0",
    "codegen.version": "5.1.0",
    "codegen.tag": "v5.1.0",
    "uramses.version": "3.55",
    "uramses.tag": "v3.55",
}

DYNGRAPH_CHANGE = {
    "component": "dyngraph",
    "old_version": "1.1.0",
    "new_version": "1.2.0",
    "old_tag": "v1.1.0",
    "new_tag": "v1.2.0",
    "title": "Console interface on Windows",
    "body": "The Windows build is now a console program.",
    "url": "https://github.com/SPS-L/stepss-dyngraph/releases/tag/v1.2.0",
    "published": "2026-08-08",
}

HELIOS_CHANGE = {
    "component": "helios",
    "old_version": "1.4.0",
    "new_version": "1.4.1",
    "old_tag": "v1.4.0",
    "new_tag": "v1.4.1",
    "title": "Convergence fix",
    "body": "Fixed a divergence on islanded networks.",
    "url": "https://github.com/SPS-L/stepss-helios/releases/tag/v1.4.1",
    "published": "2026-07-28",
}


class ComposeNotesTest(unittest.TestCase):
    def notes(self, changed, previous="v3.55"):
        return release.compose_notes("v3.55.1", PROPS, changed, "abc123", previous)

    def test_titles_the_release(self):
        self.assertIn("# STEPSS v3.55.1", self.notes([DYNGRAPH_CHANGE]))

    def test_names_what_triggered_the_rebuild(self):
        body = self.notes([DYNGRAPH_CHANGE])
        self.assertIn("**DYNGRAPH 1.2.0**", body)
        self.assertIn("was 1.1.0", body)

    def test_lists_every_component_in_the_table(self):
        body = self.notes([DYNGRAPH_CHANGE])
        for name in ("RAMSES", "Helios", "DYNGRAPH", "CODEGEN", "URAMSES"):
            self.assertIn("| %s " % name, body)

    def test_table_reports_the_pinned_versions(self):
        body = self.notes([DYNGRAPH_CHANGE])
        self.assertIn("| 1.4.1 ", body)
        self.assertIn("| 5.1.0 ", body)

    def test_embeds_the_upstream_body_verbatim(self):
        body = self.notes([DYNGRAPH_CHANGE])
        self.assertIn("The Windows build is now a console program.", body)

    def test_embeds_the_upstream_title_in_the_summary(self):
        body = self.notes([DYNGRAPH_CHANGE])
        self.assertIn("Console interface on Windows", body)

    def test_only_changed_components_get_a_details_block(self):
        body = self.notes([DYNGRAPH_CHANGE])
        self.assertEqual(1, body.count("<details"))

    def test_two_changes_get_two_details_blocks(self):
        body = self.notes([HELIOS_CHANGE, DYNGRAPH_CHANGE])
        self.assertEqual(2, body.count("<details"))
        self.assertIn("Fixed a divergence on islanded networks.", body)

    def test_publishes_the_jar_digest(self):
        self.assertIn("abc123", self.notes([DYNGRAPH_CHANGE]))

    def test_manual_release_with_no_changes_says_so(self):
        body = self.notes([])
        self.assertIn("Manual release. No component changes since v3.55.", body)

    def test_manual_release_with_no_changes_omits_the_notes_heading(self):
        body = self.notes([])
        self.assertNotIn("Upstream release notes", body)

    def test_manual_release_still_lists_the_bundle(self):
        body = self.notes([])
        self.assertIn("| RAMSES ", body)

    def test_first_ever_release_has_no_previous_tag(self):
        body = release.compose_notes("v3.55", PROPS, [], "abc123", None)
        self.assertIn("Manual release.", body)
        self.assertNotIn("since None", body)


class NotesMainTest(unittest.TestCase):
    """CLI coverage for notes_main.

    Task 5's review found that missing CLI coverage - not missing logic
    coverage - was exactly how a traceback bug shipped: _required_option's
    None-on-misuse contract is only actually exercised through the *_main
    function that calls it. Each of the three required options is checked
    missing in isolation (the other two supplied with placeholder values) so
    a bug that checks the wrong flag, or checks them in the wrong order,
    would fail one of these rather than all three uniformly. --jar also gets
    a valueless case (the flag as the last item in argv, _required_option's
    own definition of "no value") since it is last in notes_main's own
    checking order and so is the one place a valueless flag is not itself
    followed immediately by another recognised flag - the other two options'
    valueless case is not exercised here because it lands on the documented
    open edge described in the task report, not on _required_option's
    documented contract.
    """

    def test_missing_version_option_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(
                2,
                release.notes_main(
                    ["--summary", "summary.json", "--jar", "dist/stepss.jar"]
                ),
            )

    def test_missing_summary_option_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(
                2,
                release.notes_main(
                    ["--version", "v3.55.1", "--jar", "dist/stepss.jar"]
                ),
            )

    def test_missing_jar_option_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(
                2,
                release.notes_main(
                    ["--version", "v3.55.1", "--summary", "summary.json"]
                ),
            )

    def test_jar_flag_with_no_value_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(
                2,
                release.notes_main(
                    ["--version", "v3.55.1", "--summary", "summary.json", "--jar"]
                ),
            )

    def test_happy_path_prints_the_composed_notes(self):
        handle, summary_path = tempfile.mkstemp(suffix=".json")
        with os.fdopen(handle, "w") as out:
            json.dump({"changed": [DYNGRAPH_CHANGE]}, out)
        try:
            with mock.patch.object(
                release.pins, "load", return_value=PROPS
            ), mock.patch(
                "tools.ci.upstream.sha256_file", return_value="abc123"
            ), mock.patch(
                "sys.stdout", new_callable=io.StringIO
            ) as fake_stdout:
                result = release.notes_main(
                    [
                        "--version", "v3.55.1",
                        "--summary", summary_path,
                        "--jar", "dist/stepss.jar",
                    ]
                )
            self.assertEqual(0, result)
            printed = fake_stdout.getvalue()
            self.assertIn("# STEPSS v3.55.1", printed)
            self.assertIn("abc123", printed)
            self.assertIn("The Windows build is now a console program.", printed)
        finally:
            os.remove(summary_path)


if __name__ == "__main__":
    unittest.main()
