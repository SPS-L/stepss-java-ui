#!/usr/bin/env bash
# Walks the documentation addresses in Docs.java and asks stepss.sps-lab.org
# whether each one still resolves, anchor included.
# This repository has no unit-test framework; this is the substitute.
#
# Unlike the other harnesses this one needs the network, because what it checks
# is a contract with another repository rather than a value in this one. Pass
# --offline to run the structural half alone, which needs neither.
#
# Nothing but build/classes is on the classpath: Docs and DocsCheck use the
# JDK's own HTTP client and no library.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -Djava.awt.headless=true \
     -cp "build/classes" \
     my.stepss.DocsCheck "$@"
