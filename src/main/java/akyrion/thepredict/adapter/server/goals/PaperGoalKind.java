package akyrion.thepredict.adapter.server.goals;

/** ThePredict's own Paper goals, the only capabilities missing from MythicMobs and vanilla (M05.3). */
public enum PaperGoalKind {
    /** {@link FlankGoal}: walks around a shield-blocking target. */
    FLANK,
    /** {@link HoldGoal}: occupies {@code MOVE} while ThePredict controls and no tactical goal moves. */
    HOLD,
    /** {@link OwnerMarkerGoal}: never runs; marks that the generated set is installed. */
    OWNER,
    /** {@link MicroGoal}: short destinations while the tick layer is {@code FULL}. */
    MICRO
}
