package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchableDecisionTapTest {
    private static final MobKey MOB = new MobKey(UUID.fromString("00000000-0000-0000-0000-000000000001"), 0);

    @Test
    void everyEventOfTheTapIsForwardedAndNoneFallsToTheEmptyDefault() {
        var missing = new ArrayList<String>();
        for (var method : DecisionTap.class.getMethods()) {
            if (!method.isDefault() || method.getName().equals("recording")) {
                continue;
            }
            try {
                SwitchableDecisionTap.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException absent) {
                missing.add(method.getName());
            }
        }
        assertEquals(List.of(), missing, "recording() derives from detail(); every other call must be forwarded");
    }

    @Test
    void withNothingAttachedEveryCallIsIgnoredAndNothingRecords() {
        var tap = new SwitchableDecisionTap();
        assertFalse(tap.recording());
        assertFalse(DecisionTap.NONE.recording());
        everyCall(tap);
    }

    @Test
    void anAttachedRecordingHearsEveryCallUntilItIsDetached() {
        var tap = new SwitchableDecisionTap();
        var heard = new Heard();
        tap.attach(heard, () -> {
        });
        assertTrue(tap.recording());
        everyCall(tap);
        assertEquals(List.of("body", "result", "discarded", "discarded mob", "intent", "cast", "threat"), heard.calls);
        tap.detach();
        assertFalse(tap.recording());
        everyCall(tap);
        assertEquals(7, heard.calls.size());
    }

    @Test
    void aRecordingThatThrowsIsDetachedAndToldOnceAndTheCallerNeverSeesIt() {
        var tap = new SwitchableDecisionTap();
        var failures = new int[1];
        tap.attach(new DecisionTap() {
            @Override
            public void requestBody(EncounterId encounter, long sequence, String body) {
                throw new IllegalStateException("broken recorder");
            }
        }, () -> failures[0]++);
        everyCall(tap);
        assertFalse(tap.recording(), "a failing recording is detached");
        assertEquals(1, failures[0]);
        everyCall(tap);
        assertEquals(1, failures[0]);
    }

    @Test
    void recordsStayWhenTheDuelIsDetachedAndAFailingRecorderDoesNotDetachTheDuel() {
        var tap = new SwitchableDecisionTap();
        var records = new Heard();
        var duel = new Heard();
        var recordFailures = new int[1];
        var duelFailures = new int[1];
        tap.fixRecords(records, exception -> recordFailures[0]++);
        tap.attach(duel, () -> duelFailures[0]++);
        assertEquals(TapDetail.FULL, tap.detail());
        tap.detach();
        assertEquals(TapDetail.NONE, tap.detail());
        tap.fixRecords(new DecisionTap() {
            @Override
            public void requestBody(EncounterId encounter, long sequence, String body) {
                throw new IllegalStateException("records");
            }
        }, exception -> recordFailures[0]++);
        tap.attach(duel, () -> duelFailures[0]++);
        tap.requestBody(new EncounterId(1), 1, "{}");
        assertEquals(1, recordFailures[0]);
        assertEquals(0, duelFailures[0]);
        assertEquals(TapDetail.FULL, tap.detail());
        tap.fixRecords(records, exception -> recordFailures[0]++);
        tap.attach(new DecisionTap() {
            @Override
            public void requestBody(EncounterId encounter, long sequence, String body) {
                throw new IllegalStateException("duel");
            }
        }, () -> duelFailures[0]++);
        tap.requestBody(new EncounterId(1), 1, "{}");
        assertEquals(1, duelFailures[0]);
        assertEquals(TapDetail.NONE, tap.detail());
    }

    @Test
    void decisionsDetailDoesNotAskTheFourFullEventsToBuild() {
        var tap = new SwitchableDecisionTap();
        var built = new ArrayList<String>();
        tap.fixRecords(new DecisionTap() {
            @Override
            public TapDetail detail() {
                return TapDetail.DECISIONS;
            }

            @Override
            public void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome,
                                      boolean readyCast) {
                if (detail() == TapDetail.FULL) {
                    built.add("cast");
                }
            }

            @Override
            public void threatChanged(MobKey mob, UUID participant, double amount, String cause) {
                if (detail() == TapDetail.FULL) {
                    built.add("threat");
                }
            }

            @Override
            public void reactionFired(MobKey mob, akyrion.thepredict.domain.Reaction reaction) {
                if (detail() == TapDetail.FULL) {
                    built.add("reaction");
                }
            }

            @Override
            public void microChanged(MobKey mob, akyrion.thepredict.domain.MicroBehaviour from,
                                     akyrion.thepredict.domain.MicroBehaviour to) {
                if (detail() == TapDetail.FULL) {
                    built.add("micro");
                }
            }
        }, exception -> {
        });
        assertEquals(TapDetail.DECISIONS, tap.detail());
        tap.castAttempted(MOB, new TacticId("approach"), new ChoiceId("punch"), ExecutionOutcome.CAST);
        tap.threatChanged(MOB, UUID.randomUUID(), 1.0, "hit");
        tap.reactionFired(MOB, akyrion.thepredict.domain.Reaction.FINISH);
        tap.microChanged(MOB, akyrion.thepredict.domain.MicroBehaviour.NATIVE,
                akyrion.thepredict.domain.MicroBehaviour.ORBIT);
        assertEquals(List.of(), built);
    }

    private static void everyCall(DecisionTap tap) {
        var encounter = new EncounterId(1);
        tap.requestBody(encounter, 1, "{}");
        tap.resultReceived(encounter, 1, new DecisionResult.Failed(FailureKind.TIMEOUT, Optional.empty(), "late"), 5);
        tap.answerDiscarded(encounter, 1, DiscardReason.STALE_SEQUENCE);
        tap.answerDiscarded(encounter, 1, new QuestionKey("q0"), MOB, DiscardReason.TARGET_CHANGED);
        tap.intentEvent(MOB, new DecisionTap.IntentEvent(DecisionTap.IntentEventKind.LOCAL_PLANNED,
                new TacticId("approach"), Movement.APPROACH, 0, ControlLevel.BASE, ControlLevel.LOCAL, Optional.empty(),
                Optional.empty()));
        tap.castAttempted(MOB, new TacticId("approach"), new ChoiceId("punch"), ExecutionOutcome.CAST);
        tap.threatChanged(MOB, UUID.randomUUID(), 1.0, "BLOCKED_HIT");
    }

    /** A recording that notes each call. */
    private static final class Heard implements DecisionTap {
        private final List<String> calls = new ArrayList<>();

        @Override
        public boolean recording() {
            return true;
        }

        @Override
        public void requestBody(EncounterId encounter, long sequence, String body) {
            calls.add("body");
        }

        @Override
        public void resultReceived(EncounterId encounter, long sequence, DecisionResult result, long roundTripNanos) {
            calls.add("result");
        }

        @Override
        public void answerDiscarded(EncounterId encounter, long sequence, DiscardReason reason) {
            calls.add("discarded");
        }

        @Override
        public void answerDiscarded(EncounterId encounter, long sequence, QuestionKey question, MobKey mob,
                                    DiscardReason reason) {
            calls.add("discarded mob");
        }

        @Override
        public void intentEvent(MobKey mob, IntentEvent event) {
            calls.add("intent");
        }

        @Override
        public void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome) {
            calls.add("cast");
        }

        @Override
        public void threatChanged(MobKey mob, UUID participant, double amount, String cause) {
            calls.add("threat");
        }
    }
}
