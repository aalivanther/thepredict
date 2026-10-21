package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Frozen casts of the M18 simulator with M19 applied, recorded before the tick motor (M20.0, errata G2). One row is
 * one round: arms A–D, modes {@code NONE} and {@code ORACLE(0.85)}, seeds 1–200, unswapped. {@code CASTS} must keep
 * every row; the file is never rewritten by the suite.
 */
class M20GoldenTest {
    static final Path GOLDEN = Path.of("src/test/resources/golden/m20/casts-mode.tsv");
    private static final int SEEDS = 200;

    @Test
    void castsModeMatchesTheRecordTakenBeforeTheTickMotor() throws Exception {
        var rows = rows();
        if (Boolean.getBoolean("m20.writeGolden") || "1".equals(System.getenv("M20_WRITE_GOLDEN"))) {
            Files.createDirectories(GOLDEN.getParent());
            Files.write(GOLDEN, rows);
            return;
        }
        assertEquals(Files.readAllLines(GOLDEN), rows, "casts-mode.tsv");
    }

    private static List<String> rows() {
        var lines = new ArrayList<String>();
        lines.add("arm\tmode\tseed\twinner\tticks\tcasts\tsha256");
        for (var mode : List.of(new DuelSimulator.Mode.None(), new DuelSimulator.Mode.Oracle(0.85, 85L))) {
            var label = mode instanceof DuelSimulator.Mode.None ? "NONE" : "ORACLE";
            var simulator = new DuelSimulator(mode);
            for (var arm : DuelSimulator.Arm.values()) {
                for (var seed = 1; seed <= SEEDS; seed++) {
                    var result = simulator.round(arm, seed, false, DuelSimulator.Options.PLAIN);
                    lines.add(arm.name() + "\t" + label + "\t" + seed + "\t" + result.winner().name() + "\t"
                            + result.ticks() + "\t" + casts(result) + "\t" + result.eventDigest());
                }
            }
        }
        return lines;
    }

    private static String casts(DuelResult result) {
        var keys = new TreeSet<String>();
        keys.addAll(result.redCasts().keySet());
        keys.addAll(result.blueCasts().keySet());
        var text = new StringBuilder();
        for (var key : keys) {
            if (!text.isEmpty()) {
                text.append(',');
            }
            text.append(key).append(':').append(result.redCasts().getOrDefault(key, 0)).append(':')
                    .append(result.blueCasts().getOrDefault(key, 0));
        }
        return text.toString();
    }
}
