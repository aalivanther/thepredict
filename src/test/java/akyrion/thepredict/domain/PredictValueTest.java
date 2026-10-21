package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PredictValueTest {
    @Test
    void parseIgnoresCaseAndSpacesIncludingIgnoresShield() {
        assertEquals(Set.of(PredictValue.IGNORES_SHIELD, PredictValue.MELEE_ATTACK),
                PredictValue.parse(List.of(" ignores_shield", "Melee_Attack ")));
    }

    @Test
    void parseRejectsUnknownAndEmptyLists() {
        assertThrows(IllegalArgumentException.class, () -> PredictValue.parse(List.of("FLYING")));
        assertThrows(IllegalArgumentException.class, () -> PredictValue.parse(List.of()));
    }
}
