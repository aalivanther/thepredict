package akyrion.thepredict.engine;

/**
 * Ordered degradation of the tick layer (M20.3). Three ticks at a cap raise one step. Forty ticks under half of
 * both caps lower one. The cast comparison is never delayed by a step.
 */
public final class TickDegrade {
    /** Ticks at a cap before a step rises. */
    public static final int RISE_TICKS = 3;
    /** Ticks under half of both caps before a step falls. */
    public static final int FALL_TICKS = 40;

    private int step;
    private int hot;
    private int cool;

    /** Starts at step 0. */
    public TickDegrade() {
    }

    /**
     * Current step, 0 to 3.
     *
     * @return the step
     */
    public int step() {
        return step;
    }

    /**
     * One tick.
     *
     * @param capped whether a cap was hit
     * @param underHalf whether both caps stayed under half
     */
    public void note(boolean capped, boolean underHalf) {
        if (capped) {
            cool = 0;
            hot++;
            if (hot >= RISE_TICKS && step < 3) {
                step++;
                hot = 0;
            }
            return;
        }
        hot = 0;
        if (underHalf) {
            cool++;
            if (cool >= FALL_TICKS && step > 0) {
                step--;
                cool = 0;
            }
        } else {
            cool = 0;
        }
    }
}
