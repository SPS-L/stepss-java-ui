package my.stepss;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import my.stepss.platform.PlatformLauncher;

/**
 * Every documentation address the interface links to, and the ? button that
 * opens them.
 *
 * <p>One place, because a deep link is a contract with stepss-docs rather than
 * a string: a renamed heading turns an anchor into a silent landing at the top
 * of the page, and a moved page into a 404. Gathered here, the whole contract
 * is one file to re-point after a documentation reorganisation, and
 * {@link DocsCheck} can walk it and ask the site whether each address still
 * resolves. Scattered through {@code StepssUI}, neither would be true.
 *
 * <p>{@code RELEASES_URL} in {@link StepssUI} deliberately stays where it is.
 * It is a GitHub address rather than a documentation one, it is read by the
 * update check as well as opened by a menu item, and it is not part of this
 * contract.
 */
public final class Docs {

    /** The documentation site. Help &gt; User guide opens this and no more. */
    public static final String SITE = "https://stepss.sps-lab.org/";

    /**
     * One page, or one section of one, as a ? button offers it.
     *
     * <p>The title is what the popup menu shows, so it is the documentation's
     * own name for the page rather than a description of why the button is
     * where it is. A reader who has been sent to "Solver Settings" can find
     * "Solver Settings" again in the sidebar.
     */
    public static final class Page {

        private final String path;
        private final String title;

        private Page(String path, String title) {
            this.path = path;
            this.title = title;
        }

        /** The address, absolute, ready for {@link PlatformLauncher#openUrl}. */
        public String url() {
            return SITE + path;
        }

        /** The site-relative path, which is what {@link DocsCheck} fetches. */
        public String path() {
            return path;
        }

        /** The documentation's own name for this page. */
        public String title() {
            return title;
        }

        @Override
        public String toString() {
            return title + " (" + url() + ")";
        }
    }

    private static Page page(String path, String title) {
        return new Page(path, title);
    }

    // The pages, named after what they document rather than after the button
    // that opens them, so a page reached from two places stays one constant.

    /** The tour of the six tabs, one section each. */
    public static final Page TAB_SYSTEM_DATA =
            page("gui/interface/#system-data", "The System Data tab");
    public static final Page TAB_OBSERVABLES_RUNTIME =
            page("gui/interface/#runtime-observables", "Runtime observables");
    public static final Page TAB_OBSERVABLES_RECORDING =
            page("gui/interface/#recording-to-file", "Recording to file");
    public static final Page TAB_POWER_FLOW =
            page("gui/interface/#power-flow-simulation", "The Power Flow Simulation tab");
    public static final Page TAB_DYNAMIC_SIMULATION =
            page("gui/interface/#dynamic-simulation", "The Dynamic Simulation tab");
    public static final Page TAB_ANALYSIS =
            page("gui/interface/#analysis", "The Analysis tab");
    public static final Page TAB_CODEGEN =
            page("gui/interface/#codegen", "The Codegen tab");

    /** The walkthroughs. */
    public static final Page RUNNING =
            page("gui/running/", "Running a Simulation");
    public static final Page EXTRACTING_CURVES =
            page("gui/running/#5-extract-the-curves", "Extracting the curves");
    public static final Page REAL_TIME_PLOTTING =
            page("gui/first-run/#real-time-plotting", "Real-time plotting");

    /** The Simulation Guide, which owns the file formats and the settings. */
    public static final Page FILE_FORMATS =
            page("user-guide/file-formats/", "File Formats");
    public static final Page OBSERVABLES_FILE =
            page("user-guide/file-formats/#observables-file", "Observables File");
    public static final Page NETWORK =
            page("user-guide/network/", "Network Modeling");
    public static final Page DYNAMIC_MODELS =
            page("user-guide/dynamic-models/", "Dynamic Data Records");
    public static final Page DISTURBANCES =
            page("user-guide/disturbances/", "Disturbances");
    public static final Page SOLVER_SETTINGS =
            page("user-guide/solver-settings/", "Solver Settings");
    public static final Page POWER_FLOW =
            page("user-guide/power-flow/", "Power Flow");
    public static final Page ONE_LINE_DIAGRAM =
            page("user-guide/power-flow/#annotated-one-line-diagram",
                    "Annotated One-line Diagram");
    public static final Page EIGENANALYSIS =
            page("user-guide/eigenanalysis/", "Eigenanalysis");

    /** Extending STEPSS, which is what the Codegen tab is for. */
    public static final Page USER_MODELS =
            page("developer/user-models/", "User-Defined Models");
    public static final Page CODEGEN_BLOCKS =
            page("developer/codegen-blocks/", "CODEGEN Blocks");
    public static final Page CODEGEN_EXAMPLES =
            page("developer/codegen-examples/", "CODEGEN Model Examples");
    public static final Page CG_STUDIO =
            page("developer/cg-studio/", "CODEGEN Studio");

    // What each ? on the form offers, in the order the menu lists it. Where a
    // group is described by more than one page, the tab's own section comes
    // first: it answers "where am I", and the references answer "what do I put
    // in it", which is the order someone lost enough to press ? wants them in.

    public static final Page[] SYSTEM_DATA_FILES = {
        TAB_SYSTEM_DATA, FILE_FORMATS, NETWORK, DYNAMIC_MODELS, SOLVER_SETTINGS};
    public static final Page[] DISTURBANCE_FILE = {DISTURBANCES};
    public static final Page[] DIAGRAM_FILE = {ONE_LINE_DIAGRAM};

    public static final Page[] RUNTIME_OBSERVABLES = {
        TAB_OBSERVABLES_RUNTIME, REAL_TIME_PLOTTING};
    public static final Page[] RECORDING_TO_FILE = {TAB_OBSERVABLES_RECORDING};
    public static final Page[] OBSERVABLES_FILE_ROW = {
        OBSERVABLES_FILE, TAB_OBSERVABLES_RECORDING};

    public static final Page[] POWER_FLOW_TAB = {TAB_POWER_FLOW, POWER_FLOW};
    public static final Page[] DYNAMIC_SIMULATION_TAB = {
        TAB_DYNAMIC_SIMULATION, RUNNING, DISTURBANCES, SOLVER_SETTINGS};

    public static final Page[] TIME_DOMAIN_ANALYSIS = {
        TAB_ANALYSIS, EXTRACTING_CURVES, RUNNING};
    public static final Page[] SMALL_SIGNAL_ANALYSIS = {
        EIGENANALYSIS, TAB_ANALYSIS};

    public static final Page[] CODEGEN_TAB = {
        TAB_CODEGEN, USER_MODELS, CODEGEN_BLOCKS, CODEGEN_EXAMPLES, CG_STUDIO};

    /**
     * A ? button that opens the documentation for one group of controls.
     *
     * <p>One page opens straight away. Several open a menu naming each of
     * them, because a mark on its own says nothing about where it is about to
     * send you, and the pages behind these buttons are not interchangeable:
     * the System Data rows take four different kinds of file, each documented
     * separately.
     *
     * @param subject what the group is, for the tooltip, as a noun phrase that
     *                completes "Documentation for"
     * @param pages   what the button offers, in menu order, at least one
     */
    public static JButton helpButton(String subject, Page... pages) {
        if (pages.length == 0) {
            throw new IllegalArgumentException("a ? button with nothing behind it");
        }
        final List<Page> offered = Arrays.asList(pages);
        JButton button = new JButton(HelpIcon.SMALL);
        button.setToolTipText(tooltip(subject, offered));
        // The same treatment the pencils get: at the stock button insets a
        // 16px icon needs about 44px, and a mark that wide beside a heading
        // reads as an action rather than an aside.
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setName("help-" + pages[0].path());
        // Left in the tab order, and named, rather than made a decoration.
        // The button carries no text, so without this a screen reader reaches
        // it and says "button", and the one control on the tab that explains
        // the others is the one it cannot describe. The cost is one extra tab
        // stop per section, which is what a keyboard user needs to reach it at
        // all.
        button.getAccessibleContext().setAccessibleName(
                "Documentation for " + subject);
        if (offered.size() == 1) {
            final String url = offered.get(0).url();
            button.addActionListener(event -> PlatformLauncher.openUrl(url));
        } else {
            button.addActionListener(event -> {
                JPopupMenu menu = new JPopupMenu();
                for (final Page page : offered) {
                    JMenuItem item = new JMenuItem(page.title());
                    item.setToolTipText(page.url());
                    item.addActionListener(
                            chosen -> PlatformLauncher.openUrl(page.url()));
                    menu.add(item);
                }
                Point at = anchor(button, menu.getPreferredSize());
                menu.show(button, at.x, at.y);
            });
        }
        return button;
    }

    /**
     * Where to put the menu, relative to the button that opened it.
     *
     * <p>Below and left-aligned is the default and is right for the ? beside a
     * section heading. It is wrong for the three in an action bar: those sit
     * at the bottom right corner of the window, where a five-item menu opening
     * downwards runs off the bottom of the screen and one opening rightwards
     * runs off the side. Swing shifts a heavyweight popup back on screen but
     * clips a lightweight one, and which of the two it uses depends on whether
     * the menu fits inside the frame, so the same menu shows differently on
     * two machines. Deciding here means it does not.
     */
    private static Point anchor(JButton button, Dimension menu) {
        GraphicsConfiguration gc = button.getGraphicsConfiguration();
        Rectangle screen = gc != null ? gc.getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        Insets taken = gc != null
                ? Toolkit.getDefaultToolkit().getScreenInsets(gc)
                : new Insets(0, 0, 0, 0);
        Point on = button.getLocationOnScreen();

        int y = button.getHeight();
        if (on.y + y + menu.height > screen.y + screen.height - taken.bottom) {
            y = -menu.height;
        }
        int x = 0;
        if (on.x + menu.width > screen.x + screen.width - taken.right) {
            x = button.getWidth() - menu.width;
        }
        return new Point(x, y);
    }

    /**
     * What the tooltip says. It names the destination when there is one, so
     * the button is not a mystery before it is pressed, and says a choice is
     * coming when there is more than one.
     */
    private static String tooltip(String subject, List<Page> pages) {
        if (pages.size() == 1) {
            return "Documentation for " + subject + ": opens "
                    + pages.get(0).title();
        }
        StringBuilder text = new StringBuilder("<html>Documentation for ")
                .append(subject).append(":<br>");
        for (int i = 0; i < pages.size(); i++) {
            text.append(i == 0 ? "" : ", ").append(pages.get(i).title());
        }
        return text.append("</html>").toString();
    }

    private Docs() {
    }
}
