package my.stepss.ssa;

/** One row of &lt;base&gt;_modes.dat. */
public final class Mode {

    public final int index;
    public final double re;      //!< real part of lambda (1/s)
    public final double im;      //!< imaginary part of lambda (rad/s)
    public final double zeta;    //!< damping ratio (-)
    public final double freqHz;  //!< oscillation frequency (Hz)
    /** True when Re(lambda) passed the run's real_limit filter. */
    public final boolean dominant;
    /**
     * True when the eigenvalue is simple. When false the eigenvalue is
     * degenerate, its eigenvectors are not unique, and its participation
     * factors and mode shape are basis-dependent and must not be read.
     */
    public final boolean simple;

    Mode(int index, double re, double im, double zeta, double freqHz,
            boolean dominant, boolean simple) {
        this.index = index;
        this.re = re;
        this.im = im;
        this.zeta = zeta;
        this.freqHz = freqHz;
        this.dominant = dominant;
        this.simple = simple;
    }
}
