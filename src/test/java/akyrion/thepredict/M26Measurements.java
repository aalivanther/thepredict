package akyrion.thepredict;

import akyrion.thepredict.adapter.jev.JevMeasurements;
import akyrion.thepredict.engine.EnemyModel;
import akyrion.thepredict.engine.PatternStepTiming;

import java.util.UUID;

/**
 * Local timings for M26. No server and no network. The live object size is not measured: JOL is not on the
 * compile classpath, so that cell stays {@code no medido}.
 */
public final class M26Measurements {
    private M26Measurements() {
    }

    /**
     * Writes the Patterns table, the measured percentiles and the patrones column.
     *
     * @param report the report
     */
    public static void write(MeasurementReport report) {
        report.line("## Patterns (M26)");
        report.line("");
        report.line("| encuentros | participantes | array bytes | objeto |");
        report.line("| --- | --- | --- | --- |");
        var one = new EnemyModel(new UUID(0, 1), 32, 2);
        for (var encounters : new int[] {1, 5, 10}) {
            for (var participants : new int[] {1, 4, 16}) {
                report.line("| " + encounters + " | " + participants + " | "
                        + (one.arrayBytes() * encounters * participants) + " | no medido |");
            }
        }
        report.line("");
        var timing = PatternStepTiming.sample();
        report.line("paso p50 " + timing[0] + " ns p99 " + timing[1] + " ns");
        report.line("enemyKinematics p50 " + timing[2] + " ns p99 " + timing[3] + " ns");
        report.line("EnemyWatch p50 " + timing[4] + " ns p99 " + timing[5] + " ns");
        report.line("");
        report.line("Columna patrones (hechos 0, 1, 2, 4) caracteres " + JevMeasurements.patternChars(0) + ", "
                + JevMeasurements.patternChars(1) + ", " + JevMeasurements.patternChars(2) + ", "
                + JevMeasurements.patternChars(4) + " tokens " + JevMeasurements.patternTokens(0) + ", "
                + JevMeasurements.patternTokens(1) + ", " + JevMeasurements.patternTokens(2) + ", "
                + JevMeasurements.patternTokens(4) + ".");
        report.line("");
    }
}
