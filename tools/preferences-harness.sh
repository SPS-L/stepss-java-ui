#!/usr/bin/env bash
# Runs the headless checks on what a launch remembers, against the built
# classes.  This repository has no unit-test framework; this is the substitute.
#
# Like tools/chrome-harness.sh, dist/lib is NOT on the classpath: the checks
# touch java.util.prefs and nothing that launches a process.
#
# The preference store is redirected into a temporary directory rather than
# used where it lives.  Two reasons, and the second is the one that bites:
# these checks are about what is in a user's store, so running them against
# the real one would be editing the thing under test; and $HOME/.java is
# read-only under a sandbox, where java.util.prefs does not fail until it
# tries to take its lock file - Unix error code 30, several checks in.
# java.util.prefs.{user,system}Root are read by the file-based factory that
# Linux and macOS use.  Windows uses the registry and ignores both, so run
# this on a POSIX host.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
# Honour TMPDIR for the reason tools/ssa-harness.sh does: a sandboxed run can
# have /tmp read-only while TMPDIR points somewhere writable.
store=$(mktemp -d "${TMPDIR:-/tmp}/stepss-prefs-harness.XXXXXX")
trap 'rm -rf "$store"' EXIT
exec java -Djava.awt.headless=true \
     -Djava.util.prefs.userRoot="$store" \
     -Djava.util.prefs.systemRoot="$store" \
     -cp build/classes my.stepss.PreferencesCheck
