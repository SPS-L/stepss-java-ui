# STEPSS

**Static and Transient Electric Power Systems Simulation**

STEPSS is the Java (Swing) desktop GUI for the RAMSES dynamic power system simulator, part of the [STEPSS](https://stepss.sps-lab.org/) power system simulation platform. It bundles the complete simulation toolchain (RAMSES, Helios, CODEGEN, DYNGRAPH, gnuplot) into a single application, so users can load a network, run static and dynamic simulations, and analyse the results without touching the command line.

Current release: **3.55**.

## Features

- **Complete workflow in tabs**: System Data, Observables, Initialization, Dynamic Simulation, Analysis, and Codegen
- **Dynamic simulation**: runs the bundled RAMSES engine on the loaded data and disturbance files
- **Power flow**: drives the bundled Helios power-flow engine
- **Real-time plotting**: live curves during simulation via gnuplot (bus voltages, machine speeds, branch flows, wall time, and more)
- **Result extraction**: "Extract Curves" launches the bundled DYNGRAPH viewer on saved output trajectories
- **Analysis tools**: Jacobian matrix extraction and small-signal stability analysis
- **User models**: the Codegen tab generates user-written model source with CODEGEN; compiling it into a custom simulator executable returns in a later release built on gfortran
- **Observable wizard**: dialog for selecting buses, machines, shunts, branches, and injectors to record
- **Integrated editing**: opens data and disturbance files in the operating system's default editor
- **Built-in help**: online user guide, changelog viewer, and update checker
- **Cross-platform**: Windows, Linux, and macOS (Apple Silicon)

## Installation

**Requirements:** 64-bit Java 11 or later (JRE to run, JDK to build), [Apache Ant](https://ant.apache.org/) to build. Windows and Linux are x86_64; macOS is Apple Silicon (arm64) only, Intel Macs are not supported.

The prebuilt jar is published as a **release artifact** on the [releases page](https://github.com/SPS-L/stepss-java-ui/releases), not committed to this repository — `build/` and `dist/` are untracked. Download it there if you just want to run STEPSS.

### Build from source

```bash
git clone https://github.com/SPS-L/stepss-java-ui.git
cd stepss-java-ui
ant jar
```

The build (a NetBeans/Ant project) produces `dist/stepss.jar`, a self-contained jar with the Commons Exec and Commons IO libraries merged in.

Building fetches the pinned RAMSES, Helios, DYNGRAPH, and CODEGEN binaries for all three platforms (`ant fetch-payloads`, run automatically as part of `ant jar`) from their releases in the SPS-L GitHub organisation. Those component repositories are private, so the first build needs network access and the [`gh` CLI](https://cli.github.com/) authenticated with SPS-L access (`gh auth login`); downloaded archives are checksum-verified against `versions.properties` and cached in `payload-cache/`, so later builds only need network again when a pinned version changes. CI authenticates the same way, through this repository's `STEPSS_TOKEN` secret — Actions' default `GITHUB_TOKEN` is scoped to this repo alone and cannot reach the component repos.

On macOS, the current RAMSES, DYNGRAPH, and CODEGEN builds are dynamically linked against gfortran and OpenBLAS; install them first with `brew install gcc openblas`. Statically linked builds that drop this requirement are expected from those projects.

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

The jar embeds the toolchain executables for the platform it runs on and extracts them at runtime. RAMSES, Helios, DYNGRAPH (on Linux and macOS), and CODEGEN are fetched from their pinned SPS-L releases at build time (see [Installation](#installation)); gnuplot on Windows and the Windows DYNGRAPH build are committed directly.

| Tool | Role | Windows | Linux | macOS (Apple Silicon) |
|---|---|---|---|---|
| RAMSES (`dynsim`) | Dynamic simulation | yes | yes | yes |
| Helios | Power flow | yes | yes | yes |
| DYNGRAPH | Curve viewer | yes (dialog build) | yes (console) | yes (console) |
| CODEGEN | Model generation | yes | yes | yes |
| Model compilation | Custom models | not available this release | not available this release | not available this release |
| gnuplot | Real-time plotting | bundled | resolved from `PATH` | resolved from `PATH` |
| Data file editing | OS default editor | yes | yes | yes |

DYNGRAPH on Windows is the one exception to "fetched from a release": its published binaries are console-only, so the GUI still ships the committed Intel dialog build.

The bundled RAMSES is the free *Limited* build (up to 1000 buses, 2 cores). See [NOTICE](NOTICE).

In addition, the application distributes the following third-party Java libraries (merged into `stepss.jar` and shipped in `dist/lib/`): Apache Commons Exec, Apache Commons IO (both Apache License 2.0), and NetBeans AbsoluteLayout.

## Related Projects

- [PyRAMSES](https://stepss.sps-lab.org/pyramses/): Python interface for RAMSES
- [URAMSES](https://github.com/SPS-L/stepss-uramses): user models for PyRAMSES

## Documentation

- [Installation guide](https://stepss.sps-lab.org/getting-started/installation/): GUI and Java setup
- [Quick start](https://stepss.sps-lab.org/getting-started/quickstart/): running simulations from the GUI
- [STEPSS documentation site](https://stepss.sps-lab.org/): user guide, file formats, model reference
- In-app: **Help → User Guide** (opens [stepss.sps-lab.org](https://stepss.sps-lab.org/)) and **Help → Changelog**

## License

STEPSS is distributed under the **Apache License 2.0**. See [LICENSE](LICENSE). Copyright © Petros Aristidou.

The Apache license covers the Java source code in this repository only. The bundled RAMSES, Helios, CODEGEN, and DYNGRAPH executables are proprietary components under their own terms, and the bundled third-party tools and libraries (gnuplot, KLU, Apache Commons Exec/IO, NetBeans AbsoluteLayout) remain under their respective licenses. See [NOTICE](NOTICE) for the complete list. The license texts of the bundled tools are also embedded in the application.

## Authors

Developed and maintained by the [Sustainable Power Systems Laboratory (SPS-L)](https://sps-lab.org/) at the Cyprus University of Technology, under the direction of Dr. Petros Aristidou.

STEPSS was created by Dr. Petros Aristidou and Dr. Thierry Van Cutsem.
