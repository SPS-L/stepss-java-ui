#!/usr/bin/env bash
# Runs the headless observable-picker checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/scenario-harness.sh, dist/lib is NOT on the classpath: my.stepss.obs
# depends on nothing beyond the JDK and Swing, so build/classes alone is enough
# to load it. It builds controls but never a frame, so it runs headless.
#
# 'ant compile' still needs the payloads: build.xml makes -pre-compile depend on
# stage-payloads, so fetching and staging happen BEFORE javac. On a checkout with
# neither a warm payload-cache/ nor network plus authenticated gh, the build dies
# before build/classes is created and this script has nothing to run. A warm
# payload-cache/ is enough - it does not need the network again.
# java.io.tmpdir follows TMPDIR because a later check writes a file there, and
# some sandboxes mount /tmp read-only while pointing TMPDIR somewhere writable.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -Djava.awt.headless=true -Djava.io.tmpdir="${TMPDIR:-/tmp}" -cp build/classes my.stepss.obs.ObservablesHarness
