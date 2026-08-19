package my.stepss.curves;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The header RAMSES writes at the top of {@code temp_display.cur}.
 *
 * <p>Written by {@code setup_runtime_observables} in
 * stepss-ramses/src/core/ramses.f90 immediately after the file is opened, and
 * flushed there, so a reader can learn the column layout before any data row
 * exists. Every line starts with {@code #}, which is gnuplot's comment
 * character, so the {@code .plt} beside the file still plots it unchanged.
 *
 * <pre>
 * # stepss-cur 1
 * # tstop      240.000
 * # refresh        1.000
 * # ncol 5
 * # obs 1 2 1 BV 1041
 * # obs 2 3 2 o-d g6
 * # obs 3 5 1 MS g6
 * </pre>
 *
 * <p>Fields are separated by one or more spaces and must be split on
 * whitespace, not on a single space: {@code f12.3} and the integer edit
 * descriptors right-justify, so the emitted lines carry runs of padding.
 *
 * <p>This class exists so that the column map is read rather than recomputed.
 * The stride is not uniform, {@code LAT}, {@code o-d} and {@code P-d} taking
 * two columns and everything else one, and reimplementing that arithmetic in
 * Java is exactly the duplication the header was added to remove.
 */
public final class CurHeader {

    /** The only format version this build understands. */
    public static final int VERSION = 1;

    /** Refused rather than guessed: an unreadable header is never a misparse. */
    public static final class Unsupported extends Exception {

        private static final long serialVersionUID = 1L;

        Unsupported(String message) {
            super(message);
        }
    }

    /** One observable's slice of a data row. */
    public static final class Obs {

        /** Its 1-based position in the display list. */
        public final int index;
        /** Its first column, 1-based, where column 1 is always time. */
        public final int firstColumn;
        /** How many columns it occupies: 2 for LAT, o-d and P-d, else 1. */
        public final int columnCount;
        /** The display type as the user typed it, for example BV or o-d. */
        public final String type;
        /** The equipment name. */
        public final String name;
        /** The second name ON and TO carry, or "" for every other type. */
        public final String name2;

        Obs(int index, int firstColumn, int columnCount,
                String type, String name, String name2) {
            this.index = index;
            this.firstColumn = firstColumn;
            this.columnCount = columnCount;
            this.type = type;
            this.name = name;
            this.name2 = name2;
        }
    }

    /** The simulation stop time, which fixes the live x axis. */
    public final double tstop;
    /** How often the writer flushes, in seconds, which sets the poll interval. */
    public final double refresh;
    /** Total columns per data row, including the time column. */
    public final int ncol;
    /** One record per observable, in display order. */
    public final List<Obs> observables;

    private CurHeader(double tstop, double refresh, int ncol, List<Obs> observables) {
        this.tstop = tstop;
        this.refresh = refresh;
        this.ncol = ncol;
        this.observables = Collections.unmodifiableList(observables);
    }

    /**
     * @param lines the whole file, or as much of it as has been read; parsing
     *     stops at the first line that is not a comment
     * @throws Unsupported when there is no header, when its version is not
     *     {@link #VERSION}, or when the records disagree with {@code ncol}
     */
    public static CurHeader parse(List<String> lines) throws Unsupported {
        Double tstop = null;
        Double refresh = null;
        Integer ncol = null;
        List<Obs> observables = new ArrayList<Obs>();
        boolean sawVersion = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith("#")) {
                // The header is contiguous and comes first, so the first data
                // row ends it. Stopping here rather than scanning the whole
                // file is what lets a live reader parse the header out of the
                // first poll's worth of bytes.
                break;
            }
            String[] f = line.substring(1).trim().split("\\s+");
            if (f.length == 0) {
                continue;
            }
            if ("stepss-cur".equals(f[0])) {
                sawVersion = true;
                int version = intField(f, 1, "stepss-cur");
                if (version != VERSION) {
                    throw new Unsupported("This .cur file declares format version "
                            + version + ", and this build of STEPSS reads version "
                            + VERSION + " only. The engine and the interface are"
                            + " from different releases.");
                }
            } else if ("tstop".equals(f[0])) {
                tstop = doubleField(f, 1, "tstop");
            } else if ("refresh".equals(f[0])) {
                refresh = doubleField(f, 1, "refresh");
            } else if ("ncol".equals(f[0])) {
                ncol = intField(f, 1, "ncol");
            } else if ("obs".equals(f[0])) {
                if (f.length < 6) {
                    throw new Unsupported("An obs record in the .cur header has "
                            + (f.length - 1) + " fields where at least 5 are"
                            + " required: " + line);
                }
                observables.add(new Obs(
                        intField(f, 1, "obs index"),
                        intField(f, 2, "obs first column"),
                        intField(f, 3, "obs column count"),
                        f[4],
                        f[5],
                        f.length > 6 ? f[6] : ""));
            }
            // An unrecognised comment is ignored rather than refused, so a
            // later engine may add a record this build has no use for without
            // that being a version bump.
        }

        if (!sawVersion) {
            throw new Unsupported("This engine wrote no run-time header into"
                    + " temp_display.cur, so the columns cannot be identified."
                    + " Run-time curves need a RAMSES release that publishes the"
                    + " column map.");
        }
        if (tstop == null || refresh == null || ncol == null) {
            throw new Unsupported("The .cur header is missing "
                    + (tstop == null ? "tstop " : "")
                    + (refresh == null ? "refresh " : "")
                    + (ncol == null ? "ncol " : "") + "and cannot be used.");
        }

        int covered = 1;
        for (Obs o : observables) {
            if (o.firstColumn != covered + 1) {
                throw new Unsupported("Observable " + o.index + " claims column "
                        + o.firstColumn + " where column " + (covered + 1)
                        + " is next. The .cur header is inconsistent.");
            }
            covered += o.columnCount;
        }
        if (covered != ncol) {
            throw new Unsupported("The .cur header states ncol " + ncol
                    + " but its observable records cover " + covered
                    + " columns. Reading it would draw one observable's data"
                    + " under another's name.");
        }
        return new CurHeader(tstop, refresh, ncol, observables);
    }

    private static int intField(String[] f, int i, String what) throws Unsupported {
        try {
            return Integer.parseInt(field(f, i, what));
        } catch (NumberFormatException notANumber) {
            throw new Unsupported("The .cur header's " + what
                    + " is not an integer: " + f[i]);
        }
    }

    private static double doubleField(String[] f, int i, String what) throws Unsupported {
        try {
            return Double.parseDouble(field(f, i, what));
        } catch (NumberFormatException notANumber) {
            throw new Unsupported("The .cur header's " + what
                    + " is not a number: " + f[i]);
        }
    }

    private static String field(String[] f, int i, String what) throws Unsupported {
        if (i >= f.length) {
            throw new Unsupported("The .cur header's " + what + " record has no value.");
        }
        return f[i];
    }
}
