import json
import os
import shutil
import tempfile
import unittest

from tools.ci import bump, pins, upstream

PROPERTIES = """\
# header comment
ramses.version=3.55
ramses.repo=SPS-L/stepss-ramses
ramses.tag=v3.55
ramses.windows.asset=ramses-windows-x86_64-v3.55.zip
ramses.windows.asset.pattern=ramses-windows-x86_64-v@VERSION@.zip
ramses.windows.sha256=aaa
ramses.linux.asset=ramses-linux-x86_64-v3.55.tar.gz
ramses.linux.asset.pattern=ramses-linux-x86_64-v@VERSION@.tar.gz
ramses.linux.sha256=bbb
ramses.macos.asset=ramses-macos-arm64-v3.55.tar.gz
ramses.macos.asset.pattern=ramses-macos-arm64-v@VERSION@.tar.gz
ramses.macos.sha256=ccc

dyngraph.version=1.1.0
dyngraph.repo=SPS-L/stepss-dyngraph
dyngraph.tag=v1.1.0
dyngraph.windows.asset=dyngraph-windows-x86_64-v1.1.0.zip
dyngraph.windows.asset.pattern=dyngraph-windows-x86_64-v@VERSION@.zip
dyngraph.windows.sha256=ddd
dyngraph.linux.asset=dyngraph-linux-x86_64-v1.1.0.tar.gz
dyngraph.linux.asset.pattern=dyngraph-linux-x86_64-v@VERSION@.tar.gz
dyngraph.linux.sha256=eee
dyngraph.macos.asset=dyngraph-macos-arm64-v1.1.0.tar.gz
dyngraph.macos.asset.pattern=dyngraph-macos-arm64-v@VERSION@.tar.gz
dyngraph.macos.sha256=fff

uramses.version=3.55
uramses.repo=SPS-L/stepss-uramses
uramses.tag=v3.55
uramses.source.url=https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v3.55.zip
uramses.source.url.pattern=https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v@VERSION@.zip
uramses.manifest.sha256=old-manifest
"""

TOOLCHAIN = """\
"payload/ramses-windows-x86_64-v3.55.zip"
"payload/ramses-linux-x86_64-v3.55.tar.gz"
"payload/ramses-macos-arm64-v3.55.tar.gz"
"payload/dyngraph-windows-x86_64-v1.1.0.zip"
"payload/dyngraph-linux-x86_64-v1.1.0.tar.gz"
"payload/dyngraph-macos-arm64-v1.1.0.tar.gz"
"payload/uramses-kit-v3.55.zip"
"""


def release(tag, assets, name="Some release", body="Notes."):
    return upstream.Release(
        tag=tag,
        name=name,
        body=body,
        url="https://example.invalid/" + tag,
        published="2026-08-08",
        assets=assets,
    )


class FakeUpstream(object):
    """A stand-in for tools.ci.upstream with no network and no processes."""

    def __init__(self, releases):
        self.releases = releases
        self.downloaded = []

    def latest_release(self, repo):
        return self.releases[repo]

    def download_asset(self, repo, tag, asset, dest_dir):
        self.downloaded.append(asset)
        path = os.path.join(dest_dir, asset)
        if not os.path.isdir(dest_dir):
            os.makedirs(dest_dir)
        open(path, "wb").write(b"payload-" + asset.encode())
        return path

    def download_url(self, url, dest):
        self.downloaded.append(os.path.basename(dest))
        directory = os.path.dirname(dest)
        if directory and not os.path.isdir(directory):
            os.makedirs(directory)
        open(dest, "wb").write(b"uramses")
        return dest

    def sha256_file(self, path):
        return "sha-of-" + os.path.basename(path)

    def uramses_manifest_digest(self, archive, repo_root):
        return "new-manifest"


DYNGRAPH_1_2_0 = [
    "dyngraph-windows-x86_64-v1.2.0.zip",
    "dyngraph-linux-x86_64-v1.2.0.tar.gz",
    "dyngraph-macos-arm64-v1.2.0.tar.gz",
]


class BumpTestCase(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="bump-test-")
        self.addCleanup(shutil.rmtree, self.root, True)
        self.properties = os.path.join(self.root, "versions.properties")
        open(self.properties, "w").write(PROPERTIES)
        toolchain_dir = os.path.join(self.root, "src", "my", "ramses", "platform")
        os.makedirs(toolchain_dir)
        self.toolchain = os.path.join(toolchain_dir, "Toolchain.java")
        open(self.toolchain, "w").write(TOOLCHAIN)

    def upstream_with(self, dyngraph_tag="v1.1.0", dyngraph_assets=None, uramses="v3.55"):
        return FakeUpstream(
            {
                "SPS-L/stepss-ramses": release(
                    "v3.55",
                    [
                        "ramses-windows-x86_64-v3.55.zip",
                        "ramses-linux-x86_64-v3.55.tar.gz",
                        "ramses-macos-arm64-v3.55.tar.gz",
                    ],
                ),
                "SPS-L/stepss-dyngraph": release(
                    dyngraph_tag,
                    dyngraph_assets
                    or [
                        "dyngraph-windows-x86_64-v1.1.0.zip",
                        "dyngraph-linux-x86_64-v1.1.0.tar.gz",
                        "dyngraph-macos-arm64-v1.1.0.tar.gz",
                    ],
                ),
                "SPS-L/stepss-uramses": release(uramses, []),
            }
        )


class NoChangeTest(BumpTestCase):
    def test_reports_nothing_changed(self):
        summary = bump.run(self.root, up=self.upstream_with())
        self.assertEqual([], summary["changed"])

    def test_downloads_nothing(self):
        fake = self.upstream_with()
        bump.run(self.root, up=fake)
        self.assertEqual([], fake.downloaded)

    def test_leaves_the_files_untouched(self):
        original = open(self.properties).read()
        bump.run(self.root, up=self.upstream_with())
        self.assertEqual(original, open(self.properties).read())


class ChangeTest(BumpTestCase):
    def test_reports_the_changed_component(self):
        summary = bump.run(
            self.root,
            up=self.upstream_with("v1.2.0", DYNGRAPH_1_2_0),
        )
        self.assertEqual(1, len(summary["changed"]))
        entry = summary["changed"][0]
        self.assertEqual("dyngraph", entry["component"])
        self.assertEqual("1.1.0", entry["old_version"])
        self.assertEqual("1.2.0", entry["new_version"])

    def test_carries_the_upstream_notes(self):
        summary = bump.run(
            self.root, up=self.upstream_with("v1.2.0", DYNGRAPH_1_2_0)
        )
        self.assertEqual("Notes.", summary["changed"][0]["body"])
        self.assertEqual("2026-08-08", summary["changed"][0]["published"])

    def test_rewrites_the_pins(self):
        bump.run(self.root, up=self.upstream_with("v1.2.0", DYNGRAPH_1_2_0))
        props = pins.load(self.properties)
        self.assertEqual("1.2.0", props["dyngraph.version"])
        self.assertEqual("v1.2.0", props["dyngraph.tag"])
        self.assertEqual(
            "dyngraph-linux-x86_64-v1.2.0.tar.gz", props["dyngraph.linux.asset"]
        )
        self.assertEqual(
            "sha-of-dyngraph-linux-x86_64-v1.2.0.tar.gz",
            props["dyngraph.linux.sha256"],
        )

    def test_rewrites_toolchain(self):
        bump.run(self.root, up=self.upstream_with("v1.2.0", DYNGRAPH_1_2_0))
        text = open(self.toolchain).read()
        self.assertIn("payload/dyngraph-linux-x86_64-v1.2.0.tar.gz", text)
        self.assertNotIn("v1.1.0", text)

    def test_leaves_unchanged_components_alone(self):
        bump.run(self.root, up=self.upstream_with("v1.2.0", DYNGRAPH_1_2_0))
        props = pins.load(self.properties)
        self.assertEqual("3.55", props["ramses.version"])
        self.assertEqual("aaa", props["ramses.windows.sha256"])

    def test_preserves_the_header_comment(self):
        bump.run(self.root, up=self.upstream_with("v1.2.0", DYNGRAPH_1_2_0))
        self.assertIn("# header comment", open(self.properties).read())


class UramsesTest(BumpTestCase):
    def test_rewrites_url_and_manifest_digest(self):
        summary = bump.run(self.root, up=self.upstream_with(uramses="v3.60"))
        self.assertEqual("uramses", summary["changed"][0]["component"])
        props = pins.load(self.properties)
        self.assertEqual("3.60", props["uramses.version"])
        self.assertEqual(
            "https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v3.60.zip",
            props["uramses.source.url"],
        )
        self.assertEqual("new-manifest", props["uramses.manifest.sha256"])

    def test_rewrites_the_kit_resource_name(self):
        bump.run(self.root, up=self.upstream_with(uramses="v3.60"))
        self.assertIn("payload/uramses-kit-v3.60.zip", open(self.toolchain).read())


class MissingAssetTest(BumpTestCase):
    def test_renamed_asset_raises_rather_than_guessing(self):
        renamed = [
            "dyngraph-win64-1.2.0.zip",
            "dyngraph-linux-x86_64-v1.2.0.tar.gz",
            "dyngraph-macos-arm64-v1.2.0.tar.gz",
        ]
        with self.assertRaises(bump.AssetNotFound) as caught:
            bump.run(self.root, up=self.upstream_with("v1.2.0", renamed))
        message = str(caught.exception)
        self.assertIn("dyngraph-windows-x86_64-v1.2.0.zip", message)
        self.assertIn("dyngraph-win64-1.2.0.zip", message)

    def test_writes_nothing_when_an_asset_is_missing(self):
        original = open(self.properties).read()
        renamed = ["only-this.zip"]
        with self.assertRaises(bump.AssetNotFound):
            bump.run(self.root, up=self.upstream_with("v1.2.0", renamed))
        self.assertEqual(original, open(self.properties).read())


class DryRunTest(BumpTestCase):
    def test_reports_the_change(self):
        summary = bump.run(
            self.root, dry_run=True, up=self.upstream_with("v1.2.0", DYNGRAPH_1_2_0)
        )
        self.assertEqual("dyngraph", summary["changed"][0]["component"])

    def test_writes_nothing(self):
        original = open(self.properties).read()
        bump.run(
            self.root, dry_run=True, up=self.upstream_with("v1.2.0", DYNGRAPH_1_2_0)
        )
        self.assertEqual(original, open(self.properties).read())


if __name__ == "__main__":
    unittest.main()
