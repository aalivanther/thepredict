package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A small frozen slice of {@link DuelSimulator#round} (M22.0, errata G2) and the committed 200-seed matrix.
 */
class M22BaselineTest {
    static final Path GOLDEN = Path.of("src/test/resources/golden/m22/baseline-slice.tsv");
    static final Path MATRIX = Path.of("src/test/resources/golden/m22/baseline-matrix.tsv");

    @Test
    void theSliceMatchesTheFrozenBytes() throws Exception {
        assertEquals(Files.readAllLines(GOLDEN), rows(), "baseline-slice.tsv");
    }

    @Test
    void theMatrixHasSixteenHundredRowsAndAgreesOnSeedOne() throws Exception {
        var lines = Files.readAllLines(MATRIX);
        assertEquals(1601, lines.size());
        assertEquals(BaselineMatrix.HEADER, lines.getFirst());
        var spot = BaselineMatrix.rows(1, 1);
        for (var line : spot) {
            if (line.equals(BaselineMatrix.HEADER)) {
                continue;
            }
            assertEquals(true, lines.contains(line), line);
        }
        var logs = new java.util.ArrayList<Double>();
        for (var index = 1; index < lines.size(); index++) {
            var ticks = Integer.parseInt(lines.get(index).split("\t")[4]);
            if (ticks > 0) {
                logs.add(Math.log(ticks));
            }
        }
        var mean = 0.0;
        for (var value : logs) {
            mean += value;
        }
        mean /= logs.size();
        var variance = 0.0;
        for (var value : logs) {
            var delta = value - mean;
            variance += delta * delta;
        }
        variance /= logs.size() - 1.0;
        var sigma = Math.sqrt(variance);
        var note = Path.of("build/reports/m22/baseline-sigma.txt");
        Files.createDirectories(note.getParent());
        Files.writeString(note, "rows " + (lines.size() - 1) + " sigmaLogTtk " + sigma + "\n");
        assertEquals(true, sigma > 0.0);
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
