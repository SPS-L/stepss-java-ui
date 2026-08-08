import json
import os
import subprocess
import tempfile
import unittest

from tools.ci import upstream

VIEW_JSON = json.dumps(
    {
        "tagName": "v1.2.0",
        "name": "Console interface on Windows",
        "body": "Windows build is now console.",
        "url": "https://github.com/SPS-L/stepss-dyngraph/releases/tag/v1.2.0",
        "publishedAt": "2026-08-08T09:14:22Z",
        "assets": [
            {"name": "dyngraph-linux-x86_64-v1.2.0.tar.gz"},
            {"name": "dyngraph-windows-x86_64-v1.2.0.zip"},
        ],
    }
)


class FakeRun(object):
    """Stands in for subprocess.run, recording calls and replaying stdout."""

    def __init__(self, stdout="", returncode=0):
        self.stdout = stdout
        self.returncode = returncode
        self.calls = []

    def __call__(self, argv, **kwargs):
        self.calls.append(argv)
        if self.returncode != 0:
            raise subprocess.CalledProcessError(self.returncode, argv, self.stdout)
        return subprocess.CompletedProcess(argv, 0, self.stdout, "")


class LatestReleaseTest(unittest.TestCase):
    def test_parses_the_release(self):
        run = FakeRun(VIEW_JSON)
        release = upstream.latest_release("SPS-L/stepss-dyngraph", run=run)
        self.assertEqual("v1.2.0", release.tag)
        self.assertEqual("Console interface on Windows", release.name)
        self.assertEqual("Windows build is now console.", release.body)

    def test_truncates_the_timestamp_to_a_date(self):
        release = upstream.latest_release("r", run=FakeRun(VIEW_JSON))
        self.assertEqual("2026-08-08", release.published)

    def test_flattens_assets_to_names(self):
        release = upstream.latest_release("r", run=FakeRun(VIEW_JSON))
        self.assertEqual(
            [
                "dyngraph-linux-x86_64-v1.2.0.tar.gz",
                "dyngraph-windows-x86_64-v1.2.0.zip",
            ],
            release.assets,
        )

    def test_asks_gh_for_the_repo(self):
        run = FakeRun(VIEW_JSON)
        upstream.latest_release("SPS-L/stepss-dyngraph", run=run)
        argv = run.calls[0]
        self.assertEqual("gh", argv[0])
        self.assertIn("SPS-L/stepss-dyngraph", argv)

    def test_null_body_becomes_empty_string(self):
        payload = json.loads(VIEW_JSON)
        payload["body"] = None
        release = upstream.latest_release("r", run=FakeRun(json.dumps(payload)))
        self.assertEqual("", release.body)


class DownloadAssetTest(unittest.TestCase):
    def test_returns_the_destination_path(self):
        run = FakeRun()
        path = upstream.download_asset("r", "v1.2.0", "a.tar.gz", "/tmp/x", run=run)
        self.assertEqual(os.path.join("/tmp/x", "a.tar.gz"), path)

    def test_passes_the_pattern_and_dir_to_gh(self):
        run = FakeRun()
        upstream.download_asset("r", "v1.2.0", "a.tar.gz", "/tmp/x", run=run)
        argv = run.calls[0]
        self.assertIn("a.tar.gz", argv)
        self.assertIn("/tmp/x", argv)


class Sha256Test(unittest.TestCase):
    def test_hashes_known_content(self):
        handle, path = tempfile.mkstemp()
        with os.fdopen(handle, "wb") as out:
            out.write(b"abc")
        self.assertEqual(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            upstream.sha256_file(path),
        )


class UramsesManifestTest(unittest.TestCase):
    def test_parses_the_computed_digest(self):
        run = FakeRun("uramses.manifest.sha256=deadbeef\nRetained 42 files.\n")
        self.assertEqual(
            "deadbeef", upstream.uramses_manifest_digest("/tmp/u.zip", ".", run=run)
        )

    def test_compiles_before_running(self):
        run = FakeRun("uramses.manifest.sha256=deadbeef\n")
        upstream.uramses_manifest_digest("/tmp/u.zip", ".", run=run)
        self.assertEqual("javac", run.calls[0][0])
        self.assertEqual("java", run.calls[1][0])

    def test_missing_digest_line_raises(self):
        run = FakeRun("something else entirely\n")
        with self.assertRaises(ValueError):
            upstream.uramses_manifest_digest("/tmp/u.zip", ".", run=run)


if __name__ == "__main__":
    unittest.main()
