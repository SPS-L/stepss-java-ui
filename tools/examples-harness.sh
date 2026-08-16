#!/usr/bin/env bash
# Runs the headless bundled-example checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Unlike tools/ssa-harness.sh, the staged payload directory is on the classpath
# as well as build/classes: these checks extract the example payloads, and those
# are resources under src/my/stepss/payload/ that `ant compile` does not copy
# into build/classes. `ant jar` is what puts them in the jar; before that they
# are only where stage-payloads left them.
#
# dist/lib is NOT on the classpath: the examples package depends on nothing that
# launches a process or references commons-exec types.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
if [ ! -d src/my/stepss/payload ]; then
    echo "src/my/stepss/payload not found - run 'ant stage-payloads' first." >&2
    exit 1
fi
exec java -cp "build/classes:src" my.stepss.examples.ExamplesHarness
