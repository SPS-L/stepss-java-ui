package my.ramses.ssa;

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
 * <p>_pf.dat and _ms.dat are optional. The engine writes them only when at
 * least one mode passed real_limit, so a run that filtered everything leaves
 * _modes.dat alone on disk. That is a legitimate result, not a broken load.
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
            if (name.endsWith(MODES_SUFFIX) && name.length() > MODES_SUFFIX.length()) {
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
