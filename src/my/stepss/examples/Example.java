package my.stepss.examples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * One entry from {@code examples.properties}: a bundled test system, what the
 * dialog says about it, and the scenario file that sets it up.
 *
 * <p>An entry names a {@code .cfg} rather than a slot per file. Which file
 * belongs in which slot is the scenario file's job, and the example repositories
 * now ship one written in the current format, with paths relative to its own
 * folder. Restating the slots here would be a second answer to a question the
 * shipped file already answers, and the two would drift the first time an
 * example changed which load flow it opens on.
 *
 * <p>Immutable, and carries no path. An {@code Example} describes what an
 * example <em>is</em>; where a copy of it happens to live on disk belongs to
 * {@link ExampleInstaller} and to the caller that chose the directory.
 */
public final class Example {

    /** Prefix every example's packed payload resource shares. */
    static final String PAYLOAD_PREFIX = "payload/example-";

    private final String id;
    private final String name;
    private final String scale;
    private final String summary;
    private final String docs;
    private final String dir;
    private final String cfg;
    private final List<String> extra;

    Example(String id, String name, String scale, String summary, String docs,
            String dir, String cfg, List<String> extra) {
        this.id = id;
        this.name = name;
        this.scale = scale;
        this.summary = summary;
        this.docs = docs;
        this.dir = dir;
        this.cfg = cfg;
        this.extra = Collections.unmodifiableList(new ArrayList<>(extra));
    }

    /** The key this entry is declared under, and what its pin is keyed by. */
    public String id() {
        return id;
    }

    /** What the list shows, e.g. "IEEE Nordic". */
    public String name() {
        return name;
    }

    /** The one-line size, e.g. "74 buses, 20 machines, 400/220/130 kV". */
    public String scale() {
        return scale;
    }

    /** The paragraph the detail pane shows. */
    public String summary() {
        return summary;
    }

    /** Where Documentation goes: the example's own repository. */
    public String docs() {
        return docs;
    }

    /** The subdirectory name a copy is extracted into. */
    public String dir() {
        return dir;
    }

    /**
     * The scenario file this example opens, relative to its directory.
     *
     * <p>Loaded through {@code ScenarioFile} exactly as File &gt; Load
     * configuration loads any other, which is what makes the two paths one
     * path: an example that opens wrong is a scenario file that is wrong, and
     * it is wrong in the repository where it can be fixed.
     */
    public String cfg() {
        return cfg;
    }

    /** Every other file that ships: the case data, variants, README, LICENCE. */
    public List<String> extra() {
        return extra;
    }

    /** The jar resource holding this example's packed files. */
    public String payloadResource() {
        return PAYLOAD_PREFIX + id + ".zip";
    }

    /**
     * Every file this entry names, deduplicated, in declaration order.
     *
     * <p>{@code ExamplesPack} fails the build when an upstream release stops
     * carrying one of them, and a file no entry knows about cannot be shipped,
     * so the payload can never quietly grow a stale copy of something.
     *
     * <p>The scenario file is one of them, and it is also read: the packer
     * parses it and refuses a payload whose {@code .cfg} names a file this list
     * does not. That is what keeps the two halves honest without making
     * {@code .extra} derived from a file the descriptor cannot see at run time.
     *
     * <p>Deduplicated because nothing stops {@code .extra} naming the scenario
     * file too, and a manifest with a repeated path would digest differently
     * from the same content declared once.
     */
    public List<String> retained() {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        all.add(cfg);
        all.addAll(extra);
        return Collections.unmodifiableList(new ArrayList<>(all));
    }

    @Override
    public String toString() {
        return name;
    }
}
