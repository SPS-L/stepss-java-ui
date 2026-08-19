#!/usr/bin/env bash
# Runs the headless shared-plot-sink checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/ssa-harness.sh, dist/lib is NOT on the classpath: my.stepss.plot
# depends on nothing beyond the JDK, so build/classes alone is enough.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.stepss.plot.PlotHarness
