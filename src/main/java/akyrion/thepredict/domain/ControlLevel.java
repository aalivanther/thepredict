package akyrion.thepredict.domain;

/**
 * Fallback level that currently controls a mob (levels 1 to 4 of the fallback chain).
 *
 * <p>Correspondence with the states of the plan: {@code JEV} or {@code ALTERNATIVE} on an active
 * session is {@code ACTIVE}; {@code LOCAL} is {@code LOCAL}; {@code BASE} is {@code FALLBACK}.
 */
public enum ControlLevel {
    /** Level 1: the tactic Jev chose. */
    JEV,
    /** Level 2: the next valid alternative or a triggered contingency of the last answer. */
    ALTERNATIVE,
    /** Level 3: the local planner, or an intent fixed with the debug command. */
    LOCAL,
    /** Level 4: ThePredict does not control; the base AI runs. */
    BASE
}
