# STEPSS for Java

**Static and Transient Electric Power Systems Simulation**

[STEPSS](https://stepss.sps-lab.org/) is a power system simulation platform for dynamic studies of electrical grids. It is delivered in [two editions](https://stepss.sps-lab.org/getting-started/overview/#two-editions), which drive the same engines and read the same data files:

| Edition | Distributed as | Use it for |
|---|---|---|
| **STEPSS for Java**, this repository | `stepss.jar`, a desktop application | Interactive work: load a network, run it, plot curves, build models |
| **STEPSS for Python** | the `stepss` package, `pip install stepss` | Scripting, parameter sweeps, and the scientific Python stack |

Neither edition wraps the other, and a case built in one runs unchanged in the other.

This one is a Java (Swing) desktop application. It bundles the complete simulation toolchain (RAMSES, Helios, CODEGEN, DYNGRAPH, gnuplot) into a single jar, so a network can be loaded, simulated statically and dynamically, and analysed without touching the command line. **CODEGEN makes this the edition for building your own models**: it is the one component the Python edition does not carry.

## Features

- **Complete workflow in tabs**: System Data, Observables, Power Flow Simulation, Dynamic Simulation, Analysis, and Codegen
- **Bundled examples**: *File -> Open Examples* extracts a ready-to-run test system (Kundur two-area, IEEE Nordic, or the 5-bus tutorial) into your examples directory and fills in the case, so there is something to run on a fresh install
- **Dynamic simulation**: runs the bundled RAMSES engine on the loaded data and disturbance files
- **Power flow**: drives the bundled Helios power-flow engine
- **Real-time plotting**: live curves during simulation via gnuplot (bus voltages, machine speeds, branch flows, wall time, and more)
- **Result extraction**: "Extract Curves" launches the bundled DYNGRAPH viewer on saved output trajectories
- **Analysis tools**: Jacobian matrix extraction, and small-signal stability analysis computed by the engine itself (see `examples/kundur-ssa/`)
- **User models**: the Codegen tab generates user-written model source with CODEGEN and compiles it into a custom simulator with gfortran
- **Observable wizard**: dialog for selecting buses, machines, shunts, branches, and injectors to record
- **Integrated editing**: opens data and disturbance files in the operating system's default editor
- **Built-in help**: online user guide, release notes, and update checker
- **Light and dark themes**: toggled from *Tools -> Dark theme*, remembered between sessions, and applied to the title bar as well as the window; the window icon and the About lockup come in the variant that matches
- **Cross-platform**: Windows, Linux, and macOS (Apple Silicon), with menu shortcuts on each platform's own modifier

## Installation

**Requirements:** 64-bit Java 11 or later (JRE to run, JDK to build), [Apache Ant](https://ant.apache.org/) to build. Windows and Linux are x86_64; macOS is Apple Silicon (arm64) only, Intel Macs are not supported.

The prebuilt jar is published as a **release artifact** on the [releases page](https://github.com/SPS-L/stepss-java-ui/releases), not committed to this repository: `build/` and `dist/` are untracked. Download it there if you just want to run STEPSS.

### Build from source

```bash
git clone https://github.com/SPS-L/stepss-java-ui.git
cd stepss-java-ui
ant jar
```

The build (a NetBeans/Ant project) produces `dist/stepss.jar`, a self-contained jar with the Commons Exec, Commons IO and FlatLaf libraries merged in.

Building fetches the pinned RAMSES, Helios, DYNGRAPH, and CODEGEN binaries for all three platforms (`ant fetch-payloads`, run automatically as part of `ant jar`) from their releases in the SPS-L GitHub organisation. Those component repositories are private, so the first build needs network access and the [`gh` CLI](https://cli.github.com/) authenticated with SPS-L access (`gh auth login`); downloaded archives are checksum-verified against `versions.properties` and cached in `payload-cache/`, so later builds only need network again when a pinned version changes. CI authenticates the same way, through this repository's `STEPSS_TOKEN` secret, because Actions' default `GITHUB_TOKEN` is scoped to this repo alone and cannot reach the component repos.

It also fetches the pinned URAMSES kit and the three bundled example test systems (`ant fetch-uramses`, `ant fetch-examples`). Those repositories are public, so they come over plain HTTPS and need neither `gh` nor a token. Each is verified against a **content manifest** rather than the archive's own digest, because GitHub's generated source archives are not guaranteed byte-stable; the examples are additionally filtered down to the files `src/my/stepss/examples/examples.properties` names, and the build fails if a pinned release stops carrying one of them.

On macOS, the current RAMSES, DYNGRAPH, and CODEGEN builds are dynamically linked against gfortran and OpenBLAS; install them first with `brew install gcc openblas`. Statically linked builds that drop this requirement are expected from those projects.

Compiling custom models is optional and needs a Fortran toolchain on your machine: `gfortran`, GNU `make`, and OpenBLAS. On Debian/Ubuntu that is `sudo apt install gfortran make libopenblas-dev`; on macOS `brew install gcc openblas`; on Windows install [MSYS2](https://www.msys2.org/) and run `pacman -S mingw-w64-x86_64-gcc-fortran mingw-w64-x86_64-openblas make` (STEPSS looks in `C:\msys64`, or wherever `MSYS2_ROOT` points). The bundled module kits are gfortran-ABI-specific and each platform's default compiler matches its own kit; if yours does not, STEPSS reports the exact compiler version to install. Everything else in STEPSS works without any of this.

### Refreshing the application's marks

The window icon and the About lockup are PNGs in `src/my/stepss/`, rendered from the SVG sources in [stepss-docs](https://github.com/SPS-L/stepss-docs) `src/assets`, in a light and a dark variant each. They are stored rasterised so that nothing has to render vectors at runtime and no SVG library is on the classpath; the cost is that they go stale when the artwork changes, and the build does not notice. Re-export all fourteen with:

```bash
tools/refresh-marks.sh                       # expects ../stepss-docs/src/assets
tools/refresh-marks.sh /path/to/src/assets   # or say where they are
```

It needs [Inkscape](https://inkscape.org/), and that is not interchangeable with ImageMagick: `convert -resize` rasterises an SVG at the size the document declares and then scales the raster, which for the 295x100 lockup produces a visibly blurry enlargement. Inkscape rasterises the vectors at the size asked for. `tools/chrome-harness.sh` confirms every mark still resolves afterwards.

### Native installers

`ant jar` produces a jar you run with `java -jar`. `ant bundle` wraps that jar in a launcher, an icon and a Java runtime, so STEPSS installs and starts like an application and the machine it runs on needs no Java of its own:

```bash
ant bundle                              # the platform's installer: .deb, .msi or .dmg
ant bundle -Dbundle.type="--type app-image"   # just the unpacked application directory
```

It needs `jpackage`, which ships with JDK 14 and later, and it only ever builds for the platform it runs on. The three installers on a release therefore come from three CI runners rather than from one machine. The icon is `packaging/stepss.png` on Linux and `packaging/stepss.ico` on Windows, both rendered from the same stepss-docs source as the in-application marks; macOS needs a real `.icns` container, which only `iconutil` produces, so the release workflow builds one on the macOS runner and it is not committed.

The bundles are a second CI job, and nothing is published until all three of them finish. The release job creates the release as a **draft**, which creates no tag and is invisible to users; each runner attaches its installer to that draft; a final job publishes it and tells the two package managers. One runner failing means the draft is discarded and the run goes red, so there is no tag, no release and no partial set of installers, and re-running reuses the same version number. It used to publish first and attach afterwards, on the reasoning that a release carrying `stepss.jar` alone still beat no release; v3.74.17 is why it no longer does, having gone out with the Windows artifacts and neither the `.deb` nor the `.dmg` while apt went on serving the previous version.

The Linux `.deb` is the one bundle an archive serves rather than a person downloads, so `packaging/linux` overrides six of jpackage's own templates. What that buys, beyond the desktop menu entry: the RAMSES runtime libraries and gnuplot are declared as dependencies, the Fortran toolchain is a `Recommends:`, `/usr/bin/stepss` is a command you can type, and `/usr/share/doc/stepss/copyright` names each bundled component and the licence it travels under. jpackage can derive none of that, because it reads dependencies off the app image and the whole simulation toolchain is inside the jar as resources it extracts at run time.

```bash
tools/deb-harness.sh          # install the built .deb in a clean container
```

It needs Docker. Two failures are invisible without one: this machine has libgfortran and OpenBLAS installed for other reasons, and it has a desktop, so `xdg-desktop-menu` succeeds here and exits 3 on the servers, containers and WSL installs where users meet it. The harness installs the package for real, checks that `ramses` starts, and removes it again. It runs in CI before the `.deb` is attached to the draft, and the package is built on Ubuntu 24.04, which is therefore the oldest release it installs on.

The bundled runtime is the full JDK runtime rather than a trimmed `jlink` image. STEPSS extracts and runs native executables through Commons Exec, so what a module scan can see and what the application actually needs are different questions, and roughly 40MB is a fair price for removing a class of failure where the bundle starts and then cannot find a class.

### Releases

Releases are cut automatically. RAMSES, Helios, DYNGRAPH and CODEGEN dispatch to this repository when they publish, and the run re-pins `versions.properties` and the matching resource names in `Toolchain.java`, rebuilds, verifies that the bundled toolchain extracts correctly, and drafts a release with `stepss.jar` attached for the bundle job to fill in and publish. (URAMSES does not dispatch: it only ever releases in response to a RAMSES release under the same tag, and the RAMSES dispatch covers both.) The notes list every component's pinned version, and embed the upstream release notes of the components that actually moved, since four of the five component repos are private and cannot be linked to usefully.

The bundled example test systems are re-pinned by the same run, but they never trigger one: they are reported as `refreshed` rather than `changed`, and only `changed` decides whether to publish. An example repository being tagged is not a reason to publish a new STEPSS, so a refreshed example rides along with the next release instead.

STEPSS checks for a newer release when it starts and says so on the banner across the top of the window, with a link to the release page. It never blocks startup on that check and says nothing when it cannot reach github.com. Turn it off under **Tools > Check for updates at startup**.

Nothing is published until the build and the toolchain check have both passed. The commit that re-pins the build is pushed immediately before the release is created, and the tag is created by the same API call that creates the release, so a run that fails leaves no tag and no release behind, and re-running it publishes under the same version number. Any failure opens an issue.

Release numbers follow the pinned RAMSES version, with a counter for releases driven by the other components: `v3.55`, then `v3.55.1`, `v3.55.2`, and so on until RAMSES itself moves.

Running the workflow by hand (Actions → Release → Run workflow) also picks up any new component releases first, then publishes regardless: that is how a change to the Java sources alone reaches a release.

To re-pin locally instead, run `python3 -m tools.ci bump` from the repository root (add `--dry-run` to see what would change without downloading or writing anything). It rewrites `versions.properties` and `Toolchain.java` together; editing only the former leaves the build naming the old asset, which `ant jar` catches.

## Quick Start

```bash
java -jar dist/stepss.jar
```

Then, in the GUI:

1. Load the **System data** files (`.dat`) in the *System Data* tab
2. Load the **Disturbance file** (`.dst`)
3. Select observables to record (*Observables* tab or the Observable dialog)
4. Run the simulation from the *Dynamic Simulation* tab
5. Plot results with **Extract Curves** (DYNGRAPH) or watch the real-time gnuplot curves

## Bundled tools

The jar embeds the toolchain executables for the platform it runs on and extracts them at runtime. RAMSES, Helios, DYNGRAPH, and CODEGEN are fetched from their pinned SPS-L releases at build time (see [Installation](#installation)) on all three platforms; gnuplot on Windows is the only binary committed directly.

| Tool | Role | Windows | Linux | macOS (Apple Silicon) |
|---|---|---|---|---|
| RAMSES (`dynsim`) | Dynamic simulation | yes | yes | yes |
| Helios | Power flow | yes | yes | yes |
| DYNGRAPH | Curve viewer | yes | yes | yes |
| CODEGEN | Model generation | yes | yes | yes |
| Model compilation | Custom models | yes (MSYS2/MinGW) | yes (gfortran) | yes (Homebrew gcc) |
| gnuplot | Real-time plotting | bundled | resolved from `PATH` | resolved from `PATH` |
| Data file editing | OS default editor | yes | yes | yes |

DYNGRAPH is the same console program on all three platforms, but Extract Curves no longer opens it in a terminal window: STEPSS reads the trajectory's observables with `dyngraph --list`, presents them in a selection dialog, and drives the extraction through a generated command file (`-t`). Running DYNGRAPH by hand, outside STEPSS, still gives the console prompts.

The bundled RAMSES runs limited (up to 1000 buses, 2 cores) unless a `LICENSE` record is supplied among the data files. There is only one engine build; the limit is lifted by the licence the engine itself reads, not by a different binary, so STEPSS cannot tell which of the two you are running and does not claim to. The engine's own banner in the simulation output reports it.

On first run STEPSS shows the RAMSES licence and asks you to accept it. Declining exits.

In addition, the application distributes the following third-party Java libraries (merged into `stepss.jar` and shipped in `dist/lib/`): Apache Commons Exec, Apache Commons IO, and FlatLaf (all Apache License 2.0). FlatLaf is the look and feel; it renders the same on all three platforms, scales on HiDPI, and provides the dark theme offered under **Tools -> Dark theme**. Because it is a multi-release jar, `manifest.mf` declares `Multi-Release: true`.

## Related Projects

- [STEPSS for Python](https://stepss.sps-lab.org/python/): the `stepss` package, Python interface for RAMSES
- [URAMSES](https://github.com/SPS-L/stepss-uramses): user models for stepss

## Documentation

- [Installation guide](https://stepss.sps-lab.org/getting-started/installation/): GUI and Java setup
- [Quick start](https://stepss.sps-lab.org/getting-started/quickstart/): running simulations from the GUI
- [STEPSS documentation site](https://stepss.sps-lab.org/): user guide, file formats, model reference
- In-app: **Help → User Guide** (opens [stepss.sps-lab.org](https://stepss.sps-lab.org/)) and **Help → Changelog** (opens [this repository's releases](https://github.com/SPS-L/stepss-java-ui/releases))

## License

STEPSS is distributed under the **Apache License 2.0**. See [LICENSE](LICENSE). Copyright © Petros Aristidou.

The Apache license covers the Java source code in this repository only. The bundled RAMSES, Helios, CODEGEN, and DYNGRAPH executables are proprietary components under their own terms, and the bundled third-party tools and libraries (gnuplot, KLU, Apache Commons Exec/IO, FlatLaf) remain under their respective licenses. The license text of each bundled component is embedded in the application and viewable from the About dialog.

## Authors

Developed and maintained by the [Sustainable Power Systems Laboratory (SPS-L)](https://sps-lab.org/) at the Cyprus University of Technology, under the direction of Dr. Petros Aristidou.

STEPSS was created by Dr. Petros Aristidou and Dr. Thierry Van Cutsem.
