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


def example_manifest_digest(archive, repo_root, example, run=subprocess.run):
    """The content-manifest digest ExamplesPack computes for one example.

    Same reasoning as uramses_manifest_digest: the archive's own bytes are not a
    usable pin, so the digest is over the files the payload retains.

    COMPUTE also runs the completeness check, so an upstream release that
    dropped a file the descriptor names fails HERE, while deciding whether to
    pin it, rather than later at `ant jar` with the pin already written. That is
    the whole guard behind refreshing examples without a human reading the diff.

    -sourcepath is load-bearing for the same reason it is there: ExamplesPack
    reads the descriptor through ExampleCatalog, and javac given a bare file
    path has no sourcepath to resolve it through.
    """
    workdir = tempfile.mkdtemp(prefix="example-manifest-")
    try:
        src_root = os.path.join(repo_root, "src")
        source = os.path.join(
            src_root, "my", "stepss", "examples", "ExamplesPack.java"
        )
        descriptor = os.path.join(
            src_root, "my", "stepss", "examples", "examples.properties"
        )
        run(
            ["javac", "-sourcepath", src_root, "-d", workdir, source],
            check=True,
            capture_output=True,
            text=True,
        )
        result = run(
            [
                "java", "-cp", workdir,
                "my.stepss.examples.ExamplesPack",
                descriptor,
                example,
                archive,
                os.path.join(workdir, "out.zip"),
                "COMPUTE",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        prefix = "%s.manifest.sha256=" % example
        for line in result.stdout.splitlines():
            if line.startswith(prefix):
                return line.split("=", 1)[1].strip()
        raise ValueError(
            "ExamplesPack COMPUTE printed no digest line for %s. Output was:\n%s"
            % (example, result.stdout)
        )
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def uramses_manifest_digest(archive, repo_root, run=subprocess.run):
    """The content-manifest digest UramsesKitPack computes for an archive.

    URAMSES is pinned on a manifest over the files the kit retains, not on the
    archive's own bytes: GitHub's generated source archives are not guaranteed
    byte-stable, so a re-compression upstream would otherwise read as tampering.
    UramsesKitPack already knows how to compute it; COMPUTE mode prints it.

    COMPUTE also runs the router marker check, so an upstream release that
    dropped the markers fails here, while deciding whether to pin it, rather
    than later at `ant jar` with the pin already written.

    -sourcepath is load-bearing: UramsesKitPack reads the marker contract off
    RouterSplicer, in another package, and javac given a bare file path has no
    sourcepath to resolve it through. build.xml's javac gets this for free from
    its srcdir; this one has to say it.
    """
    workdir = tempfile.mkdtemp(prefix="uramses-manifest-")
    try:
        src_root = os.path.join(repo_root, "src")
        source = os.path.join(
            src_root, "my", "stepss", "platform", "UramsesKitPack.java"
        )
        run(
            ["javac", "-sourcepath", src_root, "-d", workdir, source],
            check=True,
            capture_output=True,
            text=True,
        )
        result = run(
            [
                "java", "-cp", workdir,
                "my.stepss.platform.UramsesKitPack",
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
