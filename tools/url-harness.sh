#!/usr/bin/env bash
# Runs the headless browser-command checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Only lib/commons-exec is on the classpath, and only because
# PlatformLauncher.urlCommand returns org.apache.commons.exec.CommandLine, so
# the class cannot load without it. Nothing here launches a process or opens a
# page: the checks assert the command, not the browser.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -Djava.awt.headless=true \
     -cp "build/classes:lib/commons-exec-1.3.jar" \
     my.stepss.platform.UrlLaunchCheck
