package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often a stuck mob alternates between its local plan and the base AI (pending of M10, deviation 18, for M13).
 * The measured values are in the M13 report; these tests keep their bounds: at most one fall to level 4 every two
 * evaluations, at most one probe period at level 4 each time, and no alternation at all while Jev answers.
 */
class ProgressWatchScenarioTest {
    private static final int MINUTE = LoadScenarios.MINUTE_TICKS;
    /** The fastest cadence of {@code Timing.Cadence: reactive}: an encounter evaluated every six ticks (M20.7). */
    private static final int REACTIVE_INTERVAL = 6;
    /** A long wind-up, so the fall to the alternative lands while the last cast still winds up. */
    private static final int WIND_UP = 60;

    @Test
    void aStuckLocalMobFallsToTheBaseAiAtMostOnceEveryTwoEvaluationsAndOnlyForOneProbePeriod() {
        var alternation = StuckMobs.withoutJev(MINUTE);
        var evaluationPeriod = 20;
        assertTrue(alternation.switchesToBase() <= MINUTE / (ProgressWatch.EVALUATIONS_FOR_NO_PROGRESS
                * evaluationPeriod), alternation.toString());
        assertTrue(alternation.switchesToBase() > 0, "the local mode alone never breaks the loop");
        assertTrue(alternation.ticksAt().getOrDefault(ControlLevel.BASE, 0) <= 2 * alternation.switchesFromBase(),
                alternation.toString());
        assertEquals(alternation.switchesToBase(), alternation.noProgressFallbacks());
        assertEquals(0, alternation.requests());
        assertTrue(alternation.casts() <= MINUTE / evaluationPeriod + 1,
                "one cast per local intent, which lives minimumDecisionIntervalTicks");
    }

    @Test
    void withJevAStuckMobGoesToItsAlternativeAndNeverToTheBaseAi() {
        var alternation = StuckMobs.withJevInsisting(MINUTE, ExecutionFixture.PRESS);
        assertEquals(0, alternation.switchesToBase(), alternation.toString());
        assertTrue(alternation.ticksAt().getOrDefault(ControlLevel.ALTERNATIVE, 0) > 0);
        assertTrue(alternation.noProgressFallbacks() > 0);
    }

    /**
     * PZ-07: an encounter evaluated at the reactive interval is judged by time, as often as the standard cadence, and
     * the local tactic planned again right after its fall to level 4 does not send its signal a second time.
     */
    @Test
    void evaluationsAtTheReactiveIntervalAreJudgedByTimeAndTheReplannedTacticIsNotSignalledAgain() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.open(1);
        stuck(fixture);
        var interval = fixture.session(1).profile().timing().minimumDecisionIntervalTicks();
        for (var tick = 0; tick < MINUTE; tick++) {
            fixture.run(1);
            if (tick % REACTIVE_INTERVAL == 0) {
                fixture.session(1).encounter().flatMap(fixture.sessions.encounters::find).ifPresent(encounter ->
                        fixture.steps.evaluated(encounter, fixture.clock.tick, fixture.clock.nanos));
            }
        }
        var falls = fixture.count(FallbackReason.NO_PROGRESS);
        assertTrue(falls > 0, "a mob that never moves is still found stuck");
        assertTrue(falls <= MINUTE / (ProgressWatch.EVALUATIONS_FOR_NO_PROGRESS * interval),
                "no more falls than with one evaluation every " + interval + " ticks: " + falls);
        assertEquals(List.of(ExecutionFixture.PRESS.value()), fixture.actions.signals(1),
                "each fall plans the same tactic again without a new signal");
    }

    /**
     * Point 12 of the final round: the cast that follows an alternative chosen by {@code NO_PROGRESS} goes through the
     * plain start guard of the intent control, so no start ever falls inside the wind-up of the previous cast.
     */
    @Test
    void theCastAfterAStuckAlternativeNeverStartsDuringTheWindUpOfTheLastCast() {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1, windingUp());
        stuck(fixture);
        fixture.reader.eligible.put(id(1), Set.of(ExecutionFixture.SHIELD_PRESS, ExecutionFixture.BREAK_GUARD));
        Function<QuestionSpec, Answer> insisting = question -> new Answer(Map.of(ExecutionFixture.SHIELD_PRESS, 0.6,
                ExecutionFixture.BREAK_GUARD, 0.4), 0.9);
        fixture.runAnswering(MINUTE, insisting);
        assertTrue(fixture.count(FallbackReason.NO_PROGRESS) > 0, "the stuck mob took its alternative");
        var casts = fixture.actions.castTicks(1);
        assertTrue(casts.size() >= 2, casts.toString());
        for (var index = 1; index < casts.size(); index++) {
            assertTrue(casts.get(index) - casts.get(index - 1) >= WIND_UP, "a start inside a wind-up: " + casts);
        }
    }

    private static void stuck(ExecutionFixture fixture) {
        fixture.target(1, 1);
        fixture.mobs.progress.put(id(1), new ArrayDeque<>(List.of(new MovementProgress(false, false, false,
                OptionalDouble.of(6.0)))));
    }

    /** The two approach tactics of the shield profile, whose skills wind up for {@link #WIND_UP} ticks. */
    private static TacticalProfile windingUp() {
        var base = ExecutionFixture.shieldProfile();
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        for (var entry : base.choices().entrySet()) {
            var purpose = entry.getValue().purpose().orElseThrow();
            choices.put(entry.getKey(), entry.getValue().withPurpose(new SkillPurpose(purpose.primary(),
                    purpose.roles(), purpose.delivery(), purpose.reachBlocks(), purpose.radiusBlocks(), WIND_UP,
                    purpose.cooldown(), purpose.damage(), purpose.bestWhen(), purpose.risks(), purpose.ignoresShield(),
                    purpose.origin())));
        }
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), choices,
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
    }
}
