package akyrion.thepredict.domain;

/**
 * Whether a backward escape may be cast (M24.5). A leap toward the target and any other self move are not guarded.
 */
public final class EscapeGuard {
    private EscapeGuard() {
    }

    /**
     * A disengage into a lethal far push waits. Observe and an unknown push do not block the cast.
     *
     * @param mode effective zone mode
     * @param self how the choice moves the caster
     * @param signals packed tick signals
     * @return {@code true} when the cast must wait
     */
    public static boolean blocked(ZoneMode mode, DisplacementKind self, long signals) {
        if (mode != ZoneMode.GUARD && mode != ZoneMode.FULL) {
            return false;
        }
        if (self != DisplacementKind.SELF_BACKWARD) {
            return false;
        }
        return TickSignals.push(signals, TickSignals.MOB_PUSH_FAR) == PushDanger.LETHAL.ordinal();
    }
}
