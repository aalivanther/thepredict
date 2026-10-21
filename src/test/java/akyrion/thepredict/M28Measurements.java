package akyrion.thepredict;

import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.learn.CalibrationKey;
import akyrion.thepredict.domain.learn.CalibrationTarget;
import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.GroupBand;
import akyrion.thepredict.domain.learn.HealthBand3;
import akyrion.thepredict.domain.learn.LearnedValueConsideration;
import akyrion.thepredict.domain.learn.OptionsBand;
import akyrion.thepredict.domain.learn.QuestionKind;
import akyrion.thepredict.domain.learn.TrainingSource;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.domain.learn.VoiPolicy;
import akyrion.thepredict.domain.learn.ZoneBand;
import akyrion.thepredict.engine.learn.ConfidenceCalibrator;
import akyrion.thepredict.engine.learn.CreditLedger;
import akyrion.thepredict.engine.learn.ValueTable;
import akyrion.thepredict.engine.learn.ValueTableBuilder;

/**
 * Local timings for M28. A figure past its budget is published as {@code no cumple}. Nothing here is a hypothesis
 * decision and nothing calls a network.
 */
public final class M28Measurements {
    /** Wall time of the measured closes: a real week, so the rows are the ones the live close builds (L.3). */
    private static final long EPOCH_MS = 1_790_000_000_000L;

    private M28Measurements() {
    }

    /**
     * Writes the Learning table for 20, 100 and 200 sessions.
     *
     * @param report the report
     */
    public static void write(MeasurementReport report) {
        var table = table();
        var calibrator = ConfidenceCalibrator.identity("jev-1.13.0");
        var key = new CalibrationKey("jev-1.13.0", QuestionDetail.FULL, QuestionKind.NOW,
                CalibrationTarget.ACCEPTABLE, OptionsBand.FIVE_PLUS);
        report.line("## Learning (M28)");
        report.line("");
        report.line("| sesiones | ContextKey ns | lookup ns | learned ns | calibrador ns | voi ns | ventana ns | cierre ns | delta ns |");
        report.line("| --- | --- | --- | --- | --- | --- | --- | --- | --- |");
        for (var sessions : new int[] {20, 100, 200}) {
            report.line("| " + sessions + " | " + per(sessions, () -> keys(sessions))
                    + " | " + per(sessions, () -> lookups(table, sessions))
                    + " | " + per(sessions, () -> learned(sessions))
                    + " | " + per(sessions, () -> apply(calibrator, key, sessions))
                    + " | " + per(sessions, () -> voi(sessions))
                    + " | " + per(sessions, () -> windows(sessions))
                    + " | " + per(sessions, () -> close(sessions))
                    + " | " + per(sessions, () -> deltas(sessions)) + " |");
        }
        report.line("");
        var context = per(200, () -> keys(200));
        var contextAgain = per(200, () -> keys(200));
        var lookup = per(200, () -> lookups(table, 200));
        report.line("ContextKey.of " + context + " ns y " + contextAgain + " ns " + mark(Math.max(context, contextAgain),
                100) + "; lookup " + lookup + " ns " + mark(lookup, 1_500) + ".");
        report.line("Presupuesto de referencia: ContextKey ≤ 100 ns; lookup ≤ 1500 ns; calibrador ≤ 100 ns; "
                + "voi ≤ 1000 ns; ventana ≤ 200 ns; cierre ≤ 20000 ns.");
    }

    private static ValueTable table() {
        var builder = new ValueTableBuilder();
        for (var tactic = 0; tactic < 4; tactic++) {
            builder.add(1, tactic, 3, tactic, 0.2 * tactic, 0.01, 40, 1.0);
            builder.add(1, tactic, 2, tactic, 0.1, 0.01, 40, 1.0);
            builder.add(1, tactic, 1, 0L, 0.05, 0.01, 40, 1.0);
            builder.add(0, tactic, 0, 0L, 0.0, 0.01, 40, 1.0);
        }
        return builder.build();
    }

    private static void keys(int sessions) {
        for (var index = 0; index < sessions; index++) {
            ContextKey.of(index & 3, HealthBand3.HIGH, HealthBand3.MID, false, ZoneBand.OPEN, GroupBand.SOLO, 0);
        }
    }

    private static void lookups(ValueTable table, int sessions) {
        for (var index = 0; index < sessions; index++) {
            for (var tactic = 0; tactic < 4; tactic++) {
                table.lookup(1, tactic, tactic, tactic, 20);
            }
        }
    }

    private static void learned(int sessions) {
        var values = new int[] {100, 400, 700, 900};
        var usable = new boolean[] {true, true, true, true};
        for (var index = 0; index < sessions; index++) {
            LearnedValueConsideration.factors(values, usable, 20);
        }
    }

    private static void apply(ConfidenceCalibrator calibrator, CalibrationKey key, int sessions) {
        for (var index = 0; index < sessions; index++) {
            calibrator.apply(key, "jev-1.13.0", 0.45);
        }
    }

    private static void voi(int sessions) {
        for (var index = 0; index < sessions; index++) {
            VoiPolicy.evaluate(true, "shadow", true, 1.0, 0.01, 0.01, 80, 80, 0.90, 0.0, 0.02, 0, 2, false);
        }
    }

    private static void windows(int sessions) {
        var ledger = new CreditLedger();
        for (var index = 0; index < sessions; index++) {
            ledger.onWindowClosed("press", "v2c:MELEE:mob:NONE", 1.0, 0.0, false, false, TrainingSource.SIM);
        }
    }

    private static void close(int sessions) {
        var ledger = new CreditLedger();
        ledger.onWindowClosed("press", "v2c:MELEE:mob:NONE", 1.0, 0.0, false, false, TrainingSource.SIM);
        for (var index = 0; index < sessions; index++) {
            ledger.onEncounterClosed("profile", 1.0, EPOCH_MS);
            ledger.onWindowClosed("press", "v2c:MELEE:mob:NONE", 1.0, 0.0, false, false, TrainingSource.SIM);
        }
    }

    private static void deltas(int sessions) {
        var key = new ValueStatKey("profile", "press", "v2c:MELEE:mob:NONE", "sim", "r1", 1);
        for (var index = 0; index < sessions; index++) {
            new ValueStatDelta(key, 1, 0.1, 0.0, 0.1, 0.1, 0.0, 0, 0, 0L);
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
