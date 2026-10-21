package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static akyrion.thepredict.domain.DomainFixtures.choice;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticTest {
    @Test
    void prioritiesZeroAndOneHundredAreAccepted() {
        assertEquals(0, new Tactic(tactic("hold_ground"), Movement.HOLD, Optional.empty(), Optional.empty(), 0).priority());
        assertEquals(100, new Tactic(tactic("hold_ground"), Movement.HOLD, Optional.empty(), Optional.empty(), 100).priority());
    }

    @Test
    void prioritiesOutsideTheRangeAreRejected() {
        var below = assertThrows(IllegalArgumentException.class,
                () -> new Tactic(tactic("hold_ground"), Movement.HOLD, Optional.empty(), Optional.empty(), -1));
        assertTrue(below.getMessage().contains("priority"));
        assertThrows(IllegalArgumentException.class,
                () -> new Tactic(tactic("hold_ground"), Movement.HOLD, Optional.empty(), Optional.empty(), 101));
    }

    @Test
    void flankRequiresAChoice() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new Tactic(tactic("go_around"), Movement.FLANK, Optional.empty(), Optional.empty(), 50));
        assertTrue(error.getMessage().contains("choice"));
        assertEquals(Optional.of(choice("strike")),
                new Tactic(tactic("go_around"), Movement.FLANK, Optional.of(choice("strike")), Optional.empty(), 50).choice());
    }

    @Test
    void blankDescriptionIsRejected() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new Tactic(tactic("hold_ground"), Movement.HOLD, Optional.empty(), Optional.of(" "), 10));
        assertTrue(error.getMessage().contains("description"));
    }
}
