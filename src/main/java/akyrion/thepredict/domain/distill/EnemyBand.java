package akyrion.thepredict.domain.distill;

/**
 * How many living opponents a capture sees (M29.5).
 */
public enum EnemyBand {
    /** One living opponent. */
    ONE,
    /** Two or three living opponents. */
    FEW,
    /** Four or more living opponents. */
    MANY;

    /**
     * Band of a living count.
     *
     * @param living living opponents
     * @return the band
     */
    public static EnemyBand of(int living) {
        if (living <= 1) {
            return ONE;
        }
        return living <= 3 ? FEW : MANY;
    }
}
