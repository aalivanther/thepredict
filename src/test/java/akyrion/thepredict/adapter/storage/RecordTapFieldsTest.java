package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.AnswerCacheEvent;
import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.EnemyProfileSummary;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.OptionPruning;
import akyrion.thepredict.domain.PatternCause;
import akyrion.thepredict.domain.PatternChange;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.PruneReason;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.EncounterClosed;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.SessionMetrics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-08, JS-10 and JS-22: the lines carry what their arguments know, in a fixed order, and dedup stays bounded. */
class RecordTapFieldsTest {
    private static final MobKey MOB = new MobKey(new UUID(1, 2), 0);
    private static final EncounterId ENCOUNTER = new EncounterId(7);

    @Test
    void aResultCarriesItsAnswersAndFitsSoItCanBeReplayed() {
        var events = new ArrayList<RecordEvent>();
        var tap = tap(RecordLevel.DECISIONS, events);
        var answers = Map.of(QuestionKey.of(10), new Answer(Map.of(new TacticId("strike"), 0.25,
                new TacticId("guard"), 0.75), 0.6), QuestionKey.of(2), new Answer(Map.of(new TacticId("strike"), 0.9,
                new TacticId("guard"), 0.1), 0.9));
        tap.resultReceived(ENCOUNTER, 3, new DecisionResult.Answered(answers, "jev-1.13.0", 900,
                Map.of(QuestionKey.of(5), 0.4)), 120_000_000L);
        var line = json(events.getLast());
        var answered = line.getAsJsonObject("answers");
        assertEquals(List.of("q2", "q10"), List.copyOf(answered.keySet()));
        assertEquals("guard", answered.getAsJsonObject("q10").get("choice").getAsString());
        assertEquals(0.75, answered.getAsJsonObject("q10").getAsJsonObject("probabilities").get("guard").getAsDouble());
        assertEquals(0.6, answered.getAsJsonObject("q10").get("confidence").getAsDouble());
        assertEquals(0.4, line.getAsJsonObject("fits").get("q5").getAsDouble());
    }

    @Test
    void aPlannedLineNamesItsQuestionsAndWhatStayedLocal() {
        var events = new ArrayList<RecordEvent>();
        var tap = tap(RecordLevel.DECISIONS, events);
        var question = new QuestionSpec(QuestionKey.of(0), MOB, Situation.NOW, List.of(new TacticId("strike"),
                new TacticId("guard")));
        tap.questionsPlanned(ENCOUNTER, 4, List.of(question), new Preparation.NothingToAsk(Set.of(MOB)),
                DecisionDispatcher.Outcome.NOTHING_TO_ASK, null);
        var line = json(events.getLast());
        var planned = line.getAsJsonArray("questions").get(0).getAsJsonObject();
        assertEquals("q0", planned.get("key").getAsString());
        assertEquals(MOB.entityId().toString(), planned.get("mob").getAsString());
        assertEquals("NOW", planned.get("situation").getAsString());
        assertEquals(2, planned.getAsJsonArray("options").size());
        assertEquals("q0", line.getAsJsonArray("dropped_questions").get(0).getAsString());
        assertEquals(MOB.entityId().toString(), line.getAsJsonArray("local_mobs").get(0).getAsString());
        assertEquals(0, line.getAsJsonObject("sent_options").size());
    }

    @Test
    void outcomeAndPatternLinesCarryTheirArgumentsAndNoMadeUpZero() {
        var events = new ArrayList<RecordEvent>();
        var tap = tap(RecordLevel.FULL, events);
        tap.outcomeClosed(MOB, new OutcomeWindow.Summary(ControlLevel.JEV, new TacticId("strike"), Movement.APPROACH,
                10, 50, 4.5, 1.5, true, 3, 2, false, true, 0.7, 1, Optional.empty(), Optional.empty()));
        var outcome = json(events.getLast());
        assertEquals(4.5, outcome.get("dealt").getAsDouble());
        assertEquals(1.5, outcome.get("taken").getAsDouble());
        assertEquals(3, outcome.get("casts").getAsInt());
        assertEquals(2, outcome.get("hits").getAsInt());
        assertTrue(outcome.get("target_died").getAsBoolean());
        assertTrue(outcome.get("reactions").isJsonNull() && outcome.get("micro_ticks").isJsonNull());
        var player = new UUID(3, 4);
        tap.patternChanged(ENCOUNTER, player, new PatternChange(player, EnemyPattern.SPAMS_CLICKS,
                PatternCause.ENTER, PatternState.ACTIVE));
        var pattern = json(events.getLast());
        assertTrue(pattern.get("trials").isJsonNull() && pattern.get("participant_kind").isJsonNull());
    }

    @Test
    void castThreatPruneAndCacheLinesCarryTheirArguments() {
        var events = new ArrayList<RecordEvent>();
        var tap = tap(RecordLevel.FULL, events);
        tap.castAttempted(MOB, new TacticId("strike"), new ChoiceId("slash"), ExecutionOutcome.CAST, true);
        assertEquals("slash", json(events.getLast()).get("choice").getAsString());
        assertTrue(json(events.getLast()).get("ready_cast").getAsBoolean());
        var player = new UUID(3, 4);
        tap.threatChanged(MOB, player, 2.0, "hit");
        assertEquals(player.toString(), json(events.getLast()).get("participant").getAsString());
        tap.optionsPruned(ENCOUNTER, 5, MOB, new OptionPruning.PruneResult(List.of(new TacticId("guard")),
                Map.of(new TacticId("strike"), PruneReason.values()[0]), true));
        var pruned = json(events.getLast());
        assertEquals(PruneReason.values()[0].name(), pruned.getAsJsonObject("removed").get("strike").getAsString());
        assertTrue(pruned.get("undone").getAsBoolean());
        tap.answerCache(ENCOUNTER, 6, new AnswerCacheEvent(AnswerCacheMode.values()[0], true, 40, true, 0.05, 900));
        var cache = json(events.getLast());
        assertEquals(40, cache.get("age_ticks").getAsLong());
        assertTrue(cache.get("agree_best").getAsBoolean());
        assertEquals(0.05, cache.get("confidence_delta").getAsDouble());
    }

    @Test
    void patternSummariesAreCountedForTheirOwnEncounter() {
        var events = new ArrayList<RecordEvent>();
        var tap = tap(RecordLevel.DECISIONS, events);
        var other = new EncounterId(8);
        var player = new UUID(3, 4);
        tap.patternSummary(ENCOUNTER, player, EnemyProfileSummary.empty());
        tap.patternSummary(other, player, EnemyProfileSummary.empty());
        tap.patternSummary(ENCOUNTER, player, EnemyProfileSummary.empty());
        tap.encounterClosed(other, closed(other));
        assertEquals(1, json(events.getLast()).get("pattern_summaries").getAsInt());
        tap.encounterClosed(ENCOUNTER, closed(ENCOUNTER));
        assertEquals(2, json(events.getLast()).get("pattern_summaries").getAsInt());
        assertTrue(json(events.getLast()).get("memory_digests").isJsonNull());
        assertEquals(0, json(events.getLast()).get("mobs_died").getAsInt());
    }

    @Test
    void theRunStartNamesTheRunWithoutAKeyShapedModel() {
        var events = new ArrayList<RecordEvent>();
        tap(RecordLevel.DECISIONS, events).runStart("0.0.1", "sk-SECRETVALUE_123", "abc", QuestionDetail.FULL,
                AnswerCacheMode.OFF);
        var line = json(events.getLast());
        assertEquals("run_start", line.get("type").getAsString());
        assertEquals("0.0.1", line.get("plugin_version").getAsString());
        assertEquals(RunIds.CUSTOM_MODEL, line.get("model").getAsString());
        assertEquals("decisions", line.get("records_level").getAsString());
        assertTrue(!RecordLine.format(events.getLast()).contains("SECRETVALUE"));
    }

    /** JS-08 point 1.5: the run names the question detail and the answer cache it was configured with (M21.1). */
    @Test
    void theRunStartCarriesTheConfiguredQuestionDetailAndAnswerCacheInTheSchemaOrder() {
        var events = new ArrayList<RecordEvent>();
        tap(RecordLevel.DECISIONS, events).runStart("0.0.1", "jev-1.13.0", "abc", QuestionDetail.COMPACT,
                AnswerCacheMode.SHADOW);
        var line = json(events.getLast());
        assertEquals("compact", line.get("question_detail").getAsString());
        assertEquals("shadow", line.get("answer_cache").getAsString());
        assertEquals(List.of("plugin_version", "model", "question_detail", "config_sha256", "records_level", "bodies",
                "answer_cache"), List.copyOf(events.getLast().fields().keySet()));
    }

    @Test
    void aDedupBodyIsWrittenAgainOnANewDayAndTheRememberedHashesStayBounded() {
        var clock = new MovableClock(Instant.parse("2026-10-14T23:00:00Z"));
        var bodies = new ArrayList<RecordEvent>();
        var tap = new RecordTap(RecordLevel.DECISIONS, RecordBodies.DEDUP, "run", clock, () -> 1L, () -> 2L,
                event -> {
                }, bodies::add);
        tap.requestBody(ENCOUNTER, 1, "same body");
        tap.requestBody(ENCOUNTER, 2, "same body");
        assertEquals(1, bodies.size());
        clock.now = Instant.parse("2026-10-15T00:30:00Z");
        tap.requestBody(ENCOUNTER, 3, "same body");
        assertEquals(2, bodies.size());
        for (var index = 0; index < RecordTap.MAX_SEEN_BODIES; index++) {
            tap.requestBody(ENCOUNTER, 4, "body " + index);
        }
        bodies.clear();
        tap.requestBody(ENCOUNTER, 5, "same body");
        assertEquals(1, bodies.size(), "the oldest hash was forgotten, so the body is written again");
    }

    @Test
    void theFieldsOfAnEventKeepTheirOrder() {
        var fields = new LinkedHashMap<String, String>();
        for (var name : List.of("zeta", "alpha", "mid", "beta", "omega", "gamma", "delta", "kappa")) {
            fields.put(name, "1");
        }
        var event = new RecordEvent("planned", "run", 1L, 2L, 3L, "", fields);
        assertEquals(List.copyOf(fields.keySet()), List.copyOf(event.fields().keySet()));
        assertTrue(RecordLine.format(event).endsWith("\"zeta\":1,\"alpha\":1,\"mid\":1,\"beta\":1,\"omega\":1,"
                + "\"gamma\":1,\"delta\":1,\"kappa\":1}"));
    }

    private static EncounterClosed closed(EncounterId id) {
        return new EncounterClosed(id, new UUID(5, 6), 1, 4, SessionMetrics.EncounterEnd.COMBAT_ENDED, List.of(),
                Map.of(), Set.of(), 0, 0);
    }

    private static RecordTap tap(RecordLevel level, List<RecordEvent> events) {
        return new RecordTap(level, RecordBodies.NONE, "run", Clock.systemUTC(), () -> 1L, () -> 2L, events::add);
    }

    private static JsonObject json(RecordEvent event) {
        return JsonParser.parseString(RecordLine.format(event)).getAsJsonObject();
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
