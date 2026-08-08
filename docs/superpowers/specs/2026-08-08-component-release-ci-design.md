# Component release CI — design

Date: 2026-08-08

## Problem

`stepss.jar` bundles five components — RAMSES, Helios, DYNGRAPH, CODEGEN and
URAMSES — pinned by version, asset name and digest in `versions.properties`.
When any of them publishes a release, someone has to notice, bump the pins by
hand in two files, rebuild, and publish a new STEPSS release. Nobody does this
reliably, so the bundled toolchain drifts behind its components.

The bump is a two-file edit, which is what makes it error-prone. Beyond
`versions.properties`, `Toolchain.java`'s `SPECS` carries version-suffixed
resource names (`payload/ramses-linux-x86_64-v3.55.tar.gz`) that must name the
same files. `PayloadManifestCheck`, wired into `-post-compile`, fails the build
when the two disagree — it catches the mistake but does not fix it.

## Goal

A scheduled job that detects a new release in any of the five component repos,
re-pins to it, builds a verified jar, and publishes a STEPSS release with that
jar attached and the upstream release information visible on the page. No human
step in the happy path.

## Decisions

| Decision | Choice |
|---|---|
| Human gate | None. Fully automatic publish. |
| Components watched | All five, including CODEGEN. |
| Version scheme | RAMSES version plus a patch counter: `v3.55`, `v3.55.1`, … |
| Detection | Daily scheduled poll, not `repository_dispatch`. |
| Manual runs | `workflow_dispatch` bumps to the latest assets first, then publishes unconditionally. |
| Structure | One workflow; bump logic in a script that also runs locally. |

Fully automatic publishing is safe here only because the build itself is the
gate: `PayloadManifestCheck` and `ToolchainDump` both run before a tag exists,
so a bad upstream release fails the run instead of shipping. That coupling is
load-bearing — weakening either check turns this design into one that publishes
broken jars unattended.

Polling was chosen over `repository_dispatch` because its failures are visible.
A dispatch workflow deleted from a component repo stops that component being
watched with no signal anywhere; a cron run that fails opens an issue. Instant
notification is not worth a silent blind spot in an unattended pipeline.

## Architecture

```
cron (daily) + workflow_dispatch
  │
  ├─ tools/bump-components.sh          ← always runs, on both triggers
  │    ├─ read current pins from versions.properties
  │    ├─ gh release view, per component, via STEPSS_TOKEN
  │    ├─ no component moved → empty change summary
  │    └─ some moved → download new assets, digest them,
  │                    rewrite versions.properties + Toolchain.java,
  │                    emit a JSON change summary
  │
  ├─ nothing moved and trigger is cron → stop here, green, nothing published
  ├─ derive next version from tags
  ├─ rewrite README's "Current release" line
  ├─ ant jar                 → PayloadManifestCheck guards the two-file sync
  ├─ ToolchainDump           → extracts every payload, hashes what lands
  ├─ commit pins + README to main
  ├─ tag vX.Y[.N]
  └─ gh release create, jar attached, notes composed from the change summary
```

The bump runs on both triggers, so a manually launched release picks up any
component assets released since the last run rather than rebuilding stale pins.
The triggers differ in one respect only: when nothing moved, cron stops without
publishing, whereas a manual dispatch goes on to cut a release anyway. That is
what makes `workflow_dispatch` the button for "release the current GUI source" —
without it, changes to the Java code alone would never reach a release, since
only component bumps drive the schedule.

Nothing before the commit publishes anything. A failed run leaves no tag, no
release and no commit — only an issue saying so — and the next tick retries from
unchanged pins.

### `tools/bump-components.sh`

One unit with one job: make the working tree describe the latest component
releases, and say what it changed. It does not build, commit, tag or publish,
and it is runnable locally with `--dry-run` to preview a bump without touching
files.

Inputs: `versions.properties`, `Toolchain.java`, `STEPSS_TOKEN` in the
environment. Outputs: the two files rewritten in place, plus a JSON summary on
stdout listing, per changed component, the old and new version, the upstream
release title, body, URL and publication date. It exits 0 on success whether or
not anything changed — an empty summary is what signals "no work" — and non-zero
only on genuine failure, such as an unreachable repo or a missing asset.

**Asset naming.** Asset filenames are not uniform across components. RAMSES,
DYNGRAPH and CODEGEN carry the version in the filename; Helios does not. The
script therefore reads a naming pattern per component and platform, added to
`versions.properties` next to the existing keys:

```properties
ramses.linux.asset.pattern=ramses-linux-x86_64-v@VERSION@.tar.gz
helios.linux.asset.pattern=stepss-helios-linux-x86_64.tar.gz
```

`@VERSION@` rather than `${VERSION}` because Ant expands `${…}` when it loads
the file. Ant ignores the new keys; they exist for the bumper.

If the name a pattern produces is absent from the new release's asset list, the
script fails with that name and the actual asset list. It does not fall back to
matching by pattern or by platform substring. An upstream rename is a human
decision: in a pipeline that publishes without review, silently selecting a
different file is the one failure mode that reaches users as a wrong binary.

**URAMSES.** Pinned differently from the other four and handled separately. It
is public, fetched over plain HTTPS rather than `gh`, and pinned by a
content-manifest digest over retained files rather than by the digest of the
downloaded archive — GitHub's generated source archives are not byte-stable.
Bumping it means rewriting the tag inside `uramses.source.url` and recomputing
`uramses.manifest.sha256`, which `UramsesKitPack` already supports through its
`COMPUTE` mode. The script compiles that one class, runs it against the new
archive, and parses the `uramses.manifest.sha256=…` line it prints.

**`Toolchain.java`.** Rewritten by exact literal replacement of each old asset
name with its new one. The old names are read from the pins rather than matched
by pattern, so each replacement is exact or is not attempted. URAMSES follows
the same rule against its derived resource name, `uramses-kit-v3.55.zip`.
Helios needs no edit, its names being version-free. A missed replacement is
caught downstream by `PayloadManifestCheck`.

### Version numbers

Derived from existing tags, so no counter is stored anywhere and nothing drifts.
Read `ramses.version`, list tags matching `v<version>` and `v<version>.<n>`,
and take the next free name: `v3.55` when unused, otherwise `v3.55.<max n + 1>`.
A RAMSES bump to 3.56 restarts the sequence at `v3.56` with no special case, and
a hand-made `v3.55` release simply pushes the first automatic one to `v3.55.1`.

### README

`README.md` line 7 states `Current release: **3.55**.` and would go stale the
moment a release is cut unattended. The workflow rewrites it with the version it
just derived — so it names the STEPSS release, `3.55.1`, not `ramses.version` —
and includes the change in the same commit as the pins. This runs after the
version is derived and before the build, and it is a single anchored
substitution on that one line, not a search for the version string anywhere in
the file.

The known-limitation paragraph further down is deliberately left alone. It
names the pinned URAMSES version while asserting that the version predates the
model-router marker comments, and whether that still holds after a bump is a
content judgement — substituting a new number there would keep the claim alive
while making it look freshly verified. Instead, when the URAMSES pin moves, the
run opens a review-reminder issue through the same mechanism used for failures,
naming the paragraph and the new version.

### Verification

`ant jar` runs `PayloadManifestCheck`, which fails when `Toolchain.java` and the
staged payload directory disagree — the specific failure a partial bump causes.

`ToolchainDump` then runs against the built jar. It extracts every payload for
the runner's platform and prints a sorted digest and executable bit per file, so
an upstream archive with a renamed executable inside, a truncated download, or a
layout the extractor cannot unpack fails the run rather than reaching a user.
Its output is the build's fingerprint and is kept in the job log.

Both checks run before any tag exists.

### Release notes

Four of the five component repos are private, so a link to an upstream release
renders as a 404 for anyone outside SPS-L — including most people reading the
STEPSS release page. The notes therefore embed the upstream bodies rather than
linking to them.

```markdown
# STEPSS v3.55.1

Rebuilt for **DYNGRAPH v1.2.0** (was v1.1.0).

## Bundled components

| Component | Version | Upstream release | Published |
|---|---|---|---|
| RAMSES   | 3.55  | v3.55  | 2026-07-12 |
| Helios   | 1.4.1 | v1.4.1 | 2026-07-28 |
| DYNGRAPH | 1.2.0 | v1.2.0 | 2026-08-08 |
| CODEGEN  | 5.1.0 | v5.1.0 | 2026-07-02 |
| URAMSES  | 3.55  | v3.55  | 2026-07-12 |

## Upstream release notes

<details open><summary><b>DYNGRAPH v1.2.0</b> — Console interface on Windows</summary>

  …upstream body, verbatim…

</details>

## Artifact

`stepss.jar` — SHA-256 `abc123…`
```

The table is generated from `versions.properties` after the bump, so it
describes the jar actually attached rather than what the run set out to build.
Only components that changed in this release get their notes expanded; the rest
appear in the table alone, so the page does not repeat the bundle's whole
history every time. A tick that catches two bumps produces two sections.

A manual release with no component change has no upstream notes to show. Its
body carries the table alone, under a line reading `Manual release. No component
changes since v3.55.1.` rather than an empty `Upstream release notes` heading.

### Failure handling and notices

Nobody watches a cron job, so a run that needs attention must announce itself.
The workflow raises a GitHub issue in two cases, through one shared step: a
failed run, naming the failing stage and component with a link to the run log;
and a URAMSES bump, flagging the README's known-limitation paragraph for review.
Both update a matching open issue rather than opening a second one, so a
component that fails on every tick produces one issue, not one a day.

This is what makes unattended publishing tolerable: the build gates mean the
pipeline cannot ship a broken jar, and the issue means it cannot stop working
quietly either.

### Workflow mechanics

- Runner: `ubuntu-latest`. The jar carries every platform's payloads, so there
  is nothing to matrix over.
- Java 11 (`javac.source`/`javac.target`), Ant.
- `concurrency: stepss-release`, `cancel-in-progress: false`, so a manual
  dispatch queues behind a cron run rather than racing it to the same tag.
- Auth: `STEPSS_TOKEN` throughout. Actions' default `GITHUB_TOKEN` is scoped to
  this repository and cannot read the component repos.
- Schedule: daily, plus `workflow_dispatch`. Both run the bump; only cron
  declines to publish when nothing moved.

## Out of scope

- Changing how components are pinned or verified. The digest pinning, the
  `PayloadManifestCheck` sync guard and the URAMSES content-manifest scheme are
  reused as they stand.
- Publishing per-platform artifacts. One jar, as today.
- Watching anything other than the five pinned components.
- README prose beyond the `Current release` line. The known-limitation
  paragraph's version reference is a content judgement, not a substitution, and
  is flagged for review rather than rewritten.
