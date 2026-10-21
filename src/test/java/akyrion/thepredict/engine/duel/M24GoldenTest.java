package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ZoneMode;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flat entity slice stays the M23 bytes (M24.0, errata G2 and G4). */
class M24GoldenTest {
    private static final Path M24 = Path.of("src/test/resources/golden/m24/baseline-slice.tsv");

    @Test
    void flatEntitiesMatchTheFrozenSlice() throws Exception {
        assertTrue(Files.isRegularFile(M24), "golden/m24/baseline-slice.tsv is missing");
        var off = rows(ZoneMode.OFF);
        var observe = rows(ZoneMode.OBSERVE);
        assertEquals(off, observe, "off and observe against entities");
        assertEquals(Files.readAllLines(M22BaselineTest.GOLDEN), off);
        assertEquals(Files.readAllLines(M24), off);
    }

    private static List<String> rows(ZoneMode zoneMode) {
        var lines = new ArrayList<String>();
        lines.add("arm\tmode\tseed\twinner\tticks\tsha256");
        var options = new DuelSimulator.Options(false, -1, zoneMode);
        for (var mode : List.of(new DuelSimulator.Mode.None(), new DuelSimulator.Mode.Oracle(0.85, 85L))) {
            var label = mode instanceof DuelSimulator.Mode.None ? "NONE" : "ORACLE";
            var simulator = new DuelSimulator(mode);
            for (var arm : DuelSimulator.Arm.values()) {
                var result = simulator.round(arm, 1L, false, options);
                lines.add(arm.name() + "\t" + label + "\t1\t" + result.winner().name() + "\t" + result.ticks()
                        + "\t" + result.eventDigest());
            }
        }
        return lines;
    }
}
