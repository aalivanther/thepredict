package akyrion.thepredict.domain;

/** Which reaction bands a profile evaluates ({@code Timing.Reactions}, M20.4). */
public enum ReactionScope {
    /** Every band. */
    ALL,
    /** Only {@link ReactionBand#SURVIVAL}. */
    SURVIVAL,
    /** No reactions. */
    NONE;

    /** Delivery default. */
    public static final ReactionScope DEFAULT = ALL;

    /**
     * Tells whether a band is evaluated under this scope.
     *
     * @param band reaction band
     * @return whether the band is on
     */
    public boolean admits(ReactionBand band) {
        return switch (this) {
            case ALL -> true;
            case SURVIVAL -> band == ReactionBand.SURVIVAL;
            case NONE -> false;
        };
    }
}
