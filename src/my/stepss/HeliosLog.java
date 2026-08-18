package my.stepss;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The banner and the stdout filter the Power Flow Simulation console uses to
 * show helios' progress without its result tables.
 *
 * <p>helios has no way to export a table without displaying it. Every display
 * command in the {@code D} sub-menu captures its table into
 * {@code last_display_text_} and then writes that same text to stdout
 * unconditionally (../stepss-helios/src/terminal/PlainMenu.cpp, the
 * {@code out_ << last_display_text_} at the end of each of the V/G/S/F/T/O
 * blocks); the following {@code X} writes the capture to the named file. Its
 * quiet mode - which the {@code -t} command file mode java-ui uses always
 * enables - suppresses the interactive *prompts*, not the tables, and main-menu
 * {@code O} output redirection is still a stub (PlainMenu.cpp, cmd_change_output).
 * So a run that exports six tables prints all six to the console, and the six
 * buttons that show those same tables on demand had nothing left to reveal.
 *
 * <p>The split needed is almost exactly the split helios already makes between
 * its two streams. Everything on <b>stderr</b> is progress and is kept as it
 * is: {@code [helios] Loaded <file>: N records, ...},
 * {@code helios: skipping unknown record type '...'},
 * {@code helios: status: CONVERGED (1 iterations)} and
 * {@code helios: PFCcmd.txt: N commands executed successfully}. <b>stdout</b>
 * carries the tables plus a handful of progress lines, and
 * {@link #isProgressLine} is the list of those - derived from every
 * {@code out_ <<} in PlainMenu.cpp that is neither guarded by {@code !quiet_}
 * nor {@code last_display_text_}, plus the solver status lines in
 * ../stepss-helios/src/main.cpp.
 *
 * <p>That list is a soft contract on helios' wording: a line a future helios
 * adds is one this filter would drop. The caller is what keeps that from
 * mattering - {@code StepssUI.runPFActionPerformed} tees the unfiltered stdout
 * into a buffer and dumps the whole of it into the console when helios exits
 * non-zero, so nothing can be hidden on the runs where an unrecognised line
 * would be the thing worth reading.
 */
final class HeliosLog {

    /**
     * helios' startup banner, copied verbatim from {@code print_banner()} in
     * ../stepss-helios/src/main.cpp.
     *
     * <p>It lives here rather than coming from helios because helios does not
     * print it in {@code -t} command file mode: {@code print_banner()} is
     * called only on the interactive path, so the console has always opened on
     * whatever the first loaded data file had to say. Keep the art in step by
     * hand if helios ever changes it; nothing checks that it matches, because
     * java-ui never runs the interactive path that would show the original.
     */
    static final String BANNER
            = "\n"
            + "                  Welcome to\n"
            + "\n"
            + "     H   H  EEEEE  L      III   OOO    SSS\n"
            + "     H   H  E      L       I   O   O  S\n"
            + "     HHHHH  EEEE   L       I   O   O   SSS\n"
            + "     H   H  E      L       I   O   O      S\n"
            + "     H   H  EEEEE  LLLLL  III   OOO   SSSS\n"
            + "\n"
            + "     STEPSS-Helios Power Flow Calculator\n";

    /**
     * Every stdout line helios writes that is not part of a result table.
     * Matched as a prefix of the trimmed line, because helios indents most of
     * them by one space and Windows builds end them with a carriage return.
     *
     * <p>Sources, all in ../stepss-helios: {@code Converged in},
     * {@code Maximum number of iterations reached} and
     * {@code Singular Jacobian matrix} from {@code src/main.cpp};
     * {@code Diverged after}, {@code Reset OK}, {@code Not in this menu} and
     * the rest from {@code src/terminal/PlainMenu.cpp}. Only the first four
     * and {@code Exported to:}/{@code VoltRat file written to:} can appear in
     * a run java-ui starts, whose command file is fixed; the others are here
     * so a hand-edited or future command file does not go quiet.
     */
    private static final String[] PROGRESS_PREFIXES = {
        "Converged in ",
        "Diverged after ",
        "Maximum number of iterations reached",
        "Singular Jacobian matrix",
        "Exported to: ",
        "VoltRat file written to: ",
        "Dump written to: ",
        "MATLAB file written to: ",
        "Nothing to export.",
        "Cannot open file",
        "Not in this menu",
        "Reset OK",
        "Output redirection not yet implemented",
        "dQg/dQl sensitivities not yet implemented"
    };

    private HeliosLog() {
    }

    /**
     * @param line one line of helios' stdout, without its line terminator
     * @return true if the line reports progress and belongs in the console,
     * false if it is table data (or blank padding between tables) and belongs
     * only in the .res file the following {@code X} command writes
     */
    static boolean isProgressLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (String prefix : PROGRESS_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Passes helios' stdout through {@link #isProgressLine} on its way to a
     * console sink.
     *
     * <p>Buffers to whole lines for the same reason
     * {@link TextareaOutputStream} does: Commons Exec hands over whatever a
     * read happened to return, so a chunk boundary falls mid-line often, and a
     * filter that judged chunks would let half a table row through whenever
     * one straddled a boundary.
     *
     * <p>{@link #close()} deliberately does not close the sink. The console
     * sinks are constructed once and reused by every run, so closing one would
     * take the pane out of service for the rest of the session; Commons Exec
     * does not close output pumps anyway ({@code StreamPumper} is built with
     * {@code closeWhenExhausted} false for stdout and stderr), so this only
     * matters to a caller that closes the filter itself.
     */
    static final class Filter extends OutputStream {

        private final OutputStream sink;
        private final StringBuilder buf = new StringBuilder(128);

        Filter(OutputStream sink) {
            this.sink = sink;
        }

        @Override
        public synchronized void write(int c) throws IOException {
            buf.append((char) (c & 0xff));
            if (c == '\n') {
                emitCompleteLines();
            }
        }

        /**
         * Overridden so a whole pump chunk is examined under one lock, matching
         * {@link TextareaOutputStream#write(byte[], int, int)}. The default
         * implementation loops over {@link #write(int)}, which would let
         * another thread's chunk land between two characters of this one.
         */
        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            boolean sawNewline = false;
            for (int i = 0; i < len; i++) {
                char c = (char) (b[off + i] & 0xff);
                buf.append(c);
                if (c == '\n') {
                    sawNewline = true;
                }
            }
            if (sawNewline) {
                emitCompleteLines();
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            emitCompleteLines();
            sink.flush();
        }

        /** Judges the trailing partial line too, then flushes. Never closes the sink. */
        @Override
        public synchronized void close() throws IOException {
            emitCompleteLines();
            if (buf.length() > 0) {
                String last = buf.toString();
                buf.setLength(0);
                if (isProgressLine(last)) {
                    emit(last + "\n");
                }
            }
            sink.flush();
        }

        /**
         * Hands the sink the progress lines out of everything up to and
         * including the last newline, keeping any trailing partial line
         * buffered until its newline arrives.
         */
        private void emitCompleteLines() throws IOException {
            int cut = buf.lastIndexOf("\n");
            if (cut < 0) {
                return;
            }
            String block = buf.substring(0, cut + 1);
            buf.delete(0, cut + 1);
            StringBuilder kept = new StringBuilder(block.length());
            int start = 0;
            while (start < block.length()) {
                int nl = block.indexOf('\n', start);
                String line = block.substring(start, nl);
                if (isProgressLine(line)) {
                    kept.append(line).append('\n');
                }
                start = nl + 1;
            }
            if (kept.length() > 0) {
                emit(kept.toString());
            }
        }

        /**
         * ISO-8859-1 because {@link TextareaOutputStream} turns each byte into
         * the char of the same value; encoding back the same way round-trips
         * helios' bytes unchanged rather than re-interpreting them as UTF-8.
         */
        private void emit(String text) throws IOException {
            sink.write(text.getBytes(StandardCharsets.ISO_8859_1));
        }
    }
}
