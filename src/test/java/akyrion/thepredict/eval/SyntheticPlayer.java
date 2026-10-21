package akyrion.thepredict.eval;

/**
 * A simulator-only player (M22.3). Reaction is {@code round(12 − 8·s)} and the block chance is {@code 0.2 + 0.6·s}.
 */
public final class SyntheticPlayer {
    private final String archetype;
    private final double skill;

    /**
     * Creates one synthetic.
     *
     * @param archetype one of the four names
     * @param skill skill in 0 to 1
     */
    public SyntheticPlayer(String archetype, double skill) {
        if (skill < 0.0 || skill > 1.0) {
            throw new IllegalArgumentException("skill");
        }
        this.archetype = archetype;
        this.skill = skill;
    }

    /**
     * Reaction delay in ticks.
     *
     * @return {@code round(12 − 8·s)}
     */
    public int reactionTicks() {
        return (int) Math.round(12.0 - 8.0 * skill);
    }

    /**
     * Chance of a block.
     *
     * @return {@code 0.2 + 0.6·s}
     */
    public double blockChance() {
        return 0.2 + 0.6 * skill;
    }

    /**
     * Archetype name.
     *
     * @return the name
     */
    public String archetype() {
        return archetype;
    }
}
