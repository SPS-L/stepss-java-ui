#!/usr/bin/env bash
# Runs the headless checks on the inline banner's text handling, against the
# built classes.  This repository has no unit-test framework; this is the
# substitute.
#
# Like tools/chrome-harness.sh, dist/lib is NOT on the classpath, and the run
# is headless: InlineBanner.oneLine is a static string function and touches no
# toolkit, which is exactly why it is the part that can be checked here.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -Djava.awt.headless=true -cp build/classes my.stepss.BannerCheck
