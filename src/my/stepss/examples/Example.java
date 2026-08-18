package my.stepss.examples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * One entry from {@code examples.properties}: a bundled test system, what the
 * dialog says about it, and which of its files fills which slot.
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
    private final List<String> data;
    private final String dist;
    private final String obs;
    private final String diagram;
    private final List<String> extra;

    Example(String id, String name, String scale, String summary, String docs,
            String dir, List<String> data, String dist, String obs, String diagram,
            List<String> extra) {
        this.id = id;
        this.name = name;
        this.scale = scale;
        this.summary = summary;
        this.docs = docs;
        this.dir = dir;
        this.data = Collections.unmodifiableList(new ArrayList<>(data));
        this.dist = dist;
        this.obs = obs;
        this.diagram = diagram;
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

    /** Filenames for the system-data slots, in the order they should be filled. */
    public List<String> data() {
        return data;
    }

    /** The filename for the disturbance slot. */
    public String dist() {
        return dist;
    }

    /** The filename for the observables slot. */
    public String obs() {
        return obs;
    }

    /**
     * The filename of the annotated one-line diagram template, or "".
     *
     * <p>Optional because it exists only for cases whose repository ships one.
     * When present it fills the diagram slot on the System Data tab, and Run
     * Power Flow renders it through Helios' {@code 1} command.
     */
    public String diagram() {
        return diagram;
    }

    /** Files that ship but fill no slot: variants, the README, the LICENCE. */
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
     * <p>Derived rather than declared, which is the point of the descriptor's
     * shape. Two properties follow from computing it here instead of reading a
     * separate {@code retain} key:
     *
     * <ul>
     * <li>{@code ExamplesPack} fails the build when an upstream release stops
     * carrying a file an entry names, because the retain list and the list of
     * files the interface will look for are the same list.
     * <li>A file no entry knows about cannot be shipped, so the payload can
     * never quietly grow a stale copy of something.
     * </ul>
     *
     * <p>Deduplicated because nothing stops an entry naming the same file in
     * two roles, and a manifest with a repeated path would digest differently
     * from the same content declared once. Empty slots are skipped the same
     * way: {@code dist}, {@code obs} and {@code diagram} are optional, and an
     * unfilled one is not a filename to look up or dedupe against.
     */
    public List<String> retained() {
        LinkedHashSet<String> all = new LinkedHashSet<>(data);
        addIfNamed(all, dist);
        addIfNamed(all, obs);
        addIfNamed(all, diagram);
        all.addAll(extra);
        return Collections.unmodifiableList(new ArrayList<>(all));
    }

    /**
     * Adds a slot's filename unless the slot is empty.
     *
     * <p>An unconditional add is what this replaces, and with optional slots it
     * would put "" into the set. {@code ExamplesPack} looks up every retained
     * name in the upstream archive, so that becomes a build failure naming no
     * file, and the manifest digest would cover a path that is not a path.
     */
    private static void addIfNamed(LinkedHashSet<String> all, String name) {
        if (!name.isEmpty()) {
            all.add(name);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
