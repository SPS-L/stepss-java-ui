package my.stepss.dyngraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The parsed model of {@code dyngraph --list} output: categories, type
 * tables, instance names and per-instance sub-lists. No Swing, no I/O, no
 * process calls, so it can be pinned headlessly by {@link PickerHarness}.
 *
 * <p>The format is documented in stepss-dyngraph's README and pinned by its
 * {@code tests/golden/list.txt}: line-oriented and count-prefixed, every
 * block a {@code <TAG> <count>} header followed by exactly that many lines.
 * The parser therefore never scans for a delimiter - an instance
 * legitimately named {@code END} or {@code S} must not terminate anything.
 *
 * <p>Names are taken verbatim. DYNGRAPH emits them Fortran-{@code trim()}-ed,
 * which strips trailing blanks only; a leading blank is part of the name, and
 * the console selector's comparison ({@code busname(k)==string18}) pads the
 * shorter operand with trailing blanks - so a trailing blank is insignificant
 * but a leading one is not. Left-trimming here would break the round trip
 * back through {@code -t}.
 */
public final class ObservableIndex {

    /** The one index format version this parser understands. */
    public static final int FORMAT_VERSION = 1;

    private static final String HEADER_PREFIX = "DYNGRAPH-INDEX ";

    /** Index tags of the eight instance categories, in emission order. */
    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "BUS", "SHUNT", "LOAD", "BRANCH", "SYNC", "INJ", "LINK", "DCTL"));

    /** The five categories whose types are fixed by the trajectory layout and carried in TYPES blocks. */
    public static final List<String> TYPED_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "BUS", "SHUNT", "LOAD", "BRANCH", "SYNC"));

    /** One {@code <keyword> <label>} pair from a TYPES block. */
    public static final class TypeEntry {
        /** The replay keyword, echoed back verbatim in the command file (e.g. {@code BM}). */
        public final String keyword;
        /** The human-readable label the dropdown shows (e.g. {@code voltage magnitude (pu)}). */
        public final String label;

        TypeEntry(String keyword, String label) {
            this.keyword = keyword;
            this.label = label;
        }
    }

    /**
     * One instance, with its own sub-lists. Sub-lists are keyed by instance,
     * never pooled by category: injector, link, DCTL, exciter and
     * torque-controller observables are defined by the model attached to
     * each instance, so two injectors in the same network routinely expose
     * different observables. An empty sub-list is legitimate - a machine
     * with no excitation controller has {@code EXC 0}.
     */
    public static final class Instance {
        /** The name, verbatim: a leading blank is part of it. May be empty (an all-blank name). */
        public final String name;
        /** Exciter observables (SYNC only; empty for every other category). */
        public final List<String> exc;
        /** Torque-controller observables (SYNC only). */
        public final List<String> tor;
        /** Instance observables (INJ, LINK and DCTL only). */
        public final List<String> obs;

        Instance(String name, List<String> exc, List<String> tor, List<String> obs) {
            this.name = name;
            this.exc = Collections.unmodifiableList(exc);
            this.tor = Collections.unmodifiableList(tor);
            this.obs = Collections.unmodifiableList(obs);
        }
    }

    private final Map<String, List<TypeEntry>> types;
    private final Map<String, List<Instance>> instances;

    private ObservableIndex(Map<String, List<TypeEntry>> types,
                            Map<String, List<Instance>> instances) {
        this.types = types;
        this.instances = instances;
    }

    /** The TYPES table of a category, in emission order; empty for INJ/LINK/DCTL. */
    public List<TypeEntry> types(String category) {
        List<TypeEntry> found = types.get(category);
        return found == null ? Collections.<TypeEntry>emptyList() : found;
    }

    /** The instances of a category, in emission order; empty when absent. */
    public List<Instance> instances(String category) {
        List<Instance> found = instances.get(category);
        return found == null ? Collections.<Instance>emptyList() : found;
    }

    /** True when every category carries zero instances - nothing to pick. */
    public boolean isEmpty() {
        for (String category : CATEGORIES) {
            if (!instances(category).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses {@code --list} output.
     *
     * @throws IOException on any malformed input, with the offending
     *         (1-based) line number in the message. Refuses any version
     *         other than {@link #FORMAT_VERSION}: a raised version marks a
     *         deliberately incompatible format, so parsing on would be a
     *         bug.
     */
    public static ObservableIndex parse(String text) throws IOException {
        Cursor cursor = new Cursor(text);
        String header = cursor.next("the DYNGRAPH-INDEX header");
        if (!header.startsWith(HEADER_PREFIX)) {
            throw new IOException("line 1: not a DYNGRAPH-INDEX header. The first line was: \""
                    + header + "\"");
        }
        int version;
        try {
            version = Integer.parseInt(header.substring(HEADER_PREFIX.length()).trim());
        } catch (NumberFormatException ex) {
            throw new IOException("line 1: unreadable DYNGRAPH-INDEX version in \"" + header + "\"");
        }
        if (version != FORMAT_VERSION) {
            throw new IOException("line 1: DYNGRAPH-INDEX version " + version
                    + "; this STEPSS understands version " + FORMAT_VERSION
                    + " only. A raised version marks a deliberately incompatible format,"
                    + " so refusing beats guessing.");
        }

        Map<String, List<TypeEntry>> types = new HashMap<String, List<TypeEntry>>();
        Map<String, List<Instance>> instances = new HashMap<String, List<Instance>>();
        while (true) {
            String line = cursor.next("a block header or END");
            if (line.equals("END")) {
                break;
            }
            if (line.isEmpty() && !cursor.hasNext()) {
                throw cursor.error("missing END: the stream ended without one");
            }
            if (line.startsWith("TYPES ")) {
                parseTypesBlock(cursor, line, types);
            } else {
                parseInstanceBlock(cursor, line, instances);
            }
        }
        while (cursor.hasNext()) {
            String trailing = cursor.next("nothing (the stream ended at END)");
            if (!trailing.isEmpty()) {
                throw cursor.error("content after END: \"" + trailing + "\"");
            }
        }
        return new ObservableIndex(types, instances);
    }

    private static void parseTypesBlock(Cursor cursor, String header,
            Map<String, List<TypeEntry>> types) throws IOException {
        String rest = header.substring("TYPES ".length());
        int space = rest.indexOf(' ');
        if (space <= 0) {
            throw cursor.error("malformed TYPES header \"" + header
                    + "\": expected \"TYPES <CATEGORY> <count>\"");
        }
        String category = rest.substring(0, space);
        if (!TYPED_CATEGORIES.contains(category)) {
            throw cursor.error("TYPES block for unknown category '" + category + "'");
        }
        int count = cursor.count(rest.substring(space + 1), header);
        List<TypeEntry> table = types.get(category);
        if (table == null) {
            table = new ArrayList<TypeEntry>();
            types.put(category, table);
        }
        for (int i = 0; i < count; i++) {
            String entry = cursor.next("entry " + (i + 1) + " of " + count
                    + " in the TYPES " + category + " block");
            int keywordEnd = entry.indexOf(' ');
            if (keywordEnd <= 0) {
                throw cursor.error("malformed TYPES entry \"" + entry
                        + "\": expected \"<keyword> <label>\"");
            }
            table.add(new TypeEntry(entry.substring(0, keywordEnd),
                    entry.substring(keywordEnd + 1)));
        }
    }

    private static void parseInstanceBlock(Cursor cursor, String header,
            Map<String, List<Instance>> instances) throws IOException {
        int space = header.indexOf(' ');
        if (space <= 0) {
            throw cursor.error("unexpected tag \"" + header + "\"");
        }
        String category = header.substring(0, space);
        if (!CATEGORIES.contains(category)) {
            throw cursor.error("unexpected tag '" + category + "'");
        }
        int count = cursor.count(header.substring(space + 1), header);
        List<Instance> list = instances.get(category);
        if (list == null) {
            list = new ArrayList<Instance>();
            instances.put(category, list);
        }
        List<String> none = Collections.emptyList();
        for (int i = 0; i < count; i++) {
            // Verbatim, never trimmed; and read strictly by count, so a name
            // of "END", "S" or "" is just a name.
            String name = cursor.next("name " + (i + 1) + " of " + count
                    + " in the " + category + " block");
            if (category.equals("SYNC")) {
                List<String> exc = subList(cursor, "EXC", name);
                List<String> tor = subList(cursor, "TOR", name);
                list.add(new Instance(name, exc, tor, none));
            } else if (category.equals("INJ") || category.equals("LINK")
                    || category.equals("DCTL")) {
                list.add(new Instance(name, none, none, subList(cursor, "OBS", name)));
            } else {
                list.add(new Instance(name, none, none, none));
            }
        }
    }

    private static List<String> subList(Cursor cursor, String tag, String owner)
            throws IOException {
        String header = cursor.next(tag + " header for '" + owner + "'");
        if (!header.startsWith(tag + " ")) {
            throw cursor.error("expected '" + tag + " <count>' after '" + owner
                    + "', got \"" + header + "\"");
        }
        int count = cursor.count(header.substring(tag.length() + 1), header);
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            names.add(cursor.next("name " + (i + 1) + " of " + count + " in the "
                    + tag + " block of '" + owner + "'"));
        }
        return names;
    }

    /** Line supply with 1-based numbering for error messages. */
    private static final class Cursor {
        private final String[] lines;
        private int at; // index of the next unread line

        Cursor(String text) {
            String[] raw = text.split("\n", -1);
            // The child's stdout on Windows arrives CRLF-terminated. The CR
            // is the line ending, not a name byte; everything else is kept
            // exactly as read.
            for (int i = 0; i < raw.length; i++) {
                if (raw[i].endsWith("\r")) {
                    raw[i] = raw[i].substring(0, raw[i].length() - 1);
                }
            }
            this.lines = raw;
        }

        boolean hasNext() {
            return at < lines.length;
        }

        String next(String what) throws IOException {
            if (at >= lines.length) {
                throw new IOException("line " + (at + 1)
                        + ": unexpected end of input while reading " + what);
            }
            return lines[at++];
        }

        /** An error at the line most recently returned by {@link #next}. */
        IOException error(String message) {
            return new IOException("line " + at + ": " + message);
        }

        int count(String token, String header) throws IOException {
            int n;
            try {
                n = Integer.parseInt(token.trim());
            } catch (NumberFormatException ex) {
                throw error("unreadable count in \"" + header + "\"");
            }
            if (n < 0) {
                throw error("negative count in \"" + header + "\"");
            }
            return n;
        }
    }
}
