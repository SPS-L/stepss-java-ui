#!/usr/bin/env bash
# Runs the headless small-signal results checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/picker-harness.sh, dist/lib is NOT on the classpath: the ssa
# package depends on nothing that launches a process or references
# commons-exec types, so build/classes alone is enough to load it.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.ramses.ssa.SsaHarness
