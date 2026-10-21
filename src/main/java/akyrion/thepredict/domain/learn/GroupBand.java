package akyrion.thepredict.domain.learn;

/** How many living members share the encounter, including this mob (M28.2). */
public enum GroupBand {
    /** At most one living member. */
    SOLO,
    /** Two to four living members. */
    SMALL,
    /** Five or more living members. */
    LARGE;

    /**
     * Band of a living count. With {@code group.mode} off or fair the count is still the living members (N6).
     *
     * @param living living members, including this mob
     * @return the band
     */
    public static GroupBand of(int living) {
        if (living <= 1) {
            return SOLO;
        }
        return living <= 4 ? SMALL : LARGE;
    }
}
