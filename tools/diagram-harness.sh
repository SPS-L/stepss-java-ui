#!/usr/bin/env bash
# Runs the headless one-line diagram checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Unlike tools/ssa-harness.sh, dist/lib IS on the classpath: the diagram
# package renders through Batik, so the jars in lib/ have to be reachable.
# They are put there deliberately WITHOUT Rhino, which is the state the
# application ships in and the state these checks have to run in.
#
# lib/commons-exec is on it too, for a different reason: the launcher checks
# call PlatformLauncher.editorCommand/defaultApplicationCommand, whose return
# type is org.apache.commons.exec.CommandLine, so the class cannot even load
# without it, though nothing here launches a process.
#
# lib/commons-io joined it once checkTheDiagramCommandBlock started calling
# StepssUI.diagramCommands: loading any class verifies every method's bytecode,
# and StepssUI imports org.apache.commons.io.output.TeeOutputStream (among
# others) in a method the check never calls, so the class fails to load
# without that jar even though nothing here tees a stream.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -Djava.awt.headless=true \
     -cp "build/classes:lib/batik-all-1.19.jar:lib/xmlgraphics-commons-2.11.jar:lib/xml-apis-ext-1.3.04.jar:lib/commons-exec-1.3.jar:lib/commons-io-2.11.0.jar" \
     my.stepss.diagram.DiagramCheck
