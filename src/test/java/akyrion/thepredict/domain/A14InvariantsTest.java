package akyrion.thepredict.domain;

import akyrion.thepredict.engine.ExploitBudget;
import akyrion.thepredict.engine.PatternCast;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A14InvariantsTest {
    @Test
    void exploitNeverFiresEarlyAndNeverAdvancesABusyCast() {
        var busy = 100L;
        assertFalse(ReactionPolicy.trigger(Reaction.EXPLOIT_PATTERN, 0L, Movement.APPROACH, 0.3,
                MovementSettings.DEFAULTS, true, busy, busy));
        var punish = TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET) | (1L << TickSignals.TARGET_ATTACK_RECHARGING);
        assertTrue(ReactionPolicy.trigger(Reaction.PUNISH_RECHARGE, punish, Movement.APPROACH, 0.3,
                MovementSettings.DEFAULTS, true, busy, busy));
        for (var pattern : EnemyPattern.values()) {
            var delay = CounterMap.of(pattern).delayTicks();
            assertTrue(delay >= 0);
            assertTrue(delay <= 20, pattern.name());
        }
        assertFalse(ReactionPolicy.trigger(Reaction.EXPLOIT_PATTERN, TickSignals.PRESENT, Movement.APPROACH, 0.3,
                MovementSettings.DEFAULTS, true, busy, busy, MovementSettings.DEFAULTS.meleeReachBlocks(), false,
                false));
        assertTrue(ReactionPolicy.trigger(Reaction.EXPLOIT_PATTERN, signals(), Movement.APPROACH, 0.3,
                MovementSettings.DEFAULTS, true, busy, busy, MovementSettings.DEFAULTS.meleeReachBlocks(), false,
                true));
        var since = new long[Reaction.values().length];
        java.util.Arrays.fill(since, -1L);
        since[Reaction.EXPLOIT_PATTERN.ordinal()] = busy;
        var cooldown = new long[Reaction.values().length];
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(signals(), busy, 5, ReactionScope.ALL, ReactionMask.ALL,
                true, true, Movement.APPROACH, 0.3, MovementSettings.DEFAULTS, true, busy + 100, cooldown, since,
                false, false, true));
        assertEquals(Reaction.EXPLOIT_PATTERN.ordinal(), ReactionPolicy.select(signals(), busy + 5, 5,
                ReactionScope.ALL, ReactionMask.ALL, true, true, Movement.APPROACH, 0.3, MovementSettings.DEFAULTS,
                true, busy + 100, cooldown, since, false, false, true));
        var lastOpportunity = -1;
        for (var reaction : Reaction.values()) {
            if (reaction.band() == ReactionBand.OPPORTUNITY) {
                lastOpportunity = reaction.ordinal();
            }
        }
        assertEquals(Reaction.EXPLOIT_PATTERN.ordinal(), lastOpportunity);
    }

    @Test
    void theSameSceneWithAPatternSelectsADifferentCastAndDoesNotStartEarlier() {
        var busy = 100L;
        var since = new long[Reaction.values().length];
        java.util.Arrays.fill(since, -1L);
        since[Reaction.EXPLOIT_PATTERN.ordinal()] = busy;
        var cooldown = new long[Reaction.values().length];
        var earlyWithout = ReactionPolicy.select(signals(), busy, 5, ReactionScope.ALL, ReactionMask.ALL, true, true,
                Movement.APPROACH, 0.3, MovementSettings.DEFAULTS, true, busy + 100, cooldown, since, false, false,
                false);
        var earlyWith = ReactionPolicy.select(signals(), busy, 5, ReactionScope.ALL, ReactionMask.ALL, true, true,
                Movement.APPROACH, 0.3, MovementSettings.DEFAULTS, true, busy + 100, cooldown, since, false, false,
                true);
        assertEquals(ReactionPolicy.NONE, earlyWithout);
        assertEquals(ReactionPolicy.NONE, earlyWith);
        var without = ReactionPolicy.select(signals(), busy + 5, 5, ReactionScope.ALL, ReactionMask.ALL, true, true,
                Movement.APPROACH, 0.3, MovementSettings.DEFAULTS, true, busy + 100, cooldown, since, false, false,
                false);
        var with = ReactionPolicy.select(signals(), busy + 5, 5, ReactionScope.ALL, ReactionMask.ALL, true, true,
                Movement.APPROACH, 0.3, MovementSettings.DEFAULTS, true, busy + 100, cooldown, since, false, false,
                true);
        assertNotEquals(without, with);
        assertEquals(Reaction.EXPLOIT_PATTERN.ordinal(), with);
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(3.0), OptionalDouble.empty(), 0, CooldownClass.NONE, DamageTier.MEDIUM, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        var plain = ReactionPolicy.mask(Reaction.EXPLOIT_PATTERN);
        var biased = CounterMap.of(EnemyPattern.SPAMS_CLICKS).preference();
        assertEquals(0L, plain);
        assertNotEquals(CastPreference.fits(plain, purpose), CastPreference.fits(biased, purpose));
        assertTrue(CastPreference.fits(biased, purpose));
        var factor = CounterMap.of(EnemyPattern.SPAMS_CLICKS).factor();
        assertTrue(factor > 1.0 && factor <= 1.2);
        for (var reason : PruneReason.values()) {
            assertFalse(reason.name().contains("PATTERN"), reason.name());
        }
        assertFalse(PatternCast.liveExploit(true, 0.0, new ExploitBudget(), 10, 400, false));
        var confidence = new double[EnemyPattern.values().length];
        confidence[EnemyPattern.SPAMS_CLICKS.ordinal()] = 0.9;
        confidence[1] = 0.2;
        var narrow = VarietyPicker.select(EnemyPattern.SPAMS_CLICKS.bit() | EnemyPattern.values()[1].bit(),
                confidence, 0, 1);
        var lifted = VarietyPicker.select(EnemyPattern.SPAMS_CLICKS.bit() | EnemyPattern.values()[1].bit(),
                confidence, 0, ExploitPolicy.variety(1, true, true));
        assertNotEquals(narrow, lifted);
        assertFalse(RestStreak.opens(RestStreak.next(0, true), 3));
        assertTrue(RestStreak.opens(3, 3));
    }

    /** A reading with its target, as the reader packs it: target bits and health come only with {@code HAS_TARGET}. */
    private static long signals() {
        return TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET) | (100L << TickSignals.MOB_HEALTH_SHIFT)
                | (100L << TickSignals.TARGET_HEALTH_SHIFT);
    }
}
