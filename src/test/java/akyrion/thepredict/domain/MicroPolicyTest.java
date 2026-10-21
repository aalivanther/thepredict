package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The micro table of M20.5 row by row, the action stances of M23.6, durations and sides. */
class MicroPolicyTest {
    private static final long TARGET = (1L << TickSignals.VALID) | (1L << TickSignals.HAS_TARGET);
    private static final double REACH = 3.0;

    private final MovementSettings settings = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty());

    @Test
    void hoverAndPunishRechargeNeedAPlayer() {
        var close = gap(4);
        assertEquals(MicroBehaviour.HOVER, choose(Movement.APPROACH, close, ReactionPolicy.NONE, true));
        assertEquals(MicroBehaviour.NATIVE, choose(Movement.APPROACH, close, ReactionPolicy.NONE, false));
        var recharging = TickSignals.pack(TARGET | (1L << TickSignals.TARGET_ATTACK_RECHARGING), 4, 0, 0, 0, 80, 80);
        assertEquals(MicroBehaviour.ENTER, choose(Movement.APPROACH, recharging, Reaction.PUNISH_RECHARGE.ordinal(),
                true));
        assertEquals(MicroBehaviour.NATIVE, choose(Movement.APPROACH, recharging, ReactionPolicy.NONE, true));
    }

    @Test
    void approachIsNativeOutsideTheInnerBandAndBeyondReachPlusOne() {
        assertEquals(MicroBehaviour.NATIVE, choose(Movement.APPROACH, gap(21), ReactionPolicy.NONE, true));
        assertEquals(MicroBehaviour.NATIVE, choose(Movement.APPROACH, gap(17), ReactionPolicy.NONE, true));
        assertEquals(MicroBehaviour.HOVER, choose(Movement.APPROACH, gap(16), ReactionPolicy.NONE, true));
        assertEquals(MicroBehaviour.ENTER, choose(Movement.APPROACH, gap(4), Reaction.PUNISH_CONSUMING.ordinal(),
                false));
    }

    @Test
    void keepDistanceOrbitsInsideTheBandOnly() {
        assertEquals(MicroBehaviour.ORBIT, choose(Movement.KEEP_DISTANCE, gap(20), ReactionPolicy.NONE, true));
        assertEquals(MicroBehaviour.ORBIT, choose(Movement.KEEP_DISTANCE, gap(36), ReactionPolicy.NONE, true));
        assertEquals(MicroBehaviour.NATIVE, choose(Movement.KEEP_DISTANCE, gap(19), ReactionPolicy.NONE, true));
        assertEquals(MicroBehaviour.NATIVE, choose(Movement.KEEP_DISTANCE, gap(37), ReactionPolicy.NONE, true));
    }

    @Test
    void holdRetreatFlankAndNoneStayNativeWithoutAReaction() {
        for (var movement : new Movement[] {Movement.HOLD, Movement.RETREAT, Movement.FLANK, Movement.NONE}) {
            assertEquals(MicroBehaviour.NATIVE, choose(movement, gap(4), ReactionPolicy.NONE, true), movement.name());
        }
    }

    @Test
    void reactionsPickTheirMicroWhereTheTableAllowsIt() {
        var dodge = Reaction.DODGE_SHOT.ordinal();
        for (var movement : Movement.values()) {
            var expected = movement == Movement.FLANK ? MicroBehaviour.NATIVE : MicroBehaviour.SIDESTEP;
            assertEquals(expected, choose(movement, gap(40), dodge, true), movement.name());
        }
        var shield = Reaction.RESPECT_SHIELD.ordinal();
        assertEquals(MicroBehaviour.ORBIT, choose(Movement.APPROACH, gap(30), shield, true));
        assertEquals(MicroBehaviour.ORBIT, choose(Movement.HOLD, gap(30), shield, true));
        assertEquals(MicroBehaviour.NATIVE, choose(Movement.RETREAT, gap(30), shield, true));
        var space = Reaction.SPACE_OUT.ordinal();
        assertEquals(MicroBehaviour.BACKSTEP, choose(Movement.KEEP_DISTANCE, gap(8), space, true));
        assertEquals(MicroBehaviour.BACKSTEP, choose(Movement.HOLD, gap(8), space, true));
        assertEquals(MicroBehaviour.NATIVE, choose(Movement.RETREAT, gap(8), space, true));
        var evade = Reaction.EVADE_CRITICAL.ordinal();
        assertEquals(MicroBehaviour.BACKSTEP, MicroPolicy.choose(Movement.HOLD, gap(8), evade, false, true, REACH,
                settings));
        assertEquals(MicroBehaviour.NATIVE, MicroPolicy.choose(Movement.HOLD, gap(8), evade, true, true, REACH,
                settings));
        assertEquals(MicroBehaviour.REPOSITION, choose(Movement.FLANK, gap(8), Reaction.EDGE_DANGER.ordinal(), true));
    }

    @Test
    void windUpAndStaggerStayStillAndRecoveryKeepsOnlyTheEvasiveMicros() {
        var dodge = Reaction.DODGE_SHOT.ordinal();
        for (var action : new ActionStance[] {ActionStance.WINDUP, ActionStance.STAGGERED}) {
            assertEquals(MicroBehaviour.STILL, MicroPolicy.choose(action, Movement.HOLD, gap(40), dodge, false, true,
                    REACH, settings));
        }
        assertEquals(MicroBehaviour.SIDESTEP, MicroPolicy.choose(ActionStance.RECOVERY, Movement.HOLD, gap(40), dodge,
                false, true, REACH, settings));
        assertEquals(MicroBehaviour.BACKSTEP, MicroPolicy.choose(ActionStance.RECOVERY, Movement.HOLD, gap(8),
                Reaction.SPACE_OUT.ordinal(), false, true, REACH, settings));
        assertEquals(MicroBehaviour.ORBIT, MicroPolicy.choose(ActionStance.RECOVERY, Movement.KEEP_DISTANCE, gap(24),
                ReactionPolicy.NONE, false, true, REACH, settings));
        assertEquals(MicroBehaviour.NATIVE, MicroPolicy.choose(ActionStance.RECOVERY, Movement.APPROACH, gap(4),
                Reaction.PUNISH_CONSUMING.ordinal(), false, true, REACH, settings));
        assertEquals(MicroBehaviour.NATIVE, MicroPolicy.choose(ActionStance.RECOVERY, Movement.APPROACH, gap(4),
                ReactionPolicy.NONE, false, true, REACH, settings));
        assertEquals(choose(Movement.APPROACH, gap(4), ReactionPolicy.NONE, true),
                MicroPolicy.choose(ActionStance.NONE, Movement.APPROACH, gap(4), ReactionPolicy.NONE, false, true,
                        REACH, settings));
    }

    @Test
    void durationsAndSidesFollowTheTable() {
        assertEquals(6, MicroPolicy.durationTicks(MicroBehaviour.SIDESTEP));
        assertEquals(4, MicroPolicy.durationTicks(MicroBehaviour.BACKSTEP));
        assertEquals(20, MicroPolicy.durationTicks(MicroBehaviour.ENTER));
        assertEquals(0, MicroPolicy.durationTicks(MicroBehaviour.ORBIT));
        var left = TickSignals.pack(TARGET | (1L << TickSignals.MOB_LEFT_OF_TARGET), 8, 0, 0, 0, 80, 80);
        var shield = Reaction.RESPECT_SHIELD.ordinal();
        assertEquals(MicroSide.LEFT, MicroPolicy.side(MicroBehaviour.ORBIT, shield, left, 1L, 1L, MicroSide.LEFT,
                true));
        assertEquals(MicroSide.RIGHT, MicroPolicy.side(MicroBehaviour.ORBIT, shield, gap(8), 1L, 1L, MicroSide.LEFT,
                true));
        assertEquals(MicroSide.RIGHT, MicroPolicy.side(MicroBehaviour.SIDESTEP, ReactionPolicy.NONE, gap(8), 1L, 1L,
                MicroSide.LEFT, false));
        assertEquals(MicroSide.LEFT, MicroPolicy.side(MicroBehaviour.SIDESTEP, ReactionPolicy.NONE, gap(8), 1L, 1L,
                MicroSide.RIGHT, false));
        assertEquals(MicroPolicy.side(MicroBehaviour.ORBIT, ReactionPolicy.NONE, gap(8), 7L, 3L, MicroSide.LEFT, true),
                MicroPolicy.side(MicroBehaviour.ORBIT, ReactionPolicy.NONE, gap(8), 7L, 3L, MicroSide.RIGHT, false));
        assertTrue(MicroPolicy.flipOrbit(7L, 10L, 5L, true));
        assertFalse(MicroPolicy.flipOrbit(7L, 44L, 5L, false));
        assertTrue(MicroPolicy.flipOrbit(7L, 85L, 5L, false));
    }

    private MicroBehaviour choose(Movement movement, long signals, int reaction, boolean player) {
        return MicroPolicy.choose(movement, signals, reaction, false, player, REACH, settings);
    }

    private static long gap(int quarters) {
        return TickSignals.pack(TARGET, quarters, 0, 0, 0, 80, 80);
    }
}
