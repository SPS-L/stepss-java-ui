# Small-signal stability analysis: Kundur two-area system

A ready-to-run case that computes the eigenvalues, damping ratios,
participation factors and mode shapes of the Kundur two-area benchmark, with
and without its power system stabilisers. The engine does the analysis itself;
nothing external is invoked.

## Requires a RAMSES newer than 3.60

The `EIG` disturbance this example uses was added to the engine after the v3.60
release, and `versions.properties` in this repository currently pins
**ramses.version=3.60**. Until that pin moves to a release carrying `EIG`, the
bundled engine accepts the disturbance and writes no results files.

Check which engine you have from the STEPSS window: **Help, About**, or run the
extracted `dynsim` binary with no arguments and read the banner.

The Analysis tab's **Perform small signal stability analysis** button runs the
same thing this example runs from a terminal: it writes the disturbance below,
runs the bundled engine, and copies the three results files into the working
directory you selected. It reports rather than failing silently if the analysis
refused.

## What to do

The case runs the engine directly, so it works from the GUI's working directory
or from a terminal.

### From a terminal

Extract or locate the bundled engine (the STEPSS window writes it into its
working directory as `dynsim`, or `dynsim.exe` on Windows), then:

```sh
# With the stabilisers
dynsim -t cmd.txt

# Without them, in a separate directory so the results do not overwrite
mkdir noPSS && cd noPSS
cp ../lf.dat ../dyn_noPSS.dat ../solveroptions.dat ../obs.dat ../eig.dst ../cmd_noPSS.txt .
dynsim -t cmd_noPSS.txt
```

**Run each variant in its own directory.** Both write results files named from
the basename in `eig.dst`, so a second run in the same directory overwrites the
first. Start each run in a clean directory: a stale `obs.trj` or `.trace` from
an aborted run can make the next run fail in a way that looks unrelated.

### From the STEPSS window

1. Load `lf.dat`, `dyn.dat` and `solveroptions.dat` as system data, and
   `obs.dat` as the observables. You do not need to supply `eig.dst`: the
   button uses its own bundled disturbance.
2. **Select Working Directory** on the Analysis tab, and point it where you want
   the results.
3. Press **Perform small signal stability analysis**.
4. The three results files appear in that directory.

Swap `dyn.dat` for `dyn_noPSS.dat` and repeat to get the unstable variant.

## What to expect

Each run exits **0** and writes three files:

| File | Contents |
|---|---|
| `ssa_modes.dat` | One line per mode: index, real and imaginary parts, damping ratio, frequency in Hz, a dominance flag and a simplicity flag |
| `ssa_pf.dat` | Participation factors, one line per mode and state, with the family, device and variable name inlined |
| `ssa_ms.dat` | Mode shapes: magnitude and angle of each machine's rotor speed |

The headline result, which you can read straight out of `ssa_modes.dat`:

| | inter-area mode | area 1 local | area 2 local |
|---|---|---|---|
| **`dyn_noPSS.dat`** | 0.6246 Hz, zeta = **-0.0233** | 1.085 Hz, zeta = 0.099 | 1.116 Hz, zeta = 0.097 |
| **`dyn.dat`** | 0.6237 Hz, zeta = **+0.1087** | 1.242 Hz, zeta = 0.288 | 1.295 Hz, zeta = 0.287 |

**The sign of the inter-area damping ratio flips with the stabilisers.**
Negative damping means the 0.62 Hz oscillation between the two areas grows
rather than decays, so without the PSS this operating point is small-signal
unstable. This reproduces Kundur, *Power System Stability and Control*,
Example 12.6.

To find the inter-area mode by eye, look in `ssa_modes.dat` for the line whose
frequency column is near 0.62 and whose imaginary part is positive. Its damping
ratio is the fourth column.

`ssa_pf.dat` then tells you which machines take part. The 1.085 Hz mode lists
only G1 and G2, the 1.116 Hz mode only G3 and G4, and the inter-area mode lists
all four. Machines whose participation falls below the reporting threshold are
simply absent rather than listed as zero.

## Reading the simplicity flag

The last column of `ssa_modes.dat` is 1 when the eigenvalue is simple and 0 when
it is degenerate, meaning another mode shares the same eigenvalue.

**Do not read participation factors or mode shapes for a mode flagged 0.**
Identical machine models with identical parameters produce identical poles, and
20 of this system's 70 modes are degenerate without the PSS. In a degenerate
eigenspace the individual eigenvectors are not unique, so those rows are
basis-dependent: real numbers that mean nothing physically and that would come
out differently on another machine. All the electromechanical modes in the table
above are simple, so they are safe to interpret.

## If nothing is produced

Two settings are required, and `solveroptions.dat` already has both. If you
adapt this to your own system and the results files do not appear, check them
first:

- **`$OMEGA_REF SYN`.** Under the default centre-of-inertia reference frame the
  analysis refuses, because reducing under COI would silently produce a
  plausible but wrong spectrum.
- **`$SCHEME DE`.** The integrated scheme is not supported.

Both refusals exit with code **78** and state the reason in the log, so a run
that produced nothing and exited 0 is a different problem, most likely an old
engine (see the version note above).

Systems above `$EIG_MAX_STATES` states (default 5000) are also refused, with a
message naming the limit. That is a deliberate ceiling: the reduced state matrix
is solved densely, which stops being practical at that size.

## Files

| File | Purpose |
|---|---|
| `lf.dat` | Power flow: buses, lines, transformers, operating point |
| `dyn.dat` | Dynamic data with the PSS enabled (`KSTAB = 20.0`) |
| `dyn_noPSS.dat` | Identical except `KSTAB = 0.0` on all four exciters |
| `solveroptions.dat` | Solver settings, including the two required above |
| `obs.dat` | Observables selection |
| `eig.dst` | Triggers the analysis at t = 0.001 s and stops at 0.010 s |
| `cmd.txt` | Case file for the PSS variant |
| `cmd_noPSS.txt` | Case file for the no-PSS variant |

The two variants differ in exactly one parameter, the PSS gain `KSTAB` on all
four exciters, so any difference in the results is attributable to the
stabilisers alone.

The trailing blank lines in the `cmd.txt` files are the empty run-time
observable list. They are required: without them the engine reads past the end
of the file and stops.

## Attribution

The Kundur data files are copied from
[SPS-L/stepss-test-systems](https://github.com/SPS-L/stepss-test-systems)
(Apache-2.0, `LICENSE` included here). The RAMSES implementation of the system
data is by Dr. Thierry Van Cutsem, University of Liege, 2024. Please cite the
original source of the system data:

> P. Kundur, *Power System Stability and Control*, McGraw-Hill, 1994
> (two-area system, Example 12.6).
