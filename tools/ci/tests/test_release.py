import io
import json
import os
import subprocess
import tempfile
import unittest
from unittest import mock

from tools.ci import release

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

    def test_table_has_four_columns(self):
        body = self.notes([DYNGRAPH_CHANGE])
        self.assertIn("| Component | Version | Upstream release | Published |", body)
        self.assertIn("|---|---|---|---|", body)

    def test_a_changed_component_carries_its_publication_date(self):
        body = self.notes([DYNGRAPH_CHANGE])
        self.assertIn("| DYNGRAPH | 1.2.0 | v1.2.0 | 2026-08-08 |", body)

    def test_an_unchanged_component_has_no_publication_date(self):
        # Only the components this run bumped come with an upstream date; the
        # rest get an em dash rather than a plausible-looking wrong one.
        body = self.notes([DYNGRAPH_CHANGE])
        self.assertIn("| RAMSES | 3.55 | v3.55 | &mdash; |", body)
        self.assertIn("| Helios | 1.4.1 | v1.4.1 | &mdash; |", body)

    def test_every_row_has_a_published_cell_when_nothing_changed(self):
        body = self.notes([])
        for name in ("RAMSES", "Helios", "DYNGRAPH", "CODEGEN", "URAMSES"):
            self.assertIn("| %s | " % name, body)
        self.assertEqual(5, body.count("| &mdash; |"))

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

    def test_lists_this_repositorys_own_changes(self):
        body = release.compose_notes(
            "v3.55.1", PROPS, [], "abc123", "v3.55",
            ["Merge: look and feel", "Fix the About dialog"])
        self.assertIn("## Changes in this release", body)
        self.assertIn("- Merge: look and feel", body)
        self.assertIn("- Fix the About dialog", body)

    def test_a_release_of_our_own_changes_does_not_read_as_nothing(self):
        # The fault this section exists for: a release made entirely of work
        # here used to announce itself as "No component changes since v3.55",
        # which a reader fairly takes to mean nothing changed at all.
        body = release.compose_notes(
            "v3.55.1", PROPS, [], "abc123", "v3.55", ["Merge: look and feel"])
        self.assertIn("Changes to STEPSS itself", body)
        self.assertNotIn("No component changes", body)

    def test_a_component_release_lists_its_own_changes_too(self):
        # A component-driven release can still carry work from this repository,
        # and before this it went unmentioned.
        body = release.compose_notes(
            "v3.55.1", PROPS, [DYNGRAPH_CHANGE], "abc123", "v3.55",
            ["Fix the About dialog"])
        self.assertIn("Rebuilt for", body)
        self.assertIn("- Fix the About dialog", body)

    def test_no_changes_means_no_heading(self):
        self.assertNotIn("## Changes in this release", self.notes([]))


class RepositoryChangesTest(unittest.TestCase):
    def test_no_previous_tag_means_no_history_to_report(self):
        # The first-ever release has nothing to diff against, and asking git
        # for "..HEAD" would be a different question than the one meant.
        self.assertEqual([], release.repository_changes(None))
        self.assertEqual([], release.repository_changes(""))

    def test_release_commits_are_dropped_as_bookkeeping(self):
        with mock.patch("subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess(
                [], 0, stdout=b"Release v3.74.1\nMerge: look and feel\n")
            self.assertEqual(["Merge: look and feel"],
                             release.repository_changes("v3.74"))

    def test_first_parent_so_a_merge_reports_its_summary(self):
        with mock.patch("subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 0, stdout=b"")
            release.repository_changes("v3.74")
        self.assertIn("--first-parent", run.call_args[0][0])

    def test_a_git_failure_thins_the_notes_rather_than_failing_the_release(self):
        # The build and every check have already passed by the time notes are
        # composed. Losing a section is the right cost of git being unhappy;
        # losing the release is not.
        with mock.patch("subprocess.run",
                        side_effect=subprocess.CalledProcessError(128, "git")):
            self.assertEqual([], release.repository_changes("v3.74"))
        with mock.patch("subprocess.run", side_effect=OSError("no git")):
            self.assertEqual([], release.repository_changes("v3.74"))

    def test_blank_lines_are_not_reported_as_commits(self):
        with mock.patch("subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess(
                [], 0, stdout=b"Merge: look and feel\n\n  \n")
            self.assertEqual(["Merge: look and feel"],
                             release.repository_changes("v3.74"))


class NotesMainTest(unittest.TestCase):
    """CLI coverage for notes_main.

    Task 5's review found that missing CLI coverage - not missing logic
    coverage - was exactly how a traceback bug shipped: _required_option's
    (and _optional_option's) None/_MISSING-on-misuse contract is only
    actually exercised through the *_main function that calls it. Each of
    the three required options is checked missing in isolation (the other
    two supplied with placeholder values) so a bug that checks the wrong
    flag, or checks them in the wrong order, would fail one of these rather
    than all three uniformly. Each required option also gets a valueless
    case: --jar as the last item in argv (the flag genuinely has nothing
    after it), and --version/--summary each followed immediately by the
    *next* recognised flag rather than a value of their own - the exact
    shape of traceback a Task 6 review reproduced against this file
    (`open("--jar", ...)` and friends) before _option_value treated a
    "--"-prefixed next token as no value at all. One test per required
    option also inspects the captured stderr, so a regression that keeps
    returning 2 but reverts to a message naming the wrong subcommand (e.g.
    "update-readme" instead of "notes") would still fail here. --previous
    gets its own trio: absent (valid, defaults to None - exercised by the
    happy path below), present-but-invalid (exit 2, usage message, no
    traceback), and a check that an invalid --previous is never allowed to
    reach print() at all, i.e. never gets rendered into a real release page.
    """

    def test_missing_version_option_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO) as fake_stderr:
            result = release.notes_main(
                ["--summary", "summary.json", "--jar", "dist/stepss.jar"]
            )
        self.assertEqual(2, result)
        self.assertIn("tools.ci notes", fake_stderr.getvalue())
        self.assertNotIn("update-readme", fake_stderr.getvalue())

    def test_version_flag_followed_by_another_flag_exits_2_without_raising(self):
        # Regression: --version consuming --summary as its own "value" used
        # to leave summary_path unset and crash several lines later on
        # open("--jar", ...) - a traceback, not a usage message.
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(
                2,
                release.notes_main(
                    ["--version", "--summary", "summary.json", "--jar", "dist/stepss.jar"]
                ),
            )

    def test_missing_summary_option_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO) as fake_stderr:
            result = release.notes_main(
                ["--version", "v3.55.1", "--jar", "dist/stepss.jar"]
            )
        self.assertEqual(2, result)
        self.assertIn("tools.ci notes", fake_stderr.getvalue())
        self.assertNotIn("update-readme", fake_stderr.getvalue())

    def test_summary_flag_followed_by_another_flag_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(
                2,
                release.notes_main(
                    ["--version", "v1", "--summary", "--jar", "dist/stepss.jar"]
                ),
            )

    def test_missing_jar_option_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO) as fake_stderr:
            result = release.notes_main(
                ["--version", "v3.55.1", "--summary", "summary.json"]
            )
        self.assertEqual(2, result)
        self.assertIn("tools.ci notes", fake_stderr.getvalue())
        self.assertNotIn("update-readme", fake_stderr.getvalue())

    def test_jar_flag_with_no_value_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(
                2,
                release.notes_main(
                    ["--version", "v3.55.1", "--summary", "summary.json", "--jar"]
                ),
            )

    def test_missing_previous_value_exits_2_without_raising(self):
        with mock.patch("sys.stderr", new_callable=io.StringIO) as fake_stderr:
            result = release.notes_main(
                [
                    "--version", "v1",
                    "--summary", "/dev/null",
                    "--jar", "x",
                    "--previous",
                ]
            )
        self.assertEqual(2, result)
        self.assertIn("tools.ci notes", fake_stderr.getvalue())

    def test_bad_previous_value_never_reaches_the_rendered_body(self):
        # --previous followed by --jar (a recognised flag, not a tag) must
        # fail before compose_notes is ever called - otherwise "since --jar"
        # would be printed straight into a real release page.
        with mock.patch("sys.stderr", new_callable=io.StringIO), mock.patch(
            "sys.stdout", new_callable=io.StringIO
        ) as fake_stdout:
            result = release.notes_main(
                [
                    "--version", "v1",
                    "--summary", "/dev/null",
                    "--jar", "x",
                    "--previous", "--jar", "x",
                ]
            )
        self.assertEqual(2, result)
        self.assertEqual("", fake_stdout.getvalue())

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
            # --previous was absent, which is the valid first-ever-release
            # case (compose_notes' previous_tag=None branch), not a failure.
            self.assertIn("Rebuilt for", printed)
        finally:
            os.remove(summary_path)

    def test_valid_previous_is_used_when_there_are_no_changes(self):
        handle, summary_path = tempfile.mkstemp(suffix=".json")
        with os.fdopen(handle, "w") as out:
            json.dump({"changed": []}, out)
        try:
            # repository_changes is stubbed rather than left to run: it shells
            # out to git against the checkout the suite happens to be sitting
            # in, so without this the assertion below depends on this
            # repository's history at the moment the test runs. Its own
            # behaviour is covered by RepositoryChangesTest.
            with mock.patch.object(
                release.pins, "load", return_value=PROPS
            ), mock.patch(
                "tools.ci.upstream.sha256_file", return_value="abc123"
            ), mock.patch.object(
                release, "repository_changes", return_value=[]
            ), mock.patch(
                "sys.stdout", new_callable=io.StringIO
            ) as fake_stdout:
                result = release.notes_main(
                    [
                        "--version", "v3.55.1",
                        "--summary", summary_path,
                        "--jar", "dist/stepss.jar",
                        "--previous", "v3.55",
                    ]
                )
            self.assertEqual(0, result)
            self.assertIn(
                "Manual release. No component changes since v3.55.",
                fake_stdout.getvalue(),
            )
        finally:
            os.remove(summary_path)


if __name__ == "__main__":
    unittest.main()
