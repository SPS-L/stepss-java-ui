package my.ramses.dyngraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns selections into the console keyword stream DYNGRAPH replays under
 * {@code -t}. Pure text; the grammar is pinned upstream by
 * stepss-dyngraph/tests/smoke.cmd and tests/nested.cmd:
 *
 * <pre>
 * &lt;trajectory path&gt;      line 1
 * BM                     keyword
 * BUS1                   instance name
 * SOE                    redirects to a machine's exciter sub-list
 * GEN1
 * VF                     sub-observable, from that machine's EXC block
 *                        blank line: stop selecting
 * S                      stop
 * </pre>
 *
 * <p>Keyword then name, with a third line - the sub-observable - when the
 * keyword is SOE, SOT, I, T or D, and for no other keyword. Keywords are
 * matched case-sensitively in upper case; only the trailing stop accepts
 * {@code s} as well.
 */
public final class ReplayFile {

    /**
     * Both sides of the DYNGRAPH exchange - the {@code --list} read and the
     * {@code sel.cmd} write - use ISO-8859-1, explicitly. DYNGRAPH has no
     * encoding concept: names are raw bytes copied out of the trajectory
     * file and compared byte-for-byte on the way back in. ISO-8859-1 maps
     * bytes 0-255 onto the first 256 code points and back, so the round
     * trip is exact for any byte the trajectory can contain; UTF-8 would
     * turn a byte &gt;= 0x80 into a replacement character and write
     * different bytes back. The failure would be silent - an unmatched name
     * makes the console selector re-prompt, not abort, so DYNGRAPH can exit
     * 0 having plotted the wrong curves. A non-ASCII name renders as
     * mojibake in the dialog; a wrong glyph the user can see beats a wrong
     * plot they cannot.
     */
    public static final Charset CHARSET = Charset.forName("ISO-8859-1");

    /**
     * Replay keywords for the three categories without TYPES blocks. The
     * index does not carry them, so they are hardcoded here and nowhere
     * else - accepted coupling: they are as fixed as the trajectory layout
     * itself. Sources: stepss-dyngraph/src/selec_observ.f90:329 (I,
     * injector), :360 (T, link/twoport), :393 (D, DCTL); demonstrated by
     * stepss-dyngraph/tests/nested.cmd. Keyword space and name space are
     * disjoint: a DCTL observable named ST is a name line, never the sync
     * keyword ST, because position in the stream decides.
     */
    private static final Map<String, String> REPLAY_KEYWORDS = replayKeywords();

    private static Map<String, String> replayKeywords() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("INJ", "I");
        m.put("LINK", "T");
        m.put("DCTL", "D");
        return Collections.unmodifiableMap(m);
    }

    private ReplayFile() {
    }

    /**
     * The full replay stream: trajectory path, one keyword/name(/sub) group
     * per selection in list order - list order is column order in the .cur
     * and curve order in the .plt - then the blank stop-selecting line and
     * the {@code S} stop line.
     */
    public static String emit(String trajectoryPath, List<Selection> selections) {
        if (selections == null || selections.isEmpty()) {
            // The dialog disables Plot while nothing is selected; an empty
            // stream here is a caller bug, not a user state.
            throw new IllegalArgumentException("No selections to emit");
        }
        StringBuilder out = new StringBuilder();
        out.append(trajectoryPath).append('\n');
        for (Selection s : selections) {
            String keyword = s.keyword != null ? s.keyword : REPLAY_KEYWORDS.get(s.category);
            out.append(keyword).append('\n');
            out.append(s.name).append('\n');
            if (s.sub != null) {
                out.append(s.sub).append('\n');
            }
        }
        out.append('\n');   // empty answer at the keyword prompt: stop selecting
        out.append("S\n");  // stop instead of extracting another set
        return out.toString();
    }

    /** Writes {@link #emit} to {@code selCmd} in {@link #CHARSET}. */
    public static void write(File selCmd, String trajectoryPath, List<Selection> selections)
            throws IOException {
        OutputStream out = new FileOutputStream(selCmd);
        try {
            out.write(emit(trajectoryPath, selections).getBytes(CHARSET));
        } finally {
            out.close();
        }
    }
}
