package my.stepss.examples;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Reads {@code examples.properties} into {@link Example} entries.
 *
 * <p>The one parser, used by the dialog at run time and by {@code ExamplesPack}
 * at build time. That is deliberate: the packer decides what goes into the
 * payload and the dialog decides what to look for inside it, so a second parser
 * would be two answers to the same question and the disagreement would only
 * show up as a menu entry opening onto missing files.
 *
 * <p>Order is the declared order of the {@code examples} key, not
 * {@link Properties}' own, which is a hash order and would shuffle the dialog
 * between runs.
 */
public final class ExampleCatalog {

    /** Where the descriptor lives in the jar. */
    public static final String RESOURCE = "/my/stepss/examples/examples.properties";

    /** The key listing the entries, in the order the dialog shows them. */
    private static final String INDEX = "examples";

    private ExampleCatalog() {
    }

    /** Every bundled example, in declared order, read from the jar. */
    public static List<Example> load() throws IOException {
        InputStream in = ExampleCatalog.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IOException("Missing bundled resource '" + RESOURCE
                    + "'. The examples descriptor is not in the jar.");
        }
        try {
            return load(in);
        } finally {
            in.close();
        }
    }

    /**
     * Every example in {@code in}, in declared order.
     *
     * <p>Separated from {@link #load()} so the build-time packer can read the
     * descriptor as a file, before any jar exists to read it out of.
     */
    public static List<Example> load(InputStream in) throws IOException {
        Properties props = new Properties();
        props.load(in);

        List<Example> examples = new ArrayList<>();
        for (String id : split(required(props, INDEX, INDEX))) {
            examples.add(new Example(
                    id,
                    required(props, id + ".name", id),
                    required(props, id + ".scale", id),
                    required(props, id + ".summary", id),
                    required(props, id + ".docs", id),
                    required(props, id + ".dir", id),
                    split(required(props, id + ".data", id)),
                    required(props, id + ".dist", id),
                    required(props, id + ".obs", id),
                    split(props.getProperty(id + ".extra", ""))));
        }
        if (examples.isEmpty()) {
            throw new IOException("The examples descriptor lists no examples.");
        }
        return Collections.unmodifiableList(examples);
    }

    /** The one entry with this id. */
    public static Example byId(List<Example> examples, String id) throws IOException {
        for (Example example : examples) {
            if (example.id().equals(id)) {
                return example;
            }
        }
        throw new IOException("No example with id '" + id
                + "' in the descriptor. It lists: " + examples);
    }

    /**
     * A required value, or an {@link IOException} naming the key.
     *
     * <p>Every key here is required rather than defaulted, because there is no
     * sensible default for any of them: an entry with no {@code .dist} would
     * install and leave a slot empty, and an entry with no {@code .dir} would
     * extract over the examples root itself.
     */
    private static String required(Properties props, String key, String owner)
            throws IOException {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("The examples descriptor is missing '" + key
                    + "', which '" + owner + "' needs.");
        }
        return value.trim();
    }

    /** A comma-separated list, trimmed, with empty items dropped. */
    private static List<String> split(String value) {
        List<String> items = new ArrayList<>();
        for (String item : Arrays.asList(value.split(","))) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }
}
