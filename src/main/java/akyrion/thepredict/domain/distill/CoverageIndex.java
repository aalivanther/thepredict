package akyrion.thepredict.domain.distill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable coverage of one {@code .tpld} (M29.3). The writer publishes a new index between ticks. Rows of the same
 * fine context (one per week of {@code m29_agreement_stats}) are summed, so no week hides another.
 *
 * @param cells fine cells
 * @param builtMinute minute the index was built, supplied by the caller
 * @param profileFingerprint profile fingerprint
 * @param teacherModel teacher model, or {@code outcome}
 * @param regimeActive whether the regime has a verdict
 * @param modelStale whether a teacher-model change marked the index stale
 */
public record CoverageIndex(Map<String, CoverageCell> cells, long builtMinute, String profileFingerprint,
                            String teacherModel, boolean regimeActive, boolean modelStale) {
    /** An empty index that delegates nothing. */
    public static final CoverageIndex EMPTY = new CoverageIndex(Map.of(), 0L, "", "outcome", false, false);

    /**
     * Copies the map.
     */
    public CoverageIndex {
        cells = Map.copyOf(cells);
    }

    /**
     * Looks up one cell.
     *
     * @param context fine context
     * @return the cell
     */
    public Optional<CoverageCell> lookup(String context) {
        return Optional.ofNullable(cells.get(context));
    }

    /**
     * Coverage of one capture.
     *
     * @param context fine context
     * @param candidates tactic ids
     * @param nowMinute current minute, supplied by the caller
     * @param maxAgeMinutes maximum age
     * @param fingerprint live profile fingerprint
     * @param model live teacher model
     * @param minDecisions minimum n
     * @param margin non-inferiority margin
     * @return the cause, or empty when covered
     */
    public Optional<OodCause> covered(String context, List<String> candidates, long nowMinute, int maxAgeMinutes,
                                      String fingerprint, String model, int minDecisions, double margin) {
        var stale = modelStale || nowMinute - builtMinute > maxAgeMinutes;
        var profileMatches = profileFingerprint.equals(fingerprint);
        var modelMatches = teacherModel.equals(model);
        var cell = cells.get(context);
        if (cell == null) {
            return CoverageRule.unseen(stale, profileMatches, modelMatches, regimeActive);
        }
        return CoverageRule.cause(stale, profileMatches, modelMatches, regimeActive, cell, candidates, minDecisions,
                margin);
    }

    /**
     * A copy marked stale because the teacher model changed (errata E29-13 applied).
     *
     * @return the copy
     */
    public CoverageIndex markModelStale() {
        return new CoverageIndex(cells, builtMinute, profileFingerprint, teacherModel, regimeActive, true);
    }

    /**
     * Builds an index from cells. Cells of the same context are summed: counts and sums add, the offered tactics
     * join, a drift mark sticks, and the agreement metric is weighted by {@code n}.
     *
     * @param rows the cells
     * @param builtMinute minute
     * @param fingerprint profile fingerprint
     * @param model teacher model
     * @param regimeActive regime verdict
     * @return the index
     */
    public static CoverageIndex of(List<CoverageCell> rows, long builtMinute, String fingerprint, String model,
                                   boolean regimeActive) {
        var map = new LinkedHashMap<String, CoverageCell>();
        for (var row : rows) {
            map.merge(row.context(), row, CoverageIndex::sum);
        }
        return new CoverageIndex(map, builtMinute, fingerprint, model, regimeActive, false);
    }

    private static CoverageCell sum(CoverageCell left, CoverageCell right) {
        var offered = new LinkedHashSet<String>(left.offered());
        offered.addAll(right.offered());
        var n = left.n() + right.n();
        var agreement = n <= 0 ? 0.0 : (left.agreement() * left.n() + right.agreement() * right.n()) / n;
        return new CoverageCell(left.context(), n, left.sumDelta() + right.sumDelta(),
                left.sumDeltaSq() + right.sumDeltaSq(), new ArrayList<>(offered), left.drifted() || right.drifted(),
                agreement);
    }
}
