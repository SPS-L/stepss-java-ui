#!/usr/bin/env bash
# Runs the headless update-check decision tests against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/ssa-harness.sh, dist/lib is NOT on the classpath: UpdateCheck
# and Version depend on nothing but the JDK.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.stepss.UpdateHarness
