package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Off-mode fingerprint. The rows do not call the group step. */
class M25GoldenTest {
    @Test
    void offModeRowsStaySymmetricOnThreeVersusThree() throws Exception {
        var lines = Files.readAllLines(Path.of("src/test/resources/golden/m25/off-mode.csv"));
        assertEquals(6_400, lines.size());
        var draws = 0;
        var left = 0;
        var right = 0;
        for (var line : lines) {
            var parts = line.split(",");
            assertEquals(5, parts.length, line);
            if ("3v3".equals(parts[0])) {
                assertEquals("draw", parts[3], line);
                draws++;
            } else if ("left".equals(parts[3])) {
                left++;
            } else {
                right++;
            }
        }
        assertEquals(1_600, draws);
        assertEquals(left, right);
        var report = Path.of("build/reports/m25/off-mode.md");
        Files.createDirectories(report.getParent());
        var mirror = (left * 100.0) / (left + right);
        Files.writeString(report, """
                # M25 off-mode skirmish

                Rows: 6400. 3v3 draws: 1600. Mirror of the other rows: %.1f%%.
                The rows are a deterministic fingerprint with group.mode off. They do not run mob physics.
                59%% no-solo was not measured. Separation below 0.6 blocks is NO VERIFICADO (DT-72).
                """.formatted(mirror));
        assertTrue(mirror > 40.0 && mirror < 60.0);
    }
}
