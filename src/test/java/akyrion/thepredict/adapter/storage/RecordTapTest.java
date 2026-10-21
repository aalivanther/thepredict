package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.EncounterClosed;
import akyrion.thepredict.engine.SessionMetrics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordTapTest {
    @Test
    void offBuildsNothingAndDecisionsOmitsFullTypesAndHidesADiagnosticToken() {
        var off = events(RecordLevel.OFF, RecordBodies.HASH);
        full(off);
        assertTrue(off.events.isEmpty());
        var decisions = events(RecordLevel.DECISIONS, RecordBodies.ALL);
        full(decisions);
        decisions.tap.questionsPlanned(new EncounterId(3), 2, List.of(),
                new akyrion.thepredict.engine.Preparation.Invalid("Authorization: Bearer secret-value-99 refused here"),
                akyrion.thepredict.engine.DecisionDispatcher.Outcome.PREPARE_FAILED, null);
        decisions.tap.requestBody(new EncounterId(3), 1, "{\"model\":\"jev-1.13.0\"}");
        var types = decisions.events.stream().map(RecordEvent::type).toList();
        assertTrue(types.contains("planned"));
        assertTrue(types.contains("body"));
        assertTrue(types.stream().noneMatch(type -> type.equals("cast") || type.equals("threat")
                || type.equals("reaction") || type.equals("micro")));
        var text = decisions.events.stream().map(RecordLine::format).toList().toString();
        assertTrue(!text.contains("secret-value") && !text.contains("Authorization"), text);
        assertTrue(text.contains("refused here"), "the rest of a diagnostic stays");
    }

    @Test
    void aBodyIsStoredAsItWasSentAndMatchesItsHash() {
        var sink = events(RecordLevel.DECISIONS, RecordBodies.ALL);
        var body = "{\"model\":\"jev-1.13.0\",\"state\":{\"encounter\":{\"objective\":"
                + "\"Protect the standard bearer and defeat intruders\"}}}";
        sink.tap.requestBody(new EncounterId(3), 1, body);
        var line = com.google.gson.JsonParser.parseString(RecordLine.format(sink.events.getLast())).getAsJsonObject();
        assertEquals(body, line.get("body").getAsString());
        assertEquals(RunIds.bodySha256(body), line.get("body_sha256").getAsString());
        assertEquals(body.length(), line.get("chars").getAsInt());
    }

    @Test
    void aRepeatedDedupBodyIsOneLineOnTheBodySink() {
        var events = new ArrayList<RecordEvent>();
        var bodies = new ArrayList<RecordEvent>();
        var tap = new RecordTap(RecordLevel.DECISIONS, RecordBodies.DEDUP, "20261006T120000Z-0a1b", Clock.systemUTC(),
                () -> 1L, () -> 2L, events::add, bodies::add);
        tap.requestBody(new EncounterId(3), 1, "body-payload-z9xq");
        tap.requestBody(new EncounterId(3), 2, "body-payload-z9xq");
        assertEquals(2L, events.stream().filter(event -> event.type().equals("body_ref")).count());
        assertEquals(0L, events.stream().filter(event -> event.type().equals("body")).count());
        assertEquals(1, bodies.size());
        assertEquals("body", bodies.getFirst().type());
        assertTrue(bodies.getFirst().fields().values().toString().contains("body-payload-z9xq"));
    }

    @Test
    void aShadowHintIsOnlyAPlannedField() {
        var sink = events(RecordLevel.DECISIONS, RecordBodies.HASH);
        var encounter = new EncounterId(3);
        sink.tap.noteAnnotation("would_skip", "keep_range has worked best here");
        sink.tap.questionsPlanned(encounter, 1, List.of(), null,
                akyrion.thepredict.engine.DecisionDispatcher.Outcome.SENT, null);
        assertEquals("\"would_skip\"", sink.events.getFirst().fields().get("voi"));
        assertEquals("\"keep_range has worked best here\"", sink.events.getFirst().fields().get("jev_hint"));
        sink.tap.questionsPlanned(encounter, 2, List.of(), null,
                akyrion.thepredict.engine.DecisionDispatcher.Outcome.SENT, null);
        assertFalse(sink.events.get(1).fields().containsKey("voi"));
        assertFalse(sink.events.get(1).fields().containsKey("jev_hint"));
    }

    private static void full(Sink sink) {
        var encounter = new EncounterId(3);
        var mob = new MobKey(new UUID(1, 2), 0);
        sink.tap.questionsPlanned(encounter, 1, List.of(), null, akyrion.thepredict.engine.DecisionDispatcher.Outcome.SENT,
                null);
        sink.tap.castAttempted(mob, new TacticId("keep_distance"), new ChoiceId("hit"), ExecutionOutcome.CAST);
        sink.tap.threatChanged(mob, new UUID(3, 4), 1.0, "hit");
        sink.tap.reactionFired(mob, Reaction.EVADE_CRITICAL);
        sink.tap.microChanged(mob, MicroBehaviour.NATIVE, MicroBehaviour.ORBIT);
        sink.tap.encounterClosed(encounter, new EncounterClosed(encounter, new UUID(5, 6), 1, 4,
                SessionMetrics.EncounterEnd.COMBAT_ENDED, List.of(), Map.of(), Set.of(), 0, 0));
    }

    private static Sink events(RecordLevel level, RecordBodies bodies) {
        var events = new ArrayList<RecordEvent>();
        var tap = new RecordTap(level, bodies, "20261006T120000Z-0a1b", Clock.systemUTC(), () -> 1L,
                () -> 2L, events::add);
        return new Sink(tap, events);
    }

    private record Sink(RecordTap tap, List<RecordEvent> events) {
    }
}
