package akyrion.thepredict.domain.learn;

/**
 * A looked-up value (M28.3). Absent is {@link #NONE} ({@code level < 0}), never an empty optional parameter.
 * Weak means the cell is present and {@code nEff} is below the caller's minimum. There is no single weak constant,
 * because the count varies.
 *
 * @param valueMillis posterior mean in thousandths of reward
 * @param sdMillis posterior standard deviation in thousandths
 * @param nEff effective windows
 * @param level 3 fine, 2 coarse, 1 profile, 0 tactic, or negative when absent
 * @param jevSeen questions seen in the cell
 * @param jevAgree agreements in the cell
 * @param deltaMillis stored disagreement delta
 */
public record ValueEstimate(int valueMillis, int sdMillis, int nEff, int level, int jevSeen, int jevAgree,
                            int deltaMillis) {
    /** No cell for this profile and tactic. */
    public static final ValueEstimate NONE = new ValueEstimate(0, 0, 0, -1);

    /**
     * A cell without Jev counts. The counts and the delta are zero.
     *
     * @param valueMillis posterior mean in thousandths of reward
     * @param sdMillis posterior standard deviation in thousandths
     * @param nEff effective windows
     * @param level hierarchy level
     */
    public ValueEstimate(int valueMillis, int sdMillis, int nEff, int level) {
        this(valueMillis, sdMillis, nEff, level, 0, 0, 0);
    }

    /**
     * Whether the lookup found nothing.
     *
     * @return {@code true} when {@code level < 0}
     */
    public boolean none() {
        return level < 0;
    }

    /**
     * Whether the cell is present but too thin to reorder.
     *
     * @param minimum windows required to be usable
     * @return {@code true} when present and short of {@code minimum}
     */
    public boolean weak(int minimum) {
        return level >= 0 && nEff < minimum;
    }
}
