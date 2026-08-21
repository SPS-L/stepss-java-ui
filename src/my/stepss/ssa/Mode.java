package my.stepss.ssa;

/** One row of &lt;base&gt;_modes.dat. */
public final class Mode {

    public final int index;
    public final double re;      //!< real part of lambda (1/s)
    public final double im;      //!< imaginary part of lambda (rad/s)
    public final double zeta;    //!< damping ratio (-)
    public final double freqHz;  //!< oscillation frequency (Hz)
    /**
     * The engine's own dom flag, and <b>null for any v2 modes file</b>, which
     * is to say for every run made by a current engine.
     *
     * <p>v1 wrote a dominance column here, 1 when Re(lambda) passed a
     * real_limit fixed on the EIG record, and used it to decide which modes
     * got participation factors and a mode shape at all. That is gone: all
     * three results files now carry every mode, and which of them are worth
     * showing is decided when the results are read, against a limit the
     * reader can change without running the case again.
     *
     * <p>It survives only so that an archive saved by an older engine still
     * reads honestly. On such a file {@link Boolean#FALSE} is the one and
     * only reason a simple mode can have no participation rows and no mode
     * shape, and saying so is the difference between explaining an absence
     * and inventing a cause for it. Test it with {@code == Boolean.FALSE},
     * never by unboxing.
     */
    public final Boolean dominant;
    /**
     * True when the eigenvalue is simple. When false the eigenvalue is
     * degenerate, its eigenvectors are not unique, and its participation
     * factors and mode shape are basis-dependent and must not be read.
     */
    public final boolean simple;

    Mode(int index, double re, double im, double zeta, double freqHz,
            Boolean dominant, boolean simple) {
        this.index = index;
        this.re = re;
        this.im = im;
        this.zeta = zeta;
        this.freqHz = freqHz;
        this.dominant = dominant;
        this.simple = simple;
    }
}
