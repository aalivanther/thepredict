package akyrion.thepredict.adapter.server.goals;

/** Where the base AI of a generated goal set comes from (M05.6), in order of preference. */
public enum BaseAiOrigin {
    /** {@code BaseAI} of the ThePredict profile. */
    PROFILE,
    /** {@code AIGoalSelectors} of the MythicMobs mob, without {@code clear}. */
    MOB_CONFIG,
    /** {@link BaseAiResolver#GENERIC}: melee attack and random stroll. */
    GENERIC
}
