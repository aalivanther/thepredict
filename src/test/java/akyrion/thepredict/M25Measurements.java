package akyrion.thepredict;

import akyrion.thepredict.engine.GroupStepTiming;

/**
 * Local timings of the group step (M25). No server and no network. The character column lives in
 * {@link akyrion.thepredict.adapter.jev.JevMeasurements}.
 */
public final class M25Measurements {
    private M25Measurements() {
    }

    /**
     * Writes the microseconds of one group step.
     *
     * @param report the report
     */
    public static void write(MeasurementReport report) {
        report.line("## Paso group (M25)");
        report.line("");
        report.line("| encuentros | miembros | µs |");
        report.line("| --- | --- | --- |");
        var micros = GroupStepTiming.micros();
        var encounters = GroupStepTiming.encounters();
        var members = GroupStepTiming.members();
        for (var row = 0; row < encounters.length; row++) {
            for (var column = 0; column < members.length; column++) {
                report.line("| " + encounters[row] + " | " + members[column] + " | " + micros[row][column] + " |");
            }
        }
        report.line("");
        report.line("Medido en local, sin m13Measure. La columna de caracteres está en JevMeasurements.");
        report.line("");
    }
}
