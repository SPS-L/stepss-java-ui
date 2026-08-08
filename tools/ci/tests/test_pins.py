import os
import tempfile
import unittest

from tools.ci import pins

SAMPLE = """\
# a comment
ramses.version=3.55
ramses.tag=v3.55
ramses.linux.asset=ramses-linux-x86_64-v3.55.tar.gz
ramses.linux.asset.pattern=ramses-linux-x86_64-v@VERSION@.tar.gz
ramses.windows.asset=ramses-windows-x86_64-v3.55.zip
ramses.windows.asset.pattern=ramses-windows-x86_64-v@VERSION@.zip
ramses.macos.asset=ramses-macos-arm64-v3.55.tar.gz
ramses.macos.asset.pattern=ramses-macos-arm64-v@VERSION@.tar.gz

helios.linux.asset.pattern=stepss-helios-linux-x86_64.tar.gz
helios.windows.asset.pattern=stepss-helios-windows-x64.zip
helios.macos.asset.pattern=stepss-helios-macos-arm64.tar.gz

uramses.source.url.pattern=https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v@VERSION@.zip
"""


def write_sample(text=SAMPLE):
    handle, path = tempfile.mkstemp(suffix=".properties")
    with os.fdopen(handle, "w") as out:
        out.write(text)
    return path


class LoadTest(unittest.TestCase):
    def test_reads_key_value_pairs(self):
        props = pins.load(write_sample())
        self.assertEqual("3.55", props["ramses.version"])

    def test_drops_comments_and_blank_lines(self):
        path = write_sample("# only a comment\n\n   \nx.y=1\n")
        self.assertEqual({"x.y": "1"}, pins.load(path))

    def test_keeps_values_containing_equals(self):
        path = write_sample("x.url=https://example.invalid/?a=b\n")
        self.assertEqual("https://example.invalid/?a=b", pins.load(path)["x.url"])


class TagVersionTest(unittest.TestCase):
    def test_version_of_strips_leading_v(self):
        self.assertEqual("3.55", pins.version_of("v3.55"))

    def test_version_of_leaves_bare_version_alone(self):
        self.assertEqual("3.55", pins.version_of("3.55"))

    def test_tag_of_adds_leading_v(self):
        self.assertEqual("v3.55", pins.tag_of("3.55"))

    def test_round_trips(self):
        self.assertEqual("v1.2.0", pins.tag_of(pins.version_of("v1.2.0")))


class ExpandTest(unittest.TestCase):
    def test_substitutes_the_token(self):
        self.assertEqual(
            "ramses-linux-x86_64-v3.56.tar.gz",
            pins.expand("ramses-linux-x86_64-v@VERSION@.tar.gz", "3.56"),
        )

    def test_leaves_version_free_pattern_untouched(self):
        self.assertEqual(
            "stepss-helios-linux-x86_64.tar.gz",
            pins.expand("stepss-helios-linux-x86_64.tar.gz", "1.5.0"),
        )


class AssetNamesTest(unittest.TestCase):
    def test_expands_every_platform(self):
        props = pins.load(write_sample())
        self.assertEqual(
            {
                "windows": "ramses-windows-x86_64-v3.56.zip",
                "linux": "ramses-linux-x86_64-v3.56.tar.gz",
                "macos": "ramses-macos-arm64-v3.56.tar.gz",
            },
            pins.asset_names(props, "ramses", "3.56"),
        )

    def test_version_free_names_are_stable_across_versions(self):
        props = pins.load(write_sample())
        self.assertEqual(
            pins.asset_names(props, "helios", "1.4.1"),
            pins.asset_names(props, "helios", "9.9.9"),
        )

    def test_uramses_has_no_platform_assets(self):
        props = pins.load(write_sample())
        self.assertEqual({}, pins.asset_names(props, "uramses", "3.55"))

    def test_missing_pattern_raises(self):
        with self.assertRaises(KeyError):
            pins.asset_names(pins.load(write_sample()), "dyngraph", "1.2.0")


class UramsesUrlTest(unittest.TestCase):
    def test_expands_the_tag_in_the_url(self):
        props = pins.load(write_sample())
        self.assertEqual(
            "https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v3.60.zip",
            pins.uramses_url(props, "3.60"),
        )


if __name__ == "__main__":
    unittest.main()
