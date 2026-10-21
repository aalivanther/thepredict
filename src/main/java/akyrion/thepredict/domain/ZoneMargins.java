package akyrion.thepredict.domain;

/**
 * Converts a margin in blocks into chamfer thirds, rounding up (errata E24-5).
 */
public final class ZoneMargins {
    /** One block, three thirds. */
    public static final int ONE = 3;
    /** One and a half blocks, five thirds. */
    public static final int ONE_AND_HALF = 5;
    /** Two blocks, six thirds. */
    public static final int TWO = 6;
    /** Three blocks, nine thirds. */
    public static final int THREE = 9;

    private ZoneMargins() {
    }

    /**
     * Thirds of a margin, rounded up. One and a half blocks become five thirds.
     *
     * @param blocks the margin in blocks, not negative
     * @return the thirds
     */
    public static int thirds(double blocks) {
        if (!(blocks > 0.0)) {
            return 0;
        }
        return (int) StrictMath.ceil(blocks * 3.0 - 1.0e-9);
    }
}
