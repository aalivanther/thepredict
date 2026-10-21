package akyrion.thepredict.domain;

/**
 * How a choice moves a body (M24.6). The strongest kind wins, separately for the target and the caster.
 */
public enum DisplacementKind {
    /** The choice does not move a body. */
    NONE,
    /** A knockback of about two blocks. */
    KNOCKBACK,
    /** A throw, leap or explosion. */
    STRONG_PUSH,
    /** A pull toward the caster. */
    PULL,
    /** The caster moves toward the target. */
    SELF_FORWARD,
    /** The caster moves away from the target. */
    SELF_BACKWARD,
    /** The caster moves on some other axis. */
    SELF_OTHER;

    /**
     * Whether this kind pushes the target rather than the caster.
     *
     * @return {@code true} for knockback and a strong push
     */
    public boolean pushesTarget() {
        return this == KNOCKBACK || this == STRONG_PUSH;
    }

    /**
     * The stronger of two target displacements. Strong push beats knockback, which beats pull.
     *
     * @param other one kind
     * @param current the other kind
     * @return the stronger target displacement, ignoring self movement
     */
    public static DisplacementKind strongestTarget(DisplacementKind other, DisplacementKind current) {
        return rank(other) >= rank(current) ? targetOf(other) : targetOf(current);
    }

    private static DisplacementKind targetOf(DisplacementKind kind) {
        return kind.pushesTarget() || kind == PULL ? kind : NONE;
    }

    private static int rank(DisplacementKind kind) {
        return switch (kind) {
            case STRONG_PUSH -> 3;
            case KNOCKBACK -> 2;
            case PULL -> 1;
            default -> 0;
        };
    }
}
