package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncounterEventTest {
    @Test
    void eventKeepsItsOptionalParticipantAndMob() {
        var event = new EncounterEvent(0, EventType.TOTEM_USED, Optional.of(new ParticipantAlias(1)),
                Optional.of(DomainFixtures.MOB));
        assertEquals(Optional.of(new ParticipantAlias(1)), event.participant());
    }

    @Test
    void negativeTickIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EncounterEvent(-1, EventType.RANGED_MISS, Optional.empty(), Optional.empty()));
    }
}
