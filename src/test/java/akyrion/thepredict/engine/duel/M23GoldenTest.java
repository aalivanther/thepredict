package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Entity duels without stance, telegraph or poise sections stay on the frozen M22 slice (M23.0, errata G2).
 * The full matrix stays {@code golden/m20}. This test does not rewrite either file.
 */
class M23GoldenTest {
    private static final Path M23 = Path.of("src/test/resources/golden/m23/baseline-slice.tsv");

    @Test
    void entityDefaultsMatchTheFrozenSlice() throws Exception {
        var rows = rows();
        assertEquals(Files.readAllLines(M22BaselineTest.GOLDEN), rows, "m22 slice");
        assertEquals(Files.readAllLines(M23), rows, "m23 slice");
    }

    private static List<String> rows() {
        var lines = new ArrayList<String>();
        lines.add("arm\tmode\tseed\twinner\tticks\tsha256");
        for (var mode : List.of(new DuelSimulator.Mode.None(), new DuelSimulator.Mode.Oracle(0.85, 85L))) {
            var label = mode instanceof DuelSimulator.Mode.None ? "NONE" : "ORACLE";
            var simulator = new DuelSimulator(mode);
            for (var arm : DuelSimulator.Arm.values()) {
                var result = simulator.round(arm, 1L, false, DuelSimulator.Options.PLAIN);
                lines.add(arm.name() + "\t" + label + "\t1\t" + result.winner().name() + "\t" + result.ticks()
                        + "\t" + result.eventDigest());
            }
        }
        return lines;
    }
}
