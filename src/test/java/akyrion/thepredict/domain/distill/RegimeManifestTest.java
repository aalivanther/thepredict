package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegimeManifestTest {
    @Test
    void theFirstMatchingRuleWinsAndTheLastKeyIsAll() {
        var manifest = new RegimeManifest(List.of(
                new RegimeManifest.Rule(1 << EnemyBand.MANY.ordinal(), 1 << FightPhase.LATE.ordinal(), "r1:MANY:LATE",
                        "abc"),
                new RegimeManifest.Rule(0xff, 0xff, "r1:ALL", "all")));
        assertEquals("r1:MANY:LATE", manifest.match(EnemyBand.MANY, FightPhase.LATE));
        assertEquals("r1:ALL", manifest.match(EnemyBand.ONE, FightPhase.OPENING));
        assertTrue(manifest.within(4));
        assertThrows(IllegalArgumentException.class, () -> new RegimeManifest(List.of(
                new RegimeManifest.Rule(1, 1, "r1:ONE", "abc"))));
        assertThrows(IllegalArgumentException.class,
                () -> RegimeKey.of(EnemyBand.ONE, FightPhase.MID, List.of("memory")));
    }
}
