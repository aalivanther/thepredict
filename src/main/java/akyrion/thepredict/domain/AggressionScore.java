package akyrion.thepredict.domain;

/**
 * Who should hold the melee token (M25). Pure. A member who cannot hit now scores zero.
 */
public final class AggressionScore {
    private AggressionScore() {
    }

    /**
     * Scores one member.
     *
     * @param role published role, or null when the group has none
     * @param gapQuarters gap bits of the tick signals
     * @param octant folded octant of the member around the target's look ({@link TickSignals#octant}): 0 in front,
     *        0 and 1 in sight (+150), 4 behind (−150)
     * @param canHit whether a damaging skill is ready within four ticks and the member is not recovering
     * @param holds whether this member already holds the token
     * @param named whether the plan names this member the main attacker
     * @return the score, 0 when the member cannot hit
     */
    public static int of(GroupRole role, int gapQuarters, int octant, boolean canHit, boolean holds, boolean named) {
        if (!canHit) {
            return 0;
        }
        var gap = Math.max(0, gapQuarters);
        var gapTerm = gap >= 8 ? 0 : 200 - (200 * gap) / 8;
        var sight = octant <= 1 ? 150 : octant == 4 ? -150 : 0;
        return rolePoints(role) + gapTerm + sight + (holds ? 150 : 0) + (named ? 100 : 0);
    }

    private static int rolePoints(GroupRole role) {
        if (role == null) {
            return 0;
        }
        return switch (role) {
            case PRESSURE -> 300;
            case FLANK -> 250;
            case CONTROL, RANGED -> 200;
            case GUARD -> 100;
            case SUPPORT -> 50;
            case RESERVE -> 0;
        };
    }
}
