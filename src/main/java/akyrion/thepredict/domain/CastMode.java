package akyrion.thepredict.domain;

/**
 * Which skills of a mob fire as soon as they are ready (M19.5). The user chooses it with {@code decision.castMode} and,
 * per profile, {@code Timing.CastMode}; its only runtime source is {@link DecisionTiming#castMode()}.
 */
public enum CastMode {
    /**
     * Every typed skill of the profile fires in the first tick it is ready, besides the choice of the intent; Jev still
     * sets the movement and which skill goes first. The default.
     */
    ANY_READY,
    /** Only the choice of the intent fires, with the tick precision of M19.4: the 0.0.1 behaviour and the opt-out. */
    INTENT_ONLY;

    /** Mode of a fresh installation and of every profile without {@code Timing.CastMode}: the only place (C13). */
    public static final CastMode DEFAULT = ANY_READY;
}
