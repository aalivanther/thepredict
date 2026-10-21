package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An admission never holds null, and a profile without memory facts keeps everything but the phrase. */
class MemoryAdmissionTest {
    @Test
    void nullComponentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new MemoryAdmission(null, 0, Optional.empty(),
                Optional.empty(), 0, 0.0, MemoryMode.FULL));
        assertThrows(NullPointerException.class, () -> new MemoryAdmission(MemoryAdmissionCause.COLD, 0, null,
                Optional.empty(), 0, 0.0, MemoryMode.FULL));
        assertThrows(NullPointerException.class, () -> new MemoryAdmission(MemoryAdmissionCause.COLD, 0,
                Optional.empty(), null, 0, 0.0, MemoryMode.FULL));
        assertThrows(NullPointerException.class, () -> new MemoryAdmission(MemoryAdmissionCause.COLD, 0,
                Optional.empty(), Optional.empty(), 0, 0.0, null));
    }

    @Test
    void withoutFactDropsOnlyThePhrase() {
        var admission = new MemoryAdmission(MemoryAdmissionCause.SEEDED, 5, Optional.of(PressureCause.IN_FIGHT),
                Optional.of(MemoryFact.LOST_LAST), 3, 7.5, MemoryMode.FULL);
        var bare = admission.withoutFact();
        assertTrue(bare.fact().isEmpty());
        assertEquals(new MemoryAdmission(MemoryAdmissionCause.SEEDED, 5, Optional.of(PressureCause.IN_FIGHT),
                Optional.empty(), 3, 7.5, MemoryMode.FULL), bare);
    }
}
