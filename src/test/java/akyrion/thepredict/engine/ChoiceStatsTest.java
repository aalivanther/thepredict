package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AnswerSource;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.distill.ChoiceStatDelta;
import akyrion.thepredict.domain.distill.ChoiceStatKey;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.LabelRule;
import akyrion.thepredict.engine.distill.ChoiceIntake;
import akyrion.thepredict.engine.distill.TeachingBoard;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTableBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Choice rows leave only through a closed teaching window. A live or cached answer writes nothing. */
class ChoiceStatsTest {
    @Test
    void twoMobsKeepSeparateWindowsAndALiveAnswerWritesNothing() {
        var fixture = new EngineFixtures.Sessions();
        var first = fixture.open(1);
        var second = fixture.open(2);
        var encounter = fixture.encounters.assign(first, EngineFixtures.WORLD, EngineFixtures.player(1),
                EngineFixtures.near());
        fixture.encounters.assign(second, EngineFixtures.WORLD, EngineFixtures.player(1), key -> true);
        var names = List.of("alpha", "bravo", "charlie", "delta");
        var codes = new int[names.size()];
        for (var index = 0; index < names.size(); index++) {
            codes[index] = LearnedLocalPick.tacticCode(names.get(index));
            for (var earlier = 0; earlier < index; earlier++) {
                assertFalse(codes[index] == codes[earlier]);
            }
        }
        var builder = new ValueTableBuilder();
        var means = new double[] {0.1, 0.2, 0.3, 1.0};
        for (var index = 0; index < names.size(); index++) {
            builder.add(1, codes[index], 3, 7L, means[index], 0.01, 40, 1.0);
        }
        var table = builder.build();
        first.learning().activate(table, 1, 7L);
        second.learning().activate(table, 1, 7L);
        arm(first);
        arm(second);
        var board = new TeachingBoard();
        var rows = new ArrayList<ChoiceStatDelta>();
        board.useSink(rows::addAll);
        var plan = plan(encounter, names);
        CaptureGate.apply(encounter, plan, 4L, LearningSettings.DEFAULT, DecisionTap.NONE, DistillSettings.DEFAULT,
                board);
        assertTrue(board.noted(first.key().entityId().toString()));
        assertTrue(board.noted(second.key().entityId().toString()));
        var live = new ArrayList<ChoiceStatDelta>();
        var reading = intake();
        reading.useTeaching(board, live::addAll);
        reading.acceptNow(completed(encounter, AnswerSource.LIVE), 4L, 1L);
        assertTrue(live.isEmpty());
        assertTrue(board.noted(first.key().entityId().toString()));
        assertTrue(board.noted(second.key().entityId().toString()));
        WindowCredit.record(first, summary("delta"), false, 0L);
        WindowCredit.record(second, summary("delta"), false, 0L);
        assertEquals(8, rows.size());
        arm(first);
        CaptureGate.apply(encounter, plan(encounter, names), 8L, LearningSettings.DEFAULT, DecisionTap.NONE,
                DistillSettings.DEFAULT, board);
        WindowCredit.record(first, summary("delta"), false, 0L);
        assertEquals(12, rows.size());
        assertEquals(12, rows.stream().mapToInt(ChoiceStatDelta::offered).sum());
        assertEquals(3, rows.stream().mapToInt(ChoiceStatDelta::chosen).sum());
        assertTrue(rows.stream().filter(row -> row.chosen() == 1).allMatch(row -> "delta".equals(row.key().tactic())));
        assertTrue(rows.stream().allMatch(row -> "outcome".equals(row.key().model())));
        assertTrue(rows.stream().allMatch(row -> "window".equals(row.key().source())));
        assertFalse(board.noted(first.key().entityId().toString()));
        assertTrue(board.noted(second.key().entityId().toString()));
    }

    @Test
    void aPatternOnNoneAndAJevModelWriteNoRow() {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(2);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(2),
                EngineFixtures.near());
        var builder = new ValueTableBuilder();
        builder.add(1, LearnedLocalPick.tacticCode("alpha"), 3, 7L, 1.0, 0.01, 40, 1.0);
        builder.add(1, LearnedLocalPick.tacticCode("bravo"), 3, 7L, 0.1, 0.01, 40, 1.0);
        session.learning().activate(builder.build(), 1, 7L);
        session.learning().armDistill(DistillMode.OFF, false, Optional.empty(), DistillSource.LOCAL_ORDER, "alpha",
                1L, 10, false, true, "r1:ALL", "fine", 2, true);
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW,
                List.of(new TacticId("alpha"), new TacticId("bravo")), Optional.of(new TacticId("alpha")));
        var plan = new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()),
                List.of());
        var board = new TeachingBoard();
        CaptureGate.apply(encounter, plan, 4L, LearningSettings.DEFAULT, DecisionTap.NONE, DistillSettings.DEFAULT,
                board);
        var rows = new ArrayList<ChoiceStatDelta>();
        board.useSink(rows::addAll);
        WindowCredit.record(session, summary("alpha"), false, 0L);
        assertTrue(rows.isEmpty());
        assertEquals(1, board.filtered());
        var estimates = List.of(new LabelRule.Estimate("alpha", 1.0, 0.01, 40),
                new LabelRule.Estimate("bravo", 0.1, 0.01, 40));
        assertTrue(ChoiceIntake.rows("guardian", "r1:ALL", "fine", "jev-1.13.0", List.of("alpha"), estimates, 0,
                false, 1L).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new ChoiceStatKey("p", "r1:ALL", "c", "alpha", "window", 0, "jev-1.13.0"));
        assertEquals(1, ChoiceIntake.rows("guardian", "r1:ALL", "fine", "kev-0.8b", List.of("alpha", "bravo"),
                estimates, 0, false, 1L).stream().mapToInt(ChoiceStatDelta::chosen).sum());
    }

    private static void arm(MobSession session) {
        session.learning().armDistill(DistillMode.OFF, false, Optional.empty(), DistillSource.LOCAL_ORDER, "alpha",
                1L, 10, false, true, "r1:ALL", "fine", 0, false);
    }

    private static EncounterQuestions.Plan plan(Encounter encounter, List<String> names) {
        var questions = new ArrayList<QuestionSpec>();
        var profiles = new java.util.LinkedHashMap<akyrion.thepredict.domain.MobKey, akyrion.thepredict.domain.TacticalProfile>();
        for (var session : encounter.memberSessions()) {
            questions.add(new QuestionSpec(QuestionKey.of(questions.size()), session.key(), Situation.NOW,
                    names.stream().map(TacticId::new).toList(), Optional.of(new TacticId("delta"))));
            profiles.put(session.key(), session.profile());
        }
        return new EncounterQuestions.Plan(questions, profiles, List.of());
    }

    private static OutcomeWindow.Summary summary(String tactic) {
        return new OutcomeWindow.Summary(akyrion.thepredict.domain.ControlLevel.LOCAL, new TacticId(tactic),
                akyrion.thepredict.domain.Movement.HOLD, 0, 1, 0, 0, false, 0, 0, false, false, 1.0);
    }

    private static ResultIntake intake() {
        var settings = new EngineSettings(60, 60, 4, 2, 10_000, 4, 2, 8, 4, 8, 1000, 1_000_000L);
        return new ResultIntake(new Inbox<>(8), new InFlightRegistry(), new RequestBudget(settings),
                new ServiceStatusTracker(new ServiceCheck(ServiceState.CHECKING, "start"), settings.watchdogNanos(),
                        () -> 0.5, line -> {
                        }),
                DecisionClient.unavailable(new ServiceCheck(ServiceState.CHECKING, "start")),
                new DecisionMetrics(() -> 0L), new ExecutionSteps() {
                    @Override
                    public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
                    }

                    @Override
                    public void react(MobSession session, akyrion.thepredict.domain.MobProbe probe, long tick,
                                      long nowNanos) {
                    }

                    @Override
                    public void evaluated(Encounter encounter, long tick, long nowNanos) {
                    }
                }, ServerClock.of(() -> 1L, () -> 1L), DecisionTap.NONE);
    }

    private static InboxEntry.CompletedRequest completed(Encounter encounter, AnswerSource source) {
        var key = QuestionKey.of(0);
        var request = new InFlightRequest(encounter.id(), EngineFixtures.WORLD, 1L, 1L,
                Map.of(key, new QuestionSpec(key, encounter.memberSessions().iterator().next().key(), Situation.NOW,
                        List.of(new TacticId("alpha"), new TacticId("delta")))),
                Map.of(), 1, 0L, 0L, 10L, 1, 1);
        var answered = new DecisionResult.Answered(Map.of(), "jev-1.13.0", 0, Map.of(), source, Optional.empty());
        return new InboxEntry.CompletedRequest(request, answered, 1L);
    }
}
