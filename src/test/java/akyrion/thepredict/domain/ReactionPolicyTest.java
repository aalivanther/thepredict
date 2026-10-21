package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reaction table of M20.4, one row at its boundary and its opposite, plus slots, scope and the rung mask. */
class ReactionPolicyTest {
    private static final long TARGET = (1L << TickSignals.VALID) | (1L << TickSignals.HAS_TARGET);
    private static final double CRITICAL = 0.3;

    private final MovementSettings settings = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty());

    @Test
    void dodgeAndSpaceOutAreSkippedWithoutMicro() {
        var signals = TickSignals.pack(TARGET | (1L << TickSignals.INCOMING_PROJECTILE), 40, 0, 0, 0, 80, 80);
        var cooldown = new long[Reaction.values().length];
        var since = new long[cooldown.length];
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(signals, 5, 0, ReactionScope.ALL, false, true,
                Movement.KEEP_DISTANCE, CRITICAL, settings, true, 0, cooldown, since));
        assertEquals(Reaction.DODGE_SHOT.ordinal(), ReactionPolicy.select(signals, 5, 0, ReactionScope.ALL, true, true,
                Movement.KEEP_DISTANCE, CRITICAL, settings, true, 0, cooldown, since));
        assertFalse(Reaction.DODGE_SHOT.evaluatedWithoutMicro());
        assertFalse(Reaction.SPACE_OUT.evaluatedWithoutMicro());
        assertFalse(Reaction.EDGE_DANGER.evaluatedWithoutMicro());
    }

    @Test
    void aTriggerWaitsOutItsDelay() {
        var flags = TARGET | (1L << TickSignals.TARGET_CONSUMING);
        var signals = TickSignals.pack(flags, 4, 0, 0, 0, 80, 80);
        var cooldown = new long[Reaction.values().length];
        var since = new long[cooldown.length];
        since[Reaction.PUNISH_CONSUMING.ordinal()] = 10;
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(signals, 12, 3, ReactionScope.ALL, false, true,
                Movement.APPROACH, CRITICAL, settings, true, 0, cooldown, since));
        assertEquals(Reaction.PUNISH_CONSUMING.ordinal(), ReactionPolicy.select(signals, 13, 3, ReactionScope.ALL,
                false, true, Movement.APPROACH, CRITICAL, settings, true, 0, cooldown, since));
        since[Reaction.PUNISH_CONSUMING.ordinal()] = -1;
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(signals, 13, 0, ReactionScope.ALL, false, true,
                Movement.APPROACH, CRITICAL, settings, true, 0, cooldown, since));
    }

    @Test
    void withoutATargetTheTriggersThatReadZerosStayOffAndTheEdgeStepStays() {
        var noTarget = TickSignals.pack(1L << TickSignals.VALID, 0, 0, 0, 0, 10, 0);
        var withTarget = TickSignals.pack(TARGET, 0, 0, 0, 0, 10, 0);
        for (var reaction : new Reaction[] {Reaction.EVADE_CRITICAL, Reaction.FINISH, Reaction.SPACE_OUT}) {
            assertFalse(ReactionPolicy.trigger(reaction, noTarget, Movement.HOLD, CRITICAL, settings, true, 5, 5),
                    reaction.name());
            assertTrue(ReactionPolicy.trigger(reaction, withTarget, Movement.HOLD, CRITICAL, settings, true, 5, 5),
                    reaction.name());
        }
        var cooldown = new long[Reaction.values().length];
        var since = new long[cooldown.length];
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(noTarget, 5, 0, ReactionScope.ALL, true, true,
                Movement.HOLD, CRITICAL, settings, true, 5, cooldown, since));
        assertEquals(Reaction.EVADE_CRITICAL.ordinal(), ReactionPolicy.select(withTarget, 5, 0, ReactionScope.ALL,
                true, true, Movement.HOLD, CRITICAL, settings, true, 5, cooldown, since));
        var edge = TickSignals.pack((1L << TickSignals.VALID) | (1L << TickSignals.MOB_ON_GROUND), 0, 0, 0, 0, 80, 0,
                PushDanger.LETHAL.ordinal(), PushDanger.NONE.ordinal(), PushDanger.NONE.ordinal(),
                PushDanger.NONE.ordinal());
        assertEquals(Reaction.EDGE_DANGER.ordinal(), ReactionPolicy.select(edge, 5, 0, ReactionScope.ALL,
                ReactionMask.ALL, true, true, Movement.HOLD, CRITICAL, settings, true, 0, cooldown, since, true,
                false));
    }

    @Test
    void everyReactionButTheEdgeStepNeedsATargetEvenWhenItsTargetBitsAreSet() {
        var bits = (1L << TickSignals.INCOMING_PROJECTILE) | (1L << TickSignals.TARGET_CONSUMING)
                | (1L << TickSignals.TARGET_ATTACK_RECHARGING) | (1L << TickSignals.TARGET_BLOCKING);
        var noTarget = TickSignals.pack((1L << TickSignals.VALID) | bits, 4, 0, 0, 0, 80, 10, PushDanger.NONE.ordinal(),
                PushDanger.NONE.ordinal(), PushDanger.NONE.ordinal(), PushDanger.LETHAL.ordinal());
        var withTarget = noTarget | (1L << TickSignals.HAS_TARGET);
        for (var reaction : new Reaction[] {Reaction.DODGE_SHOT, Reaction.PUNISH_CONSUMING, Reaction.PUNISH_RECHARGE,
                Reaction.RESPECT_SHIELD, Reaction.HAZARD_PUSH, Reaction.EXPLOIT_PATTERN}) {
            assertFalse(ReactionPolicy.trigger(reaction, noTarget, Movement.APPROACH, CRITICAL, settings, true, 0, 5,
                    settings.meleeReachBlocks(), true, true), "SP-15: " + reaction.name() + " without a target");
            assertTrue(ReactionPolicy.trigger(reaction, withTarget, Movement.APPROACH, CRITICAL, settings, true, 0, 5,
                    settings.meleeReachBlocks(), true, true), reaction.name() + " with its target");
        }
    }

    @Test
    void evadeCriticalHoldsAtTheCriticalRatioAndTheMeleeReach() {
        assertTrue(holds(Reaction.EVADE_CRITICAL, TickSignals.pack(TARGET, 10, 0, 0, 0, 30, 80), Movement.HOLD));
        assertFalse(holds(Reaction.EVADE_CRITICAL, TickSignals.pack(TARGET, 10, 0, 0, 0, 31, 80), Movement.HOLD));
        assertFalse(holds(Reaction.EVADE_CRITICAL, TickSignals.pack(TARGET, 11, 0, 0, 0, 30, 80), Movement.HOLD));
    }

    @Test
    void dodgeShotHoldsOnAnIncomingShotOrADrawnBowOutsideMeleeInSight() {
        assertTrue(holds(Reaction.DODGE_SHOT, TickSignals.pack(TARGET | (1L << TickSignals.INCOMING_PROJECTILE), 4, 0,
                0, 0, 80, 80), Movement.HOLD));
        var drawn = TARGET | (1L << TickSignals.TARGET_SHOT_READY);
        assertTrue(holds(Reaction.DODGE_SHOT, TickSignals.pack(drawn, 11, 0, 0, 0, 80, 80), Movement.HOLD));
        assertFalse(holds(Reaction.DODGE_SHOT, TickSignals.pack(drawn, 10, 0, 0, 0, 80, 80), Movement.HOLD));
        assertFalse(ReactionPolicy.trigger(Reaction.DODGE_SHOT, TickSignals.pack(drawn, 11, 0, 0, 0, 80, 80),
                Movement.HOLD, CRITICAL, settings, false, 0, 5));
    }

    @Test
    void punishmentsShieldAndFinishHoldAtTheirBoundaries() {
        assertTrue(holds(Reaction.PUNISH_CONSUMING, TickSignals.pack(TARGET | (1L << TickSignals.TARGET_CONSUMING),
                40, 0, 0, 0, 80, 80), Movement.APPROACH));
        assertFalse(holds(Reaction.PUNISH_CONSUMING, TickSignals.pack(TARGET, 40, 0, 0, 0, 80, 80),
                Movement.APPROACH));
        var recharging = TARGET | (1L << TickSignals.TARGET_ATTACK_RECHARGING);
        assertTrue(holds(Reaction.PUNISH_RECHARGE, TickSignals.pack(recharging, 20, 0, 0, 0, 80, 80),
                Movement.APPROACH));
        assertFalse(holds(Reaction.PUNISH_RECHARGE, TickSignals.pack(recharging, 21, 0, 0, 0, 80, 80),
                Movement.APPROACH));
        var blocking = TickSignals.pack(TARGET | (1L << TickSignals.TARGET_BLOCKING), 8, 0, 0, 0, 80, 80);
        assertTrue(holds(Reaction.RESPECT_SHIELD, blocking, Movement.APPROACH));
        assertFalse(holds(Reaction.RESPECT_SHIELD, blocking, Movement.FLANK));
        assertTrue(holds(Reaction.FINISH, TickSignals.pack(TARGET, 40, 0, 0, 0, 80, 30), Movement.APPROACH));
        assertFalse(holds(Reaction.FINISH, TickSignals.pack(TARGET, 40, 0, 0, 0, 80, 31), Movement.APPROACH));
    }

    @Test
    void spaceOutHoldsRightAfterACastInsideReachPlusHalfABlock() {
        var close = TickSignals.pack(TARGET, 12, 0, 0, 0, 80, 80);
        assertTrue(ReactionPolicy.trigger(Reaction.SPACE_OUT, close, Movement.KEEP_DISTANCE, CRITICAL, settings, true,
                8, 10));
        assertTrue(ReactionPolicy.trigger(Reaction.SPACE_OUT, close, Movement.HOLD, CRITICAL, settings, true, 10, 10));
        assertFalse(ReactionPolicy.trigger(Reaction.SPACE_OUT, close, Movement.HOLD, CRITICAL, settings, true, 7, 10));
        assertFalse(ReactionPolicy.trigger(Reaction.SPACE_OUT, close, Movement.HOLD, CRITICAL, settings, true, 11, 10));
        assertFalse(ReactionPolicy.trigger(Reaction.SPACE_OUT, close, Movement.APPROACH, CRITICAL, settings, true, 10,
                10));
        assertFalse(ReactionPolicy.trigger(Reaction.SPACE_OUT, TickSignals.pack(TARGET, 13, 0, 0, 0, 80, 80),
                Movement.HOLD, CRITICAL, settings, true, 10, 10));
        assertFalse(ReactionPolicy.trigger(Reaction.SPACE_OUT, TickSignals.pack(TARGET
                | (1L << TickSignals.TARGET_ATTACK_RECHARGING), 12, 0, 0, 0, 80, 80), Movement.HOLD, CRITICAL,
                settings, true, 10, 10));
    }

    @Test
    void theEdgeStepAndTheHazardPushNeedTheirZoneInputs() {
        var edge = TickSignals.pack(TARGET | (1L << TickSignals.MOB_ON_GROUND), 8, 0, 0, 0, 80, 80,
                PushDanger.LETHAL.ordinal(), PushDanger.NONE.ordinal(), PushDanger.NONE.ordinal(),
                PushDanger.LETHAL.ordinal());
        assertTrue(ReactionPolicy.trigger(Reaction.EDGE_DANGER, edge, Movement.HOLD, CRITICAL, settings, true, 0, 5,
                3.0, false));
        assertFalse(ReactionPolicy.trigger(Reaction.EDGE_DANGER, TickSignals.pack(TARGET, 8, 0, 0, 0, 80, 80,
                PushDanger.LETHAL.ordinal(), 0, 0, 0), Movement.HOLD, CRITICAL, settings, true, 0, 5, 3.0, false));
        assertFalse(ReactionPolicy.trigger(Reaction.EDGE_DANGER, TickSignals.pack(TARGET
                | (1L << TickSignals.MOB_ON_GROUND), 8, 0, 0, 0, 80, 80, PushDanger.HARM.ordinal(), 0, 0, 0),
                Movement.HOLD, CRITICAL, settings, true, 0, 5, 3.0, false));
        assertTrue(ReactionPolicy.trigger(Reaction.HAZARD_PUSH, edge, Movement.HOLD, CRITICAL, settings, true, 0, 5,
                3.0, true));
        assertFalse(ReactionPolicy.trigger(Reaction.HAZARD_PUSH, edge, Movement.HOLD, CRITICAL, settings, true, 0, 5,
                3.0, false));
        var cooldown = new long[Reaction.values().length];
        var since = new long[cooldown.length];
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(edge, 5, 0, ReactionScope.ALL, ReactionMask.ALL, true,
                true, Movement.FLANK, CRITICAL, settings, true, 0, cooldown, since, false, false));
        assertEquals(Reaction.EDGE_DANGER.ordinal(), ReactionPolicy.select(edge, 5, 0, ReactionScope.ALL,
                ReactionMask.ALL, true, true, Movement.FLANK, CRITICAL, settings, true, 0, cooldown, since, true,
                false));
        assertEquals(Reaction.HAZARD_PUSH.ordinal(), ReactionPolicy.select(edge, 5, 0, ReactionScope.ALL,
                ReactionMask.ALL, false, true, Movement.FLANK, CRITICAL, settings, true, 0, cooldown, since, true,
                true));
    }

    @Test
    void aSlotTakesAReactionOnlyWhenItIsFreeExpiredOrTheNewOneComesFirst() {
        assertTrue(ReactionPolicy.replaces(ReactionPolicy.NONE, 0, 10, Reaction.FINISH));
        assertTrue(ReactionPolicy.replaces(Reaction.FINISH.ordinal(), 10, 10, Reaction.RESPECT_SHIELD));
        assertTrue(ReactionPolicy.replaces(Reaction.RESPECT_SHIELD.ordinal(), 20, 10, Reaction.PUNISH_CONSUMING));
        assertFalse(ReactionPolicy.replaces(Reaction.PUNISH_CONSUMING.ordinal(), 20, 10, Reaction.RESPECT_SHIELD));
        assertFalse(ReactionPolicy.replaces(Reaction.EVADE_CRITICAL.ordinal(), 20, 10, Reaction.EVADE_CRITICAL));
    }

    @Test
    void scopeMaskCooldownAndControlFilterTheWalk() {
        var both = TickSignals.pack(TARGET | (1L << TickSignals.TARGET_CONSUMING), 4, 0, 0, 0, 10, 80);
        var cooldown = new long[Reaction.values().length];
        var since = new long[cooldown.length];
        assertEquals(Reaction.EVADE_CRITICAL.ordinal(), select(both, ReactionScope.ALL, ReactionMask.ALL, cooldown,
                since, true));
        assertEquals(Reaction.EVADE_CRITICAL.ordinal(), select(both, ReactionScope.SURVIVAL, ReactionMask.ALL,
                cooldown, since, true));
        assertEquals(ReactionPolicy.NONE, select(both, ReactionScope.NONE, ReactionMask.ALL, cooldown, since, true));
        assertEquals(ReactionPolicy.NONE, select(both, ReactionScope.ALL, ReactionMask.ALL, cooldown, since, false));
        cooldown[Reaction.EVADE_CRITICAL.ordinal()] = 6;
        assertEquals(Reaction.PUNISH_CONSUMING.ordinal(), select(both, ReactionScope.ALL, ReactionMask.ALL, cooldown,
                since, true));
        assertEquals(ReactionPolicy.NONE, select(both, ReactionScope.SURVIVAL, ReactionMask.ALL, cooldown, since,
                true));
        var noPunish = ReactionMask.without(Reaction.PUNISH_CONSUMING, Reaction.EVADE_CRITICAL);
        cooldown[Reaction.EVADE_CRITICAL.ordinal()] = 0;
        assertEquals(Reaction.EVADE_CRITICAL.ordinal(), select(both, ReactionScope.ALL, noPunish, cooldown, since,
                true));
        cooldown[Reaction.EVADE_CRITICAL.ordinal()] = 6;
        assertEquals(ReactionPolicy.NONE, select(both, ReactionScope.ALL, noPunish, cooldown, since, true));
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(0L, 5, 0, ReactionScope.ALL, true, true,
                Movement.APPROACH, CRITICAL, settings, true, 0, cooldown, since));
    }

    @Test
    void thePreferenceMasksStayTheTableOfM204() {
        assertEquals(CastPreference.role(SkillRole.FINISHER), ReactionPolicy.mask(Reaction.FINISH));
        assertEquals(0L, ReactionPolicy.mask(Reaction.DODGE_SHOT));
        assertEquals(0L, ReactionPolicy.mask(Reaction.SPACE_OUT));
        assertEquals(0L, ReactionPolicy.mask(Reaction.EDGE_DANGER));
        assertEquals(CastPreference.role(SkillRole.ESCAPE) | CastPreference.role(SkillRole.DEFENSE)
                | CastPreference.role(SkillRole.SUSTAIN), ReactionPolicy.mask(Reaction.EVADE_CRITICAL));
    }

    private boolean holds(Reaction reaction, long signals, Movement movement) {
        return ReactionPolicy.trigger(reaction, signals, movement, CRITICAL, settings, true, 0, 5);
    }

    private int select(long signals, ReactionScope scope, long mask, long[] cooldown, long[] since, boolean controlled) {
        return ReactionPolicy.select(signals, 5, 0, scope, mask, true, controlled, Movement.APPROACH, CRITICAL,
                settings, true, 0, cooldown, since);
    }
}
