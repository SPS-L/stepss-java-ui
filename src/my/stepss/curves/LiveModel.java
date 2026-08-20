package my.stepss.curves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The run-time observables of one simulation, as they arrive.
 *
 * <p>Owns growable buffers, one per observable, fed by whoever is tailing
 * {@code temp_display.cur}. Every buffer is private and every read produces an
 * immutable {@link CurveData} snapshot, because the thread that appends is not
 * the thread that paints: the {@link my.stepss.plot.PlotSink} backends are EDT
 * only, so what crosses over must be finished.
 *
 * <p>Copying the whole history per snapshot rather than sharing the buffers is
 * deliberate. {@code tsample} defaults to 10 ms and the interface offers three
 * observable rows, so a four-minute run holds about 24,000 samples across at
 * most three panels: about a megabyte, copied once per flush. Handing over a
 * volatile array reference and a captured length would avoid that at the cost
 * of a copy-on-grow race no headless check can demonstrate.
 *
 * <p>Two threads touch an instance, and the split is not the usual one. The
 * poller owns everything mutable: {@link #accept}, {@link #snapshot},
 * {@link #samples} and {@link #skippedRows} are its alone, and none of them is
 * synchronised. The EDT calls {@link #panelCount} and {@link #axesOf} while
 * building the panels, which is safe only because both read state fixed by the
 * constructor and never written again. Adding a panel after construction, or
 * making the axes mutable, breaks that silently: there is no lock here to
 * catch it.
 */
public final class LiveModel {

    /** How the columns of one row become one panel's series. */
    private static final class Panel {

        private final CurHeader.Obs obs;
        private final CurveAxes axes;
        private final boolean phasePlane;
        private final boolean weighted;
        private double[] x = new double[256];
        private double[] y = new double[256];
        private double[] w;
        private int n;

        Panel(CurHeader.Obs obs, double tstop) {
            this.obs = obs;
            this.axes = axesFor(obs, tstop);
            String type = obs.type.toUpperCase(Locale.ROOT);
            // The type says what the second column MEANS; columnCount says
            // whether it is actually there. A header is self-consistent as
            // long as firstColumn/columnCount line up with ncol, so a
            // one-column "obs ... LAT eq1" or "obs ... o-d g6" is a header
            // CurHeader accepts, and append() would read row[first + 1] past
            // the end of the row without this guard. Degrading to plotting
            // the first column is safe; reading past the row is not.
            this.phasePlane = ("O-D".equals(type) || "P-D".equals(type))
                    && obs.columnCount >= 2;
            this.weighted = "LAT".equals(type) && obs.columnCount >= 2;
            if (weighted) {
                this.w = new double[256];
            }
        }

        void append(double[] row) {
            if (n == x.length) {
                x = Arrays.copyOf(x, n * 2);
                y = Arrays.copyOf(y, n * 2);
                if (w != null) {
                    w = Arrays.copyOf(w, n * 2);
                }
            }
            int first = obs.firstColumn - 1;
            // A phase plane plots the pair against each other: gnuplot.f90:147
            // uses varcol+1 for x and varcol for y, and the engine writes
            // omega then delta, so x is the second column of the pair.
            x[n] = phasePlane ? row[first + 1] : row[0];
            y[n] = row[first];
            if (w != null) {
                w[n] = row[first + 1];
            }
            n++;
        }

        CurveData snapshot() {
            double[] xs = Arrays.copyOf(x, n);
            double[] ys = Arrays.copyOf(y, n);
            double[] ws = w == null ? null : Arrays.copyOf(w, n);
            String label = axes.title.isEmpty() ? obs.type : axes.title;
            return new CurveData(java.util.Collections.singletonList(
                    new CurveSeries(label, axes.yLabel, xs, ys, ws)), null, 0);
        }
    }

    private final CurHeader header;
    private final List<Panel> panels = new ArrayList<Panel>();
    private int skipped;
    private int samples;

    public LiveModel(CurHeader header) {
        this.header = header;
        for (CurHeader.Obs obs : header.observables) {
            panels.add(new Panel(obs, header.tstop));
        }
    }

    public int panelCount() {
        return panels.size();
    }

    /** The header's declared flush cadence, in seconds. */
    public double refresh() {
        return header.refresh;
    }

    public CurveAxes axesOf(int panel) {
        return panels.get(panel).axes;
    }

    public CurveData snapshot(int panel) {
        return panels.get(panel).snapshot();
    }

    /** How many rows could not be read, over the whole run. */
    public int skippedRows() {
        return skipped;
    }

    /** How many rows have been drawn, over the whole run. */
    public int samples() {
        return samples;
    }

    /**
     * Appends whatever of these lines is data.
     *
     * <p>A comment is header, already parsed. A row whose field count
     * disagrees with {@code ncol} is the torn last line the writer leaves when
     * it flushes mid-row, or damage; either way it is counted and dropped
     * rather than guessed at. A non-finite field is dropped for the reason
     * {@link CurReader} documents: it propagates through the axis arithmetic
     * and blanks the frame.
     */
    public void accept(List<String> lines) {
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\s+");
            if (fields.length != header.ncol) {
                skipped++;
                continue;
            }
            double[] row = new double[header.ncol];
            boolean ok = true;
            for (int i = 0; i < header.ncol; i++) {
                try {
                    row[i] = Double.parseDouble(fields[i]);
                } catch (NumberFormatException notANumber) {
                    ok = false;
                    break;
                }
                if (!Double.isFinite(row[i])) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                skipped++;
                continue;
            }
            for (Panel p : panels) {
                p.append(row);
            }
            samples++;
        }
    }

    /**
     * The frame for one observable, from its display type.
     *
     * <p>Titles, axis captions and the identity overlay are transcribed from
     * the gnuplot script the engine used to write, stepss-ramses
     * src/io/gnuplot.f90:84-133, so a user's figures do not change meaning
     * along with their renderer. Three deliberate departures:
     *
     * <ul>
     * <li>The {@code LAT} title drops its observation window, which lives in
     * the settings file rather than in the header.</li>
     * <li>The {@code TO} x caption drops its run of padding spaces, which was
     * a hand-made centring hack for a layout this does not have.</li>
     * <li>{@code o-d} and {@code P-d} gain a title. gnuplot.f90 sets none for
     * either, and because it never unsets one either, a phase-plane panel
     * inherited whatever title the panel above it had set. That is a bug in
     * the script rather than an intended blank, so it is not reproduced.</li>
     * </ul>
     */
    public static CurveAxes axesFor(CurHeader.Obs obs, double tstop) {
        String type = obs.type.toUpperCase(Locale.ROOT);
        switch (type) {
            case "RT":
                return new CurveAxes("Simulated VS Real time",
                        "simulation time (s)", "elapsed time (s)",
                        0.0, tstop, false, true);
            case "SOL":
                return new CurveAxes("Solutions VS time", "t (s)",
                        "nb. of inj. solutions", 0.0, tstop, false, false);
            case "LAT":
                return new CurveAxes("Equipment: " + obs.name, "t (s)",
                        "S (MVA)", 0.0, tstop, false, false);
            case "O-D":
                return new CurveAxes("Machine " + obs.name, "delta (pu)",
                        "omega (pu)", Double.NaN, Double.NaN, false, false);
            case "P-D":
                return new CurveAxes("Machine " + obs.name, "delta (pu)",
                        "P (MW)", Double.NaN, Double.NaN, false, false);
            case "BV":
                return new CurveAxes("BUS " + obs.name, "t (s)", "V (pu)",
                        0.0, tstop, false, false);
            case "BPO":
            case "BPE":
                return new CurveAxes("BRANCH " + obs.name, "t (s)", "P (MW)",
                        0.0, tstop, false, false);
            case "BQO":
            case "BQE":
                return new CurveAxes("BRANCH " + obs.name, "t (s)", "Q (MVAr)",
                        0.0, tstop, false, false);
            case "MS":
            case "COI":
                return new CurveAxes("Machine " + obs.name, "t (s)",
                        "Omega (pu)", 0.0, tstop, false, false);
            case "ON":
                return new CurveAxes("Injector " + obs.name + " Observable "
                        + obs.name2, "t (s)", "", 0.0, tstop, false, false);
            case "TO":
                return new CurveAxes("TWOP " + obs.name + " Observable "
                        + obs.name2, "t (s)", obs.name2, 0.0, tstop, false, false);
            default:
                // gnuplot.f90's own else branch: an untitled frame over time,
                // which is better than refusing to draw a type the engine
                // accepted.
                return new CurveAxes("", "t (s)", "", 0.0, tstop, false, false);
        }
    }
}
