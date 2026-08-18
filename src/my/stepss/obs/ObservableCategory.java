package my.stepss.obs;

/**
 * One row of the observable picker: a category of equipment, the controls that
 * choose members of it, and the keyword the observables file names it by.
 *
 * <p>The categories are RAMSES', not this application's. {@code add_observ}
 * (stepss-ramses/src/io/observ.f90:171-333) accepts eight keywords, and every
 * one of them is wired end to end in the engine: allocated in
 * {@code observ_init}, named into the trajectory header by {@code observ_fin},
 * and sampled every step by its own section of {@code write_observ}. The
 * picker offered five of them until this class existed.
 */
public final class ObservableCategory {

    /**
     * The eight observable categories, in the order {@code observ_fin} writes
     * them into the trajectory and {@code dyngraph --list} emits them.
     *
     * <p>Three are spelled differently in the two places a user meets them,
     * which is why {@link #tooltip()} names both: the observables file says
     * {@code IMPLOAD}, {@code INJEC} and {@code TWOP} where DYNGRAPH's listing
     * says {@code LOAD}, {@code INJ} and {@code LINK}.
     *
     * <p>{@code IMPLOAD} and {@code LOAD} are the same block, not two kinds of
     * load: RAMSES' LOAD module holds only a conductance and a susceptance per
     * entry, so a load there is an impedance load by construction. Dynamic
     * loads are modelled as injectors and are observed under {@code INJEC}.
     */
    public enum Kind {
        BUS("BUS", "BUS", "Buses", ""),
        SHUNT("SHUNT", "SHUNT", "Shunts", ""),
        IMPLOAD("IMPLOAD", "LOAD", "Impedance loads",
                "Names beginning M_ are synthesised from the power mismatch"
                + " at that bus, not declared in the data."),
        BRANCH("BRANCH", "BRANCH", "Branches", ""),
        SYNC("SYNC", "SYNC", "Synchronous machines", ""),
        INJEC("INJEC", "INJ", "Injectors",
                "Dynamic loads are injectors, so they belong here rather than"
                + " under impedance loads."),
        TWOP("TWOP", "LINK", "Two-port injectors", ""),
        DCTL("DCTL", "DCTL", "Discrete controllers", "");

        private final String keyword;
        private final String tag;
        private final String label;
        private final String note;

        Kind(String keyword, String tag, String label, String note) {
            this.keyword = keyword;
            this.tag = tag;
            this.label = label;
            this.note = note;
        }

        /** The keyword the observables file uses, as add_observ reads it. */
        public String keyword() {
            return keyword;
        }

        /** What {@code dyngraph --list} calls this category. */
        public String tag() {
            return tag;
        }

        /** What the row says on screen. */
        public String label() {
            return label;
        }

        /** Both spellings, so a user who read one can find the other. */
        public String tooltip() {
            StringBuilder text = new StringBuilder("<html>Writes <b>")
                    .append(keyword)
                    .append(" &lt;name&gt;</b> lines into the observables file.");
            if (!keyword.equals(tag)) {
                text.append("<br>DYNGRAPH lists these under <b>")
                        .append(tag).append("</b>.");
            }
            if (!note.isEmpty()) {
                text.append("<br>").append(note);
            }
            return text.append("</html>").toString();
        }
    }
}
