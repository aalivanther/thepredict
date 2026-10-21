package akyrion.thepredict.domain;

/**
 * Why a zone guard changed a cast or a step (M24.5, errata E24-2).
 */
public enum ZoneGuardKind {
    /** A destination failed the safe-cell query. */
    UNSAFE_DESTINATION,
    /** An orbit or a sidestep took the other side. */
    SIDE_SWITCHED,
    /** A backstep became a sidestep. */
    BACKSTEP_REPLACED,
    /** The caster would have stepped into a lethal push. */
    ESCAPE_BLOCKED,
    /** A lethal push against a player waited. */
    PUSH_DEFERRED,
    /** A lethal push was skipped after the deferral cap. */
    PUSH_SKIPPED,
    /** A tell was not opened because its answer did not fit the ground. */
    TELL_INFEASIBLE,
    /** The mob stepped away from a lethal edge. */
    EDGE_REPOSITION,
    /** Two safe-cell queries failed, so the micro went native. */
    MICRO_NO_SAFE_DEST
}
