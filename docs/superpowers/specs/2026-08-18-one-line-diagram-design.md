# Annotated one-line diagrams for `stepss-java-ui`

Add an optional annotated SVG slot to the System Data tab, render it through
Helios' `1` command as part of every power flow run, and show the result in a
window of its own so two runs can be compared side by side. Make the
disturbance file optional, so a power-flow-only case is a first class thing to
load. Bundle the 6-bus microgrid as the example that demonstrates all of it.

Status: approved 2026-08-18, with five corrections made during execution after
probing the real engine and the real renderer. They are recorded here rather
than edited into the body, so this stays a record of what was approved. The
implementation plan carries the corrected code.

- **The strict SVG DOM refuses the bundled example, and Part 5 as approved
  could not render it.** `6bus.svg` carries `<version>1.0</version>` inside
  `<desc>`, left by WinFIG, which inherits the SVG default namespace and is not
  an SVG element. Batik throws away the whole drawing over it. This is not
  peculiar to the file: Helios does text substitution and does not parse XML,
  so the element survives into `in_diagram.svg` and the feature would fail on
  its own demo case on every run. `SvgImage` now installs a lenient DOM
  implementation that remaps an unknown element into the null namespace, where
  GVT ignores it. Verified: the unmodified file renders identically to a copy
  with the element deleted.
- **Part 5's external-resource check asserted the wrong outcome.** Batik's
  default user agent refuses the fetch *and* aborts the render, so such a
  document does not render at all. The security property is free, as approved,
  but a template referencing an external image now shows the render-failed
  banner rather than rendering without it.
- **The Rhino risk in "The risk that is not asserted away" does not exist.**
  `batik-all` carries no Rhino classes and transcodes without them. The check
  stays, as what holds that answer in place.
- **Part 4 says Helios "refuses when input and output are the same path". It
  does not.** Its guard compares the two typed strings, so an absolute template
  and the relative output name slip past it and it overwrites the template with
  its own output. Measured: the template came back with every placeholder
  substituted and the original destroyed. The canonical-path check Part 4 calls
  for is what prevents data loss, not merely a blank diagram.
- **A missing template does worse than print a line.** `cmd_diagram` returns
  without consuming the output-name line, so Helios reads that line as a
  main-menu command and aborts the run with exit 1, losing the `VT` export the
  completion thread waits on. The existence check must happen before the
  command file is written, as Part 4 has it.

Part 5 also gained a resize listener: as approved, the panel re-rendered only
on a gesture or the first paint, so growing the window left the previous image
scaled up and soft.

## Why

Helios has drawn annotated one-line diagrams since it was written. `PlainMenu`
offers `1 : display outputs on 1-line diagram`, `DiagramRenderer` substitutes
`%A` to `%U` placeholder codes out of the solved network into a template SVG,
and `../stepss-helios/examples/6bus_mg.svg` has been the reference template all
along. None of it is reachable from this application. A user who wants the
picture has to leave STEPSS, run `helios` in a terminal, load the same data
files by hand, and type `1`.

That is the whole gap. The engine work is done; what is missing is a slot to
name the template in, three lines in the command file, and somewhere to put the
picture.

The bundled examples make the gap worse rather than better, because every one
of them is a dynamic case. There is nothing in the menu that opens on a power
flow and stops there, so the tab that Helios drives has no example at all.
`SPS-L/stepss-6-bus-MG` is exactly that case, and it ships a template with the
placeholders already in it.

## Scope

In:

- The disturbance file stops being labelled required, and the examples
  descriptor stops requiring one.
- `stepss-6-bus-MG` joins the bundled examples, pinned like the other three.
- A third section on the System Data tab naming an optional template SVG, with
  a button that opens it in whatever the platform uses for SVG.
- The template is rendered by the same Helios run that solves the power flow.
- A non-modal window per run showing the result, with a banner carrying the
  run's status when it did not converge.

Out:

- Editing the diagram inside STEPSS. The edit button hands off to the
  platform's own application, which is Inkscape for anyone who has it.
- Any diagram for the Dynamic Simulation tab. RAMSES has no equivalent of
  Helios' `1`, and inventing one is a different piece of work.
- Authoring placeholders. Writing `%A D` into a drawing is done in the SVG
  editor, and the placeholder table belongs in `stepss-docs`.

# Part 1: the disturbance file becomes optional

`jLabel9` reads `<html><b>Disturbance file</b> (required)</html>`. It becomes
`(optional)`.

That is the whole functional change, and the reason is worth recording because
it looks like it should be larger. `createPFCCommandFile()` never reads
`fileDist`: it writes the data file paths, the `D` export block, `VT`, and `E`.
The power flow has therefore always run without a disturbance file. The label
was the only thing saying otherwise, and it was saying it on a tab shared by
both engines.

The dynamic side already refuses correctly. `createCommandFile()` returns
"No disturbance file is loaded. Add one on the System Data tab." when the field
is empty, and the caller shows that sentence in the banner. Nothing there
changes: a case with no `.dst` can run a power flow and cannot run a
simulation, which is the correct behaviour and was already implemented.

# Part 2: the 6-bus microgrid example

## Upstream prerequisite

`SPS-L/stepss-6-bus-MG` is public, has a `main` branch, and has **no tags and
no releases**. The examples pipeline pins `<id>.tag`, `<id>.source.url` and a
content manifest digest, so there is nothing to pin until a release exists.

Cut `v1.0.0` there first. Push the lightweight tag before creating the release:
`gh release create` with `--target` on an unpushed tag answers 422.

## The descriptor learns optional slots

`ExampleCatalog.load()` reads `.dist` and `.obs` through `required()`, which
throws when either is absent. `Example.retained()` then adds both to the
retained set unconditionally. An entry omitting them would therefore fail to
parse, and if it did parse, `ExamplesPack` would look in the upstream archive
for a file named `""` and fail the build naming nothing.

Three changes:

- `ExampleCatalog.load()` reads `.dist` and `.obs` with a new `optional()`
  helper defaulting to `""`. `.data` stays required: an example with no data
  file is not an example.
- `Example.retained()` adds `dist` and `obs` only when they are non-empty. The
  set is a `LinkedHashSet`, so an empty string would otherwise become a
  retained path.
- A new optional key `.svg`, exposed as `Example.diagram()`, naming the
  template that fills the new slot. It is retained like any other named file,
  so `ExamplesPack` fails the build if an upstream release stops carrying it.

`StepssUI.applyExample()` currently writes
`new File(dir, example.dist()).getAbsolutePath()` unconditionally. With an
empty `dist` that resolves to the example **directory itself**, which would put
a directory path in the disturbance field and look like a loaded file. It must
write `""` when the example declares no disturbance, and the same for
observables. `saveOutputTrajButton.setSelected(true)` moves inside the
observables branch: ticking "save output trajectory" for a case with nothing to
observe is a setting that cannot do anything.

## The entry

```properties
examples=kundur, nordic, five-bus, six-bus

six-bus.name=6-bus microgrid
six-bus.scale=6 buses, 2 generators, 6/11 kV
six-bus.summary=A power-flow-only case: six buses at 6 and 11 kV, two lines, \
four transformers, loads at D and E, and generators at A (slack) and F. \
There is no dynamic data and no disturbance scenario, so this example runs on \
the Power Flow tab and not the Dynamic Simulation one. It is the case the \
one-line diagram feature is demonstrated with: 6bus.svg is a template carrying \
the %A to %U placeholder codes, and Run Power Flow fills it in with the solved \
voltages, angles and flows. volt_rat.dat holds the solved bus voltages as the \
VT command writes them.
six-bus.docs=https://github.com/SPS-L/stepss-6-bus-MG
six-bus.dir=six-bus-microgrid
six-bus.data=6bus.dat
six-bus.svg=6bus.svg
six-bus.extra=volt_rat.dat, README.md, LICENSE
```

No `.dist` and no `.obs`, deliberately. Shipping an empty `.dst` so the entry
looks like the other three would be a file that claims to be a disturbance
scenario for a case whose own README says it has none.

The summary leads with "A power-flow-only case" because the dialog's detail
pane is where a user finds out what they are about to open, and opening a case
that cannot run a simulation is the one thing about this example that will
surprise someone who has used the other three.

Appended to the index rather than inserted, so the existing three keep their
order.

## Pins, build and CI

- `versions.properties`: a `six-bus.*` block matching the other three
  (`version`, `repo`, `tag`, `source.url`, `source.url.pattern`,
  `manifest.sha256`). The digest comes from running `ant fetch-examples` and
  then `ExamplesPack` with `COMPUTE` in place of the digest argument, which
  prints the value to paste in.
- `build.xml`: one `fetch-example` line in `fetch-examples` and one
  `pack-example` line in `stage-payloads`.
- `tools/ci/pins.py`: `six-bus` added to `EXAMPLES`.
- `tools/ci/release.py`: `"six-bus": "6-bus microgrid"` in the display name
  table, so the release notes name it as they name the others.

Nothing in `Toolchain.java` changes. Example payloads carry no version in their
resource name, which is what `versions.properties` already records as the
reason an example bump touches that file alone.

## The harness

`ExamplesHarness.checkInstalls()` builds its slot list as
`data() + dist() + obs()` and then asserts each named file exists with content.
With optional slots it must skip an empty one, or it will assert that a file
named `""` has content and fail for every power-flow-only example.

`checkCatalogIsSane()` gains two checks: that an entry naming a `.svg` names one
ending in `.svg`, and that every entry either names both a `dist` and an `obs`
or names neither. The second is not pedantry: an example with a disturbance and
no observables would load into a form that can start a simulation and record
nothing from it, and if that combination is ever wanted it should be a decision
someone makes rather than a typo that gets through.

# Part 3: the diagram slot on the System Data tab

## The row

A third section below the disturbance one:

```
One-line diagram annotated SVG (optional)
[ Load File ] [ .../six-bus-microgrid/6bus.svg              ] [pencil]
```

Built in `layoutSystemDataTab()`, which already assembles the whole tab from
`heading()` and `fileRow()` calls against a `GridBagLayout`. Nothing in
`StepssUI.form` changes, and nothing in the generated `initComponents` changes:
`fileDiagram`, `loadDiagram` and `nppDiagramButton` are ordinary fields created
in code, exactly as `layoutSystemDataTab()` already creates its headings and
struts. The heading is a `JLabel` built in the method rather than a form label,
which is the first of the three sections not to have one, and is the reason the
`heading()` helper takes a label rather than a string today. It gains a string
overload.

The file chooser filters on `svg` with the description "Annotated one-line
diagram", matching the shape of the nine `Ramses Data File` choosers above it.

`clearDataFilesActionPerformed` clears the new field along with the ten data
rows and the disturbance.

`styleEditButtons()` gains `nppDiagramButton`, so the pencil, the tooltip, the
toolbar button treatment and the zero margin are applied to it with the other
twelve rather than being set separately and drifting.

## Opening it in the right application

The twelve existing pencils call `nppOpen()`, which calls
`PlatformLauncher.openInEditor()`. That method tries `Desktop.EDIT`, then
`Desktop.OPEN`, and then falls back per platform to `notepad.exe`,
`open -t` and `xdg-open`. Two of those three fallbacks force a **text** editor,
which is right for a `.dat` and wrong for a drawing: an SVG would open as XML
source.

So the diagram row gets a sibling, `PlatformLauncher.openInDefaultApplication(File)`:
the same `Desktop.EDIT` then `Desktop.OPEN` attempt, because EDIT is what opens
Inkscape for anyone who has it associated, but falling back to `xdg-open`,
plain `open` and `cmd /c start ""` instead. The twelve data-file buttons keep
`openInEditor` and the behaviour they have.

The user-facing promise is "your default SVG viewer or editor", and that is
honest: on a machine with no SVG editor associated, `Desktop.EDIT` fails and
`Desktop.OPEN` hands it to the browser, which is a viewer.

## Persistence

The slot is part of a saved study, because it is part of what
`createPFCCommandFile()` writes. `Scenario` gains a `diagram` field with its
getter and setter, `ScenarioBinding` gains a constructor argument and reads and
writes it, `bindScenario()` passes `fileDiagram`, and `ScenarioFile` gains a
`diagram` key written and read as a path through the existing
`ScenarioPaths.resolve` treatment, so it goes relative when it sits beside the
`.cfg` and absolute otherwise.

**`stepss.format` stays at 1.** This is deliberate and the reason matters.
`ScenarioFile.checkFormat()` throws on `format < FORMAT` with "declares
scenario format 1, which no STEPSS release ever wrote". Bumping to 2 would
therefore make this build reject every `.cfg` any user has already saved.
Adding the key at format 1 instead means an older build reading a newer file
applies everything it understands and reports one advisory sentence through
`reportUnknownKeys`, which is the graceful direction and the one this format
was designed for.

That `format < FORMAT` branch is a latent trap for whoever does need a real
format bump one day. It is noted here and not fixed: making it right means
deciding what an older file should do, which is a decision for the change that
needs it and not for this one.

`ScenarioHarness` gains the field in its round-trip check.

# Part 4: producing the annotated SVG

## The command file

`createPFCCommandFile()` appends, when the slot holds a file that exists,
immediately **before** the `VT` block:

```
1
<absolute path to the template>
in_diagram.svg
```

`1` is a main-menu command, like `VT`, so it sits outside the `D` display
sub-menu block. `cmd_diagram()` reads two lines: the template path and the
output name. The output is a bare name, so it lands in Helios' working
directory, which `simulExecutor.setWorkingDirectory(myTempDir)` has already set
to the session's working directory.

One Helios run, not two. The diagram is rendered from `net_` after the solve,
so there is no second invocation and no re-solve.

## Before `VT`, not after

The completion thread waits for `in_volt_trfo.dat`, which `VT` writes, and
treats its appearance as meaning the run is finished. Emitting `1` after `VT`
would make the sentinel fire while the diagram was still being written.
Emitting it before keeps the sentinel honest at no cost.

## Three failures that would otherwise be silent

- **Helios refuses input and output being the same path.** If the slot points
  at the working directory's own `in_diagram.svg`, `cmd_diagram()` prints
  "output file must be different from input file !" and renders nothing.
  Compare canonical paths when writing the command file and say so in the
  banner instead of letting the run look successful and produce no picture.
- **`cmd_diagram()` writes its errors to stdout, and the console filters them
  out.** `HeliosLog.isProgressLine` matches a fixed list of prefixes and drops
  everything else as table data. `PROGRESS_PREFIXES` gains `"Open "`,
  `"This file does not exist !"`, `"output file must be different from input
  file !"` and `"DiagramRenderer: "`, which is the prefix of the renderer's own
  exception text. `HeliosLog`'s class comment already describes this list as a
  soft contract on Helios' wording; these four are derived from `cmd_diagram()`
  and `DiagramRenderer::render` the same way the existing fourteen were derived.
- **A failed render does not fail the run.** `cmd_diagram()` catches its own
  exceptions and does not set `error_`, so a malformed or unreadable template
  leaves the exit status untouched. The absence of the output file is the only
  reliable signal, which is what Part 6 keys the "converged but no diagram"
  banner off.

`deletePFCResultFiles()` gains `in_diagram.svg`, for the reason its comment
already gives for the other seven: a run that aborts before reaching `1` must
not leave the previous run's diagram to be picked up as belonging to this one.

# Part 5: the diagram window

## What it is

`my.stepss.diagram.DiagramWindow`, a non-modal `JFrame`, one per run, cascaded
so the second does not land exactly on the first. The title carries the
template's file name, a run number and a timestamp, because telling two windows
apart at a glance is the entire reason a new one opens rather than the old one
updating.

`SsaResultsWindow` already solves the cascade, in a private method with a
private static counter and a wrap at eight. Rather than copy thirty lines,
lift it into `my.stepss.WindowCascade` and have both use it. That is a change
to code this work sits directly beside, not unrelated refactoring, and the SSA
window's behaviour is unchanged by it.

## Rendering: a `BufferedImage`, produced by Batik

What the window paints is a `BufferedImage`. It keeps the parsed `SVGDocument`
behind that image so it can re-render (see the navigation section below), but
there is no live canvas, no GVT scene graph held between renders and no
document that anything can animate or script. Batik's `ImageTranscoder` is
abstract over exactly the two methods that make this cheap:

```java
public abstract BufferedImage createImage(int width, int height);
public abstract void writeImage(BufferedImage img, TranscoderOutput output);
```

so a small subclass captures the image in memory instead of encoding it to a
stream. Two things fall out:

- **`batik-codec` is never loaded.** That module exists to encode PNG, and the
  JDK's own `ImageIO.write(img, "png", file)` covers the Save as PNG button.
- **No PNG is written unless the user asks for one.** Helios writes
  `in_diagram.svg` into the working directory as it always would; the window
  converts it and keeps the pixels.

Save as PNG is the more useful of the two save buttons to offer, because a PNG
is what pastes into a report or a slide without being mangled. Save as SVG is
offered beside it, since the vector file is the one to keep for a paper.

## Not `JSVGCanvas`

`JSVGCanvas` starts an `UpdateManager` thread, follows external references
through its document loader, and wires up the script bridge. For a static
drawing all three are surface with no purpose, and two of them are the shape of
CVE-2022-44729 and CVE-2022-44730. The transcoder path with a restrictive
`UserAgent`, static bridge state and external resource loading refused closes
that by construction. This is not about the canvas specifically: it is about
not letting an SVG sitting in a user's working directory reach the network.

## Navigating a complicated diagram

A one-line diagram is read by squinting at numbers on it, and a realistic
network is far larger than a window. So the panel is built for zoom and pan
from the start rather than having them added to a picture that assumes it fits.

**The image is always the size of the viewport, never the size of the
diagram.** `SVGAbstractTranscoder` takes a `KEY_AOI` rectangle in the
document's own user space alongside `KEY_WIDTH` and `KEY_HEIGHT`, and renders
that rectangle into exactly that many pixels: it scales by `width/aoi.width`
and translates the origin (`SVGAbstractTranscoder` lines 241 to 254). Zooming
is therefore shrinking the AOI rectangle, and panning is moving it. Both
produce a fresh transcode of the visible region at device resolution.

That is what makes this scale. A full-document render at 800% of a large
network is hundreds of megabytes; the viewport render is the same handful of
megabytes at every zoom level, because it is bounded by the window and not by
the diagram. No pixel cap is needed, no `OutOfMemoryError` is reachable by
zooming, and a bus voltage label at 800% is as sharp as at 100% because it is
rendered from vectors at that scale rather than magnified from a bitmap.

The AOI is always given the viewport's aspect ratio. Batik takes a single
uniform scale from the two axes and centres the result when they disagree, so
matching the aspect makes both of those a no-op and keeps our arithmetic the
only thing deciding what is on screen.

Controls:

- **Fit** and **100%** buttons, plus zoom in and zoom out.
- **Wheel to zoom, anchored at the pointer**, so the thing under the cursor
  stays under the cursor. Button zoom anchors on the viewport centre. Without
  anchoring, zooming into a large diagram throws away whatever the user was
  looking at, which is the difference between a usable viewer and one that is
  abandoned after two clicks.
- **Drag to pan**, with a hand cursor, and arrow keys for fine movement.
- **Double-click to fit**, as the way back when lost.

Zoom is bounded to between a twentieth of Fit and fifty times it, and the pan
is clamped so the diagram cannot be pushed entirely off screen. Both exist for
the same reason: the only way out of either is Fit, and a user who does not
know that is stuck looking at nothing.

**Interaction stays responsive by separating the cheap answer from the correct
one.** During a drag or a wheel burst, the existing image is blitted at its new
offset or scale, which is immediate; a coalescing timer of about 120 ms then
issues one fresh transcode when the gesture settles. So a complicated diagram
tracks the mouse and sharpens a moment later, instead of rendering per mouse
event and lurching. This is the one piece of real complexity in the panel and
it is the piece that decides whether the feature is pleasant on a large
network.

**Save as PNG saves the whole diagram, not the viewport.** A saved figure is
for a report, so it renders the full document at a fixed generous width rather
than whatever happens to be on screen. Zoom is a reading aid; it is not a
crop tool, and a user who zoomed in to check a number and then saved would
otherwise get that number and nothing else.

## The window holds its own copy

The SVG is read and parsed into an `SVGDocument` once, when the window opens,
and every subsequent transcode runs from that document. Re-reading the file on
each zoom would mean the next Run Power Flow, which deletes and rewrites
`in_diagram.svg`, could change what an already open window shows the next time
it is zoomed.

This is the same reasoning `SsaResultsWindow` records for holding its own
parsed results, and it is what makes comparing two runs side by side actually
true rather than approximately true.

## The dependency

Three jars in `lib/`, beside the three already there, all Apache 2.0:

| Jar | Approximate size |
|---|---|
| `batik-all-1.19.jar` | 4.4 MB |
| `xmlgraphics-commons-2.11.jar` | 0.7 MB |
| `xml-apis-ext-1.3.04.jar` | 85 KB |

About 5 MB on a 34 MB jar. Registered in `nbproject/project.properties` as
`file.reference.*` entries on `javac.classpath`, copied into `dist/lib` by the
existing `ant jar`, and named in `packaging/linux/copyright`, which ships as
`/usr/share/doc/stepss/copyright` in the `.deb`.

`batik-all` rather than the individual modules. The transcoder's closure is
`batik-bridge`, `batik-anim`, `batik-dom`, `batik-gvt`, `batik-svg-dom`,
`batik-css`, `batik-parser`, `batik-awt-util`, `batik-util`, `batik-xml`,
`batik-ext`, `batik-constants`, `batik-i18n`, `batik-svggen` and the shared
resources jar. Taking them separately would put 18 jars in `lib/` to save
roughly 1 MB and would make the closure something a human has to keep right by
hand.

Nothing about component licensing changes. These are bundled third-party
libraries, not STEPSS components, so `getting-started/license.md` in
`stepss-docs` and the four summaries that point at it are untouched.

## The risk that is not asserted away

`batik-all` bundles `batik-script`, whose Rhino interpreter factory is
registered through `META-INF/services`, and Rhino itself is not shipped.
Batik's `InterpreterPool` is expected to swallow the missing class, and in
static bridge state no scripting environment is constructed at all. That is
expected behaviour, not verified behaviour, so it is proved rather than
assumed: `DiagramCheck` transcodes with no Rhino on the classpath and fails
loudly if it cannot.

If it does not survive, the fallback is the individual module jars with
`batik-script` excluded, at the cost of the 18 jar closure above.

# Part 6: what the window says when the run failed

The window opens on **every** run with a diagram configured, converged or not.
On a failure it shows the original template, unannotated, under a banner.

## One decision, two renderings

`StepssUI.describeHeliosExit(exitValue, stderrText)` already derives exactly
this from exactly these two inputs, per the exit-status contract in
`../stepss-helios/README.md`. Writing a second copy for the banner would be two
answers to one question, and they would drift.

So a package-private `my.stepss.HeliosOutcome` is factored out of it, holding a
severity, a headline and a detail. `describeHeliosExit` builds its
`HeliosStatusDialog` from that value and keeps its signature, so its call site
and its documented contract are unchanged. `DiagramWindow` builds its banner
from the same value.

The headline is the status-bar phrasing, so the window agrees with the bottom
right of the main frame, and the reason Helios gave follows underneath when
there is one.

| Exit | Banner |
|---|---|
| 0 | none |
| 2 | **Power flow did NOT converge (max iterations).** The diagram below is drawn from an unconverged solution. Do not use these values. |
| 1 | **Helios could not process the input.** The diagram below is the unannotated template. |
| other | **Helios exited abnormally (status N).** Treat the values below with suspicion. |

The parenthesised reason comes from the `helios: status: TOKEN (detail)` line
on stderr, which the existing `HELIOS_STATUS_LINE` pattern already extracts and
which is absent from older engines, in which case the headline stands alone.

One case has no exit status behind it: a run that **converged** and still
produced no `in_diagram.svg`, because `cmd_diagram()` swallowed a template
error. That gets its own banner naming the template and saying the render
failed, and the window shows the template unannotated. Without it a broken
template would produce a window that looks like a successful run with no
numbers on it.

# Testing

This repository has no unit test framework; the harness scripts under `tools/`
are the substitute, and each is a `main()` on the build classpath.

New: `tools/diagram-harness.sh` running `my.stepss.diagram.DiagramCheck`,
headless, with no Rhino on the classpath.

- `6bus.svg` parses and transcodes to an image of the expected dimensions with
  non-background pixels in it. This is the end-to-end assertion that answers
  the `batik-script` question.
- The same document transcodes twice at one image size with two different AOI
  rectangles, and the two images differ. That is the assertion that zoom and
  pan re-render the region rather than scaling a bitmap.
- A tenfold zoom produces an image of the same pixel size as Fit, proving the
  render cost is bounded by the viewport and not by the zoom level.
- Two AOI rectangles differing only by a translation produce images that differ
  by that translation, which is the pan arithmetic checked without a mouse.
- Zoom clamping holds at both ends, and the pan clamp refuses to put the
  document's bounding box entirely outside the viewport.
- A `UserAgent` asked for an external resource refuses rather than fetching.
- A malformed SVG raises rather than producing a blank image.
- `HeliosOutcome.of()` returns the four rows of the table above for exit
  values 0, 1, 2 and 3, with and without a `helios: status:` line.

That last one is worth calling out. `describeHeliosExit` is package-visible and
its comment says it is "exercised directly by tests without needing a live
GUI", but **nothing in the repository calls it**. This gives it its first
coverage.

Extended: `tools/examples-harness.sh` for the optional slots, and
`tools/scenario-harness.sh` for the `diagram` key round trip.

Manual, on the 6-bus example, because no harness can drive Helios:

1. File > Open Examples, 6-bus microgrid, Fresh copy. One data slot filled, the
   diagram slot filled, disturbance and observables empty.
2. Run Power Flow. Window opens, no banner, values on the diagram.
3. Run Power Flow again. A second window, offset, both readable, both showing
   their own run.
4. Wheel-zoom to 400% over bus D. The point under the cursor stays under the
   cursor, and the labels stay sharp rather than going soft.
5. Drag to pan across the diagram. It tracks the mouse and sharpens when the
   drag stops. Double-click returns to Fit.
6. Save as PNG while zoomed in. The saved file holds the whole diagram, not
   the visible corner.
7. Edit `6bus.dat` to something that cannot converge. Run. Window opens with
   the red banner and the reason.
8. Clear the diagram slot. Run. No window, and no `1` in `PFCcmd.txt`.
9. Point the slot at a file that is not an SVG. Run. The render failure banner,
   naming the file.
10. Dynamic Simulation tab, Run. Refused, naming the missing disturbance file.
11. Save configuration, clear the form, Load configuration. The diagram slot
   comes back.

# Documentation follow-up

`stepss-docs` gains a one-line diagram section under `user-guide/power-flow`,
carrying the placeholder table from `DiagramRenderer.cpp`:

| Code | Argument | Value |
|---|---|---|
| `%A` | bus | voltage magnitude (pu) |
| `%B` | bus | voltage (kV) |
| `%C` | bus | angle (degrees) |
| `%D` | branch, bus | P flow at that bus end (MW) |
| `%E` | branch, bus | Q flow at that bus end (Mvar) |
| `%F` | branch | loading fraction |
| `%G` | generator | P (MW) |
| `%H` | bus | shunt Q (Mvar) |
| `%J` | transformer | tap position |
| `%K` | generator | Q (Mvar) |
| `%L` | SVC | Q (Mvar) |
| `%R` | bus | load P (MW) |
| `%S` | bus | load Q (Mvar) |
| `%T` | branch | breaker status, blank or `X` |
| `%U` | generator | breaker status, blank or `X` |

`%I` and `%M` through `%Q` are recognised and return `unknown`, because they
need zone and sensitivity data the model does not carry yet. They must be
documented as unsupported rather than omitted, or someone will write one into a
drawing and get the word `unknown` on their diagram with nothing explaining it.

The `stepss-java-ui` README gains the new slot in its description of the System
Data tab.

# Done when

- A power flow runs with no disturbance file and the tab no longer claims one
  is required.
- 6-bus microgrid appears in Open Examples, extracts, and fills one data slot
  and the diagram slot and no others.
- Run Power Flow on it opens a window with the solved values on the diagram.
- A second run opens a second window and the first still shows its own run.
- The diagram can be zoomed and panned, stays sharp at every zoom level, and
  renders no more pixels at 800% than at Fit.
- A run that does not converge opens a window showing the template under a
  banner carrying the same status the bottom right of the main window shows.
- `tools/diagram-harness.sh`, `tools/examples-harness.sh` and
  `tools/scenario-harness.sh` pass.
- `ant jar` succeeds with the three new jars, and the `.deb` copyright names
  them.
