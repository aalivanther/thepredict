package akyrion.thepredict.engine;

/**
 * Drops one session to level 4 after an exception while the coordinator handled it (M10.6, rule C7), the way the
 * execution drops a session it failed itself: the outcome window of the failed tactic closes, the progress is
 * forgotten and the tap hears the drop with {@code INTERNAL_ERROR}. The {@link ExecutionSteps} of production
 * implement it ({@link TacticalExecution}); {@link SessionUpkeep} uses it when its steps do. Server thread only.
 */
public interface SessionFailure {
    /**
     * Drops an active session to level 4.
     *
     * @param session the session that failed
     * @param tick current server tick
     */
    void failed(MobSession session, long tick);
}
