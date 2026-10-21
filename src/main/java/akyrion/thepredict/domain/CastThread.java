package akyrion.thepredict.domain;

/**
 * Thread on which a cast of ThePredict runs the mechanics of its skill (M19.4, revision 4). Global setting
 * {@code decision.castThread}, read only by {@code adapter.server.execution}.
 */
public enum CastThread {
    /**
     * The skill metadata is built synchronous, so the {@code delay} mechanics of the skill wait in server ticks and the
     * later mechanics run on the server thread; {@code busyUntilTick} is then exact. The default.
     */
    SAME_TICK,
    /** The metadata of the 0.0.1 ({@code sync = false}): a {@code delay N} waits wall-clock time off the server thread. */
    MYTHIC_DEFAULT;

    /** Thread of a fresh installation: the only place of this default (C13). */
    public static final CastThread DEFAULT = SAME_TICK;
}
