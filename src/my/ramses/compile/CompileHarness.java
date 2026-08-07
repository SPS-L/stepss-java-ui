package my.ramses.compile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Headless checks for the parts of the compile pipeline that need neither a
 * Fortran toolchain nor an extracted kit. This repository has no unit-test
 * framework and is not gaining one, so this is where splice behaviour is
 * pinned down; the pipeline as a whole is verified behaviourally in the
 * acceptance runs.
 */
public final class CompileHarness {

    private static int failures = 0;

    public static void main(String[] args) {
        checkKindParsing();
        checkSpliceInsertsBothPoints();
        checkSpliceIsIdempotentOverPristineSource();
        checkMissingMarkerFails();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkKindParsing() {
        expect("kind exc", "exc", RouterSplicer.kindOf("exc_ENTSOE_lim.f90"));
        expect("kind twop", "twop", RouterSplicer.kindOf("twop_MY_MODEL.f90"));
        expect("kind dctl", "dctl", RouterSplicer.kindOf("dctl_thing.f90"));
        expect("model name", "exc_ENTSOE_lim", RouterSplicer.modelNameOf("exc_ENTSOE_lim.f90"));
        expect("router path", "src/usr_twop_models.f90", RouterSplicer.routerFor("twop"));
        expectThrows("unknown kind rejected", "zzz_model.f90");
        expectThrows("no underscore rejected", "model.f90");
        expectThrows("empty prefix rejected", "_model.f90");
    }

    private static void checkSpliceInsertsBothPoints() {
        try {
            String out = RouterSplicer.splice(pristine(), "exc",
                    Arrays.asList("exc_ALPHA", "exc_BETA"));
            expect("external alpha", 1, count(out, "external :: exc_ALPHA"));
            expect("external beta", 1, count(out, "external :: exc_BETA"));
            expect("case alpha", 1, count(out, "case('exc_ALPHA')"));
            expect("pointer alpha", 1, count(out, "exc_ptr=>exc_ALPHA"));
            expect("markers survive", 1, count(out, RouterSplicer.CASES_MARKER));
            expect("external precedes marker", true,
                    out.indexOf("external :: exc_ALPHA")
                            < out.indexOf(RouterSplicer.EXTERNALS_MARKER));
            expect("case precedes marker", true,
                    out.indexOf("case('exc_ALPHA')")
                            < out.indexOf(RouterSplicer.CASES_MARKER));
        } catch (Exception ex) {
            fail("splice threw: " + ex);
        }
    }

    /**
     * The GUI resets the kit to pristine before every compile, so splicing
     * twice from pristine must give exactly one entry per model - never two.
     * This is the specific defect the reset exists to prevent.
     */
    private static void checkSpliceIsIdempotentOverPristineSource() {
        try {
            String first = RouterSplicer.splice(pristine(), "exc", Arrays.asList("exc_ALPHA"));
            String second = RouterSplicer.splice(pristine(), "exc", Arrays.asList("exc_ALPHA"));
            expect("second splice equals first", true, first.equals(second));
            expect("exactly one case", 1, count(second, "case('exc_ALPHA')"));
        } catch (Exception ex) {
            fail("idempotence check threw: " + ex);
        }
    }

    private static void checkMissingMarkerFails() {
        try {
            RouterSplicer.splice("subroutine x\nend subroutine x\n", "exc",
                    Arrays.asList("exc_ALPHA"));
            fail("missing marker should have thrown");
        } catch (java.io.IOException expected) {
            pass("missing marker rejected");
        } catch (Exception ex) {
            fail("wrong exception for missing marker: " + ex);
        }
    }

    /** A minimal stand-in with the same shape as the real router. */
    private static String pristine() {
        List<String> lines = new ArrayList<String>();
        lines.add("subroutine assoc_exciter_ptr(modelname,exc_ptr)");
        lines.add("   external :: exc_KUNDUR");
        lines.add("   " + RouterSplicer.EXTERNALS_MARKER);
        lines.add("   select case (modelname4)");
        lines.add("      case('exc_kundur')");
        lines.add("         exc_ptr=>exc_KUNDUR");
        lines.add("      " + RouterSplicer.CASES_MARKER);
        lines.add("   end select");
        lines.add("end subroutine assoc_exciter_ptr");
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            n++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return n;
    }

    private static void expect(String what, Object want, Object got) {
        if (want.equals(got)) {
            pass(what);
        } else {
            fail(what + ": wanted <" + want + "> got <" + got + ">");
        }
    }

    private static void expectThrows(String what, String fileName) {
        try {
            RouterSplicer.kindOf(fileName);
            fail(what + ": no exception");
        } catch (IllegalArgumentException expected) {
            pass(what);
        }
    }

    private static void pass(String what) {
        System.out.println("PASS  " + what);
    }

    private static void fail(String what) {
        failures++;
        System.out.println("FAIL  " + what);
    }
}
