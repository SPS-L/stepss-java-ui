package my.stepss.curves;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the data file DYNGRAPH writes beside its .plt.
 *
 * <p>Format, from stepss-dyngraph/src/extract.f90:63-67: no header; one row
 * per time step written as {@code 20000(E15.7E3,1x)} followed by {@code ' ;'};
 * column 1 is time and column i+1 is selection i, because selection order IS
 * column order (stepss-java-ui ReplayFile:76-79, and caption.f90:35-40
 * generates the .plt from the same ordering).
 *
 * <p>The .plt sitting beside it is never read. It is a gnuplot program rather
 * than a data format, it embeds arbitrary user-authored gnuplot, and the
 * caller already knows every label; see the design spec's invariant.
 */
public final class CurReader {

    private CurReader() {
    }

    /**
     * @param cur the file DYNGRAPH wrote
     * @param labels one label per selection, in selection order
     */
    public static CurveData read(File cur, List<String> labels) throws IOException {
        List<String> lines = Files.readAllLines(cur.toPath(), StandardCharsets.ISO_8859_1);
        return parse(lines, cur, labels);
    }

    /**
     * The parse, split out so the harness can drive it from string literals.
     *
     * <p>ISO-8859-1 above rather than UTF-8 for the same reason ReplayFile
     * uses it: these are Fortran-written bytes, and a byte outside UTF-8's
     * valid sequences would otherwise be replaced rather than read.
     */
    public static CurveData parse(List<String> lines, File source, List<String> labels) {
        int columns = labels.size() + 1;
        List<double[]> rows = new ArrayList<double[]>();
        int skipped = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            // A comment, not a row. DYNGRAPH writes none, but RAMSES writes
            // seven at the top of its own .cur and both files live in the same
            // working directory. Counting them as unreadable rows reported
            // damage over undamaged data.
            if (line.startsWith("#")) {
                continue;
            }
            // The row terminator, not a field. Written unconditionally by
            // extract.f90, including on the degenerate time-only file.
            if (line.endsWith(";")) {
                line = line.substring(0, line.length() - 1).trim();
            }
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split("\\s+");
            if (fields.length != columns) {
                skipped++;
                continue;
            }
            double[] row = new double[columns];
            boolean ok = true;
            for (int i = 0; i < columns; i++) {
                try {
                    row[i] = Double.parseDouble(fields[i]);
                } catch (NumberFormatException notANumber) {
                    ok = false;
                    break;
                }
                // gfortran writes NaN, Infinity, +Infinity or -Infinity into
                // an E15.7E3 field for a non-finite value, and
                // Double.parseDouble accepts every one of them, so this is a
                // row that parses without being data.
                //
                // Keeping it is not a cosmetic problem. CurvePanel.bounds
                // propagates the value through Math.min/Math.max, which
                // return NaN for a NaN argument; the tLo > tHi fallback
                // cannot rescue it because every comparison against NaN is
                // false; and the AXES themselves then come out as y1="NaN",
                // so the user gets a blank frame with a legend over a header
                // stating that nothing was skipped. Dropping the row makes it
                // one of the "N unreadable row(s) skipped" CurveWindow.header
                // already reports, and matches gnuplot, which treats a
                // non-finite sample as a gap and still draws the axes and the
                // good part of the trace.
                if (!Double.isFinite(row[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                rows.add(row);
            } else {
                skipped++;
            }
        }

        List<CurveSeries> series = new ArrayList<CurveSeries>();
        for (int s = 0; s < labels.size(); s++) {
            double[] t = new double[rows.size()];
            double[] v = new double[rows.size()];
            for (int r = 0; r < rows.size(); r++) {
                t[r] = rows.get(r)[0];
                v[r] = rows.get(r)[s + 1];
            }
            String label = labels.get(s);
            series.add(new CurveSeries(label, CurveSeries.unitOf(label), t, v));
        }
        return new CurveData(series, source, skipped);
    }
}
