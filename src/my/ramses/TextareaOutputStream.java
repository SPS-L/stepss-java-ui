/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package my.ramses;

import java.io.IOException;
import java.io.OutputStream;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Streams a child process' output into a {@link JTextArea}, a line at a time.
 *
 * <p><b>One instance per stream.</b> Commons Exec's {@code PumpStreamHandler}
 * runs a separate thread for the child's stdout and stderr, so handing the
 * same instance to both puts two threads on one line buffer. Characters from
 * the two streams then interleave mid-word - real helios output came out as
 * {@code "=h e l i-o3s.:5 s t a t Qu=s:  C-O0N.V7E R G E D"}, two copies of
 * one line woven together - and the non-atomic capture-and-reset in the old
 * flush() also dropped and duplicated whole lines. Give each stream its own
 * instance; they can share the {@code JTextArea} safely, because every append
 * is marshalled onto the event dispatch thread, which serialises them.
 *
 * <p>Only complete lines are handed to the text area. A partial line stays
 * buffered until its newline arrives, so a line from one stream is never
 * split around a line from the other. Methods are synchronized as well: that
 * alone cannot prevent two streams sharing a buffer from interleaving - a
 * partial line from one would still be followed by the other's text - but it
 * keeps a single instance safe for a caller that writes to it from more than
 * one thread.
 */
class TextareaOutputStream extends OutputStream {

    private final JTextArea area;
    private final StringBuilder buf = new StringBuilder(128);

    /** First thread seen writing here; see {@link #noteWriter()}. */
    private Thread writer;
    private boolean sharingReported;

    public TextareaOutputStream(final JTextArea area) throws IOException {
        this.area = area;
    }

    @Override
    public synchronized void write(int c) throws IOException {
        noteWriter();
        buf.append((char) (c & 0xff));
        if (c == '\n') {
            emitCompleteLines();
        }
    }

    /**
     * Reports, once, that two threads are writing to this instance.
     *
     * <p>Synchronizing keeps the buffer's own state consistent, but it cannot
     * make a shared line buffer produce whole lines: one stream's partial line
     * sits in the buffer while the other appends to it, and the two come out
     * concatenated. The only fix is an instance per stream, so a second writing
     * thread is a defect in the caller, not a condition to tolerate quietly.
     *
     * <p>Reported rather than thrown: this runs on a Commons Exec pump thread,
     * whose {@code StreamPumper} catches and discards exceptions, so throwing
     * would lose the child's output and say nothing. Only writes are checked -
     * {@code close()} legitimately arrives on the executor thread.
     */
    private void noteWriter() {
        Thread current = Thread.currentThread();
        if (writer == null) {
            writer = current;
            return;
        }
        if (writer != current && !sharingReported) {
            sharingReported = true;
            System.err.println("TextareaOutputStream: written by two threads ("
                    + writer.getName() + " and " + current.getName()
                    + "). Give each stream its own instance - sharing one"
                    + " interleaves their characters mid-line.");
        }
    }

    /** @return true once two different threads have written here. */
    synchronized boolean sawSharing() {
        return sharingReported;
    }

    /**
     * Overridden so a whole pump chunk is appended under one lock.
     * {@link OutputStream}'s default implementation loops over
     * {@link #write(int)}, which would let another thread's chunk land between
     * two characters of this one.
     */
    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        noteWriter();
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
    public synchronized void close() {
        emitAll();
    }

    @Override
    public synchronized void flush() {
        emitCompleteLines();
    }

    /**
     * Hands over everything up to and including the last newline, keeping any
     * trailing partial line buffered. Capture and reset happen together under
     * this object's lock, so nothing appended concurrently can be lost between
     * them - the defect behind the truncated lines.
     */
    private void emitCompleteLines() {
        int cut = buf.lastIndexOf("\n");
        if (cut < 0) {
            return;
        }
        String str = buf.substring(0, cut + 1);
        buf.delete(0, cut + 1);
        appendOnEdt(str);
    }

    /** Flushes whatever is left, complete line or not. Used when the stream closes. */
    private void emitAll() {
        if (buf.length() == 0) {
            return;
        }
        String str = buf.toString();
        buf.setLength(0);
        appendOnEdt(str);
    }

    private void appendOnEdt(final String str) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                area.append(str);
            }
        });
    }

    public synchronized void message(String msg) {
        if (buf.length() > 0 && buf.charAt(buf.length() - 1) != '\n') {
            buf.append('\n');
        }
        buf.append(msg);
        buf.append('\n');
        emitCompleteLines();
    }
}
