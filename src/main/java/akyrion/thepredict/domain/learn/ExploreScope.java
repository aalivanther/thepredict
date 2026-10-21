package akyrion.thepredict.domain.learn;

/** Where exploration is allowed (M28.5, rule A16). */
public enum ExploreScope {
    /** The simulator. Epsilon comes from the tool, default 0.10. */
    SIM,
    /** The labelled bank. */
    BANK,
    /** An entity duel, and only when {@code learning.exploreInDuels} is on. */
    DUEL_ENTITY,
    /** A mob whose file says {@code PredictorTraining: true}. */
    PLAYER_TRAINING
}
