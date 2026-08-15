package my.stepss.compile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inserts CODEGEN-generated models into the uramses router files, at the
 * marker comments those files carry.
 *
 * <p>Pure text in, pure text out: no file or process access, so it can be
 * exercised headlessly without a compiler or a kit on disk.
 *
 * <p>Every router names its procedure pointer {@code <kind>_ptr}, so one rule
 * covers all five. The routers differ in ways this class never has to know
 * about - {@code external ::} versus bare {@code external}, {@code modelname4}
 * versus {@code modelname5} - because the markers, not the surrounding syntax,
 * locate both insertion points.
 */
public final class RouterSplicer {

    public static final String EXTERNALS_MARKER = "!<<STEPSS-GUI:EXTERNALS>>";
    public static final String CASES_MARKER = "!<<STEPSS-GUI:CASES>>";

    public static final List<String> KINDS = Collections.unmodifiableList(
            Arrays.asList("exc", "inj", "tor", "twop", "dctl"));

    private RouterSplicer() {
    }

    /**
     * The model kind, taken as the leading segment up to the first underscore.
     *
     * <p>The pre-P1 implementation used {@code substring(0, 3)}, which
     * silently ignored dctl models and throws on any name shorter than three
     * characters. An unrecognised kind is an error here, not a silent skip:
     * a model the user asked for that never reaches the router would compile
     * clean and then fail at run time with an unresolved model name.
     */
    public static String kindOf(String fileName) {
        String base = stripExtension(fileName);
        int underscore = base.indexOf('_');
        if (underscore <= 0) {
            throw new IllegalArgumentException("Model file '" + fileName
                    + "' has no <kind>_<name> prefix. Expected one of " + KINDS + ".");
        }
        String kind = base.substring(0, underscore);
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("Model file '" + fileName
                    + "' has unknown kind '" + kind + "'. Expected one of " + KINDS + ".");
        }
        return kind;
    }

    /** {@code exc_ENTSOE_lim.f90} -> {@code exc_ENTSOE_lim}. */
    public static String modelNameOf(String fileName) {
        return stripExtension(fileName);
    }

    /** Kit-relative path of the router owning a kind. */
    public static String routerFor(String kind) {
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("Unknown model kind: " + kind);
        }
        return "src/usr_" + kind + "_models.f90";
    }

    /**
     * Returns {@code source} with an {@code external} declaration and a
     * select-case entry added for each model, skipping any model that is
     * already registered.
     *
     * <p>The kit ships some models pre-registered directly in the router -
     * for example {@code exc_ENTSOE_lim}, which arrives with its
     * {@code external} declaration and {@code case} entry already live,
     * outside the spliced region. A user who runs that shipped example
     * through Codegen would otherwise get a second, duplicate case label
     * spliced in, and upstream's Makefile fails the build on exactly that.
     * "Already registered" is detected the same way regardless of whether
     * the entry was there from the start or was left by a previous splice
     * (see {@link #isAlreadyRegistered}) - splice() cannot tell those two
     * origins apart from the text alone, and does not need to: either way,
     * the existing entry already dispatches to the model correctly, and
     * staging overwrites its {@code .f90} with the regenerated source.
     *
     * @throws IOException if either marker is missing, which means the kit
     *         predates the marker contract.
     */
    public static String splice(String source, String kind, List<String> models)
            throws IOException {
        requireMarker(source, EXTERNALS_MARKER, kind);
        requireMarker(source, CASES_MARKER, kind);

        StringBuilder externals = new StringBuilder();
        StringBuilder cases = new StringBuilder();
        for (String model : models) {
            if (isAlreadyRegistered(source, kind, model)) {
                continue;
            }
            externals.append("   external :: ").append(model).append('\n');
            cases.append("      case('").append(model).append("')\n");
            cases.append("         ").append(kind).append("_ptr=>").append(model).append("\n\n");
        }

        String out = insertBefore(source, EXTERNALS_MARKER, externals.toString());
        out = insertBefore(out, CASES_MARKER, cases.toString());
        return out;
    }

    /**
     * True when {@code source} already carries a live (non-comment)
     * {@code <kind>_ptr=>model} pointer assignment - the router's own way
     * of dispatching to a model, whether that line arrived with the kit or
     * from an earlier splice. A line whose first non-blank character is
     * {@code !} is a comment and does not count, so a marker line or an
     * explanatory comment mentioning a model name can never be mistaken for
     * a live registration.
     */
    private static boolean isAlreadyRegistered(String source, String kind, String model) {
        Pattern pointer = Pattern.compile(
                Pattern.quote(kind) + "_ptr\\s*=>\\s*" + Pattern.quote(model) + "\\b");
        for (String rawLine : source.split("\n", -1)) {
            String line = rawLine.trim();
            if (line.startsWith("!")) {
                continue;
            }
            Matcher m = pointer.matcher(line);
            if (m.find()) {
                return true;
            }
        }
        return false;
    }

    private static void requireMarker(String source, String marker, String kind)
            throws IOException {
        if (source.indexOf(marker) < 0) {
            throw new IOException("Router for kind '" + kind + "' does not carry the "
                    + marker + " marker. The bundled uramses kit predates the marker "
                    + "contract this build requires.");
        }
    }

    /** Inserts {@code text} on its own lines immediately before the marker's line. */
    private static String insertBefore(String source, String marker, String text) {
        int at = source.indexOf(marker);
        int lineStart = source.lastIndexOf('\n', at);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        return source.substring(0, lineStart) + text + source.substring(lineStart);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
