package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.3 in the engine: one cast per intent, the meaning of each outcome and the extra cooldown of compatibility. */
class CastSemanticsTest {
    private static final Function<QuestionSpec, Answer> PRESS_ON =
            nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9));

    private final ExecutionFixture fixture = new ExecutionFixture();

    private void engage() {
        engage(ExecutionFixture.profile(ExecutionFixture.timing(1), 0));
    }

    private void engage(TacticalProfile profile) {
        fixture.ready();
        fixture.open(1, profile);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
    }

    /** Lifetime of a local intent with the profile of these tests: min(100, 20) ticks. */
    private static final int LOCAL_LIFETIME = 20;
    /** Native cooldown of the skill of the doubles unless a test sets its own. */
    private static final int COOLDOWN = ExecutionFixture.Actions.DEFAULT_COOLDOWN_TICKS;

    @Test
    void aRenewalThatArrivesBeforeTheSkillIsReadyNeverCastsAgain() {
        for (var attempt = 0; attempt < 7; attempt++) {
            fixture.actions.script(1, ExecutionOutcome.NOT_ELIGIBLE);
        }
        engage();
        fixture.answer(0, PRESS_ON);
        var casts = castTicks(60);
        assertTrue(casts.size() >= 2, casts.toString());
        assertEquals(7, fixture.actions.casts(1) - casts.size(),
                "the seven refusals are the only attempts before a cast, and a renewal does not ask MythicMobs again");
        assertTrue(fixture.client.requests.size() >= 2);
        for (var index = 1; index < casts.size(); index++) {
            assertTrue(casts.get(index) - casts.get(index - 1) >= COOLDOWN, casts.toString());
        }
    }

    @Test
    void aRenewedIntentGetsItsChoiceBackAtTheCooldownOfItsSkillWithoutSignalOrNewStart() {
        fixture.actions.cooldowns.set(ExecutionFixture.STRIKE, 30);
        engage();
        fixture.answer(0, PRESS_ON);
        fixture.run(1);
        var start = fixture.session(1).intent().orElseThrow().startTick();
        var casts = castTicks(300);
        assertTrue(casts.size() >= 9 && casts.size() <= 10, casts.toString());
        for (var index = 1; index < casts.size(); index++) {
            var gap = casts.get(index) - casts.get(index - 1);
            assertTrue(gap >= 30 && gap <= 32, casts.toString());
        }
        assertEquals(start, fixture.session(1).intent().orElseThrow().startTick(), "hysteresis and hold untouched");
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertEquals(1, fixture.actions.signals(1).size(), "a re-armed choice sends no new signal");
        assertEquals(casts.size() + 1, fixture.count(ExecutionOutcome.CAST), "each armed choice is counted once");
    }

    @Test
    void noAnswerRecastsBeforeTheSkillIsReadyAndEachEpochCastsAtMostOnce() {
        engage();
        fixture.answer(0, PRESS_ON);
        var castsPerEpoch = new HashMap<Integer, Long>();
        var castsPerRequest = new HashMap<Integer, Long>();
        for (var tick = 0; tick < 300; tick++) {
            var before = fixture.actions.casts(1);
            fixture.runAnswering(1, PRESS_ON);
            var cast = fixture.actions.casts(1) - before;
            castsPerEpoch.merge(fixture.session(1).choiceEpoch(), cast, Long::sum);
            castsPerRequest.merge(fixture.client.requests.size(), cast, Long::sum);
        }
        assertTrue(castsPerEpoch.values().stream().allMatch(count -> count <= 1), castsPerEpoch.toString());
        assertTrue(castsPerRequest.values().stream().allMatch(count -> count <= 1), castsPerRequest.toString());
    }

    /** Runs {@code ticks} ticks answering every request and returns the ticks at which a cast was attempted. */
    private List<Long> castTicks(int ticks) {
        var casts = new ArrayList<Long>();
        var seen = fixture.actions.casts(1);
        for (var tick = 0; tick < ticks; tick++) {
            fixture.runAnswering(1, PRESS_ON);
            if (fixture.actions.casts(1) > seen && fixture.session(1).lastOutcome()
                    .filter(outcome -> outcome == ExecutionOutcome.CAST).isPresent()) {
                casts.add(fixture.clock.tick);
            }
            seen = fixture.actions.casts(1);
        }
        return casts;
    }

    @Test
    void notEligibleAndDeferredAreRetriedEveryReactiveCycleUntilTheCast() {
        fixture.actions.script(1, ExecutionOutcome.NOT_ELIGIBLE, ExecutionOutcome.DEFERRED_INVULNERABLE,
                ExecutionOutcome.CAST);
        engage();
        fixture.answer(0, PRESS_ON);
        fixture.run(1);
        fixture.run(8);
        assertEquals(3, fixture.actions.casts(1));
        assertEquals(Optional.of(ExecutionOutcome.CAST), fixture.session(1).lastOutcome());
        assertEquals(1, fixture.count(ExecutionOutcome.NOT_ELIGIBLE));
        assertEquals(1, fixture.count(ExecutionOutcome.DEFERRED_INVULNERABLE));
    }

    @Test
    void aRejectionByMythicMobsConsumesTheChoiceForItsEpochAndTheNextEpochTriesAgain() {
        fixture.actions.script(1, ExecutionOutcome.REJECTED_BY_MYTHICMOBS);
        engage();
        fixture.answer(0, PRESS_ON);
        fixture.run(1);
        assertEquals(1, fixture.actions.casts(1));
        var rejectedAt = fixture.session(1).choiceConsumedTick().orElseThrow();
        var epoch = fixture.session(1).choiceEpoch();
        while (fixture.clock.tick < rejectedAt + LOCAL_LIFETIME - 1) {
            fixture.runAnswering(1, PRESS_ON);
        }
        assertEquals(1, fixture.actions.casts(1), "never retried within its epoch");
        assertTrue(fixture.session(1).intent().orElseThrow().skillConsumed());
        assertEquals(Optional.of(ExecutionOutcome.REJECTED_BY_MYTHICMOBS), fixture.session(1).lastOutcome());
        fixture.runAnswering(4, PRESS_ON);
        assertEquals(2, fixture.actions.casts(1), "the next epoch, one local intent lifetime later, tries again");
        assertEquals(epoch + 1, fixture.session(1).choiceEpoch());
        assertEquals(Optional.of(ExecutionOutcome.CAST), fixture.session(1).lastOutcome());
        assertEquals(1, fixture.count(ExecutionOutcome.REJECTED_BY_MYTHICMOBS));
    }

    @Test
    void aSkippedShieldMovesToTheNextAlternativeOfTheAnswer() {
        fixture.actions.script(1, ExecutionOutcome.SKIPPED_SHIELD);
        engage();
        fixture.answer(0, nowAndContingency(ExecutionFixture.between(PRESS, 0.6, KEEP, 0.3, 0.9),
                prefer(FALL_BACK, 0.7, 0.9)));
        fixture.run(1);
        assertEquals(ControlLevel.ALTERNATIVE, fixture.view(1).level());
        assertEquals(KEEP, fixture.view(1).tactic());
        assertTrue(fixture.actions.signals(1).contains(KEEP.value()));
    }

    @Test
    void aChoiceNeverCastBeforeTheIntentExpiresRecordsExpiredOnce() {
        for (var index = 0; index < 100; index++) {
            fixture.actions.script(1, ExecutionOutcome.NOT_ELIGIBLE);
        }
        engage();
        fixture.answer(0, PRESS_ON);
        fixture.run(110);
        assertEquals(1, fixture.count(ExecutionOutcome.EXPIRED));
    }

    @Test
    void theExtraCooldownOfCompatibilityHoldsTheNextCastWithoutAskingMythicMobs() {
        engage(ExecutionFixture.profile(ExecutionFixture.timing(1), 60));
        fixture.answer(0, PRESS_ON);
        fixture.run(1);
        var castTick = fixture.clock.tick - 1;
        assertEquals(castTick + 60, fixture.session(1).nextChoiceTick());
        var keep = nowAndContingency(prefer(KEEP, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9));
        fixture.runAnswering(22, keep);
        assertEquals(KEEP, fixture.view(1).tactic());
        fixture.runAnswering(22, PRESS_ON);
        assertEquals(PRESS, fixture.view(1).tactic());
        assertTrue(fixture.clock.tick < castTick + 60);
        assertEquals(1, fixture.actions.casts(1));
        assertEquals(Optional.of(ExecutionOutcome.NOT_ELIGIBLE), fixture.session(1).lastOutcome());
        fixture.runAnswering(25, PRESS_ON);
        assertEquals(2, fixture.actions.casts(1));
    }

    @Test
    void noActionHappensFromTheCallbackThreadAndEveryActionRunsOnTheTickThread() throws InterruptedException {
        engage();
        var completer = new Thread(() -> fixture.answer(0, PRESS_ON), "ThePredict-SystemOne-test");
        completer.start();
        completer.join();
        assertTrue(fixture.actions.calls.isEmpty());
        fixture.run(3);
        assertEquals(1, fixture.actions.casts(1));
        assertTrue(fixture.actions.threads.stream().allMatch(Thread.currentThread()::equals));
    }

    @Test
    void aRetriedOutcomeIsCountedAndLoggedOnceForTheSameIntent() {
        for (var index = 0; index < 10; index++) {
            fixture.actions.script(1, ExecutionOutcome.NOT_ELIGIBLE);
        }
        engage();
        fixture.answer(0, PRESS_ON);
        fixture.run(30);
        assertTrue(fixture.actions.casts(1) >= 11, "the cast is retried every reactive cycle");
        assertEquals(1, fixture.count(ExecutionOutcome.NOT_ELIGIBLE));
        assertEquals(1, fixture.count(ExecutionOutcome.CAST));
        assertEquals(1, fixture.log.stream().filter(line -> line.endsWith(": NOT_ELIGIBLE")).count());
        assertEquals(Optional.of(ExecutionOutcome.CAST), fixture.session(1).lastOutcome());
    }
}
