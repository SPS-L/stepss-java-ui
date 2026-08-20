package my.stepss.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The disturbance file a dynamic simulation needs, checked before the engine
 * is launched.
 *
 * <p>A run has no horizon of its own: it ends at the {@code STOP} record of
 * its {@code .dst}. The engine enforces that, but only from inside
 * {@code get_disturb}, after the settings, the network and every model have
 * been read, and it reports it into the trace file rather than to the window.
 * Checking here costs one pass over a small text file and turns a late abort
 * into a sentence naming the file.
 *
 * <p>The rules are the engine's, transcribed from {@code src/io/disturb.f90},
 * and deliberately no stricter: a guard that refused a file the engine accepts
 * would be worse than no guard at all.
 *
 * <ul>
 * <li>A line that is blank once trimmed, or opens with {@code #} or {@code !},
 *     is a comment.</li>
 * <li>Every other line is a record: the text up to its first blank is the
 *     time, and the remainder is the description.</li>
 * <li>Reading ends at the first record whose description contains
 *     {@code STOP}, as a substring and case sensitively, which is
 *     {@code index(desc_dist(nbdist),'STOP') /= 0} in the engine. Records
 *     after it are never read.</li>
 * </ul>
 */
public final class DisturbanceFile {

    /** The keyword that ends a run. */
    private static final String STOP = "STOP";

    private DisturbanceFile() {
    }

    /**
     * Whether this file can end a run.
     *
     * @param file the disturbance file named on the System Data tab
     * @return null when the file carries a timed {@code STOP}, otherwise a
     *     sentence naming the file and what it is missing, ready to hand to
     *     the banner
     */
    public static String problem(File file) {
        if (file == null || !file.isFile()) {
            String name = file == null ? "" : file.getName();
            return "The disturbance file " + name + " does not exist.";
        }
        int records = 0;
        boolean untimedStop = false;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty() || text.charAt(0) == '#' || text.charAt(0) == '!') {
                    continue;
                }
                int blank = text.indexOf(' ');
                if (blank < 0) {
                    // The engine refuses a record with fewer than two fields.
                    // Noting a bare STOP separately is what stops this
                    // reporting "no STOP record" with STOP on the screen.
                    untimedStop = untimedStop || text.contains(STOP);
                    continue;
                }
                records++;
                if (text.substring(blank + 1).contains(STOP)) {
                    return null;
                }
            }
        } catch (IOException ex) {
            return "The disturbance file " + file.getName()
                    + " could not be read: " + ex.getMessage();
        }
        if (untimedStop) {
            return "The disturbance file " + file.getName() + " has a STOP without"
                    + " a time.\n\nEvery record starts with its time, as in"
                    + " \"240.0 STOP\".";
        }
        if (records == 0) {
            return "The disturbance file " + file.getName()
                    + " has no disturbance records.";
        }
        return "The disturbance file " + file.getName() + " has no STOP record."
                + "\n\nA dynamic simulation ends at its STOP time, so add a line"
                + " such as \"240.0 STOP\".";
    }
}
