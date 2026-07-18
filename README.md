# STEPSS

**Static and Transient Electric Power Systems Simulation**

STEPSS is the Java (Swing) desktop GUI for the RAMSES dynamic power system simulator — part of the [STEPSS](https://stepss.sps-lab.org/) power system simulation platform. It bundles the complete simulation toolchain (RAMSES, PFC, CODEGEN, DYNGRAPH, gnuplot) into a single application, so users can load a network, run static and dynamic simulations, and analyse the results without touching the command line.

Current release: **3.40**.

## Features

- **Complete workflow in tabs** — System Data, Observables, Initialization, Dynamic Simulation, Analysis, and Codegen
- **Dynamic simulation** — runs the bundled RAMSES engine on the loaded data and disturbance files
- **Power flow** — drives the bundled PFC (Power Flow Computation) executable
- **Real-time plotting** — live curves during simulation via gnuplot (bus voltages, machine speeds, branch flows, wall time, and more)
- **Result extraction** — "Extract Curves" launches the bundled DYNGRAPH viewer on saved output trajectories
- **Analysis tools** — Jacobian matrix extraction and small-signal stability analysis
- **User models** — the Codegen tab compiles user-written models with CODEGEN and saves a custom simulator executable
- **Observable wizard** — dialog for selecting buses, machines, shunts, branches, and injectors to record
- **Integrated editing** — opens data and disturbance files in the bundled Notepad++ (via Wine on Linux)
- **Built-in help** — embedded user guide PDF, online changelog viewer, and update checker
- **Cross-platform** — 64-bit Windows and Linux

## Installation

**Requirements:** 64-bit Java 11 or later (JRE to run, JDK to build), [Apache Ant](https://ant.apache.org/) to build. A prebuilt `dist/stepss.jar` ships in the repository, so building is only needed if you change the source.

### Build from source

```bash
git clone https://github.com/SPS-L/stepss-java-ui.git
cd stepss-java-ui
ant jar
```

The build (a NetBeans/Ant project) produces `dist/stepss.jar` — a self-contained jar with the Commons Exec and Commons IO libraries merged in.

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

The jar embeds the toolchain executables (Windows and Linux) and extracts them at runtime:

| Tool | Role |
|---|---|
| RAMSES (`dynsim`) | Dynamic (time-domain) simulation engine |
| PFC | Power flow computation |
| CODEGEN | Compiles user-written dynamic models |
| DYNGRAPH | Curve viewer for output trajectories |
| gnuplot | Real-time plotting during simulation |
| Notepad++ | Data file editing (runs via Wine on Linux) |
| URAMSES | Skeleton project for user models |
| User guide | PDF opened from the Help menu |

The bundled RAMSES is the free *Limited* build (up to 1000 buses, 2 cores) — see [NOTICE](NOTICE).

## Related Projects

- [PyRAMSES](https://stepss.sps-lab.org/pyramses/) — Python interface for RAMSES
- [URAMSES](https://github.com/SPS-L/stepss-uramses) — user models for PyRAMSES

## Documentation

- [Installation guide](https://stepss.sps-lab.org/getting-started/installation/) — GUI and Java setup
- [Quick start](https://stepss.sps-lab.org/getting-started/quickstart/) — running simulations from the GUI
- [STEPSS documentation site](https://stepss.sps-lab.org/) — user guide, file formats, model reference
- In-app: **Help → User Guide** (bundled PDF) and **Help → Changelog**

## License

STEPSS is distributed under the **Apache License 2.0** — see [LICENSE](LICENSE). Copyright © Petros Aristidou.

The Apache license covers the Java source code in this repository only. The bundled RAMSES, PFC, and CODEGEN executables are proprietary components under their own terms — see [NOTICE](NOTICE) for details.

## Authors

Developed and maintained by the [Sustainable Power Systems Laboratory (SPS-L)](https://sps-lab.org/) at the Cyprus University of Technology, under the direction of Dr. Petros Aristidou.

STEPSS was created by Dr. Petros Aristidou and Dr. Thierry Van Cutsem.
