# Component Release CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A daily GitHub Actions run that re-pins `stepss.jar` to the newest RAMSES, Helios, DYNGRAPH, CODEGEN and URAMSES releases, builds and verifies the jar, and publishes a STEPSS release carrying the upstream release notes.

**Architecture:** A Python package under `tools/ci/` holds all the logic and is runnable locally; `.github/workflows/release.yml` is a thin sequence of calls into it. All network access lives in one module (`upstream.py`) so every other module is unit-tested without network. The existing build guards — `PayloadManifestCheck` in `ant jar`, and `ToolchainDump` against the built jar — are the gate that makes unattended publishing safe, and both run before any tag exists.

**Tech Stack:** Python 3 (standard library only), GitHub Actions, `gh` CLI (preinstalled on `ubuntu-latest`), Apache Ant, Java 11 (Temurin).

**Spec:** `docs/superpowers/specs/2026-08-08-component-release-ci-design.md`

## Global Constraints

- **Python: standard library only.** No pip installs anywhere. `ubuntu-latest` ships Python 3 and `gh`.
- **Java 11.** `javac.source=11`, `javac.target=11` in `nbproject/project.properties`. The workflow uses Temurin 11, not the runner default.
- **Deviation from spec, already agreed:** the bumper is `tools/ci/` (Python), invoked as `python3 -m tools.ci <subcommand>`, not `tools/bump-components.sh`. Everything else in the spec stands.
- **Placeholder token is `@VERSION@`**, never `${VERSION}` — Ant expands `${…}` when it loads `versions.properties`.
- **`versions.properties` is rewritten line-by-line, never regenerated.** The file is heavily commented and those comments are load-bearing documentation. A writer that reformats it or drops comments is a defect.
- **Writers only ever update keys that already exist.** Adding a key silently is how a typo becomes an unnoticed no-op. Missing key → raise.
- **Never guess at an asset name.** If the name a pattern produces is absent from the upstream release, fail with both the expected name and the actual asset list.
- **Auth is `STEPSS_TOKEN`** for anything touching the component repos. Actions' default `GITHUB_TOKEN` is scoped to this repo and cannot read them. `GITHUB_TOKEN` is used only for issues in this repo.
- **Tests never hit the network.** `upstream.py` takes an injectable runner; every other module is pure.
- **Run all tests with:** `python3 -m unittest discover -s tools/ci/tests -t .` from the repository root.
- **The five components are** `ramses`, `helios`, `dyngraph`, `codegen`, `uramses`. The three platforms are `windows`, `linux`, `macos`. URAMSES has no per-platform assets — it is a single source archive.

## File Structure

| File | Responsibility |
|---|---|
| `versions.properties` | *Modify.* Gains `*.asset.pattern` and `uramses.source.url.pattern` keys. |
| `tools/__init__.py` | *Create.* Makes `python3 -m tools.ci` work. Empty. |
| `tools/ci/__init__.py` | *Create.* Empty. |
| `tools/ci/pins.py` | *Create.* Read/write `versions.properties`, expand patterns, rewrite `Toolchain.java`. Pure. |
| `tools/ci/upstream.py` | *Create.* The only module that touches the network or spawns `gh`/`javac`. |
| `tools/ci/bump.py` | *Create.* Orchestrates detect → download → digest → rewrite, emits the change summary. |
| `tools/ci/release.py` | *Create.* Version derivation, README line, release-notes composition. Pure. |
| `tools/ci/notify.py` | *Create.* Create-or-update a GitHub issue. |
| `tools/ci/__main__.py` | *Create.* CLI dispatch for the workflow. |
| `tools/ci/tests/*.py` | *Create.* Unit tests, one module per source module. |
| `.github/workflows/release.yml` | *Create.* The workflow. |
| `README.md` | *Modify.* Document the release automation. |

**The change summary** is the contract between Task 4 (producer) and Tasks 6 and 8 (consumers). It is JSON on stdout:

```json
{
  "changed": [
    {
      "component": "dyngraph",
      "old_version": "1.1.0",
      "new_version": "1.2.0",
      "old_tag": "v1.1.0",
      "new_tag": "v1.2.0",
      "title": "Console interface on Windows",
      "body": "full upstream release body, verbatim",
      "url": "https://github.com/SPS-L/stepss-dyngraph/releases/tag/v1.2.0",
      "published": "2026-08-08"
    }
  ]
}
```

`"changed": []` means nothing moved. The list is ordered as `ramses, helios, dyngraph, codegen, uramses`.

---

### Task 1: Asset-name patterns and pin reading

**Files:**
- Modify: `versions.properties`
- Create: `tools/__init__.py`, `tools/ci/__init__.py`, `tools/ci/pins.py`
- Test: `tools/ci/tests/__init__.py`, `tools/ci/tests/test_pins.py`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `pins.COMPONENTS: tuple[str, ...]` — `("ramses", "helios", "dyngraph", "codegen", "uramses")`
  - `pins.PLATFORMS: tuple[str, ...]` — `("windows", "linux", "macos")`
  - `pins.load(path: str) -> dict[str, str]`
  - `pins.version_of(tag: str) -> str` — `"v3.55"` → `"3.55"`
  - `pins.tag_of(version: str) -> str` — `"3.55"` → `"v3.55"`
  - `pins.expand(pattern: str, version: str) -> str`
  - `pins.asset_names(props: dict[str, str], component: str, version: str) -> dict[str, str]` — platform → filename, `{}` for `uramses`
  - `pins.uramses_url(props: dict[str, str], version: str) -> str`

- [ ] **Step 1: Write the failing test**

Create `tools/ci/tests/__init__.py` (empty) and `tools/ci/tests/test_pins.py`:

```python
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'tools.ci'`

- [ ] **Step 3: Write the minimal implementation**

Create empty `tools/__init__.py` and `tools/ci/__init__.py`, then `tools/ci/pins.py`:

```python
"""Read and rewrite the component pins in versions.properties.

Pure: no network, no subprocesses. Everything here is driven by the file's
own contents, so the module is fully unit-testable.
"""

COMPONENTS = ("ramses", "helios", "dyngraph", "codegen", "uramses")
PLATFORMS = ("windows", "linux", "macos")

VERSION_TOKEN = "@VERSION@"


def load(path):
    """Parses a Java properties file into a dict, dropping comments."""
    props = {}
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def version_of(tag):
    """'v3.55' -> '3.55'. A bare version passes through unchanged."""
    return tag[1:] if tag.startswith("v") else tag


def tag_of(version):
    """'3.55' -> 'v3.55'. Every component tags releases this way."""
    return version if version.startswith("v") else "v" + version


def expand(pattern, version):
    return pattern.replace(VERSION_TOKEN, version)


def asset_names(props, component, version):
    """Platform -> expected asset filename for this component at this version.

    URAMSES ships a source archive rather than per-platform assets, so it
    maps to nothing here; see uramses_url.
    """
    if component == "uramses":
        return {}
    names = {}
    for platform in PLATFORMS:
        key = "%s.%s.asset.pattern" % (component, platform)
        if key not in props:
            raise KeyError("Missing asset pattern: " + key)
        names[platform] = expand(props[key], version)
    return names


def uramses_url(props, version):
    key = "uramses.source.url.pattern"
    if key not in props:
        raise KeyError("Missing asset pattern: " + key)
    return expand(props[key], version)
```

- [ ] **Step 4: Add the pattern keys to `versions.properties`**

Insert each `*.asset.pattern` line directly beneath the `*.asset` line it describes, keeping every existing comment and blank line exactly where it is. The full set to add:

```properties
ramses.windows.asset.pattern=ramses-windows-x86_64-v@VERSION@.zip
ramses.linux.asset.pattern=ramses-linux-x86_64-v@VERSION@.tar.gz
ramses.macos.asset.pattern=ramses-macos-arm64-v@VERSION@.tar.gz

helios.windows.asset.pattern=stepss-helios-windows-x64.zip
helios.linux.asset.pattern=stepss-helios-linux-x86_64.tar.gz
helios.macos.asset.pattern=stepss-helios-macos-arm64.tar.gz

dyngraph.windows.asset.pattern=dyngraph-windows-x86_64-v@VERSION@.zip
dyngraph.linux.asset.pattern=dyngraph-linux-x86_64-v@VERSION@.tar.gz
dyngraph.macos.asset.pattern=dyngraph-macos-arm64-v@VERSION@.tar.gz

codegen.windows.asset.pattern=codegen-windows-x86_64-v@VERSION@.zip
codegen.linux.asset.pattern=codegen-linux-x86_64-v@VERSION@.tar.gz
codegen.macos.asset.pattern=codegen-macos-arm64-v@VERSION@.tar.gz

uramses.source.url.pattern=https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v@VERSION@.zip
```

Also replace the file's opening comment block, which documents the old manual
procedure, with:

```properties
# Pinned component releases, maintained by .github/workflows/release.yml.
#
# Each component has a `.asset.pattern` beside its `.asset`: the pattern is
# what CI expands (@VERSION@ -> the new version) to work out what the next
# release's asset will be called. Helios' names carry no version, so its
# patterns are literals. @VERSION@ rather than ${VERSION} because Ant expands
# ${...} when it loads this file.
#
# To bump by hand: run `python3 -m tools.ci bump` to rewrite this file and the
# matching resource names in src/my/ramses/platform/Toolchain.java together.
# Editing only this file leaves Toolchain.java naming the old asset, which
# `ant jar` catches via PayloadManifestCheck.
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: PASS, 14 tests.

- [ ] **Step 6: Verify the Ant build still loads the file**

Run: `ant -q jar`
Expected: BUILD SUCCESSFUL. `payload-cache/` is already warm for every pinned asset, so this needs no network. The new keys are unused by Ant and must not disturb it. If this fails with a property error, the pattern lines contain a `${` — fix and re-run.

- [ ] **Step 7: Commit**

```bash
git add versions.properties tools/__init__.py tools/ci/__init__.py tools/ci/pins.py tools/ci/tests/__init__.py tools/ci/tests/test_pins.py
git commit -m "Add asset-name patterns and a reader for the component pins"
```

---

### Task 2: Comment-preserving writes and Toolchain.java rewriting

**Files:**
- Modify: `tools/ci/pins.py`
- Test: `tools/ci/tests/test_pins.py`

**Interfaces:**
- Consumes: `pins.load` from Task 1.
- Produces:
  - `pins.set_values(path: str, updates: dict[str, str]) -> None` — in-place, comment-preserving. Raises `KeyError` if any key is absent.
  - `pins.rewrite_toolchain(path: str, renames: dict[str, str]) -> int` — `{old_filename: new_filename}`; returns the number of replacements made. Raises `ValueError` if a rename's old name is absent from the file.

- [ ] **Step 1: Write the failing test**

Append to `tools/ci/tests/test_pins.py`:

```python
TOOLCHAIN = """\
package my.ramses.platform;

public final class Toolchain {
    static void build() {
        s.add(new ToolSpec(RAMSES)
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/ramses-linux-x86_64-v3.55.tar.gz", ToolSpec.Kind.TGZ,
                "ramses", "dynsim", true)));
        s.add(new ToolSpec(URAMSES)
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/uramses-kit-v3.55.zip", URAMSES_DIR,
                java.util.Arrays.asList("build/"))));
    }
}
"""


def write_toolchain(text=TOOLCHAIN):
    handle, path = tempfile.mkstemp(suffix=".java")
    with os.fdopen(handle, "w") as out:
        out.write(text)
    return path


class SetValuesTest(unittest.TestCase):
    def test_updates_the_named_key(self):
        path = write_sample()
        pins.set_values(path, {"ramses.version": "3.56"})
        self.assertEqual("3.56", pins.load(path)["ramses.version"])

    def test_leaves_other_keys_alone(self):
        path = write_sample()
        pins.set_values(path, {"ramses.version": "3.56"})
        self.assertEqual("v3.55", pins.load(path)["ramses.tag"])

    def test_preserves_comments_and_blank_lines(self):
        path = write_sample()
        pins.set_values(path, {"ramses.version": "3.56"})
        with open(path) as handle:
            text = handle.read()
        self.assertIn("# a comment", text)
        self.assertIn("\n\nhelios.linux.asset.pattern=", text)

    def test_preserves_line_count(self):
        path = write_sample()
        before = len(open(path).readlines())
        pins.set_values(path, {"ramses.version": "3.56"})
        self.assertEqual(before, len(open(path).readlines()))

    def test_unknown_key_raises_and_writes_nothing(self):
        path = write_sample()
        original = open(path).read()
        with self.assertRaises(KeyError):
            pins.set_values(path, {"ramses.version": "3.56", "nope.version": "1"})
        self.assertEqual(original, open(path).read())


class RewriteToolchainTest(unittest.TestCase):
    def test_replaces_the_resource_name(self):
        path = write_toolchain()
        count = pins.rewrite_toolchain(
            path,
            {"ramses-linux-x86_64-v3.55.tar.gz": "ramses-linux-x86_64-v3.56.tar.gz"},
        )
        self.assertEqual(1, count)
        self.assertIn("payload/ramses-linux-x86_64-v3.56.tar.gz", open(path).read())

    def test_leaves_the_old_name_behind_nowhere(self):
        path = write_toolchain()
        pins.rewrite_toolchain(
            path,
            {"ramses-linux-x86_64-v3.55.tar.gz": "ramses-linux-x86_64-v3.56.tar.gz"},
        )
        self.assertNotIn("ramses-linux-x86_64-v3.55.tar.gz", open(path).read())

    def test_handles_the_uramses_kit_name(self):
        path = write_toolchain()
        count = pins.rewrite_toolchain(
            path, {"uramses-kit-v3.55.zip": "uramses-kit-v3.60.zip"}
        )
        self.assertEqual(1, count)
        self.assertIn("payload/uramses-kit-v3.60.zip", open(path).read())

    def test_unchanged_name_is_a_no_op(self):
        path = write_toolchain()
        count = pins.rewrite_toolchain(
            path,
            {"stepss-helios-linux-x86_64.tar.gz": "stepss-helios-linux-x86_64.tar.gz"},
        )
        self.assertEqual(0, count)

    def test_absent_old_name_raises_and_writes_nothing(self):
        path = write_toolchain()
        original = open(path).read()
        with self.assertRaises(ValueError):
            pins.rewrite_toolchain(path, {"not-there-v1.zip": "not-there-v2.zip"})
        self.assertEqual(original, open(path).read())
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: FAIL with `AttributeError: module 'tools.ci.pins' has no attribute 'set_values'`

- [ ] **Step 3: Write the implementation**

Append to `tools/ci/pins.py`:

```python
def set_values(path, updates):
    """Updates existing keys in place, preserving comments and layout.

    versions.properties carries the documentation for its own format, so it is
    rewritten line by line rather than regenerated. Keys are only ever updated,
    never appended: a key that is absent means the caller has a typo, and
    appending it would produce a file the Ant build silently ignores.
    """
    with open(path, "r", encoding="utf-8") as handle:
        lines = handle.readlines()

    remaining = dict(updates)
    out = []
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key = stripped.split("=", 1)[0].strip()
            if key in remaining:
                out.append("%s=%s\n" % (key, remaining.pop(key)))
                continue
        out.append(line)

    if remaining:
        raise KeyError("Not present in %s: %s" % (path, ", ".join(sorted(remaining))))

    with open(path, "w", encoding="utf-8") as handle:
        handle.writelines(out)


def rewrite_toolchain(path, renames):
    """Repoints Toolchain.java's payload resource names at the new assets.

    Exact literal replacement, never pattern matching: the old names come from
    the pins the caller just read, so a replacement either matches exactly or
    is a bug worth failing on. An old name that is missing means Toolchain.java
    and versions.properties had already drifted apart.
    """
    with open(path, "r", encoding="utf-8") as handle:
        text = handle.read()

    original = text
    replacements = 0
    for old, new in renames.items():
        if old == new:
            continue
        needle = PAYLOAD_PREFIX + old
        if needle not in text:
            raise ValueError("%s does not name %s" % (path, needle))
        replacements += text.count(needle)
        text = text.replace(needle, PAYLOAD_PREFIX + new)

    if text != original:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
    return replacements
```

Add near `VERSION_TOKEN`:

```python
PAYLOAD_PREFIX = "payload/"
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: PASS, 24 tests.

- [ ] **Step 5: Commit**

```bash
git add tools/ci/pins.py tools/ci/tests/test_pins.py
git commit -m "Rewrite pins and Toolchain resource names without disturbing layout"
```

---

### Task 3: The upstream boundary

**Files:**
- Create: `tools/ci/upstream.py`
- Test: `tools/ci/tests/test_upstream.py`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `upstream.Release` — `namedtuple("Release", "tag name body url published assets")`, where `assets` is a `list[str]` of filenames and `published` is `"YYYY-MM-DD"`.
  - `upstream.latest_release(repo: str, run=subprocess.run) -> Release`
  - `upstream.download_asset(repo: str, tag: str, asset: str, dest_dir: str, run=subprocess.run) -> str` — returns the downloaded path
  - `upstream.download_url(url: str, dest: str, opener=urllib.request.urlopen) -> str`
  - `upstream.sha256_file(path: str) -> str` — lowercase hex
  - `upstream.uramses_manifest_digest(archive: str, repo_root: str, run=subprocess.run) -> str`

Every function that shells out takes an injectable `run`, defaulting to `subprocess.run`. That injection point is the whole reason this module is separate: it is the only place tests have to fake.

- [ ] **Step 1: Write the failing test**

Create `tools/ci/tests/test_upstream.py`:

```python
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'tools.ci.upstream'`

- [ ] **Step 3: Write the implementation**

Create `tools/ci/upstream.py`:

```python
"""Everything that leaves the machine.

Isolated in one module so the rest of the package is pure and testable. Each
function that spawns a process takes an injectable `run`; tests pass a fake and
never touch the network.

The component repos are private, so their assets are fetched through `gh`,
which resolves the numeric asset id and authenticates in one step. URAMSES is
public and its source archive comes over plain HTTPS, mirroring what
build.xml's fetch-uramses target does.
"""

import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import urllib.request
from collections import namedtuple

Release = namedtuple("Release", "tag name body url published assets")

_VIEW_FIELDS = "tagName,name,body,url,publishedAt,assets"


def latest_release(repo, run=subprocess.run):
    """The repo's latest release, as GitHub defines latest."""
    result = run(
        ["gh", "release", "view", "--repo", repo, "--json", _VIEW_FIELDS],
        check=True,
        capture_output=True,
        text=True,
    )
    data = json.loads(result.stdout)
    return Release(
        tag=data["tagName"],
        name=data.get("name") or "",
        body=data.get("body") or "",
        url=data["url"],
        published=(data.get("publishedAt") or "")[:10],
        assets=[asset["name"] for asset in data.get("assets") or []],
    )


def download_asset(repo, tag, asset, dest_dir, run=subprocess.run):
    if not os.path.isdir(dest_dir):
        os.makedirs(dest_dir)
    run(
        [
            "gh", "release", "download", tag,
            "--repo", repo,
            "--pattern", asset,
            "--dir", dest_dir,
            "--clobber",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return os.path.join(dest_dir, asset)


def download_url(url, dest, opener=urllib.request.urlopen):
    directory = os.path.dirname(dest)
    if directory and not os.path.isdir(directory):
        os.makedirs(directory)
    response = opener(url)
    try:
        with open(dest, "wb") as out:
            shutil.copyfileobj(response, out)
    finally:
        response.close()
    return dest


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 16), b""):
            digest.update(chunk)
    return digest.hexdigest()


def uramses_manifest_digest(archive, repo_root, run=subprocess.run):
    """The content-manifest digest UramsesKitPack computes for an archive.

    URAMSES is pinned on a manifest over the files the kit retains, not on the
    archive's own bytes: GitHub's generated source archives are not guaranteed
    byte-stable, so a re-compression upstream would otherwise read as tampering.
    UramsesKitPack already knows how to compute it; COMPUTE mode prints it.
    """
    workdir = tempfile.mkdtemp(prefix="uramses-manifest-")
    try:
        source = os.path.join(
            repo_root, "src", "my", "ramses", "platform", "UramsesKitPack.java"
        )
        run(["javac", "-d", workdir, source], check=True, capture_output=True, text=True)
        result = run(
            [
                "java", "-cp", workdir,
                "my.ramses.platform.UramsesKitPack",
                archive,
                os.path.join(workdir, "out.zip"),
                "COMPUTE",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        for line in result.stdout.splitlines():
            if line.startswith("uramses.manifest.sha256="):
                return line.split("=", 1)[1].strip()
        raise ValueError(
            "UramsesKitPack COMPUTE printed no digest line. Output was:\n"
            + result.stdout
        )
    finally:
        shutil.rmtree(workdir, ignore_errors=True)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: PASS, 35 tests.

- [ ] **Step 5: Verify the digest helper against the real archive**

`payload-cache/stepss-uramses-3.55.zip` is present and `versions.properties`
pins `uramses.manifest.sha256=404eb8953b83054fc9854874cce1e4b070104842ad64475156beaadc535cd0be`.

Run:
```bash
python3 -c "from tools.ci import upstream; print(upstream.uramses_manifest_digest('payload-cache/stepss-uramses-3.55.zip', '.'))"
```
Expected: exactly `404eb8953b83054fc9854874cce1e4b070104842ad64475156beaadc535cd0be`. A different value means the parser or the invocation is wrong — stop and fix before continuing, because this digest is what the URAMSES pin rests on.

- [ ] **Step 6: Commit**

```bash
git add tools/ci/upstream.py tools/ci/tests/test_upstream.py
git commit -m "Add the upstream boundary: gh queries, downloads, digests"
```

---

### Task 4: Bump orchestration

**Files:**
- Create: `tools/ci/bump.py`, `tools/ci/__main__.py`
- Test: `tools/ci/tests/test_bump.py`

**Interfaces:**
- Consumes: `pins.load`, `pins.set_values`, `pins.rewrite_toolchain`, `pins.asset_names`, `pins.uramses_url`, `pins.version_of`, `pins.tag_of` (Tasks 1–2); `upstream.Release`, `upstream.latest_release`, `upstream.download_asset`, `upstream.download_url`, `upstream.sha256_file`, `upstream.uramses_manifest_digest` (Task 3).
- Produces:
  - `bump.REPOS: dict[str, str]` — component → `owner/repo`, read from the pins at runtime.
  - `bump.run(repo_root: str, dry_run: bool = False, up=upstream) -> dict` — returns `{"changed": [...]}` matching the change-summary schema in File Structure.
- CLI: `python3 -m tools.ci bump [--dry-run]` prints that dict as JSON to stdout.

`up` is the injected upstream module — tests pass a fake with the same five function names.

- [ ] **Step 1: Write the failing test**

Create `tools/ci/tests/test_bump.py`:

```python
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'tools.ci.bump'`

- [ ] **Step 3: Write the implementation**

Create `tools/ci/bump.py`:

```python
"""Re-pin the build to the newest component releases.

Detects, downloads and rewrites; it does not build, commit, tag or publish.
All validation happens before the first write, so a run that fails leaves the
working tree exactly as it found it.
"""

import json
import os

from . import pins, upstream as _upstream

PAYLOAD_CACHE = "payload-cache"
TOOLCHAIN = os.path.join("src", "my", "ramses", "platform", "Toolchain.java")
PROPERTIES = "versions.properties"


class AssetNotFound(Exception):
    """An expected asset name is absent from the upstream release.

    Never recovered from by matching some other asset: in a pipeline that
    publishes without review, silently picking a different file is the one
    failure that reaches users as a wrong binary.
    """


def run(repo_root, dry_run=False, up=_upstream):
    properties_path = os.path.join(repo_root, PROPERTIES)
    toolchain_path = os.path.join(repo_root, TOOLCHAIN)
    cache = os.path.join(repo_root, PAYLOAD_CACHE)
    props = pins.load(properties_path)

    changed = []
    updates = {}
    renames = {}

    for component in pins.COMPONENTS:
        repo = props.get("%s.repo" % component)
        if repo is None:
            continue
        release = up.latest_release(repo)
        new_version = pins.version_of(release.tag)
        old_version = props["%s.version" % component]
        if new_version == old_version:
            continue

        if component == "uramses":
            _plan_uramses(
                repo_root, cache, props, new_version, updates, renames, up
            )
        else:
            _plan_component(
                component, cache, props, release, old_version, new_version,
                updates, renames, up,
            )

        updates["%s.version" % component] = new_version
        updates["%s.tag" % component] = release.tag
        changed.append(
            {
                "component": component,
                "old_version": old_version,
                "new_version": new_version,
                "old_tag": pins.tag_of(old_version),
                "new_tag": release.tag,
                "title": release.name,
                "body": release.body,
                "url": release.url,
                "published": release.published,
            }
        )

    if changed and not dry_run:
        pins.set_values(properties_path, updates)
        pins.rewrite_toolchain(toolchain_path, renames)

    return {"changed": changed}


def _plan_component(
    component, cache, props, release, old_version, new_version, updates, renames, up
):
    """Validates and downloads one component's assets, recording the rewrites.

    Every expected name is checked against the release's asset list before
    anything is downloaded, so a partial rename upstream fails fast rather
    than after three downloads.
    """
    expected = pins.asset_names(props, component, new_version)
    for platform, name in sorted(expected.items()):
        if name not in release.assets:
            raise AssetNotFound(
                "%s %s has no asset named %s (platform %s).\n"
                "The release contains: %s\n"
                "Either the upstream naming changed - update "
                "%s.%s.asset.pattern in versions.properties - or the release "
                "is incomplete."
                % (
                    component, release.tag, name, platform,
                    ", ".join(release.assets) or "(none)",
                    component, platform,
                )
            )

    old_names = pins.asset_names(props, component, old_version)
    for platform, name in sorted(expected.items()):
        path = up.download_asset(props["%s.repo" % component], release.tag, name, cache)
        updates["%s.%s.asset" % (component, platform)] = name
        updates["%s.%s.sha256" % (component, platform)] = up.sha256_file(path)
        renames[old_names[platform]] = name


def _plan_uramses(repo_root, cache, props, new_version, updates, renames, up):
    """URAMSES is public and pinned on a content manifest, not on the archive.

    See upstream.uramses_manifest_digest for why the archive's own bytes are
    not a usable pin.
    """
    url = pins.uramses_url(props, new_version)
    dest = os.path.join(cache, "stepss-uramses-%s.zip" % new_version)
    up.download_url(url, dest)
    updates["uramses.source.url"] = url
    updates["uramses.manifest.sha256"] = up.uramses_manifest_digest(dest, repo_root)
    old_version = props["uramses.version"]
    renames["uramses-kit-v%s.zip" % old_version] = "uramses-kit-v%s.zip" % new_version


def main(argv):
    dry_run = "--dry-run" in argv
    summary = run(os.getcwd(), dry_run=dry_run)
    print(json.dumps(summary, indent=2))
    return 0
```

Create `tools/ci/__main__.py`:

```python
"""CLI entry point: python3 -m tools.ci <subcommand>"""

import sys

from . import bump

COMMANDS = {
    "bump": bump.main,
}


def main(argv):
    if len(argv) < 2 or argv[1] not in COMMANDS:
        sys.stderr.write(
            "Usage: python3 -m tools.ci <%s> [options]\n" % "|".join(sorted(COMMANDS))
        )
        return 2
    return COMMANDS[argv[1]](argv[2:])


if __name__ == "__main__":
    sys.exit(main(sys.argv))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: PASS, 50 tests.

- [ ] **Step 5: Verify the dry run against the real repos**

Run: `python3 -m tools.ci bump --dry-run`
Expected: JSON on stdout. `git status` must show no modifications afterwards. If the five repos are all still at their pinned versions the output is `{"changed": []}`; if any has moved on, the entry describes it. Either is a pass — a non-empty diff in `git status` is not.

This step needs `gh` authenticated with SPS-L access. If it fails on auth rather than on logic, note it and move on; the workflow provides `STEPSS_TOKEN`.

- [ ] **Step 6: Commit**

```bash
git add tools/ci/bump.py tools/ci/__main__.py tools/ci/tests/test_bump.py
git commit -m "Detect component releases and re-pin the build to them"
```

---

### Task 5: Version derivation and the README line

**Files:**
- Create: `tools/ci/release.py`
- Modify: `tools/ci/__main__.py`
- Test: `tools/ci/tests/test_release.py`

**Interfaces:**
- Consumes: `pins.load` (Task 1).
- Produces:
  - `release.next_version(ramses_version: str, existing_tags: Iterable[str]) -> str` — returns a tag, e.g. `"v3.55"` or `"v3.55.1"`
  - `release.update_readme(path: str, version: str) -> bool` — `version` may be `"v3.55.1"` or `"3.55.1"`; returns whether the file changed. Raises `ValueError` if the anchor line is absent.
- CLI: `python3 -m tools.ci next-version` prints the tag; `python3 -m tools.ci update-readme --version <tag>` rewrites line 7.

- [ ] **Step 1: Write the failing test**

Create `tools/ci/tests/test_release.py`:

```python
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'tools.ci.release'`

- [ ] **Step 3: Write the implementation**

Create `tools/ci/release.py`:

```python
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
```

Extend `COMMANDS` in `tools/ci/__main__.py`:

```python
from . import bump, release

COMMANDS = {
    "bump": bump.main,
    "next-version": release.next_version_main,
    "update-readme": release.update_readme_main,
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: PASS, 63 tests.

- [ ] **Step 5: Verify against the real README**

Run: `python3 -m tools.ci next-version`
Expected: `v3.55` (this repo has no tags yet).

Run: `python3 -m tools.ci update-readme --version v3.55.9` then `git diff README.md`
Expected: exactly one changed line, line 7, now reading `Current release: **3.55.9**.` The known-limitation paragraph on line 27 must be untouched.

Then revert it: `git checkout README.md`

- [ ] **Step 6: Commit**

```bash
git add tools/ci/release.py tools/ci/__main__.py tools/ci/tests/test_release.py
git commit -m "Derive the release version from tags and keep the README line current"
```

---

### Task 6: Release notes

**Files:**
- Modify: `tools/ci/release.py`, `tools/ci/__main__.py`
- Test: `tools/ci/tests/test_release.py`

**Interfaces:**
- Consumes: `pins.load` (Task 1); the change-summary schema from Task 4.
- Produces:
  - `release.compose_notes(version: str, props: dict[str, str], changed: list[dict], jar_sha256: str, previous_tag: str | None) -> str`
- CLI: `python3 -m tools.ci notes --version <tag> --summary <path.json> --jar <path> [--previous <tag>]` prints the body to stdout.

- [ ] **Step 1: Write the failing test**

Append to `tools/ci/tests/test_release.py`:

```python
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: FAIL with `AttributeError: module 'tools.ci.release' has no attribute 'compose_notes'`

- [ ] **Step 3: Write the implementation**

Append to `tools/ci/release.py`:

```python
DISPLAY_NAMES = {
    "ramses": "RAMSES",
    "helios": "Helios",
    "dyngraph": "DYNGRAPH",
    "codegen": "CODEGEN",
    "uramses": "URAMSES",
}


def compose_notes(version, props, changed, jar_sha256, previous_tag):
    """The release body.

    Four of the five component repos are private, so a link to an upstream
    release is a 404 for most people reading this page. The upstream bodies are
    therefore embedded rather than linked, which is the whole point of the
    exercise: the bundle's provenance has to be readable here or it is not
    readable anywhere.
    """
    lines = ["# STEPSS %s" % version, ""]

    if changed:
        described = ", ".join(
            "**%s %s** (was %s)"
            % (DISPLAY_NAMES[entry["component"]], entry["new_version"],
               entry["old_version"])
            for entry in changed
        )
        lines.append("Rebuilt for %s." % described)
    elif previous_tag:
        lines.append("Manual release. No component changes since %s." % previous_tag)
    else:
        lines.append("Manual release.")
    lines.append("")

    lines.append("## Bundled components")
    lines.append("")
    lines.append("| Component | Version | Upstream release |")
    lines.append("|---|---|---|")
    for component in pins.COMPONENTS:
        lines.append(
            "| %s | %s | %s |"
            % (
                DISPLAY_NAMES[component],
                props["%s.version" % component],
                props["%s.tag" % component],
            )
        )
    lines.append("")

    if changed:
        lines.append("## Upstream release notes")
        lines.append("")
        for entry in changed:
            summary = "<b>%s %s</b>" % (
                DISPLAY_NAMES[entry["component"]],
                entry["new_tag"],
            )
            if entry["title"]:
                summary += " &mdash; %s" % entry["title"]
            lines.append("<details open><summary>%s</summary>" % summary)
            lines.append("")
            lines.append(entry["body"] or "_No release notes upstream._")
            lines.append("")
            lines.append("</details>")
            lines.append("")

    lines.append("## Artifact")
    lines.append("")
    lines.append("`stepss.jar` &mdash; SHA-256 `%s`" % jar_sha256)
    lines.append("")

    return "\n".join(lines)


def notes_main(argv):
    version = _required_option(argv, "--version")
    summary_path = _required_option(argv, "--summary")
    jar_path = _required_option(argv, "--jar")
    previous = argv[argv.index("--previous") + 1] if "--previous" in argv else None

    from . import upstream

    with open(summary_path, "r", encoding="utf-8") as handle:
        summary = json.load(handle)
    props = pins.load("versions.properties")
    print(
        compose_notes(
            version,
            props,
            summary["changed"],
            upstream.sha256_file(jar_path),
            previous or None,
        )
    )
    return 0
```

Extend `COMMANDS` in `tools/ci/__main__.py`:

```python
COMMANDS = {
    "bump": bump.main,
    "next-version": release.next_version_main,
    "update-readme": release.update_readme_main,
    "notes": release.notes_main,
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: PASS, 76 tests.

- [ ] **Step 5: Eyeball a real body**

```bash
echo '{"changed":[{"component":"dyngraph","old_version":"1.1.0","new_version":"1.2.0","old_tag":"v1.1.0","new_tag":"v1.2.0","title":"Console interface on Windows","body":"The Windows build is now a console program.","url":"https://example.invalid","published":"2026-08-08"}]}' > /tmp/summary.json
python3 -m tools.ci notes --version v3.55.1 --summary /tmp/summary.json --jar dist/stepss.jar
```

Expected: valid Markdown with a five-row table reflecting the current pins, one
`<details>` block, and the real SHA-256 of `dist/stepss.jar`. Read it as a user
would — if the provenance is not obvious from the page, the notes are wrong even
though the tests pass.

- [ ] **Step 6: Commit**

```bash
git add tools/ci/release.py tools/ci/__main__.py tools/ci/tests/test_release.py
git commit -m "Compose release notes that embed the upstream release bodies"
```

---

### Task 7: Issue notices

**Files:**
- Create: `tools/ci/notify.py`
- Modify: `tools/ci/__main__.py`
- Test: `tools/ci/tests/test_notify.py`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `notify.raise_or_update(title: str, body: str, label: str, run=subprocess.run) -> str` — returns `"created"` or `"commented"`.
- CLI: `python3 -m tools.ci notify --title <t> --body <b> --label <l>`

One step serves both cases the spec names: a failed run, and a URAMSES bump that leaves the README's known-limitation paragraph needing review. Both dedupe against an open issue with the same title, so a component that fails on every tick produces one issue rather than one a day.

- [ ] **Step 1: Write the failing test**

Create `tools/ci/tests/test_notify.py`:

```python
import json
import subprocess
import unittest

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


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'tools.ci.notify'`

- [ ] **Step 3: Write the implementation**

Create `tools/ci/notify.py`:

```python
"""Raise a GitHub issue when a run needs a human.

Nobody watches a cron job, so a run that fails - or that bumps URAMSES and
leaves the README's known-limitation paragraph stale - has to say so somewhere
durable. Deduped on the exact title against open issues carrying the label, so
a component that fails on every tick produces one issue, not one a day.
"""

import json
import subprocess


def raise_or_update(title, body, label, run=subprocess.run):
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


def main(argv):
    def option(name, default=None):
        if name in argv:
            return argv[argv.index(name) + 1]
        if default is None:
            raise SystemExit("Missing required option: " + name)
        return default

    print(
        raise_or_update(
            option("--title"), option("--body"), option("--label", "release-automation")
        )
    )
    return 0
```

Extend `COMMANDS` in `tools/ci/__main__.py`:

```python
from . import bump, notify, release

COMMANDS = {
    "bump": bump.main,
    "next-version": release.next_version_main,
    "update-readme": release.update_readme_main,
    "notes": release.notes_main,
    "notify": notify.main,
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `python3 -m unittest discover -s tools/ci/tests -t . -v`
Expected: PASS, 80 tests.

- [ ] **Step 5: Commit**

```bash
git add tools/ci/notify.py tools/ci/__main__.py tools/ci/tests/test_notify.py
git commit -m "Raise a deduped issue when a run needs a human"
```

---

### Task 8: The workflow

**Files:**
- Create: `.github/workflows/release.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: every CLI subcommand from Tasks 4–7.
- Produces: the published release. Nothing consumes this task.

Required repository setup, which the implementer must confirm with the maintainer rather than assume:
- Secret `STEPSS_TOKEN` exists with read access to the five component repos and write access to this one.
- Label `release-automation` exists (`gh label create release-automation --description "Opened by the release workflow"`).
- Settings → Actions → General → Workflow permissions allows Actions to create releases and issues.

- [ ] **Step 1: Write the workflow**

Create `.github/workflows/release.yml`:

```yaml
name: Release

# Watches the five pinned component repos daily. When any of them has a new
# release, re-pins the build to it, verifies the jar, and publishes a STEPSS
# release carrying the upstream notes. A manual run always publishes, which is
# how a GUI-only change reaches a release - the schedule alone never would,
# since only component bumps drive it.
on:
  schedule:
    - cron: "0 3 * * *"
  workflow_dispatch:

concurrency:
  group: stepss-release
  cancel-in-progress: false

permissions:
  contents: write
  issues: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - name: Check out
        uses: actions/checkout@v4
        with:
          # Full history so `git tag --list` can derive the next version, and
          # STEPSS_TOKEN so the push at the end is authorised.
          fetch-depth: 0
          token: ${{ secrets.STEPSS_TOKEN }}

      - name: Set up Java 11
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "11"

      - name: Re-pin to the latest component releases
        id: bump
        env:
          GH_TOKEN: ${{ secrets.STEPSS_TOKEN }}
        run: |
          set -euo pipefail
          python3 -m tools.ci bump > summary.json
          cat summary.json
          count=$(python3 -c "import json;print(len(json.load(open('summary.json'))['changed']))")
          echo "count=$count" >> "$GITHUB_OUTPUT"
          if [ "$count" -gt 0 ] || [ "${{ github.event_name }}" = "workflow_dispatch" ]; then
            echo "proceed=true" >> "$GITHUB_OUTPUT"
          else
            echo "proceed=false" >> "$GITHUB_OUTPUT"
            echo "No component moved and this is a scheduled run; nothing to publish."
          fi

      - name: Derive the release version
        id: version
        if: steps.bump.outputs.proceed == 'true'
        run: |
          set -euo pipefail
          version=$(python3 -m tools.ci next-version)
          previous=$(git tag --list --sort=-v:refname | head -n 1)
          echo "version=$version" >> "$GITHUB_OUTPUT"
          echo "previous=$previous" >> "$GITHUB_OUTPUT"

      - name: Update the README's release line
        if: steps.bump.outputs.proceed == 'true'
        run: python3 -m tools.ci update-readme --version "${{ steps.version.outputs.version }}"

      - name: Build the jar
        if: steps.bump.outputs.proceed == 'true'
        env:
          GH_TOKEN: ${{ secrets.STEPSS_TOKEN }}
        run: ant jar

      - name: Verify the bundled toolchain
        if: steps.bump.outputs.proceed == 'true'
        run: java -cp dist/stepss.jar my.ramses.platform.ToolchainDump

      - name: Compose the release notes
        if: steps.bump.outputs.proceed == 'true'
        run: |
          set -euo pipefail
          python3 -m tools.ci notes \
            --version "${{ steps.version.outputs.version }}" \
            --summary summary.json \
            --jar dist/stepss.jar \
            --previous "${{ steps.version.outputs.previous }}" > notes.md
          cat notes.md

      - name: Commit and tag
        if: steps.bump.outputs.proceed == 'true'
        run: |
          set -euo pipefail
          git config user.name "stepss-release-bot"
          git config user.email "actions@github.com"
          git add versions.properties src/my/ramses/platform/Toolchain.java README.md
          if git diff --cached --quiet; then
            echo "No pin changes to commit (manual release of unchanged sources)."
          else
            git commit -m "Release ${{ steps.version.outputs.version }}"
            git push
          fi
          git tag "${{ steps.version.outputs.version }}"
          git push origin "${{ steps.version.outputs.version }}"

      - name: Publish the release
        if: steps.bump.outputs.proceed == 'true'
        env:
          GH_TOKEN: ${{ secrets.STEPSS_TOKEN }}
        run: |
          gh release create "${{ steps.version.outputs.version }}" \
            dist/stepss.jar \
            --title "STEPSS ${{ steps.version.outputs.version }}" \
            --notes-file notes.md \
            --latest

      - name: Flag the README's URAMSES caveat for review
        if: steps.bump.outputs.proceed == 'true'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          set -euo pipefail
          bumped=$(python3 -c "import json;print(any(c['component']=='uramses' for c in json.load(open('summary.json'))['changed']))")
          if [ "$bumped" = "True" ]; then
            python3 -m tools.ci notify \
              --title "Review the README's URAMSES known-limitation paragraph" \
              --body "URAMSES was re-pinned in ${{ steps.version.outputs.version }}. README.md's known-limitation paragraph still names the previous version while asserting that it predates the model-router marker comments. Whether that limitation still holds is a content judgement the workflow deliberately does not make - please check it." \
              --label release-automation
          fi

      - name: Report failure
        if: failure()
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          python3 -m tools.ci notify \
            --title "Release workflow failed" \
            --body "The release workflow failed. Run: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}" \
            --label release-automation
```

- [ ] **Step 2: Check the workflow parses**

```bash
python3 -c "
import sys
try:
    import yaml
except ImportError:
    sys.exit('PyYAML absent locally; rely on the Actions parse instead')
yaml.safe_load(open('.github/workflows/release.yml'))
print('workflow parses')
"
```

Expected: `workflow parses`, or the PyYAML-absent message. If it reports a YAML
error, fix it — GitHub will otherwise reject the file silently by never running it.

- [ ] **Step 3: Run the full test suite once more**

Run: `python3 -m unittest discover -s tools/ci/tests -t .`
Expected: PASS, 80 tests. Nothing in this task should have changed that number.

- [ ] **Step 4: Document the automation in the README**

Add this subsection to `README.md` immediately after the "Build from source" section:

```markdown
### Releases

Releases are cut automatically. A daily GitHub Actions run checks the five
pinned components — RAMSES, Helios, DYNGRAPH, CODEGEN and URAMSES — for new
releases; when one has moved, it re-pins `versions.properties` and the matching
resource names in `Toolchain.java`, rebuilds, verifies the bundled toolchain
extracts correctly, and publishes a release with `stepss.jar` attached. The
release notes embed each component's own release notes, since four of the five
component repos are private and cannot be linked to usefully.

Release numbers follow the pinned RAMSES version, with a counter for releases
driven by the other components: `v3.55`, then `v3.55.1`, `v3.55.2`, and so on
until RAMSES itself moves.

Running the workflow by hand (Actions → Release → Run workflow) also picks up
any new component releases first, then publishes regardless — that is how a
change to the Java sources alone reaches a release.

To re-pin locally instead, run `python3 -m tools.ci bump` (add `--dry-run` to
see what would change). It rewrites `versions.properties` and `Toolchain.java`
together; editing only the former leaves the build naming the old asset, which
`ant jar` catches.
```

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/release.yml README.md
git commit -m "Add the daily release workflow and document it"
```

- [ ] **Step 6: First live run — verify before trusting it**

This is the only step that cannot be tested beforehand. After pushing, trigger
Actions → Release → Run workflow manually and watch it. Confirm, in order:

1. The bump step prints a summary (probably `{"changed": []}`) and sets `proceed=true` because the trigger is manual.
2. `ant jar` downloads the pinned payloads through `STEPSS_TOKEN` and succeeds — this is the first real proof the token reaches the private repos.
3. `ToolchainDump` prints a file list with `exec=true` on the executables.
4. A `v3.55` release appears with `stepss.jar` attached and a five-row component table.

If step 2 fails on auth, the secret is missing or under-scoped; that is a
repository settings problem, not a code one. If the release appears but the
table is wrong, fix `compose_notes` and delete the release and tag before
re-running, or the version derivation will skip to `v3.55.1`.

---

## Self-Review

**Spec coverage:**

| Spec section | Task |
|---|---|
| Asset naming patterns, `@VERSION@` | 1 |
| Comment-preserving `versions.properties` writes | 2 |
| `Toolchain.java` literal rewriting | 2 |
| URAMSES content-manifest pin, `COMPUTE` | 3, 4 |
| Fail loudly on a renamed asset | 4 |
| Detection, download, digest, change summary | 4 |
| `--dry-run` | 4 |
| Version derived from tags | 5 |
| README `Current release` line; caveat paragraph left alone | 5, 7 |
| Release notes embedding upstream bodies | 6 |
| Manual release with no changes | 6 |
| Failure and URAMSES-review issues, deduped | 7 |
| Daily cron, `workflow_dispatch` publishes unconditionally | 8 |
| `PayloadManifestCheck` and `ToolchainDump` before any tag | 8 |
| `concurrency: stepss-release`, Java 11, `STEPSS_TOKEN` | 8 |

No gaps.

**Placeholder scan:** every step carries runnable content. The one step that
cannot be pre-verified — the first live workflow run, Task 8 Step 6 — says so
explicitly and lists what to check.

**Type consistency:** `pins.version_of`/`tag_of` are used consistently across
Tasks 4, 5 and 6. The change-summary keys defined in Task 4 (`component`,
`old_version`, `new_version`, `old_tag`, `new_tag`, `title`, `body`, `url`,
`published`) are exactly the keys Task 6's `compose_notes` and Task 8's
workflow read. `up` in `bump.run` is duck-typed against the five `upstream`
functions the fake implements.
