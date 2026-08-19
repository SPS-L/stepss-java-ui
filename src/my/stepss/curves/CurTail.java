package my.stepss.curves;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the lines appended to a file since the last call.
 *
 * <p>A live viewer polls once per flush for the whole length of a run, so
 * re-reading the file each time is quadratic in the number of rows, and a long
 * real-time run is exactly where that bites. This holds a byte offset instead
 * and consumes only up to the last newline, keeping any trailing partial line
 * for the next poll: the writer flushes mid-row, so a torn final line is the
 * normal state of the file rather than an error.
 *
 * <p>Not thread safe, and deliberately so: one poller thread owns an instance
 * for the life of a run.
 *
 * <p>ISO-8859-1 rather than UTF-8, matching {@link CurReader}: these are
 * Fortran-written bytes and a byte outside UTF-8's valid sequences would
 * otherwise be replaced rather than read.
 */
public final class CurTail {

    private final File file;
    private long offset;
    private StringBuilder partial = new StringBuilder();
    private boolean truncated;

    public CurTail(File file) {
        this.file = file;
    }

    /** Whether the writer has created the file yet. */
    public boolean exists() {
        return file.isFile();
    }

    /** How many bytes have been consumed. */
    public long offset() {
        return offset;
    }

    /**
     * Whether the most recent {@link #poll()} found the file shorter than the
     * offset and started again from the top.
     *
     * <p>A re-run opens the file with {@code status='replace'}, which
     * truncates it. A reader that kept its offset across that would skip the
     * new run's header and resume mid-row, so this is how it learns to throw
     * its parsed state away. Cleared by the following poll.
     */
    public boolean truncatedSinceLastPoll() {
        return truncated;
    }

    /**
     * @return the complete lines appended since the last call, without their
     *     line terminators, oldest first; empty when the file does not exist
     *     yet or nothing whole has been added
     */
    public List<String> poll() throws IOException {
        truncated = false;
        if (!file.isFile()) {
            return Collections.emptyList();
        }
        long length = file.length();
        if (length < offset) {
            // Shorter than what has been consumed: the file was replaced.
            offset = 0L;
            partial.setLength(0);
            truncated = true;
            length = file.length();
        }
        if (length == offset) {
            return Collections.emptyList();
        }
        byte[] buffer = new byte[(int) Math.min(length - offset, 1 << 20)];
        int read;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            read = raf.read(buffer);
        }
        if (read <= 0) {
            return Collections.emptyList();
        }
        offset += read;
        partial.append(new String(buffer, 0, read, StandardCharsets.ISO_8859_1));

        List<String> lines = new ArrayList<String>();
        int from = 0;
        for (int i = 0; i < partial.length(); i++) {
            if (partial.charAt(i) == '\n') {
                String line = partial.substring(from, i);
                // A CRLF writer leaves the carriage return on the line.
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lines.add(line);
                from = i + 1;
            }
        }
        partial = new StringBuilder(partial.substring(from));
        return lines;
    }
}
