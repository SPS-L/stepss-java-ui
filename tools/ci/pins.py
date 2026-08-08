"""Read and rewrite the component pins in versions.properties.

Pure: no network, no subprocesses. Everything here is driven by the file's
own contents, so the module is fully unit-testable.
"""

COMPONENTS = ("ramses", "helios", "dyngraph", "codegen", "uramses")
PLATFORMS = ("windows", "linux", "macos")

VERSION_TOKEN = "@VERSION@"
PAYLOAD_PREFIX = "payload/"


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


def validate_toolchain(path, renames):
    """Checks every rename's old payload name is present, writing nothing.

    A caller that needs to touch more than one file for a single logical
    change (see bump.run) can call this before writing any of them, so a
    rename that would later fail rewrite_toolchain is caught while every file
    is still untouched, rather than partway through a multi-file write.
    """
    with open(path, "r", encoding="utf-8") as handle:
        text = handle.read()

    for old, new in renames.items():
        if old == new:
            continue
        needle = PAYLOAD_PREFIX + old
        if needle not in text:
            raise ValueError("%s does not name %s" % (path, needle))


def rewrite_toolchain(path, renames):
    """Repoints Toolchain.java's payload resource names at the new assets.

    Exact literal replacement, never pattern matching: the old names come from
    the pins the caller just read, so a replacement either matches exactly or
    is a bug worth failing on. An old name that is missing means Toolchain.java
    and versions.properties had already drifted apart. Delegates that check to
    validate_toolchain first, so this function can never raise partway through
    its own write.
    """
    validate_toolchain(path, renames)

    with open(path, "r", encoding="utf-8") as handle:
        text = handle.read()

    original = text
    replacements = 0
    for old, new in renames.items():
        if old == new:
            continue
        needle = PAYLOAD_PREFIX + old
        replacements += text.count(needle)
        text = text.replace(needle, PAYLOAD_PREFIX + new)

    if text != original:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
    return replacements
