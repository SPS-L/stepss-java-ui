package my.stepss.ssa;

import java.awt.Color;

/**
 * Single source of truth for plot styling. Both backends read from this so
 * they cannot drift. Each entry defines a class name, its colour on a light
 * ground and on a dark one, stroke width, and optional font size in pixels
 * (null for non-text classes).
 *
 * <p>The two hex values sit on the same row rather than in two parallel
 * arrays: a second array is a second thing to keep in the same order and the
 * same length, and this file exists because that kind of copy does not stay
 * in step.
 *
 * <p>Which palette a caller wants is not the same question for the two
 * backends, and they deliberately answer it differently. {@link SwingSink}
 * draws on screen and follows the theme, so two bright rectangles no longer
 * sit in an otherwise dark window. {@link SvgSink} always takes the light one:
 * a saved figure goes into a report or a paper, where it is printed or set on
 * a white page, and it should not come out inverted because of what the
 * application happened to be wearing when the button was pressed.
 */
final class PlotStyle {

    static final class Entry {
        final String cls;
        final String lightHex;
        final String darkHex;
        final float width;
        final Integer fontPx;

        Entry(String cls, String lightHex, String darkHex, float width, Integer fontPx) {
            this.cls = cls;
            this.lightHex = lightHex;
            this.darkHex = darkHex;
            this.width = width;
            this.fontPx = fontPx;
        }

        /** This class's colour on the ground in use. */
        String hex(boolean dark) {
            return dark ? darkHex : lightHex;
        }
    }

    /**
     * The dark column is a transposition of the light one rather than a fresh
     * set of colours, so the two themes say the same things.
     *
     * <p>Crimson stays crimson and blue stays blue, lightened only as far as a
     * dark ground needs, because both carry meaning: crimson is the stability
     * boundary and the unstable marker, and it is the same crimson the modes
     * table flags an unstable row in, which is why the dark value here is the
     * one that table already uses. The grid and the damping rays are furniture
     * rather than data, and their dark values are picked to sit at the same
     * contrast against a dark ground that the light ones have against white:
     * 1.6:1 and 2.9:1. Raising them to the 3:1 that data-bearing ink wants
     * would make the guides louder in dark than in light, which is the same
     * kind of untruth as leaving them invisible.
     */
    static final Entry[] ENTRIES = {
        new Entry("axis", "#333333", "#d0d4d6", 1.0f, null),
        new Entry("grid", "#cccccc", "#64686a", 0.5f, null),
        new Entry("bound", "#dc143c", "#ff6b83", 1.5f, null),
        new Entry("ray", "#999999", "#8d9193", 1.0f, null),
        new Entry("pole", "#1f77b4", "#5fa8dc", 1.5f, null),
        new Entry("unstable", "#dc143c", "#ff6b83", 2.0f, null),
        new Entry("shape", "#1f77b4", "#5fa8dc", 2.0f, null),
        new Entry("label", "#333333", "#d0d4d6", 0.0f, 11),
        new Entry("title", "#333333", "#d0d4d6", 0.0f, 13),
    };

    /** The ground a saved figure is drawn on, whatever the application wears. */
    static final String EXPORT_BACKGROUND = "#ffffff";

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

    /** A "#rrggbb" from the table above as a Color. */
    static Color color(String hex) {
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }

    /**
     * Whether ink on this background wants the dark column. Measured off the
     * colour rather than asked of the look and feel, so it stays right under
     * the system fallback too, which is neither of the two FlatLaf themes and
     * can be either brightness.
     */
    static boolean isDark(Color background) {
        if (background == null) {
            return false;
        }
        double luminance = (0.299 * background.getRed()
                + 0.587 * background.getGreen()
                + 0.114 * background.getBlue()) / 255.0;
        return luminance < 0.5;
    }

    private PlotStyle() {
    }
}
