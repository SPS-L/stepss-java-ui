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

- **Complete workflow in tabs**: System Data, Observables, Initialization, Dynamic Simulation, Analysis, and Codegen
- **Dynamic simulation**: runs the bundled RAMSES engine on the loaded data and disturbance files
- **Power flow**: drives the bundled Helios power-flow engine
- **Real-time plotting**: live curves during simulation via gnuplot (bus voltages, machine speeds, branch flows, wall time, and more)
- **Result extraction**: "Extract Curves" launches the bundled DYNGRAPH viewer on saved output trajectories
- **Analysis tools**: Jacobian matrix extraction and small-signal stability analysis
- **User models**: the Codegen tab generates user-written model source with CODEGEN and compiles it into a custom simulator with gfortran
- **Observable wizard**: dialog for selecting buses, machines, shunts, branches, and injectors to record
- **Integrated editing**: opens data and disturbance files in the operating system's default editor
- **Built-in help**: online user guide, release notes, and update checker
- **Cross-platform**: Windows, Linux, and macOS (Apple Silicon)

## Installation

**Requirements:** 64-bit Java 11 or later (JRE to run, JDK to build), [Apache Ant](https://ant.apache.org/) to build. Windows and Linux are x86_64; macOS is Apple Silicon (arm64) only, Intel Macs are not supported.

The prebuilt jar is published as a **release artifact** on the [releases page](https://github.com/SPS-L/stepss-java-ui/releases), not committed to this repository: `build/` and `dist/` are untracked. Download it there if you just want to run STEPSS.

### Build from source

```bash
git clone https://github.com/SPS-L/stepss-java-ui.git
cd stepss-java-ui
ant jar
```

The build (a NetBeans/Ant project) produces `dist/stepss.jar`, a self-contained jar with the Commons Exec and Commons IO libraries merged in.

Building fetches the pinned RAMSES, Helios, DYNGRAPH, and CODEGEN binaries for all three platforms (`ant fetch-payloads`, run automatically as part of `ant jar`) from their releases in the SPS-L GitHub organisation. Those component repositories are private, so the first build needs network access and the [`gh` CLI](https://cli.github.com/) authenticated with SPS-L access (`gh auth login`); downloaded archives are checksum-verified against `versions.properties` and cached in `payload-cache/`, so later builds only need network again when a pinned version changes. CI authenticates the same way, through this repository's `STEPSS_TOKEN` secret, because Actions' default `GITHUB_TOKEN` is scoped to this repo alone and cannot reach the component repos.

On macOS, the current RAMSES, DYNGRAPH, and CODEGEN builds are dynamically linked against gfortran and OpenBLAS; install them first with `brew install gcc openblas`. Statically linked builds that drop this requirement are expected from those projects.

Compiling custom models is optional and needs a Fortran toolchain on your machine: `gfortran`, GNU `make`, and OpenBLAS. On Debian/Ubuntu that is `sudo apt install gfortran make libopenblas-dev`; on macOS `brew install gcc openblas`; on Windows install [MSYS2](https://www.msys2.org/) and run `pacman -S mingw-w64-x86_64-gcc-fortran mingw-w64-x86_64-openblas make` (STEPSS looks in `C:\msys64`, or wherever `MSYS2_ROOT` points). The bundled module kits are gfortran-ABI-specific and each platform's default compiler matches its own kit; if yours does not, STEPSS reports the exact compiler version to install. Everything else in STEPSS works without any of this.

### Releases

Releases are cut automatically. A daily GitHub Actions run checks the five pinned components (RAMSES, Helios, DYNGRAPH, CODEGEN and URAMSES) for new releases; when one has moved, it re-pins `versions.properties` and the matching resource names in `Toolchain.java`, rebuilds, verifies that the bundled toolchain extracts correctly, and publishes a release with `stepss.jar` attached. The notes list every component's pinned version, and embed the upstream release notes of the components that actually moved, since four of the five component repos are private and cannot be linked to usefully.

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

The bundled RAMSES is the free *Limited* build (up to 1000 buses, 2 cores).

In addition, the application distributes the following third-party Java libraries (merged into `stepss.jar` and shipped in `dist/lib/`): Apache Commons Exec, Apache Commons IO (both Apache License 2.0), and NetBeans AbsoluteLayout.

## Related Projects

- [PyRAMSES](https://stepss.sps-lab.org/pyramses/): Python interface for RAMSES
- [URAMSES](https://github.com/SPS-L/stepss-uramses): user models for PyRAMSES

## Documentation

- [Installation guide](https://stepss.sps-lab.org/getting-started/installation/): GUI and Java setup
- [Quick start](https://stepss.sps-lab.org/getting-started/quickstart/): running simulations from the GUI
- [STEPSS documentation site](https://stepss.sps-lab.org/): user guide, file formats, model reference
- In-app: **Help → User Guide** (opens [stepss.sps-lab.org](https://stepss.sps-lab.org/)) and **Help → Changelog** (opens [this repository's releases](https://github.com/SPS-L/stepss-java-ui/releases))

## License

STEPSS is distributed under the **Apache License 2.0**. See [LICENSE](LICENSE). Copyright © Petros Aristidou.

The Apache license covers the Java source code in this repository only. The bundled RAMSES, Helios, CODEGEN, and DYNGRAPH executables are proprietary components under their own terms, and the bundled third-party tools and libraries (gnuplot, KLU, Apache Commons Exec/IO, NetBeans AbsoluteLayout) remain under their respective licenses. The license text of each bundled component is embedded in the application and viewable from the About dialog.

## Authors

Developed and maintained by the [Sustainable Power Systems Laboratory (SPS-L)](https://sps-lab.org/) at the Cyprus University of Technology, under the direction of Dr. Petros Aristidou.

STEPSS was created by Dr. Petros Aristidou and Dr. Thierry Van Cutsem.
