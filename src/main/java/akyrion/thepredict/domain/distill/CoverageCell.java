package akyrion.thepredict.domain.distill;

import java.util.List;

/**
 * One fine cell of a coverage index (M29.3).
 *
 * @param context fine context text
 * @param n outcome comparisons
 * @param sumDelta sum of differences
 * @param sumDeltaSq sum of squares
 * @param offered tactics offered at least once
 * @param drifted whether a drift alarm removed the cell
 * @param agreement metric only, never a coverage input
 */
public record CoverageCell(String context, int n, double sumDelta, double sumDeltaSq, List<String> offered,
                           boolean drifted, double agreement) {
    /**
     * Copies the offered list.
     */
    public CoverageCell {
        offered = List.copyOf(offered);
    }

    /**
     * The outcome lower bound.
     *
     * @return the bound
     */
    public double lowerBound() {
        return NormalBound.lower(n, sumDelta, sumDeltaSq);
    }
}
