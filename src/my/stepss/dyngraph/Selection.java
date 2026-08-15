package my.stepss.dyngraph;

/**
 * One picked observable: index category, replay keyword (for the five typed
 * categories), instance name and optional sub-observable.
 *
 * <p>{@link #label()} reproduces the exact strings DYNGRAPH's console
 * selector writes into {@code desc_obs} - which become the .plt curve
 * titles - so the dialog's Selected list reads the same as the plot the user
 * gets (stepss-dyngraph/src/selec_observ.f90:92,123,157,203,299,316,322,
 * 355,388,432). {@code SOE} and {@code SOT} render as {@code excit control:}
 * and {@code torque control:} rather than their TYPES labels ("observable of
 * excitation controller"), because that is what the console produces. The
 * console concatenates fixed-width Fortran strings, so the real titles carry
 * blank padding between the pieces; this label composes the same words and
 * punctuation from the already-trimmed index strings and does not reproduce
 * that padding.
 */
public final class Selection {

    /** {@link ObservableIndex} category tag: BUS, SHUNT, LOAD, BRANCH, SYNC, INJ, LINK or DCTL. */
    public final String category;
    /** Replay keyword from the category's TYPES table (BM ... SOT); null for INJ/LINK/DCTL, whose keywords {@link ReplayFile} owns. */
    public final String keyword;
    /** The keyword's TYPES label; null for INJ/LINK/DCTL. */
    public final String typeLabel;
    /** Instance name, verbatim - a leading blank is part of it. */
    public final String name;
    /** Sub-observable name; required for SOE/SOT and for INJ/LINK/DCTL, forbidden otherwise. */
    public final String sub;

    public Selection(String category, String keyword, String typeLabel, String name, String sub) {
        if (!ObservableIndex.CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unknown category: " + category);
        }
        if (name == null) {
            throw new IllegalArgumentException("Instance name must not be null");
        }
        boolean typed = ObservableIndex.TYPED_CATEGORIES.contains(category);
        if (typed) {
            if (keyword == null || typeLabel == null) {
                throw new IllegalArgumentException(category
                        + " selections carry a keyword and its label from the TYPES table");
            }
        } else {
            if (keyword != null || typeLabel != null) {
                throw new IllegalArgumentException(category
                        + " selections carry no keyword: the replay keyword is ReplayFile's, not the index's");
            }
        }
        boolean needsSub = requiresSub(category, keyword);
        if (needsSub && sub == null) {
            throw new IllegalArgumentException("A " + (typed ? keyword : category)
                    + " selection needs a sub-observable");
        }
        if (!needsSub && sub != null) {
            throw new IllegalArgumentException("A " + keyword
                    + " selection takes no sub-observable");
        }
        this.category = category;
        this.keyword = keyword;
        this.typeLabel = typeLabel;
        this.name = name;
        this.sub = sub;
    }

    /**
     * Whether a (category, keyword) pair takes a third replay line: true for
     * SOE, SOT and every INJ/LINK/DCTL pick, false for every other keyword.
     */
    public static boolean requiresSub(String category, String keyword) {
        if ("INJ".equals(category) || "LINK".equals(category) || "DCTL".equals(category)) {
            return true;
        }
        return "SOE".equals(keyword) || "SOT".equals(keyword);
    }

    /** The desc_obs-format display label; see the class comment. */
    public String label() {
        if ("BUS".equals(category)) {
            return "bus " + name + ": " + typeLabel;
        }
        if ("SHUNT".equals(category)) {
            return "shunt " + name + ": " + typeLabel;
        }
        if ("LOAD".equals(category)) {
            return "impedance load " + name + ": " + typeLabel;
        }
        if ("BRANCH".equals(category)) {
            return "branch " + name + ": " + typeLabel;
        }
        if ("SYNC".equals(category)) {
            if ("SOE".equals(keyword)) {
                return "sync mach " + name + ": excit control: " + sub;
            }
            if ("SOT".equals(keyword)) {
                return "sync mach " + name + ": torque control: " + sub;
            }
            return "sync mach " + name + ": " + typeLabel;
        }
        if ("INJ".equals(category)) {
            return "injector " + name + ": " + sub;
        }
        if ("LINK".equals(category)) {
            return "link " + name + ": " + sub;
        }
        return "DCTL " + name + ": " + sub;
    }

    /** The Selected JList renders selections through toString. */
    @Override
    public String toString() {
        return label();
    }
}
