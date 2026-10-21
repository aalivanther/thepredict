package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.DecisionDispatcher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shadow distill is a field on the planned line. The schema version stays 1. */
class DistillShadowParityTest {
    @Test
    void aShadowNoteKeepsSchemaOneAndTheNextLineDropsIt() {
        var events = new ArrayList<RecordEvent>();
        var tap = new RecordTap(RecordLevel.DECISIONS, RecordBodies.NONE, "m29", Clock.systemUTC(), () -> 1L,
                () -> 2L, events::add);
        var encounter = new EncounterId(3);
        tap.noteDistill("{\"delegated\":false}", "2");
        tap.questionsPlanned(encounter, 1, List.of(), null, DecisionDispatcher.Outcome.SENT, null);
        var line = RecordLine.format(events.getFirst());
        assertTrue(line.contains("\"v\":1"));
        assertTrue(line.contains("\"distill\":{\"delegated\":false}"));
        assertTrue(line.contains("\"pattern_facts\":2"));
        tap.questionsPlanned(encounter, 2, List.of(), null, DecisionDispatcher.Outcome.SENT, null);
        assertFalse(events.get(1).fields().containsKey("distill"));
        assertEquals("1", RecordLine.format(events.get(1)).contains("\"v\":1") ? "1" : "");
    }
}
