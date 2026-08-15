#!/usr/bin/env bash
# Runs the headless observable-picker checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Unlike tools/compile-harness.sh, dist/lib is NOT on the classpath: PickerHarness
# deliberately depends on nothing that launches a process or references
# commons-exec types, so build/classes alone is enough to load it.
#
# 'ant compile' still needs the payloads: build.xml makes -pre-compile depend on
# stage-payloads, so fetching and staging happen BEFORE javac. On a checkout with
# neither a warm payload-cache/ nor network plus authenticated gh, the build dies
# before build/classes is created and this script has nothing to run. A warm
# payload-cache/ is enough - it does not need the network again.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.stepss.dyngraph.PickerHarness
