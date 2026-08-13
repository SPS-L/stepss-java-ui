package my.ramses.ssa;

/** One row of <base>_pf.dat. */
public final class Participation {

    public final int mode;
    public final int state;
    public final double pf;      //!< participation factor, largest per mode normalised to 1 (-)
    public final String family;  //!< SYN, TOR, EXC, INJ, DCTL
    public final String device;
    public final String variable;

    Participation(int mode, int state, double pf, String family, String device,
            String variable) {
        this.mode = mode;
        this.state = state;
        this.pf = pf;
        this.family = family;
        this.device = device;
        this.variable = variable;
    }
}
