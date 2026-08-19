#!/usr/bin/env bash
# Runs the headless curve-viewer checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/ssa-harness.sh, dist/lib is NOT on the classpath: my.stepss.curves
# depends on the JDK alone and on nothing that launches a process, so
# build/classes alone is enough to load it.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
# The tail checks create a temporary directory, so honour TMPDIR rather than
# taking java.io.tmpdir's /tmp default: a sandboxed or containerised run can
# have /tmp read-only while TMPDIR points somewhere writable, and there the
# checks fail on their fixtures rather than on anything they are testing.
exec java -Djava.io.tmpdir="${TMPDIR:-/tmp}" -cp build/classes my.stepss.curves.CurveHarness
