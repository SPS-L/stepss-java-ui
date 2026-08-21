package my.stepss;

/**
 * Headless checks for the inline banner's text handling.
 *
 * <p>This repository has no unit-test framework and is not gaining one, so
 * this main() is the substitute, in the shape {@code ConsoleSinkCheck} and
 * {@code PreferencesCheck} already use. Only {@link InlineBanner#oneLine} is
 * exercised, because it is the part with a trap in it and the only part that
 * runs without a display: the banner itself is a Swing component and is
 * covered by acceptance against a running window.
 *
 * <p>The trap is that a {@code JLabel} drops a newline instead of breaking on
 * it, so the words either side are joined. That is not visible in the source
 * of a message - it reads as a correctly wrapped paragraph - and it is not
 * visible in a dialog, where the same string is right. It only shows on the
 * banner, as "a systemwith more states".
 */
public final class BannerCheck {

    private static int failures;

    private BannerCheck() {
    }

    public static void main(String[] args) {
        checkNewlinesBecomeSpaces();
        checkRunsCollapse();
        checkNothingElseIsLost();
        checkEdges();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** The failure that prompted this: two words joined across a wrap. */
    private static void checkNewlinesBecomeSpaces() {
        expect("a newline separates the words it wrapped",
                "given $SCHEME DE and $OMEGA_REF SYN",
                InlineBanner.oneLine("given $SCHEME DE and\n$OMEGA_REF SYN"));
        expect("a Windows line ending does too", "one two",
                InlineBanner.oneLine("one\r\ntwo"));
        expect("so does a tab", "one two", InlineBanner.oneLine("one\ttwo"));
    }

    /**
     * A blank line between paragraphs is two newlines, and turning each into a
     * space would leave a gap in the middle of the line.
     */
    private static void checkRunsCollapse() {
        expect("a paragraph break is one space", "first para. second para.",
                InlineBanner.oneLine("first para.\n\nsecond para."));
        expect("an indented continuation is one space", "a b",
                InlineBanner.oneLine("a\n    b"));
    }

    /** The text is joined, never shortened. */
    private static void checkNothingElseIsLost() {
        String message = "The results basename \"my.run-1\" cannot be used.\n\n"
                + "It names the three results files and is written into the\n"
                + "EIG disturbance record.";
        String flat = InlineBanner.oneLine(message);
        expect("the quoted basename survives intact", true,
                flat.contains("\"my.run-1\""));
        expect("the last sentence is still there", true,
                flat.endsWith("EIG disturbance record."));
        expect("nothing is left to wrap on", false, flat.contains("\n"));
        // Every word of the original, in order, and no others.
        expect("no word is dropped or invented",
                String.join(" ", message.trim().split("\\s+")), flat);
    }

    /** The shapes a caller can hand it that are not prose. */
    private static void checkEdges() {
        expect("a null message is empty rather than \"null\"", "",
                InlineBanner.oneLine(null));
        expect("an empty message stays empty", "", InlineBanner.oneLine(""));
        expect("whitespace only is empty", "", InlineBanner.oneLine("  \n\t "));
        expect("surrounding space is trimmed", "text",
                InlineBanner.oneLine("\n  text  \n"));
        expect("a message already on one line is unchanged",
                "Power flow finished", InlineBanner.oneLine("Power flow finished"));
    }

    private static void expect(String what, Object want, Object got) {
        if (want == null ? got == null : want.equals(got)) {
            System.out.println("PASS  " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what + ": wanted <" + want + "> got <" + got + ">");
        }
    }
}
