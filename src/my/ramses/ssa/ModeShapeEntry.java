package my.ramses.ssa;

/** One row of <base>_ms.dat: a machine's rotor-speed phasor in one mode. */
public final class ModeShapeEntry {

    public final int mode;
    public final int state;
    public final double magnitude;  //!< normalised so the largest in the mode is 1 (-)
    public final double angleDeg;   //!< relative to the largest entry (deg)
    public final String device;

    ModeShapeEntry(int mode, int state, double magnitude, double angleDeg,
            String device) {
        this.mode = mode;
        this.state = state;
        this.magnitude = magnitude;
        this.angleDeg = angleDeg;
        this.device = device;
    }
}
