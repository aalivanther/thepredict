package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.AgreementStatDelta;
import akyrion.thepredict.domain.distill.CoverageCell;
import akyrion.thepredict.domain.distill.CoverageIndex;
import akyrion.thepredict.domain.distill.DistilledPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the coverage index and publishes it with the distilled policy (M29.3). Startup and the agreement
 * page between ticks both call {@link #refresh}.
 */
public final class DistillPublish {
    private DistillPublish() {
    }

    /**
     * Publishes an index built from agreement rows and the given policy.
     *
     * @param runtime the runtime
     * @param rows agreement rows, outcome columns only
     * @param policy the student policy
     * @param minute minute supplied by the caller
     * @param fingerprint profile fingerprint
     * @param teacher teacher model
     * @param regimeActive whether the regime verdict is loaded
     */
    public static void refresh(DistillRuntime runtime, List<AgreementStatDelta> rows, DistilledPolicy policy,
                               long minute, String fingerprint, String teacher, boolean regimeActive) {
        var cells = new ArrayList<CoverageCell>();
        for (var row : rows) {
            if (row.n() <= 0) {
                continue;
            }
            var offered = new ArrayList<String>();
            policy.cells().get(row.context()).ifPresent(order -> offered.addAll(order.tactics()));
            var compared = Math.max(1, row.compared());
            cells.add(new CoverageCell(row.context(), row.n(), row.sumDelta(), row.sumDeltaSq(), offered, false,
                    row.agreed() / (double) compared));
        }
        runtime.publish(CoverageIndex.of(cells, minute, fingerprint, teacher, regimeActive), policy);
    }
}
