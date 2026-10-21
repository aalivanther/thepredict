package akyrion.thepredict.domain.learn;

/** How many options the question offered (M28.4). */
public enum OptionsBand {
    /** Two options. */
    TWO,
    /** Three or four options. */
    THREE_FOUR,
    /** Five or more options. */
    FIVE_PLUS;

    /**
     * Band of a count.
     *
     * @param options how many options
     * @return the band
     */
    public static OptionsBand of(int options) {
        if (options <= 2) {
            return TWO;
        }
        return options <= 4 ? THREE_FOUR : FIVE_PLUS;
    }
}
