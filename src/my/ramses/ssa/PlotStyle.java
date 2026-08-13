package my.ramses.ssa;

/**
 * Single source of truth for plot styling. Both backends read from this so
 * they cannot drift. Each entry defines a class name, hex colour, stroke
 * width, and optional font size in pixels (null for non-text classes).
 */
final class PlotStyle {

    static final class Entry {
        final String cls;
        final String hex;
        final float width;
        final Integer fontPx;

        Entry(String cls, String hex, float width, Integer fontPx) {
            this.cls = cls;
            this.hex = hex;
            this.width = width;
            this.fontPx = fontPx;
        }
    }

    static final Entry[] ENTRIES = {
        new Entry("axis", "#333333", 1.0f, null),
        new Entry("grid", "#cccccc", 0.5f, null),
        new Entry("bound", "#dc143c", 1.5f, null),
        new Entry("ray", "#999999", 1.0f, null),
        new Entry("pole", "#1f77b4", 1.5f, null),
        new Entry("unstable", "#dc143c", 2.0f, null),
        new Entry("shape", "#1f77b4", 2.0f, null),
        new Entry("label", "#333333", 0.0f, 11),
        new Entry("title", "#333333", 0.0f, 13),
    };

    /**
     * The entry for cls, or the axis default when unknown.
     */
    static Entry of(String cls) {
        for (Entry e : ENTRIES) {
            if (e.cls.equals(cls)) {
                return e;
            }
        }
        return ENTRIES[0];  // default to axis
    }

    private PlotStyle() {
    }
}
