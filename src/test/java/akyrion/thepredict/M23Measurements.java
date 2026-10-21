package akyrion.thepredict;

import akyrion.thepredict.adapter.storage.JsonText;
import akyrion.thepredict.adapter.storage.RecordEvent;
import akyrion.thepredict.adapter.storage.RecordLine;
import akyrion.thepredict.domain.StanceMachine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/**
 * Local cost of one stance step per session (M23). The numbers are what this machine measured. Nothing leaves it.
 */
public final class M23Measurements {
    private M23Measurements() {
    }

    /**
     * Appends the Stances and telegraphs (M23) table for 20, 100 and 200 sessions and writes the baseline note.
     *
     * @param report the measurement report
     * @throws IOException if the baseline note cannot be written
     */
    public static void write(MeasurementReport report) throws IOException {
        report.line("");
        report.line("## Stances and telegraphs (M23)");
        report.line("");
        report.line("| Sesiones | p50 / p95 / p99 / max µs por tick |");
        report.line("|---|---|");
        var note = new StringBuilder();
        note.append("# Stances and telegraphs (M23)\n\n");
        note.append("Medido en local con dobles. Sesión de servidor pendiente. Sin llamadas de pago.\n\n");
        note.append("| Sesiones | p50 / p95 / p99 / max µs por tick |\n|---|---|\n");
        for (var sessions : new int[] {20, 100, 200}) {
            var row = "| " + sessions + " | " + MeasurementReport.micros(sample(sessions)) + " |";
            report.line(row);
            note.append(row).append('\n');
        }
        var line = "| telegraph | " + MeasurementReport.micros(telegraphLine()) + " |";
        report.line(line);
        note.append(line).append('\n');
        report.line("");
        report.line("Una pasada es un `StanceMachine.step` por sesión, sin aviso abierto. ");
        report.line("La fila `telegraph` es un `RecordLine.format` de una línea ya armada, en µs.");
        note.append("\nLa fila `telegraph` es un `RecordLine.format` de una línea ya armada, en µs. ");
        note.append("El presupuesto del plan es 1 µs por offer. Los defaults no se mueven.\n");
        var output = Path.of("build/reports/m23/baseline.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, note.toString());
    }

    private static long[] sample(int sessions) {
        var states = new long[sessions];
        var samples = new long[32];
        var out = new long[5];
        for (var sample = 0; sample < samples.length; sample++) {
            var started = System.nanoTime();
            for (var index = 0; index < sessions; index++) {
                StanceMachine.step(out, states[index], sample, 0L, Long.MAX_VALUE, 0, -1, false, true, false, 2, 20,
                        StanceMachine.EVENT_NONE, 0, 0);
                states[index] = out[0];
            }
            samples[sample] = System.nanoTime() - started;
        }
        return samples;
    }

    private static long[] telegraphLine() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote("00000000-0000-0000-0000-000000000017"));
        fields.put("profile", JsonText.quote("guardian"));
        fields.put("choice", JsonText.quote("slam"));
        fields.put("danger", JsonText.quote("SEVERE"));
        fields.put("answer", JsonText.quote("BLOCK"));
        fields.put("result", JsonText.quote("LANDED"));
        fields.put("end", JsonText.quote("RELEASED"));
        fields.put("short_windup", JsonText.quote("lead"));
        fields.put("rung", "8");
        var event = new RecordEvent("telegraph", "run", 1L, 2L, 3L, "", fields);
        var samples = new long[32];
        for (var sample = 0; sample < samples.length; sample++) {
            var started = System.nanoTime();
            RecordLine.format(event);
            samples[sample] = System.nanoTime() - started;
        }
        return samples;
    }
}
