#!/usr/bin/env bash
# Runs the headless checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ] || [ ! -d dist/lib ]; then
    echo "build/classes or dist/lib not found - run 'ant jar' first." >&2
    exit 1
fi
# dist/lib is on the classpath because PlatformLauncher (whose PATH lookup the
# harness checks) has commons-exec types in its signatures, so loading it needs
# that jar even though no check ever launches a process. 'ant jar' produces
# dist/lib, so the guard above already covers it.
CP="build/classes:dist/lib/*"

java -cp "$CP" my.ramses.compile.CompileHarness

# Headless so the Swing sink checks need no display; they construct a
# JTextArea and drain the event queue, never a window.
java -Djava.awt.headless=true -cp "$CP" my.ramses.ConsoleSinkCheck
