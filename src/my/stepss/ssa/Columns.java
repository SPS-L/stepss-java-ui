package my.stepss.ssa;

import java.io.IOException;

/**
 * Fixed-offset field extraction for the three results files ssa.f90 writes.
 *
 * <p>Splitting these lines on whitespace is wrong and fails silently. The
 * a8 and a20 name fields are written as stored, so a LEADING blank is part
 * of the name while trailing blanks are padding, and a device name may
 * contain an embedded blank. PickerHarness documents the same trap for the
 * dyngraph index. Every field is therefore taken by offset.
 *
 * <p>Ends are clamped to the line length, because an all-blank trailing a20
 * is routinely stripped by editors and by CRLF normalisation.
 */
final class Columns {

    private Columns() {
    }

    /** The field at [from,to), trailing blanks removed, leading blanks kept. */
    static String slice(String line, int from, int to) {
        if (from >= line.length()) {
            return "";
        }
        String raw = line.substring(from, Math.min(to, line.length()));
        int end = raw.length();
        while (end > 0 && raw.charAt(end - 1) == ' ') {
            end--;
        }
        return raw.substring(0, end);
    }

    static double num(String line, int from, int to, int lineNo) throws IOException {
        String text = slice(line, from, to).trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new IOException("line " + lineNo + ": cannot read a number from <"
                    + text + "> at columns " + (from + 1) + "-" + to);
        }
    }

    static int integer(String line, int from, int to, int lineNo) throws IOException {
        String text = slice(line, from, to).trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IOException("line " + lineNo + ": cannot read an integer from <"
                    + text + "> at columns " + (from + 1) + "-" + to);
        }
    }
}
