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
# Honour TMPDIR for the same reason tools/curve-harness.sh does: these checks
# write fixtures into a temporary directory, and a sandboxed or containerised
# run can have /tmp read-only while TMPDIR points somewhere writable. Without
# this the checks fail on their fixtures rather than on anything they test.
exec java -Djava.io.tmpdir="${TMPDIR:-/tmp}" -cp build/classes my.stepss.ssa.SsaHarness
