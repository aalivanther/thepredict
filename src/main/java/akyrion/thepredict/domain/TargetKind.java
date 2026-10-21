package akyrion.thepredict.domain;

/** Whether the fight is against a player (M22.8). */
public enum TargetKind {
    /** A player. Floors and entropy apply. */
    PLAYER,
    /** Anything else. Floor 0 and bench power. */
    ENTITY;

    /**
     * The context token.
     *
     * @return {@code player} or {@code entity}
     */
    public String token() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
