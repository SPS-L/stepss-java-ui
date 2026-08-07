package my.ramses.compile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        checkSpliceIsDeterministic();
        checkSpliceOnAlreadySplicedOutputIsIdempotent();
        checkSpliceSkipsAlreadyRegisteredModel();
        checkSpliceSkipsOnlyTheRegisteredModel();
        checkMissingMarkerFails();
        checkAbiParsing();
        checkCompilerSelection();
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
     * splice() has no mutable state, so calling it twice from the same
     * untouched pristine string must yield byte-identical output. This is a
     * cheap sanity check, not proof that splicing is safe to repeat - it
     * says nothing about what happens when the second call is fed the first
     * call's output instead of pristine source. See
     * {@link #checkSpliceOnAlreadySplicedOutputDuplicatesCase()} for that.
     */
    private static void checkSpliceIsDeterministic() {
        try {
            String first = RouterSplicer.splice(pristine(), "exc", Arrays.asList("exc_ALPHA"));
            String second = RouterSplicer.splice(pristine(), "exc", Arrays.asList("exc_ALPHA"));
            expect("second splice equals first", true, first.equals(second));
            expect("exactly one case from pristine", 1, count(second, "case('exc_ALPHA')"));
        } catch (Exception ex) {
            fail("determinism check threw: " + ex);
        }
    }

    /**
     * Pins the behaviour added to close the exc_ENTSOE_lim duplicate-case
     * defect: splice() now skips a model already registered via a live
     * {@code <kind>_ptr=>model} assignment, and a model spliced in by an
     * earlier call is registered exactly that way. So feeding splice() its
     * own output for the same model is now idempotent - the second call
     * finds {@code exc_ptr=>exc_ALPHA} already live and adds nothing more.
     * This check used to pin the opposite (a second, duplicate case on
     * every re-splice); that was real behaviour before the fix, not a
     * hypothetical, and this update is the "revisit the reset logic" signal
     * that old docstring called for. {@link ModelCompiler#prepare} still
     * resets the kit to pristine before every compile regardless - this
     * idempotency is a second line of defence, not a replacement for that
     * reset, since nothing here helps two different models that happen to
     * collide, or a router the reset failed to actually restore.
     */
    private static void checkSpliceOnAlreadySplicedOutputIsIdempotent() {
        try {
            String once = RouterSplicer.splice(pristine(), "exc", Arrays.asList("exc_ALPHA"));
            String twice = RouterSplicer.splice(once, "exc", Arrays.asList("exc_ALPHA"));
            expect("re-splicing an already-registered model adds no extra case", 1,
                    count(twice, "case('exc_ALPHA')"));
            expect("re-splicing an already-registered model adds no extra external", 1,
                    count(twice, "external :: exc_ALPHA"));
            expect("re-splicing onto already-spliced output changes nothing further",
                    true, once.equals(twice));
        } catch (Exception ex) {
            fail("idempotent re-splice check threw: " + ex);
        }
    }

    /**
     * The literal acceptance scenario: a model that arrives with the kit
     * already registered, exactly like {@code exc_ENTSOE_lim} in the real
     * {@code src/usr_exc_models.f90}. {@code pristine()} plays that role
     * with its built-in {@code exc_KUNDUR}, live outside the spliced
     * region. Splicing it again must not duplicate its external or case -
     * upstream's Makefile fails the build on a duplicate case label, and
     * that is exactly the failure this check exists to prevent.
     */
    private static void checkSpliceSkipsAlreadyRegisteredModel() {
        try {
            String out = RouterSplicer.splice(pristine(), "exc", Arrays.asList("exc_KUNDUR"));
            expect("no duplicate external for an already-registered model", 1,
                    count(out, "external :: exc_KUNDUR"));
            expect("no duplicate pointer assignment for an already-registered model", 1,
                    count(out, "exc_ptr=>exc_KUNDUR"));
            expect("markers still present", 1, count(out, RouterSplicer.CASES_MARKER));
        } catch (Exception ex) {
            fail("already-registered skip check threw: " + ex);
        }
    }

    /**
     * The realistic mixed case: a user's Codegen run includes both the
     * shipped, already-registered example and a genuinely new model.
     * Skipping the registered one must not affect splicing the new one in
     * beside it.
     */
    private static void checkSpliceSkipsOnlyTheRegisteredModel() {
        try {
            String out = RouterSplicer.splice(pristine(), "exc",
                    Arrays.asList("exc_KUNDUR", "exc_ALPHA"));
            expect("already-registered model still not duplicated", 1,
                    count(out, "external :: exc_KUNDUR"));
            expect("new model alongside it is still spliced in", 1,
                    count(out, "external :: exc_ALPHA"));
            expect("new model's case is still spliced in", 1,
                    count(out, "case('exc_ALPHA')"));
        } catch (Exception ex) {
            fail("mixed registered/new splice check threw: " + ex);
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

    private static void checkAbiParsing() {
        expect("abi 15", 15, FortranToolchain.parseAbi(
                "GFORTRAN module version '15' created from probe.f90"));
        expect("abi 16", 16, FortranToolchain.parseAbi(
                "GFORTRAN module version '16' created from probe.f90"));
        expect("abi absent", -1, FortranToolchain.parseAbi("not a module banner"));
        expect("abi unterminated", -1, FortranToolchain.parseAbi("module version '15"));
    }

    /**
     * Exercises {@link FortranToolchain#choose} directly against synthetic
     * candidate lists, rather than through {@code pickCompiler}, so these
     * checks do not depend on which compilers (if any) happen to be
     * installed on the machine running the harness. Covers the leniency
     * contract end to end: a missing ABI on either side must never turn a
     * compiler that exists into a reported "not found" - the one case this
     * method was added to close was an unreadable kit ABI with no plain
     * gfortran but a versioned one present, which used to fall through to
     * null.
     */
    private static void checkCompilerSelection() {
        expect("unknown kit abi, only a versioned compiler: picks it, not null",
                "gfortran-13",
                FortranToolchain.choose(-1, Arrays.asList(
                        new FortranToolchain.Candidate("gfortran-13", false, 15))));

        expect("unknown kit abi, plain and versioned both present: prefers plain",
                "gfortran",
                FortranToolchain.choose(-1, Arrays.asList(
                        new FortranToolchain.Candidate("gfortran", true, -1),
                        new FortranToolchain.Candidate("gfortran-13", false, 15))));

        expect("known kit abi, plain's abi unreadable: accepted leniently",
                "gfortran",
                FortranToolchain.choose(15, Arrays.asList(
                        new FortranToolchain.Candidate("gfortran", true, -1))));

        expect("known kit abi, plain mismatches: falls through to matching versioned",
                "gfortran-15",
                FortranToolchain.choose(15, Arrays.asList(
                        new FortranToolchain.Candidate("gfortran", true, 16),
                        new FortranToolchain.Candidate("gfortran-15", false, 15))));

        expect("known kit abi, nothing matches: still returns a real compiler, not null",
                "gfortran-16",
                FortranToolchain.choose(15, Arrays.asList(
                        new FortranToolchain.Candidate("gfortran-16", false, 16))));

        String none = FortranToolchain.choose(15, Collections.<FortranToolchain.Candidate>emptyList());
        if (none == null) {
            pass("no compiler on PATH at all: null");
        } else {
            fail("no compiler on PATH at all: wanted <null> got <" + none + ">");
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
