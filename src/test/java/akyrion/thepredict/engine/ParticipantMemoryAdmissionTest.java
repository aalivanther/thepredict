package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.MemoryAdmissionCause;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.MemoryEntryState;
import akyrion.thepredict.domain.MemoryFact;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantMemoryAdmissionTest {
    @Test
    void exemptRecordAndNotLoadedStayOutOfTheFight() {
        var settings = MemorySettings.DEFAULT;
        var counters = new MemoryCounters();
        var subject = new UUID(1, 1);
        var exempt = MemoryAdmissionPolicy.admit(MemoryMode.FULL, true, MemoryEntryState.LOADED, new byte[0], false,
                false, "boss", false, 4, 1, settings, subject, counters, DifficultySection.SOLO, PartyBand.ONE);
        assertEquals(MemoryAdmissionCause.EXEMPT, exempt.cause());
        var record = MemoryAdmissionPolicy.admit(MemoryMode.RECORD, false, MemoryEntryState.LOADED, new byte[0], false,
                false, "boss", false, 4, 1, settings, subject, counters, DifficultySection.SOLO, PartyBand.ONE);
        assertEquals(MemoryAdmissionCause.MODE_RECORD, record.cause());
        assertTrue(record.fact().isEmpty());
        var missing = MemoryAdmissionPolicy.admit(MemoryMode.FULL, false, MemoryEntryState.PENDING, new byte[0], false,
                false, "boss", false, 4, 1, settings, subject, counters, DifficultySection.SOLO, PartyBand.ONE);
        assertEquals(MemoryAdmissionCause.NOT_LOADED, missing.cause());
        assertEquals(1, counters.get(MemoryCounters.Kind.NOT_LOADED_AT_ADMISSION));
        var capped = MemoryAdmissionPolicy.cap(List.of(Optional.of(MemoryFact.LOST_LAST), Optional.of(MemoryFact.BEAT_BEFORE),
                Optional.empty()), 1);
        assertTrue(capped.get(0).isPresent());
        assertTrue(capped.get(1).isEmpty());
    }
}
