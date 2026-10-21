package akyrion.thepredict.domain;

/**
 * How much of the tick layer a session needs (M20.3). A warm session still casts on the tick a skill comes due.
 */
public enum TickTier {
    /** Signals, reactions and micro every tick, and a cast when it is due. */
    HOT,
    /** A cast every tick; signals, reactions and micro every other tick. */
    WARM,
    /** Nothing. The session is not controlled, has no target, or no cast is scheduled. */
    COLD;

    /**
     * Classifies one session.
     *
     * @param controlled level is Jev, alternative or local, or a debug intent is current
     * @param target the last probe still sees the target
     * @param nextReadyTick scheduled cast, or {@code Long.MAX_VALUE} when none is
     * @param now current tick
     * @param beyond the distance band is beyond
     * @param nativeMicro the published micro is native
     * @param armed a preference slot or a trigger is waiting out its delay
     * @return the tier
     */
    public static TickTier of(boolean controlled, boolean target, long nextReadyTick, long now, boolean beyond,
                              boolean nativeMicro, boolean armed) {
        if (!controlled || !target || nextReadyTick == Long.MAX_VALUE) {
            return COLD;
        }
        var warm = beyond || nativeMicro && !armed && nextReadyTick > now + 2;
        return warm ? WARM : HOT;
    }

    /**
     * Classifies one session that may be inside a tell (M23.9). The seven-argument method is unchanged. An action or
     * an open tell never returns {@link #COLD} while the mob is controlled and still has a target.
     *
     * @param controlled level is Jev, alternative or local, or a debug intent is current
     * @param target the last probe still sees the target
     * @param nextReadyTick scheduled cast, or {@code Long.MAX_VALUE} when none is
     * @param now current tick
     * @param beyond the distance band is beyond
     * @param nativeMicro the published micro is native
     * @param armed a preference slot or a trigger is waiting out its delay
     * @param acting an action stance is set or a tell is open
     * @return the tier
     */
    public static TickTier of(boolean controlled, boolean target, long nextReadyTick, long now, boolean beyond,
                              boolean nativeMicro, boolean armed, boolean acting) {
        if (!acting) {
            return of(controlled, target, nextReadyTick, now, beyond, nativeMicro, armed);
        }
        if (!controlled || !target) {
            return COLD;
        }
        var ready = nextReadyTick == Long.MAX_VALUE ? now : nextReadyTick;
        var tier = of(controlled, target, ready, now, beyond, nativeMicro, armed);
        return tier == COLD ? HOT : tier;
    }

    /**
     * Classifies one session that may hold a group token (M25). The eight-argument method is unchanged. A token
     * holder is never {@link #COLD} while the mob is controlled and still has a target.
     *
     * @param controlled level is Jev, alternative or local, or a debug intent is current
     * @param target the last probe still sees the target
     * @param nextReadyTick scheduled cast, or {@code Long.MAX_VALUE} when none is
     * @param now current tick
     * @param beyond the distance band is beyond
     * @param nativeMicro the published micro is native
     * @param armed a preference slot or a trigger is waiting out its delay
     * @param acting an action stance is set or a tell is open
     * @param holdingToken the session holds a melee or ranged token
     * @return the tier
     */
    public static TickTier of(boolean controlled, boolean target, long nextReadyTick, long now, boolean beyond,
                              boolean nativeMicro, boolean armed, boolean acting, boolean holdingToken) {
        if (!holdingToken) {
            return of(controlled, target, nextReadyTick, now, beyond, nativeMicro, armed, acting);
        }
        if (!controlled || !target) {
            return COLD;
        }
        var ready = nextReadyTick == Long.MAX_VALUE ? now : nextReadyTick;
        var tier = of(controlled, target, ready, now, beyond, nativeMicro, armed);
        return tier == COLD ? HOT : tier;
    }
}
