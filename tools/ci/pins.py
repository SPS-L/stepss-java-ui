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
