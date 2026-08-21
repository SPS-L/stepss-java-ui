package my.stepss.ssa;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One small-signal run: the three files ssa.f90 wrote under a shared
 * basename, plus where they came from.
 *
 * <p>_pf.dat and _ms.dat are optional, and are treated as empty when absent
 * rather than failing the load. A v1 engine wrote them only when at least one
 * mode passed real_limit, so a run that filtered everything legitimately left
 * _modes.dat alone on disk; a v2 engine writes both for every run, so their
 * absence there means an incomplete copy instead. {@link SsaModes#formatVersion}
 * is what tells the two apart, and neither is a reason to refuse the run that
 * is present.
 */
public final class SsaResults {

    private static final String MODES_SUFFIX = "_modes.dat";

    private final File directory;
    private final String basename;
    private final SsaModes modes;
    private final SsaParticipation participation;
    private final SsaModeShapes shapes;

    private SsaResults(File directory, String basename, SsaModes modes,
            SsaParticipation participation, SsaModeShapes shapes) {
        this.directory = directory;
        this.basename = basename;
        this.modes = modes;
        this.participation = participation;
        this.shapes = shapes;
    }

    public File directory() {
        return directory;
    }

    public String basename() {
        return basename;
    }

    public SsaModes modes() {
        return modes;
    }

    public SsaParticipation participation() {
        return participation;
    }

    public SsaModeShapes shapes() {
        return shapes;
    }

    /** Every basename in dir for which a modes file exists, sorted. */
    public static List<String> basenames(File dir) {
        List<String> found = new ArrayList<String>();
        String[] names = dir == null ? null : dir.list();
        if (names == null) {
            return found;
        }
        for (String name : names) {
            // isFile(), not just the suffix: a directory named X_modes.dat
            // would otherwise be offered as a run and then fail in load()
            // with "no X_modes.dat in ...", about a name that plainly exists.
            if (name.endsWith(MODES_SUFFIX) && name.length() > MODES_SUFFIX.length()
                    && new File(dir, name).isFile()) {
                found.add(name.substring(0, name.length() - MODES_SUFFIX.length()));
            }
        }
        Collections.sort(found);
        return found;
    }

    public static SsaResults load(File dir, String basename) throws IOException {
        File modesFile = new File(dir, basename + MODES_SUFFIX);
        if (!modesFile.isFile()) {
            throw new IOException("no " + modesFile.getName() + " in " + dir);
        }
        SsaModes parsedModes = SsaModes.parse(read(modesFile));
        File pfFile = new File(dir, basename + "_pf.dat");
        File msFile = new File(dir, basename + "_ms.dat");
        SsaParticipation parsedPf = pfFile.isFile()
                ? SsaParticipation.parse(read(pfFile))
                : SsaParticipation.parse("");
        SsaModeShapes parsedMs = msFile.isFile()
                ? SsaModeShapes.parse(read(msFile))
                : SsaModeShapes.parse("");
        return new SsaResults(dir, basename, parsedModes, parsedPf, parsedMs);
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The modes whose real part is above {@code limit}, i.e. those a reader
     * has asked to call dominant.
     *
     * <p>Computed here rather than read from the file. The engine used to
     * decide this, from a real_limit fixed on the EIG record, and write
     * participation factors and mode shapes for the survivors alone; a mode
     * outside the limit was then visible in the modes table with nothing
     * behind it, and widening the limit meant running the case again. All
     * three files now carry every mode, so this is a question about the
     * display and is answered every time the number changes.
     *
     * <p>Strictly greater than, matching the engine's old {@code
     * Re(lambda) > real_limit} exactly, so the same limit selects the same
     * modes as it did before and an archived v1 run reads the way it did when
     * it was made.
     *
     * <p>Input order is preserved, so composing this after {@link
     * #electromechanical} keeps that method's sort by frequency.
     */
    public static List<Mode> aboveRealLimit(List<Mode> all, double limit) {
        List<Mode> kept = new ArrayList<Mode>();
        for (Mode mode : all) {
            if (mode.re > limit) {
                kept.add(mode);
            }
        }
        return kept;
    }

    /**
     * The electromechanical band, reproducing the python-ui notebook's
     * electromechanical(): 0.1 to 2.5 Hz with Im &gt; 0, sorted by frequency.
     * The Im &gt; 0 test is what collapses each conjugate pair to a single
     * row, since the two members are one physical oscillation.
     */
    public static List<Mode> electromechanical(List<Mode> all) {
        List<Mode> kept = new ArrayList<Mode>();
        for (Mode mode : all) {
            if (mode.freqHz > 0.1 && mode.freqHz < 2.5 && mode.im > 0.0) {
                kept.add(mode);
            }
        }
        Collections.sort(kept, new Comparator<Mode>() {
            @Override
            public int compare(Mode a, Mode b) {
                return Double.compare(a.freqHz, b.freqHz);
            }
        });
        return kept;
    }
}
