package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MemoryAdmission;
import akyrion.thepredict.domain.MemoryAdmissionCause;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemoryUpdate;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.EncounterClosed;
import akyrion.thepredict.engine.SessionMetrics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRecordLinesTest {
    @Test
    void loadSeedUpdateAdminAndCloseCarryMemory() {
        var events = new ArrayList<RecordEvent>();
        var tap = new RecordTap(RecordLevel.FULL, RecordBodies.NONE, "run", Clock.systemUTC(), () -> 1L,
                () -> 2L, events::add);
        var subject = new UUID(1, 1);
        tap.memoryLoad(subject, "LOADED");
        tap.memoryAdmitted(new EncounterId(7), subject, new MemoryAdmission(MemoryAdmissionCause.COLD, 3,
                Optional.empty(), Optional.empty(), 0, 0, MemoryMode.RECORD));
        tap.memoryApplied(subject, new MemoryUpdate(1, true, 10, 12, 0, 0, 2));
        tap.memoryAdmin("reset", "reset");
        tap.noteMemoryDigests(2);
        tap.encounterClosed(new EncounterId(7), new EncounterClosed(new EncounterId(7), new UUID(0, 8), 1, 4,
                SessionMetrics.EncounterEnd.COMBAT_ENDED, List.of(), Map.of(), Set.of(), 0, 0));
        assertEquals("memory_load", events.get(0).type());
        assertEquals("memory_seed", events.get(1).type());
        assertEquals("memory_update", events.get(2).type());
        assertEquals("memory_admin", events.get(3).type());
        assertEquals("encounter_close", events.get(4).type());
        assertEquals("2", events.get(4).fields().get("memory_digests"));
        assertEquals(1, RecordReader.parse(List.of(RecordLine.format(events.get(1)))).size());
    }
}
