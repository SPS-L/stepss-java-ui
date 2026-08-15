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
            repo_root, "src", "my", "stepss", "platform", "UramsesKitPack.java"
        )
        run(["javac", "-d", workdir, source], check=True, capture_output=True, text=True)
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
