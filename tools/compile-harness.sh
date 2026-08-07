#!/usr/bin/env bash
# Runs the headless compile-pipeline checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant jar' first." >&2
    exit 1
fi
exec java -cp build/classes my.ramses.compile.CompileHarness
