package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.EvalGate;
import akyrion.thepredict.eval.EvalStats;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything one run of the effectiveness bench measured, and the verdicts read from it (§7.0 of the simulator audit):
 * a configuration of ThePredict passes when every validity gate passes and the lower bound of every one of its cells,
 * one-sided Wilson with Bonferroni over its cells, is at least {@value BenchSettings#GOAL}; with a gate failed there is
 * no verdict.
 *
 * @param settings the run
 * @param gates the validity gates
 * @param cells the cells without Jev, by ThePredict configuration
 * @param jev the cells of the sensitivity to Jev
 * @param sensitivities cells outside the verdict that show what a figure depends on
 * @param rivals the rivals
 * @param searches the searches that ran
 * @param arms the matrix A to D measured again
 * @param seconds the wall time of the run
 */
record BenchOutcome(BenchSettings settings, List<BenchGates.Gate> gates,
                    Map<BenchProfiles.Timing, List<BenchCell.Result>> cells, List<BenchCell.Result> jev,
                    List<BenchCell.Result> sensitivities, List<BenchRivals.Rival> rivals, List<BenchRivals.SearchLog> searches, List<BenchArms.Row> arms,
                    double seconds) {
    /** The one-sided quantile of a family of cells. */
    static double z(int cells) {
        return EvalStats.bonferroniZ(BenchSettings.ALPHA, Math.max(1, cells));
    }

    /** Whether every gate that a configuration depends on passed. */
    boolean gatesPassed(BenchProfiles.Timing timing) {
        for (var gate : gates) {
            if ((gate.timing().isEmpty() || gate.timing().get() == timing) && !gate.passed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The verdict of a configuration without Jev: {@code PASS_SIM_ONLY}, {@code FAIL}, or {@code INCONCLUSIVE} when a
     * gate failed.
     */
    String verdict(BenchProfiles.Timing timing) {
        return verdict(cells.get(timing), gatesPassed(timing));
    }

    /** The verdict of a family of cells, through {@link EvalGate}. */
    static String verdict(List<BenchCell.Result> family, boolean gates) {
        if (!gates) {
            return EvalGate.INCONCLUSIVE;
        }
        var z = z(family.size());
        var stages = new String[family.size()];
        for (var at = 0; at < family.size(); at++) {
            stages[at] = family.get(at).lower(z) >= BenchSettings.GOAL ? EvalGate.PASS_SIM_ONLY : EvalGate.FAIL;
        }
        return EvalGate.overall(stages);
    }

    /** The cell with the lowest bound of a family. */
    static Optional<BenchCell.Result> worst(List<BenchCell.Result> family) {
        var z = z(family.size());
        BenchCell.Result worst = null;
        for (var cell : family) {
            if (worst == null || cell.lower(z) < worst.lower(z)) {
                worst = cell;
            }
        }
        return Optional.ofNullable(worst);
    }

    /** The cells of the sensitivity to Jev of one configuration. */
    List<BenchCell.Result> jev(BenchProfiles.Timing timing) {
        return jev.stream().filter(cell -> cell.spec().timing() == timing).toList();
    }
}
