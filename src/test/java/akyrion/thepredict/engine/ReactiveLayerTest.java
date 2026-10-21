package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.4: contingencies, target changes and {@code NO_PROGRESS}, with a controllable clock and no sleeps. */
class ReactiveLayerTest {
    private static final MovementSettings MOVEMENT = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15,
            OptionalDouble.empty());

    private final ExecutionFixture fixture = new ExecutionFixture();

    private void engage(int contingencies, TacticId now, TacticId melee, TacticId critical) {
        fixture.ready();
        fixture.open(1, ExecutionFixture.profile(ExecutionFixture.timing(contingencies), 0));
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
        fixture.answer(0, question -> switch (question.situation()) {
            case NOW -> prefer(now, 0.7, 0.9);
            case IF_TARGET_IN_MELEE -> prefer(melee, 0.7, 0.9);
            case IF_OWN_HEALTH_CRITICAL -> prefer(critical, 0.7, 0.9);
        });
        fixture.run(1);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
    }

    @Test
    void theMeleeContingencyAppliesWithinTwoTicksAndOnlyOnce() {
        engage(1, KEEP, FALL_BACK, GUARD);
        fixture.reader.distances.put(EngineFixtures.id(1), DistanceBand.MELEE);
        fixture.run(2);
        var view = fixture.view(1);
        assertEquals(ControlLevel.ALTERNATIVE, view.level());
        assertEquals(FALL_BACK, view.tactic());
        assertTrue(fixture.session(1).intent().orElseThrow().contingencies().isEmpty());
        assertTrue(fixture.sessions.encounters.find(fixture.session(1).encounter().orElseThrow()).orElseThrow()
                .contextPending(), "a used contingency asks for reconsideration");
        fixture.reader.distances.put(EngineFixtures.id(1), DistanceBand.RANGED);
        fixture.run(2);
        fixture.reader.distances.put(EngineFixtures.id(1), DistanceBand.MELEE);
        fixture.run(4);
        assertEquals(1, fixture.actions.signals(1).stream().filter(FALL_BACK.value()::equals).count());
    }

    @Test
    void theCriticalHealthContingencyHasPrecedence() {
        engage(2, KEEP, FALL_BACK, GUARD);
        assertEquals(2, fixture.client.requests.getFirst().questions().stream()
                .filter(question -> question.situation() != Situation.NOW).count());
        fixture.reader.distances.put(EngineFixtures.id(1), DistanceBand.MELEE);
        fixture.reader.health.put(EngineFixtures.id(1), HealthBand.CRITICAL);
        fixture.run(2);
        assertEquals(GUARD, fixture.view(1).tactic());
        assertEquals(List.of(Situation.IF_TARGET_IN_MELEE),
                List.copyOf(fixture.session(1).intent().orElseThrow().contingencies().keySet()));
    }

    @Test
    void aTargetChangeInvalidatesTheIntentAndGivesTheBaseAiControlUntilTheNextDecision() {
        engage(1, KEEP, FALL_BACK, GUARD);
        fixture.reader.probeTargets.put(EngineFixtures.id(1), EngineFixtures.player(2));
        fixture.run(2);
        assertEquals(IntentView.base(0), fixture.view(1));
        assertTrue(fixture.session(1).intent().isEmpty());
        assertEquals(Optional.of(FallbackReason.EXPIRED), fixture.session(1).lastFallbackReason());
    }

    @Test
    void twoEvaluationsWithoutProgressGiveNoProgressAndTheNextAlternative() {
        engage(1, PRESS, FALL_BACK, GUARD);
        var stuck = new MovementProgress(false, false, false, OptionalDouble.of(8.0));
        fixture.mobs.progress.put(EngineFixtures.id(1), new ArrayDeque<>(List.of(stuck)));
        fixture.runUntilRequest(2);
        assertEquals(1, fixture.session(1).noProgressStreak());
        fixture.answer(1, nowAndContingency(prefer(PRESS, 0.7, 0.9), prefer(FALL_BACK, 0.7, 0.9)));
        fixture.runUntilRequest(3);
        assertEquals(1, fixture.count(FallbackReason.NO_PROGRESS));
        assertEquals(ControlLevel.ALTERNATIVE, fixture.view(1).level());
        assertEquals(FALL_BACK, fixture.view(1).tactic(), "the first alternative, ties ranked by name");
        assertEquals(Optional.of(FallbackReason.NO_PROGRESS), fixture.session(1).lastFallbackReason());
        assertEquals(2, fixture.mobs.progressReads.size(), "progress is read once per evaluation of a moving intent");
    }

    @Test
    void theReactiveCycleNeverCapturesProbesOrReadsProgress() {
        engage(1, KEEP, FALL_BACK, GUARD);
        var captures = fixture.reader.captures;
        var probes = fixture.reader.probes;
        var reads = fixture.mobs.progressReads.size();
        var session = fixture.session(1);
        var probe = new MobProbe(session.key(), EngineFixtures.WORLD, HealthBand.HEALTHY,
                Optional.of(EngineFixtures.player(1)), Optional.of(DistanceBand.RANGED));
        for (var index = 0; index < 100; index++) {
            fixture.steps.react(session, probe, fixture.clock.tick, fixture.clock.nanos);
        }
        assertEquals(captures, fixture.reader.captures);
        assertEquals(probes, fixture.reader.probes);
        assertEquals(reads, fixture.mobs.progressReads.size());
    }

    @Test
    void progressFollowsTheExpectedDirectionOfEachMovement() {
        assertTrue(ProgressWatch.advanced(Movement.APPROACH, MOVEMENT, 7.5, 8.0));
        assertFalse(ProgressWatch.advanced(Movement.APPROACH, MOVEMENT, 7.6, 8.0));
        assertTrue(ProgressWatch.advanced(Movement.RETREAT, MOVEMENT, 6.5, 6.0));
        assertFalse(ProgressWatch.advanced(Movement.RETREAT, MOVEMENT, 6.2, 6.0));
        assertTrue(ProgressWatch.advanced(Movement.KEEP_DISTANCE, MOVEMENT, 4.5, 4.0));
        assertFalse(ProgressWatch.advanced(Movement.KEEP_DISTANCE, MOVEMENT, 12.0, 12.2));
        assertTrue(ProgressWatch.reached(Movement.APPROACH, MOVEMENT, 3.0));
        assertFalse(ProgressWatch.reached(Movement.APPROACH, MOVEMENT, 3.1));
        assertTrue(ProgressWatch.reached(Movement.RETREAT, MOVEMENT, 9.5));
        assertTrue(ProgressWatch.reached(Movement.KEEP_DISTANCE, MOVEMENT, 7.0));
        assertFalse(ProgressWatch.reached(Movement.KEEP_DISTANCE, MOVEMENT, 4.9));
    }

    @Test
    void anEvaluationNeedsTheMovementGoalUnlessTheAimIsReachedAndOnlyTheGoalCountsWithoutAReference() {
        var session = fixture.open(9);
        assertTrue(ProgressWatch.madeProgress(Movement.APPROACH, MOVEMENT, progress(false, 2.9), session));
        assertFalse(ProgressWatch.madeProgress(Movement.APPROACH, MOVEMENT, progress(false, 8.0), session));
        assertTrue(ProgressWatch.madeProgress(Movement.APPROACH, MOVEMENT, progress(true, 8.0), session));
        session.progressReferenceBlocks(8.0);
        assertFalse(ProgressWatch.madeProgress(Movement.APPROACH, MOVEMENT, progress(true, 7.8), session));
        assertTrue(ProgressWatch.madeProgress(Movement.APPROACH, MOVEMENT,
                new MovementProgress(true, true, false, OptionalDouble.empty()), session));
        assertFalse(ProgressWatch.madeProgress(Movement.FLANK, MOVEMENT, progress(false, 3.0), session));
        assertTrue(ProgressWatch.madeProgress(Movement.FLANK, MOVEMENT, progress(true, 3.0), session));
        assertTrue(ProgressWatch.madeProgress(Movement.HOLD, MOVEMENT, progress(false, 3.0), session));
    }

    private static MovementProgress progress(boolean running, double distance) {
        return new MovementProgress(running, running, false, OptionalDouble.of(distance));
    }
}
