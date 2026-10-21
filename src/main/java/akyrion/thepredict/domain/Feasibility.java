package akyrion.thepredict.domain;

/**
 * Whether the answer a tell would ask can be performed on the current ground (M24.6).
 */
public enum Feasibility {
    /** The answer has a cell that passes the filters. */
    FEASIBLE,
    /** Every candidate cell fails the filters. */
    INFEASIBLE,
    /** The ground is not ready, or a cell the answer needs is unknown. */
    UNKNOWN
}
