package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The smoke run of {@code effectivenessBench} inside {@code check}: the duel pack, a handful of seeds and tiny
 * searches. Two runs write the same tables byte for byte, and the report has its verdict, its gates, its rivals, its
 * cells, their damage and conduct, the sensitivities and its limits. The full run is the Gradle task, outside {@code check}.
 */
class EffectivenessBenchSmokeTest {
    @TempDir
    Path root;

    @Test
    void twoSmokeRunsWriteTheSameTablesAndAFullReport() throws IOException {
        var first = run(root.resolve("first"));
        var second = run(root.resolve("second"));
        for (var table : List.of("celdas.tsv", "puertas.tsv", "rivales.tsv", "jev.tsv", "busqueda.tsv", "brazos.tsv",
                "conducta.tsv", "danio.tsv", "habilidades.tsv")) {
            assertEquals(Files.readString(first.resolve(table)), Files.readString(second.resolve(table)), table);
        }
        var report = Files.readString(first.resolve("banco.md"));
        for (var section : List.of("## Veredicto", "## Pregunta y criterio", "## Método", "## Puertas de validez",
                "## Rivales", "## Celdas", "## Daño por minuto", "## Conducta", "## Sensibilidad a Jev",
                "## Sensibilidades", "## Matriz A–D", "## Límites", "## Reproducción")) {
            assertTrue(report.contains(section), section);
        }
        assertTrue(report.contains("defaults de entrega, sin Jev:"));
        assertEquals(1 + 4 + 4 + 4, Files.readAllLines(first.resolve("celdas.tsv")).size(),
                "a header, four cells, the four with retreat_and_heal on APPROACH and the four with Approach.Speed 1.2");
        assertTrue(Files.isRegularFile(first.resolve("clasicos/K1-legal-equal.yml")));
        assertTrue(Files.readString(first.resolve("congelados/" + BenchFrozen.ADVERSARIAL)).contains("profile: '"
                + BenchProfiles.sha256(Kit.K1, BenchProfiles.Timing.DEFAULTS) + "'"));
    }

    private Path run(Path output) throws IOException {
        var outcome = EffectivenessBench.run(BenchSettings.quick(root.resolve("frozen")), output);
        BenchReport.write(outcome, output);
        return output;
    }
}
