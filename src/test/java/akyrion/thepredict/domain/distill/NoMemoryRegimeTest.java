package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoMemoryRegimeTest {
    @Test
    void aRegimeKeyRefusesMemoryAndFightRegimeHasNoMemoryExtra() {
        var regime = FightRegime.of(1, 5, 10, 40);
        assertEquals(EnemyBand.ONE, regime.band());
        assertEquals(FightPhase.OPENING, regime.phase());
        assertEquals(List.of(), regime.extras());
        assertEquals("r1:ONE:OPENING", regime.key().text());
        assertThrows(IllegalArgumentException.class,
                () -> RegimeKey.of(EnemyBand.ONE, FightPhase.OPENING, List.of("memory")));
    }
}
