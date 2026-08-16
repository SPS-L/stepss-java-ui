#!/bin/sh
# Installs the built .deb in a clean container and checks that what it declares
# is enough to run STEPSS.
# Usage: tools/deb-harness.sh [path/to/stepss_*.deb]
#
# The reason this needs a container: jpackage derives Depends: from the app
# image alone - the launcher, the bundled JRE and the jar - and the simulation
# toolchain is in none of those. RAMSES, Helios, DYNGRAPH, CODEGEN and the
# URAMSES kit travel as .tar.gz resources inside stepss.jar and are extracted at
# run time, so nothing RAMSES links against appears in the control file unless
# build.xml puts it there by hand. ToolchainDump, which CI already runs, reports
# the payloads' digests and the executable bit and never loads one, so it passed
# for every release in which RAMSES was unloadable.
#
# A workstation cannot show either failure this catches. It has libgfortran and
# OpenBLAS installed for unrelated reasons, and it has a desktop, so
# xdg-desktop-menu succeeds there and fails with "No writable system menu
# directory found" on the servers and containers users actually hit.
#
# The script runs itself inside the container, which is what --in-container is:
# not a public argument, and the half below the marker never runs on a
# workstation.
set -e

# ---------------------------------------------------------------- in container
if [ "${1:-}" = "--in-container" ]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  # Before the package, and deliberately nothing else: unzip is needed to reach
  # the payload inside the jar, and it pulls in nothing that could stand in for
  # a dependency stepss failed to declare.
  apt-get install -y -qq unzip > /dev/null

  echo "=== installing the package for real ==="
  # Not --dry-run: that resolves dependencies and never runs postinst, which is
  # where the headless failure lived.
  apt-get install -y -qq /work/stepss.deb

  echo "=== the launcher, and a command to type ==="
  test -x /opt/stepss/bin/STEPSS || { echo "FAIL: /opt/stepss/bin/STEPSS is missing"; exit 1; }
  test -L /usr/bin/stepss || { echo "FAIL: /usr/bin/stepss is not a symlink"; exit 1; }
  test -x /usr/bin/stepss || { echo "FAIL: /usr/bin/stepss does not resolve"; exit 1; }
  ls -l /usr/bin/stepss

  echo "=== the copyright file, where Debian looks for it ==="
  head -3 /usr/share/doc/stepss/copyright

  echo "=== gnuplot came with it ==="
  # An interactive build, not gnuplot-nox: that one satisfies a bare
  # Depends: gnuplot through Provides: and then draws nothing.
  command -v gnuplot || { echo "FAIL: gnuplot is not installed"; exit 1; }
  gnuplot --version

  echo "=== RAMSES resolves every library it needs ==="
  mkdir -p /tmp/payload
  cd /tmp/payload
  unzip -o -q /opt/stepss/lib/app/stepss.jar 'my/stepss/payload/ramses-linux-*.tar.gz'
  tar xzf my/stepss/payload/ramses-linux-*.tar.gz
  ldd ./ramses
  if ldd ./ramses | grep -q 'not found'; then
    echo "FAIL: RAMSES has unresolved libraries, so Depends: is incomplete." >&2
    echo "Add them to bundle.linux.deps in build.xml." >&2
    exit 1
  fi
  # ldd is not proof on its own: it resolves sonames without loading the
  # program. Running it is. No data file is given, so it prints its banner and
  # exits complaining about that, which is all this needs it to reach.
  ./ramses < /dev/null > ramses.log 2>&1 || true
  grep -q "Version:" ramses.log || {
    echo "FAIL: ramses did not start. Its output was:" >&2
    cat ramses.log >&2
    exit 1
  }
  echo "ramses started."

  echo "=== removal takes the links with it ==="
  apt-get remove -y -qq stepss > /dev/null
  test ! -e /usr/bin/stepss || { echo "FAIL: /usr/bin/stepss survived removal"; exit 1; }
  test ! -e /usr/share/doc/stepss/copyright || { echo "FAIL: the copyright link survived removal"; exit 1; }
  echo "links removed."

  echo "ALL CHECKS PASSED"
  exit 0
fi

# ------------------------------------------------------------------- on a host
cd "$(dirname "$0")/.."

deb="${1:-}"
if [ -z "$deb" ]; then
  deb=$(ls bundle/*.deb 2>/dev/null | head -n 1)
fi
if [ ! -f "$deb" ]; then
  echo "No .deb to test. Pass one, or run 'ant bundle -Dbundle.type=--type deb' first." >&2
  exit 1
fi

echo "=== what the package declares ==="
dpkg-deb -I "$deb"

fail=0
check_field() {
  if ! dpkg-deb -I "$deb" | grep -qF "$1"; then
    echo "FAIL: the control file has no '$1'" >&2
    fail=1
  fi
}
# Every one of these comes from packaging/linux/control or from
# bundle.linux.deps in build.xml, and every one is silently dropped if jpackage
# stops honouring the resource directory or renames a template token. The
# container run below would catch the missing dependencies through ldd, but
# nothing at all would catch the maintainer or the section.
check_field "Maintainer: SPS-Lab <stepss@sps-lab.org>"
check_field "Section: science"
check_field "Recommends: gfortran, make, libopenblas-dev"
for dep in libgfortran5 libgomp1 libopenblas0 "gnuplot-x11 | gnuplot-qt"; do
  if ! dpkg-deb --field "$deb" Depends | grep -qF -- "$dep"; then
    echo "FAIL: Depends: does not name $dep" >&2
    fail=1
  fi
done
[ "$fail" -eq 0 ] || exit 1
echo "control file: ok"
echo

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp "$deb" "$work/stepss.deb"
cp "$0" "$work/harness.sh"

# ubuntu:24.04 because that is what the .deb is built on, and therefore the
# oldest release it can be installed on: jpackage reads the dependency names off
# the build machine, and 24.04's libasound2t64 does not exist before the 64-bit
# time_t transition. Installing on an older image is expected to fail on that
# name and would say nothing about this package.
#
# Pulled separately and retried, because in CI this is the one step that can
# fail for a reason that has nothing to do with the package: Docker Hub rate
# limits anonymous pulls per IP, and hosted runners share theirs. A red leg here
# holds up the .deb, so it should mean the package is wrong, not that the
# registry was busy.
attempt=1
until docker pull -q ubuntu:24.04; do
  if [ "$attempt" -ge 3 ]; then
    echo "Could not pull ubuntu:24.04 after $attempt attempts." >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 15
done

docker run --rm -v "$work":/work:ro ubuntu:24.04 sh /work/harness.sh --in-container
