package akyrion.thepredict;

import akyrion.thepredict.domain.distill.CellOrders;
import akyrion.thepredict.domain.distill.ChoiceStatDelta;
import akyrion.thepredict.domain.distill.ChoiceStatKey;
import akyrion.thepredict.domain.distill.CoverageCell;
import akyrion.thepredict.domain.distill.CoverageIndex;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.DistilledPolicy;
import akyrion.thepredict.domain.distill.FightRegime;
import akyrion.thepredict.domain.distill.PreferenceTree;
import akyrion.thepredict.domain.distill.RegimeKey;
import akyrion.thepredict.domain.distill.TacticOrder;
import akyrion.thepredict.engine.distill.RegimeSelector;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Local timings for M29. A figure past its budget is published as {@code no cumple}. Nothing calls a network.
 * 3.67 rounds/s remains the M22.3 target and is not measured here.
 */
public final class M29Measurements {
    private M29Measurements() {
    }

    /**
     * Writes the report to standard output.
     *
     * @param arguments ignored
     */
    public static void main(String[] arguments) {
        var report = new MeasurementReport();
        write(report);
        System.out.println(report.text());
    }

    /**
     * Writes the Distill table for 20, 100 and 200 sessions.
     *
     * @param report the report
     */
    public static void write(MeasurementReport report) {
        var policy = policy();
        var index = index();
        var selector = new RegimeSelector(40, 6);
        report.line("## Distill (M29)");
        report.line("");
        report.line("| sesiones | régimen ns | choose ns | cobertura ns | auditoría ns | delta ns |");
        report.line("| --- | --- | --- | --- | --- | --- |");
        for (var sessions : new int[] {20, 100, 200}) {
            report.line("| " + sessions
                    + " | " + per(sessions, () -> regime(selector, sessions))
                    + " | " + per(sessions, () -> choose(policy, sessions))
                    + " | " + per(sessions, () -> cover(index, sessions))
                    + " | " + per(sessions, () -> audit(sessions))
                    + " | " + per(sessions, () -> deltas(sessions)) + " |");
        }
        report.line("");
        var choose = per(200, () -> choose(policy, 200));
        var audit = per(200, () -> audit(200));
        var together = per(200, () -> both(policy, index, 200));
        report.line("choose " + choose + " ns " + mark(choose, 300) + "; auditoría " + audit + " ns "
                + mark(audit, 100) + "; régimen+choose+cobertura " + together + " ns " + mark(together, 2000) + ".");
        report.line("Presupuesto de referencia: choose ≤ 300 ns; auditoría ≤ 100 ns; régimen+árbol+cobertura ≤ 2000 ns. "
                + "Cifras de media por sesión, no p99.");
    }

    private static DistilledPolicy policy() {
        var order = new TacticOrder(List.of("t0", "t1", "t2", "t3"), List.of(400, 300, 200, 100), DistillSource.TABLE);
        return new DistilledPolicy("guardian", RegimeKey.ALL, new CellOrders(Map.of("fine", order)),
                PreferenceTree.leaf(order));
    }

    private static CoverageIndex index() {
        var cell = new CoverageCell("fine", 50, -0.5, 0.005, List.of("t0", "t1", "t2", "t3"), false, 0.0);
        return CoverageIndex.of(List.of(cell), 0L, "finger", "outcome", true);
    }

    private static void regime(RegimeSelector selector, int sessions) {
        for (var index = 0; index < sessions; index++) {
            FightRegime.of(1 + (index % 5), index % 50, 10, 40);
            selector.offer(RegimeKey.ALL, index);
            selector.onCapture(index + 40L);
        }
    }

    private static void choose(DistilledPolicy policy, int sessions) {
        var bands = new int[] {0, 0, 1, 0, 0, 0, 0};
        var candidates = List.of("t0", "t1", "t2", "t3");
        for (var index = 0; index < sessions; index++) {
            policy.choose(candidates, "fine", bands);
        }
    }

    private static void cover(CoverageIndex index, int sessions) {
        var candidates = List.of("t0", "t1");
        for (var ignored = 0; ignored < sessions; ignored++) {
            index.covered("fine", candidates, 0L, 60, "finger", "outcome", 50, 0.02);
        }
    }

    private static void audit(int sessions) {
        for (var index = 0; index < sessions; index++) {
            akyrion.thepredict.engine.distill.DistillStep.consider(new akyrion.thepredict.engine.distill.DistillStep.Request(
                    DistillMode.ASSIST, true, Optional.empty(), DistillSource.TABLE, "press", 1L, index, 10, false, false,
                    1, index, 30));
        }
    }

    private static void both(DistilledPolicy policy, CoverageIndex index, int sessions) {
        regime(new RegimeSelector(40, 6), sessions);
        choose(policy, sessions);
        cover(index, sessions);
    }

    private static void deltas(int sessions) {
        var key = new ChoiceStatKey("guardian", "r1:ALL", "fine", "t0", "window", 0, "outcome");
        for (var index = 0; index < sessions; index++) {
            new ChoiceStatDelta(key, 1, 1, 0L);
        }
    }

    private static long per(int sessions, Runnable work) {
        for (var index = 0; index < 2; index++) {
            work.run();
        }
        var repeat = 20;
        var start = System.nanoTime();
        for (var index = 0; index < repeat; index++) {
            work.run();
        }
        return (System.nanoTime() - start) / (repeat * (long) sessions);
    }

    private static String mark(long nanos, long budget) {
        return nanos <= budget ? "cumple" : "no cumple";
    }
}
