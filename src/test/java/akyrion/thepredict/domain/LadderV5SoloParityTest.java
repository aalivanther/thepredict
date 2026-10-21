package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LadderV5SoloParityTest {
    @Test
    void v5AddsOnlyExploitShareAndKeepsTheV4Weights() throws Exception {
        assertEquals("/difficulty/ladder-v5.json", PressureLadder.RESOURCE);
        assertTrue(PressureLadder.bundledMatches());
        var live = new String(PressureLadder.class.getResourceAsStream("/difficulty/ladder-v5.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var frozen = new String(LadderV5SoloParityTest.class.getResourceAsStream("/difficulty/ladder-v4.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(live.contains("\"k\": 0.12"));
        assertTrue(frozen.contains("\"k\": 0.12"));
        assertTrue(live.contains("exploitShare"));
        assertFalse(frozen.contains("exploitShare"));
        assertEquals(0.0, PressureLadder.levers(0, 0, 0, true).exploitShare());
        assertEquals(0.5, PressureLadder.levers(8, 0, 0, true).exploitShare());
        assertEquals(0.5, PressureLadder.bench(5).exploitShare());
    }
}
