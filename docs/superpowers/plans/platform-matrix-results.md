# Task 15: Platform matrix acceptance — results

Branch: `cross-platform-toolchain-spec` (worked on directly, no branch switch).
Commit under test: `4d0935b` ("docs: document three-platform support and the fetch-based build").
Verification performed on: Linux x86_64 (this machine), `DISPLAY=:0`, OpenJDK 21.0.11,
Apache Ant 1.10.14, `gh` 2.45.0 authenticated to an SPS-L-org account.

No Windows and no macOS machine is available in this environment, and no human was available
to click through the interactive checklist. Per the task's scope, every row that would require
either is recorded as `PENDING` (hardware) or `NOT VERIFIED` (a human at the screen) — never
inferred from the Linux result or invented.

## Step 1: Clean-clone build

This is the check that matters most, because it proves the fetch-based build (Tasks 2 and 11)
reproduces from a commit plus network with nothing pre-staged — no `payload-cache/`, no `dist/`,
no `build/`, no `src/my/ramses/payload/`.

The task's branch `cross-platform-toolchain-spec` exists only in this local repository (it has
not been pushed to `origin` on GitHub — `git ls-remote --heads origin` on the real remote does
not list it), so "clone the repo fresh" was done as a local-filesystem clone of this working
copy, which exercises exactly the same git object-database/checkout path a remote clone would
and starts from the same zero-state (no build artefacts, no caches). The clone's HEAD followed
the source repo's currently-checked-out branch automatically.

**Commands, exactly as run:**

```
$ git clone /home/apetros/Code/stepss/stepss-java-ui <scratchpad>/task15-clean-clone/clean-build
Cloning into 'clean-build'...
done.

$ cd <scratchpad>/task15-clean-clone/clean-build
$ git checkout cross-platform-toolchain-spec
Already on 'cross-platform-toolchain-spec'
Your branch is up to date with 'origin/cross-platform-toolchain-spec'.

$ git log --oneline -1
4d0935b docs: document three-platform support and the fetch-based build

# Confirmed genuinely clean before building:
$ ls payload-cache dist build src/my/ramses/payload
ls: cannot access 'payload-cache': No such file or directory
ls: cannot access 'dist': No such file or directory
ls: cannot access 'build': No such file or directory
ls: cannot access 'src/my/ramses/payload': No such file or directory

$ ant clean jar
...
     [echo] verified ramses-windows-x86_64-v3.55.zip
     [echo] verified ramses-linux-x86_64-v3.55.tar.gz
     [echo] verified ramses-macos-arm64-v3.55.tar.gz
     [echo] verified stepss-helios-windows-x64.zip
     [echo] verified stepss-helios-linux-x86_64.tar.gz
     [echo] verified stepss-helios-macos-universal.tar.gz
     [echo] verified dyngraph-linux-x86_64-v1.1.0.tar.gz
     [echo] verified dyngraph-macos-arm64-v1.1.0.tar.gz
     [echo] verified codegen-windows-x86_64-v5.1.0.zip
     [echo] verified codegen-linux-x86_64-v5.1.0.tar.gz
     [echo] verified codegen-macos-arm64-v5.1.0.tar.gz
...
BUILD SUCCESSFUL
Total time: 18 seconds

$ ls -la dist/stepss.jar
-rw-rw-r-- 1 apetros apetros 27654507 Aug  7 14:57 dist/stepss.jar
```

**Result: `BUILD SUCCESSFUL`, 18 seconds, jar = 27,654,507 bytes (26.4 MiB / 27.7 MB decimal).**

No manual step was needed beyond the environment prerequisite the design already documents in
README.md: `gh` installed and `gh auth login`-authenticated with access to the private SPS-L
repos. Given that, the build fetched and SHA-256-verified all eleven pinned release payloads
(ramses × 3 platforms, helios × 3, dyngraph × 2, codegen × 3) over the network and produced a
working jar from nothing but the commit — exactly the property the fetch-based redesign
(Tasks 2 and 11) exists to guarantee.

**One expected deviation from the brief's own text:** Task 15's brief (written early in the
plan, before Tasks 10–12 trimmed the jar) says "roughly 32 MB." The actual current size,
27.7 MB, reflects Task 10 dropping the bundled `DOC.zip` (51.9 → 48.4 MB) and Task 12 dropping
the 90-file/12 MB `URAMSES` Intel-compile source tree (30.3 → 28.7 MB), both already recorded
in `progress.md`. Rebuilding the *existing* working tree (not a fresh clone) from the same
commit with warm caches produced a jar of the identical size, 27,654,507 bytes, confirming the
number is stable, not an artefact of this one clone.

The clean-clone directory and its `payload-cache/` were left under the scratchpad for this
session; they are not part of the repository and were not committed.

## Step 2/4: Per-platform, per-check matrix

The brief's ten checklist items, one row each. Linux carries what was actually verified in this
environment; Windows and macOS are `PENDING` with the hardware named — no result was invented
for either.

**Correction to the brief's own text, verified against the shipped code:** item 8 as written
("*Compile* is enabled on Windows") predates the Task 12 human ruling that dropped custom-model
compilation from this release **entirely**. `CompileActionPerformed` (RamsesUI.java:4254) shows
the same "Compiling custom models is not available in this release... compilation returns in a
later release built on gfortran" message unconditionally, with no platform branch at all. The
row below reflects the true current behaviour, not the brief's stale text (Task 14 made the same
correction in the README/spec docs).

| # | Check | Linux x86_64 | Windows x86_64 | macOS arm64 |
|---|---|---|---|---|
| 1 | App opens; About reports `Version: 3.55` | PARTIAL — a real, mapped `STEPSS` window was confirmed by the bounded launch smoke test below; the About dialog itself was not opened (interactive, out of scope). `version.txt` = `3.55`, matching the brief. | PENDING — requires Windows x86_64 hardware | PENDING — requires Apple Silicon hardware |
| 2 | Load a `.dat` and a `.dst` from `stepss-test-systems` | NOT VERIFIED — requires a human at the screen | PENDING — requires Windows x86_64 hardware | PENDING — requires Apple Silicon hardware |
| 3 | *Run Power Flow* → helios output appears; `in_net.res` written | NOT VERIFIED interactively here, but the command-file/helios invocation this button drives was independently proven correct in Task 13's differential test: the shipping Linux helios binary produced 1680 numeric fields across `in_net`/`in_gen`/`in_bal`/`in_volt_trfo.res` that matched the old PFC engine exactly, from the committed `createPFCCommandFile()` with no hand-tuning (see `task-13-report.md`). Not re-run in this task. | PENDING | PENDING |
| 4 | *Run Simulation* → curves stream; completes | NOT VERIFIED — requires a human at the screen | PENDING | PENDING |
| 5 | *Extract Curves* → dyngraph opens (dialog on Windows, terminal elsewhere) | NOT VERIFIED — requires a human at the screen | PENDING | PENDING |
| 6 | Editor button → file opens in the system default editor | NOT VERIFIED — requires a human at the screen | PENDING | PENDING |
| 7 | *Open Terminal* and *Open Explorer* both work | NOT VERIFIED — requires a human at the screen | PENDING | PENDING |
| 8 | Codegen tab → generation works; Compile shows the correct per-platform behaviour | Generation NOT VERIFIED interactively. Compile behaviour code-verified (see correction above): shows "not available in this release" on Linux, confirmed by reading `CompileActionPerformed`. | PENDING — and expect the **same** "not available in this release" message as Linux/macOS, not a working Visual Studio/Compile path (brief's text is stale; see correction above) | PENDING — same message expected |
| 9 | Real-time plotting works, or degrades with the gnuplot message if absent | NOT VERIFIED interactively; the null-safe degrade path (`Toolchain.gnuplot()` returns `null` when not on `PATH`) was code-verified during Task 7's fix round, not exercised live in this task | PENDING | PENDING |
| 10 | Exit → tool directory removed, working directory intact | PARTIAL/INDIRECT — the smoke test below terminated the process with `SIGTERM`, which bypasses `formWindowClosing`'s cleanup path by design (that code only runs on the GUI's own confirm-exit route); a `stepssTools*` directory was left behind and removed by hand, consistent with — but not a substitute for — exercising the real confirm-exit dialog with a real working directory selected | PENDING | PENDING |

## Automated Linux checks (fully verified)

### Toolchain fingerprint on all three platforms

`Platform.current()` resolves from the JVM's `os.name`/`os.arch` system properties (see
`src/my/ramses/platform/Platform.java`), so Windows and macOS extraction/manifest logic can be
exercised on this Linux box by overriding those two properties — this proves the manifest
resolves correctly and every payload is present, digest-checked, and extracted without throwing;
it is **not** a substitute for running the real binaries on real hardware (nothing here executes
`ramses.exe`, `helios.exe`, etc., or exercises OS-specific launch/terminal/editor code).

```
$ ant clean jar                                             # BUILD SUCCESSFUL

$ java -cp dist/stepss.jar my.ramses.platform.ToolchainDump  # real Linux
platform=LINUX_X86_64
CODEGEN    c24bd2b4...  exec=true
dyngraph   864bbbfd...  exec=true
dynsim     83a8202d...  exec=true
helios     7b86e9af...  exec=true

$ java -cp dist/stepss.jar -Dos.name="Windows 10" -Dos.arch="amd64" \
       my.ramses.platform.ToolchainDump                      # simulated Windows
platform=WINDOWS_X86_64
CODEGEN.exe   ...  exec=true
dyngraph.exe  ...  exec=true
dynsim.exe    ...  exec=true
helios.exe    ...  exec=true
gnuplot/...   (81 more files, bundled gnuplot payload — Windows only)  exec=false

$ java -cp dist/stepss.jar -Dos.name="Mac OS X" -Dos.arch="aarch64" \
       my.ramses.platform.ToolchainDump                      # simulated macOS
platform=MACOS_ARM64
CODEGEN    ...  exec=true
dyngraph   ...  exec=true
dynsim     ...  exec=true
helios     ...  exec=true
```

**Result: all three platforms resolve without throwing** `UnsupportedPlatformException` or any
other exception (stderr empty on all three runs). Linux and macOS each extract exactly the four
primary tools (`ramses`/`dynsim`, `helios`, `dyngraph`, `codegen`) with the executable bit set,
matching `Toolchain.SPECS` — neither platform has a `gnuplot` payload (both resolve it from
`PATH` at runtime instead, per `Toolchain.gnuplot()`). Windows additionally extracts the
85-file bundled gnuplot payload (only the primary `pgnuplot.exe`/`gnuplot.exe`/`wgnuplot*.exe`
files, not marked executable — matching the old Windows-only behaviour of that one payload).
This matches the manifest exactly; no unexpected files, no missing files, no crash on any of
the three platform codepaths.

### Bounded, non-interactive launch smoke test (Linux)

Started the jar, confirmed a real window, captured stderr, terminated it, cleaned up.

```
$ DISPLAY=:0 java -jar dist/stepss.jar &         # backgrounded, stdout/stderr captured to files
[wait 8s]
$ ps -p <pid> -o pid,cmd
    PID CMD
2065539 java -jar dist/stepss.jar

$ DISPLAY=:0 xwininfo -root -tree | grep -i stepss
0xa00390 "STEPSS": (...)  3006x1696+66+32  +66+32
0xc00007 "STEPSS": ("my-ramses-RamsesUI" "my-ramses-RamsesUI")  3006x1659+0+37  +66+69
```

- **Window:** a real, mapped, on-screen top-level window titled `STEPSS`, `WM_CLASS
  my-ramses-RamsesUI` — same signature as Task 9's smoke test on the pre-helios build.
- **stderr/stdout:** both empty for the entire run — no exceptions, no `SEVERE` log lines.
- **Child processes:** `ps --ppid <pid>` was empty throughout — expected, since this test never
  loaded data or ran a simulation, so nothing spawns `gnuplot`/`dynsim`/a terminal.
- **Termination:** `kill -TERM <pid>`; process exited, window disappeared from `xwininfo`
  immediately after.
- **Cleanup:** `SIGTERM` bypasses `formWindowClosing`'s own cleanup path (as in Task 9), so one
  `/tmp/stepssTools11329673186889946287` directory was left behind — removed by hand. Confirmed
  no `stepss`/`ramses`-named entries remain under `/tmp` afterward. This is the expected,
  already-documented consequence of killing the process instead of using its own exit dialog,
  not a new defect.

## Step 3: macOS prerequisite check

**Not attempted**, per the task's explicit instruction — it needs Apple Silicon hardware, which
does not exist in this environment. Recorded here as the documented interim state rather than as
a defect, exactly as the brief and `README.md` already describe it:

> On macOS, the current RAMSES, DYNGRAPH, and CODEGEN builds are dynamically linked against
> gfortran and OpenBLAS; install them first with `brew install gcc openblas`. Statically linked
> builds that drop this requirement are expected from those projects.
> — `README.md:41`, matching `docs/superpowers/specs/2026-08-06-cross-platform-toolchain-design.md:56-58`

Until the upstream static builds land, a fresh macOS run is expected to fail on first simulation
with a dynamic-link error naming `libgfortran`, and `brew install gcc openblas` is expected to
resolve it. Neither half of that expectation was exercised here — it needs a human on Apple
Silicon hardware to run the jar before installing anything, observe the failure, then install and
re-run.

## Deferred-findings triage list

Every `minor (deferred)` line from `progress.md`, so none are lost before the release decision.
One-line statement of user impact for each. Items already resolved by later tasks are marked
so and excluded from the "outstanding" count below.

| # | Origin | Finding | What it means for a user |
|---|---|---|---|
| 1 | Task 2 | `<exec>` for `gh` in `fetch-payloads` relies on Ant's default failure text instead of a custom `<fail>` distinguishing "gh missing" / "not authenticated" / "asset not found" (`build.xml:101`) | Not user-facing — a maintainer whose build fails sees a generic Ant exec-failure trace instead of a message pointing at the actual cause, slowing down build troubleshooting only. |
| 2 | Task 2 | `--skip-existing` means an interrupted download leaves a zero-byte/partial file that is skipped, not re-fetched, on the next build; the digest check then fails until the file is deleted by hand from `payload-cache/` | Not user-facing (build-time only) — a maintainer whose network drops mid-download gets a confusing digest-mismatch error instead of an automatic retry, until they manually clear the cache. |
| 3 | Task 3 | `isRosettaTranslated()` calls `waitFor()` before draining the child's stdout | No effect today (`sysctl -n` prints one short line). If that helper process ever produced enough output to fill the pipe buffer, app startup on Apple Silicon could hang waiting on a full pipe — currently only a latent risk, not observed. |
| 4 | Task 6 | `runInTerminal`'s Linux branch passes `argv` as discrete tokens after `-e`, which gnome-terminal's *legacy* `-e` form expects as one pre-parsed string | Only reachable if a Linux user has neither `x-terminal-emulator` nor `xterm` installed, so the launcher falls back to a legacy-`-e` gnome-terminal: *Open Terminal* could fail to open with the expected arguments/working directory on that narrow configuration. |
| 5 | Task 7 | `extractAll()` eagerly extracted `URAMSES.zip` into the tool dir on Windows although nothing read it there | **Resolved, not outstanding** — Task 12 removed the entire `URAMSES` manifest entry, payload and 90-file source tree when custom-model compilation was dropped from this release. Verified: `grep -rln URAMSES src/my/ramses/**/*.java` is now empty. |
| 6 | Task 8 | Four behaviour changes judged improvements but not explicitly called out at the time: dyngraph launch blocking→async (EDT no longer freezes); Linux terminal prefers `x-terminal-emulator` over hardcoded `xterm`; `nppOpen` reports "does not exist" instead of opening an empty buffer; kill sites no longer abort their cleanup block when `taskkill`/`killall` itself fails to launch | All four are net positive for the user (fewer freezes, better terminal choice, clearer error, more robust cleanup) — flagged only because they were undisclosed scope creep at the time, not because any carries risk. |
| 7 | Task 11 | `LICENSE`/`BUILDINFO.txt` inside the dyngraph and codegen release tarballs are skipped by the pinned manifest member and reach no user-visible path | A user cannot view those two files from inside the app (they can still see them on the upstream GitHub release page); no functional impact on dyngraph/codegen themselves. |
| 8 | Task 11 → Task 13 | Tools-menu item text was corrected to "Open in Editor" (Task 13), but 12 button tooltips (`nppData1Button`…`nppDstButton`, etc.) still read "Click to edit the file in Notepad++." — verified directly: `grep -c "Click to edit the file in Notepad++" RamsesUI.java` = 12 | Cosmetic/confusing only — the tooltip names software (Notepad++) that is no longer bundled; clicking the button correctly opens the OS default editor regardless of what the tooltip says. |
| 9 | Task 13 | The temp command file `runPFActionPerformed` writes is still named `PFCcmd.txt`, a residual name from the retired PFC engine now that helios consumes it (`RamsesUI.java:2511,3994`) | Cosmetic only — a user or support engineer inspecting the working directory sees a filename referencing an engine (PFC) that no longer exists; the file's content and consumer (helios) are correct. |
| 10 | Task 13 — **flagged by the ledger as needing triage before release** | `runPFActionPerformed` deletes only `in_volt_trfo.dat` before starting a helios run (`RamsesUI.java:3975-3982`); `in_svc.res` and the other `.res` files are not cleared first. If helios aborts mid-sequence, a previous run's `in_svc.res` survives and `loadGensActionPerformed` appends it, unchanged, under a freshly-loaded generator table from the *current* (possibly aborted) run. | **HIGH — this is the item this document is required to surface.** A user could see SVC results from a stale, possibly-failed prior run displayed alongside genuinely fresh results from the current run, with nothing on screen indicating the SVC numbers are old. Recommend a release-time decision: either delete all five `.res` files before each run (not just `in_volt_trfo.dat`), or surface staleness in the UI, before shipping. |

**Outstanding count: 9 of 10** (item 5 is resolved). None of the nine are release-blocking crashes;
item 10 is the one the release owner should explicitly decide on, per the ledger's own flag.

## Open engine-level items (helios, not this repo's UI)

Both are properties of the `helios` engine itself, verified independently by the controller in
Task 13, not compensated for anywhere in this repo's Java code, and out of this task's scope to
fix — recorded here so they are not lost before the release decision.

1. **Transformer tap off-by-one.** Across all 26 transformers in the Task 13 differential test,
   helios reports the transformer tap and max-tap *integers* uniformly one lower than the old PFC
   engine — a 1-based (PFC) vs 0-based (helios) numbering convention difference. Every physical
   column (voltages, MW/Mvar, ratios) matched exactly; only this one integer index differs, and
   by exactly 1 in every case. **What it means for a user:** anyone reading a transformer's tap
   position number and comparing it against expectations set by the old PFC-based numbering will
   see a number one lower than before; the transformer's actual physical state is represented
   correctly. Needs helios/PFC convention reconciliation upstream, not a UI fix.

2. **helios non-convergence on the HQ system, filed as
   [SPS-L/stepss-helios#1](https://github.com/SPS-L/stepss-helios/issues/1).** On
   `stepss-test-systems/HQ-system-with-expanded-loads/lf.dat` (2565 buses, 17 SVCs), helios
   reports "Maximum number of iterations reached!" where the old PFC engine converged in 2
   iterations (SVC Q all 0.0 vs PFC's 925 Mvar; losses 9266 MW vs PFC's 2552 MW). **helios still
   exits 0 and writes every result file as if the run succeeded** — nothing downstream, GUI
   included, can currently tell a non-converged run from a converged one by exit code or file
   presence alone. **What it means for a user:** on a large or heavily-loaded system, a user could
   load completely invalid power-flow results (nonsensical losses, zero SVC output) with zero
   indication anything went wrong, because the tool reports success. This is the more serious of
   the two engine items and is already filed upstream for the helios maintainers; the GUI has no
   way to detect the failure until helios's own exit/reporting behaviour changes.

## macOS prerequisite state (documented interim, not a defect)

Current macOS binaries for `ramses`, `dyngraph` and `codegen` are dynamically linked against
gfortran and OpenBLAS and require `brew install gcc openblas` before first use; their Linux and
Windows counterparts already ship statically linked. Statically linked macOS replacements are
expected from the upstream projects, at which point `versions.properties`' `helios.macos.asset`
line (currently pinned to `stepss-helios-macos-universal.tar.gz`, with an explicit code comment
noting the future rename to `stepss-helios-macos-arm64.tar.gz`) and the `brew` prerequisite in
README.md both get retired in a follow-up version bump. This is the documented, intentional state
of this release for macOS — not something Task 15 found wrong, and not something this task
attempted to fix or work around.

## Verdict

`PLATFORM_MATRIX: pass-with-gaps`

**What passed cleanly, fully automated, independently reproducible:**
- The clean-clone build (Step 1) — `BUILD SUCCESSFUL` from a bare commit plus network, no manual
  staging, jar = 27,654,507 bytes.
- The toolchain-fingerprint check on all three platform codepaths (simulated Windows/macOS via
  `-Dos.name`/`-Dos.arch`, real Linux) — all three resolve without throwing, manifests match
  `Toolchain.SPECS` exactly.
- The bounded Linux launch smoke test — real mapped window, empty stderr, clean termination, no
  leaked child processes.

**What remains outstanding, and who must do it:**

1. **The entire interactive nine-item checklist (checklist items 2, 4–7, 9, and the About-box
   half of item 1) on Linux** — needs a human at this screen. Item 3 has strong indirect evidence
   from Task 13's differential test but was not re-run live here; item 8 was code-verified but not
   clicked through; item 10 has only the indirect SIGTERM-bypass evidence noted above.
2. **The full ten-item checklist on Windows x86_64 and on macOS arm64, plus the macOS Step 3
   prerequisite check (`brew install gcc openblas` resolving the expected `libgfortran`
   dynamic-link failure)** — no such hardware exists in this environment. Every Windows/macOS row
   above is `PENDING`, naming the required hardware.
3. **A release-time decision on the stale-`in_svc.res` risk** (deferred-findings item 10 above) —
   the highest-stakes open item in this document: a user can currently be shown stale SVC results
   as if they were fresh, with no indication of staleness, if a helios run aborts mid-sequence.
4. Awareness only, no action required before release: the eight other deferred-findings items
   (cosmetic tooltips/filenames, build-diagnostics niceties, two engine-level helios items already
   filed or documented as interim state).

The project owner — or whoever has access to Windows and macOS hardware and can sit at a Linux
screen — should complete items 1 and 2 and make an explicit call on item 3 before treating this
branch's platform support as fully proven for release.
