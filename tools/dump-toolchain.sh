#!/bin/sh
# Prints a deterministic fingerprint of the extracted toolchain.
# Usage: tools/dump-toolchain.sh > after.txt
set -e
cd "$(dirname "$0")/.."
ant -q jar > /dev/null
java -cp dist/stepss.jar my.ramses.platform.ToolchainDump
