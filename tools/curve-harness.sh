#!/usr/bin/env bash
# Runs the headless curve-viewer checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/ssa-harness.sh, dist/lib is NOT on the classpath: my.stepss.curves
# depends on my.stepss.plot and the JDK and on nothing that launches a process,
# so build/classes alone is enough to load it.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.stepss.curves.CurveHarness
