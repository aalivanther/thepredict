package akyrion.thepredict.domain;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentViewTest {
    private static IntentView view(ControlLevel level, long expiresAtNanos) {
        return new IntentView(0, level, Movement.HOLD, new TacticId("hold_ground"), 0.8f, expiresAtNanos);
    }

    @Test
    void negativeZeroConfidenceIsNormalizedToPositiveZero() {
        var view = new IntentView(0, ControlLevel.LOCAL, Movement.HOLD, new TacticId("hold_ground"), -0.0f, 1L);
        assertEquals(Float.floatToRawIntBits(0.0f), Float.floatToRawIntBits(view.confidence()));
        assertEquals(view, new IntentView(0, ControlLevel.LOCAL, Movement.HOLD, new TacticId("hold_ground"), 0.0f, 1L));
    }

    @Test
    void controlsBeforeTheDeadlineAndNotAtIt() {
        var view = view(ControlLevel.LOCAL, 1_000L);
        assertTrue(view.controls(999L));
        assertFalse(view.controls(1_000L));
    }

    @Test
    void controlsAcrossNanoTimeOverflow() {
        var view = view(ControlLevel.JEV, Long.MIN_VALUE + 10);
        assertTrue(view.controls(Long.MAX_VALUE - 10));
        assertFalse(view.controls(Long.MIN_VALUE + 10));
    }

    @Test
    void jevAlternativeAndLocalControlButBaseNever() {
        assertTrue(view(ControlLevel.JEV, 100L).controls(0L));
        assertTrue(view(ControlLevel.ALTERNATIVE, 100L).controls(0L));
        assertTrue(view(ControlLevel.LOCAL, 100L).controls(0L));
        assertFalse(view(ControlLevel.BASE, 100L).controls(0L));
    }

    @Test
    void absentAndBaseNeverControl() {
        assertFalse(IntentView.ABSENT.controls(Long.MIN_VALUE));
        assertEquals(-1, IntentView.ABSENT.generation());
        assertEquals(TacticId.NONE, IntentView.ABSENT.tactic());
        var base = IntentView.base(4);
        assertFalse(base.controls(Long.MIN_VALUE));
        assertEquals(Movement.NONE, base.movement());
        assertEquals(4, base.generation());
    }

    @Test
    void confidenceOutsideTheUnitIntervalIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new IntentView(0, ControlLevel.LOCAL, Movement.HOLD, TacticId.NONE, 1.01f, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new IntentView(0, ControlLevel.LOCAL, Movement.HOLD, TacticId.NONE, Float.NaN, 0L));
    }

    @Test
    void generationBelowMinusOneIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new IntentView(-2, ControlLevel.BASE, Movement.NONE, TacticId.NONE, 0f, 0L));
        assertThrows(IllegalArgumentException.class, () -> IntentView.base(-1));
    }

    @Test
    void theFieldsOfM22ToM26AreChecked() {
        assertEquals(full(), full(5, (byte) 40, 120L, 6, (byte) 2, 1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(9, (byte) 40, 120L, 6, (byte) 2, 1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(-2, (byte) 40, 120L, 6, (byte) 2, 1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 101, 120L, 6, (byte) 2, 1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) -2, 120L, 6, (byte) 2, 1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 40, -2L, 6, (byte) 2, 1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 40, 120L, -1, (byte) 2, 1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 40, 120L, 6, (byte) 4, 1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 40, 120L, 6, (byte) 2, -1, 3, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 40, 120L, 6, (byte) 2, 1, -1, 2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 40, 120L, 6, (byte) 2, 1, 3, -2, 4, (byte) 4));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 40, 120L, 6, (byte) 2, 1, 3, 2, -1, (byte) 4));
        assertThrows(IllegalArgumentException.class,
                () -> full(5, (byte) 40, 120L, 6, (byte) 2, 1, 3, 2, 4, (byte) EnemyAction.COUNT));
        assertThrows(IllegalArgumentException.class, () -> full(5, (byte) 40, 120L, 6, (byte) 2, 1, 3, 2, 4, (byte) -2));
    }

    @Test
    void eachCopyChangesOnlyTheFieldsItOwns() {
        var view = full();
        var micro = view.withMicro(MicroBehaviour.STILL, MicroSide.LEFT);
        assertEquals(MicroBehaviour.STILL, micro.micro());
        assertEquals(view, micro.withMicro(view.micro(), view.microSide()));
        assertEquals(view, view.withPressure(-1).withPressure(view.pressure()));
        assertEquals(view, view.withZone(0, (byte) 0).withZone(view.zoneFacts(), view.pushDanger()));
        assertEquals(view, view.withPatterns(0, (byte) -1).withPatterns(view.targetPatternMask(), view.forecast()));
        assertEquals(view, view.withGroup(Optional.empty(), Optional.empty(), 0, 0, -1)
                .withGroup(view.role(), view.plan(), view.tokenMask(), view.squadSize(), view.slotIndex()));
        assertEquals(view, view.withPosture(Movement.HOLD, CombatStance.NEUTRAL, false, (byte) -1, -1, -1L)
                .withPosture(view.movement(), view.stance(), view.enraged(), view.poisePercent(),
                        view.answer().orElseThrow().ordinal(), view.impactAtTick()));
        assertEquals(Optional.empty(), view.withPosture(Movement.HOLD, CombatStance.NEUTRAL, false, (byte) -1,
                TellAnswer.values().length, -1L).answer(), "an ordinal past the last answer clears it");
    }

    @Test
    void aRepublishedIntentCarriesWhatTheOtherStepsPublished() {
        var published = full();
        var fresh = new IntentView(3, ControlLevel.LOCAL, Movement.HOLD, new TacticId("hold_ground"), 0.4f, 1_000L);
        var expected = new IntentView(3, ControlLevel.LOCAL, Movement.HOLD, new TacticId("hold_ground"), 0.4f, 1_000L,
                published.micro(), published.microSide(), published.pressure(), published.stance(),
                published.enraged(), published.poisePercent(), published.answer(), published.impactAtTick(),
                published.zoneFacts(), published.pushDanger(), published.role(), published.plan(),
                published.tokenMask(), published.squadSize(), published.slotIndex(), published.targetPatternMask(),
                published.forecast());
        assertEquals(expected, fresh.carrying(published));
        var copiedByHand = fresh.withMicro(published.micro(), published.microSide())
                .withPressure(published.pressure()).withZone(published.zoneFacts(), published.pushDanger());
        assertEquals(0, copiedByHand.tokenMask(), "copying micro, rung and zone alone drops the token");
        assertEquals(Optional.empty(), copiedByHand.role());
    }

    @Test
    void aViewOfAnotherGenerationCarriesNothing() {
        var fresh = new IntentView(3, ControlLevel.LOCAL, Movement.HOLD, new TacticId("hold_ground"), 0.4f, 1_000L);
        assertEquals(fresh, fresh.carrying(IntentView.ABSENT));
        var previousSession = IntentView.base(2).withGroup(GroupRole.PRESSURE.ordinal(), -1, 1, 4, 0).withPressure(7);
        assertEquals(fresh, fresh.carrying(previousSession), "the board of an older session is not carried");
    }

    @Test
    void theGroupIsPublishedByOrdinalWithoutOptionalParameters() {
        var view = IntentView.base(2).withGroup(GroupRole.FLANK.ordinal(), GroupPlan.SURROUND_TARGET.ordinal(), 1, 3, 2);
        assertEquals(IntentView.base(2).withGroup(Optional.of(GroupRole.FLANK), Optional.of(GroupPlan.SURROUND_TARGET),
                1, 3, 2), view);
        var none = view.withGroup(-1, -1, 0, 0, -1);
        assertEquals(Optional.empty(), none.role());
        assertEquals(Optional.empty(), none.plan());
        assertEquals(-1, none.slotIndex());
        var beyond = view.withGroup(GroupRole.values().length, GroupPlan.values().length, 0, 0, -1);
        assertEquals(Optional.empty(), beyond.role());
        assertEquals(Optional.empty(), beyond.plan());
        assertThrows(IllegalArgumentException.class, () -> view.withGroup(0, 0, -1, 0, -1));
    }

    @Test
    void patternsReachTheVocabularyOnlyInBiasAndFull() {
        var mask = EnemyPattern.SHIELD_TURTLE.bit();
        var forecast = EnemyAction.STRIKE.ordinal();
        for (var mode : PatternMode.values()) {
            var shown = mode == PatternMode.BIAS || mode == PatternMode.FULL;
            var view = IntentView.base(1).withPatterns(mode, mask, forecast);
            assertEquals(shown ? mask : 0, view.targetPatternMask(), mode.name());
            assertEquals(shown ? forecast : -1, view.forecast(), mode.name());
        }
        assertEquals(-1, IntentView.base(1).withPatterns(PatternMode.FULL, mask, EnemyAction.COUNT).forecast());
        assertEquals(-1, IntentView.base(1).withPatterns(PatternMode.FULL, mask, -5).forecast());
    }

    @Test
    void theShowsQueriesAgreeWithTheCopiesTheyStandFor() {
        var base = IntentView.base(2);
        var group = base.withGroup(GroupRole.FLANK.ordinal(), GroupPlan.SURROUND_TARGET.ordinal(), 1, 3, 2);
        assertTrue(group.showsGroup(GroupRole.FLANK.ordinal(), GroupPlan.SURROUND_TARGET.ordinal(), 1, 3, 2));
        assertFalse(group.showsGroup(GroupRole.FLANK.ordinal(), GroupPlan.SURROUND_TARGET.ordinal(), 0, 3, 2));
        assertFalse(group.showsGroup(-1, GroupPlan.SURROUND_TARGET.ordinal(), 1, 3, 2));
        assertTrue(base.showsGroup(-1, -1, 0, 0, -1));
        var mask = EnemyPattern.SHIELD_TURTLE.bit();
        for (var mode : PatternMode.values()) {
            var shown = base.withPatterns(mode, mask, EnemyAction.STRIKE.ordinal());
            assertTrue(shown.showsPatterns(mode, mask, EnemyAction.STRIKE.ordinal()), mode.name());
            assertEquals(shown.equals(base), base.showsPatterns(mode, mask, EnemyAction.STRIKE.ordinal()), mode.name());
        }
    }

    @Test
    void theShowsQueriesAllocateNothing() {
        var view = full();
        var hits = 0;
        for (var step = 0; step < 200; step++) {
            hits += view.showsGroup(step % 3, step % 2, 1, 3, 2) ? 1 : 0;
            hits += view.showsPatterns(PatternMode.BIAS, step % 5, step % 4) ? 1 : 0;
        }
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            return;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        var thread = Thread.currentThread().threadId();
        var before = bean.getThreadAllocatedBytes(thread);
        for (var step = 0; step < 200; step++) {
            hits += view.showsGroup(step % 3, step % 2, 1, 3, 2) ? 1 : 0;
            hits += view.showsPatterns(PatternMode.BIAS, step % 5, step % 4) ? 1 : 0;
        }
        assertEquals(before, bean.getThreadAllocatedBytes(thread));
        assertTrue(hits > 0);
    }

    private static IntentView full() {
        return full(5, (byte) 40, 120L, 6, (byte) 2, 1, 3, 2, 4, (byte) 4);
    }

    private static IntentView full(int pressure, byte poise, long impact, int zone, byte push, int tokens, int squad,
                                   int slot, int patterns, byte forecast) {
        return new IntentView(3, ControlLevel.JEV, Movement.FLANK, new TacticId("press_attack"), 0.7f, 900L,
                MicroBehaviour.ORBIT, MicroSide.RIGHT, pressure, CombatStance.PRESSING, true, poise,
                Optional.of(TellAnswer.DODGE), impact, zone, push, Optional.of(GroupRole.FLANK),
                Optional.of(GroupPlan.SURROUND_TARGET), tokens, squad, slot, patterns, forecast);
    }
}
