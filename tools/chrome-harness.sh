#!/usr/bin/env bash
# Runs the headless window-chrome checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/picker-harness.sh, dist/lib is NOT on the classpath: ChromeCheck
# touches only Swing and my.ramses.EditIcon, and reaches RamsesUI for one static
# method that takes a JMenuBar, so build/classes alone is enough to load it.
#
# 'ant compile' still needs the payloads: build.xml makes -pre-compile depend on
# stage-payloads, so fetching and staging happen BEFORE javac. A warm
# payload-cache/ is enough - it does not need the network again.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.ramses.ChromeCheck
