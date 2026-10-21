package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A12 as a property: a player beside the void changes nothing but the push decision (M24.6). */
class PushFairnessTest {
    private static final long TARGET = (1L << TickSignals.VALID) | (1L << TickSignals.HAS_TARGET);
    private static final long[] POSTURES = {0L, 1L << TickSignals.TARGET_BLOCKING,
            1L << TickSignals.TARGET_ATTACK_RECHARGING, 1L << TickSignals.TARGET_CONSUMING,
            1L << TickSignals.INCOMING_PROJECTILE};

    @Test
    void anEntityIsAllowedAndAPlayerLethalMediumIsNot() {
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(false, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.LIGHT, Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE));
        assertEquals(PushDecision.DEFER, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.MEDIUM, Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE, true));
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.NONE,
                DangerClass.MEDIUM, Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE));
        assertFalse(ZoneFact.TARGET_NEAR_DROP.exposed());
    }

    @Test
    void tenThousandSituationsDoNotRewardAVoidBesideAPlayer() {
        var drop = 1 << ZoneFact.TARGET_NEAR_DROP.ordinal();
        var cooldown = new long[Reaction.values().length];
        var armed = new long[Reaction.values().length];
        var fired = 0;
        for (var index = 0; index < 10_000; index++) {
            var movement = Movement.values()[index % Movement.values().length];
            var plain = TerrainScore.factor(movement, 0, (index & 1) == 0, true);
            var withVoid = TerrainScore.factor(movement, drop, (index & 1) == 0, true);
            var flags = TARGET | POSTURES[index % POSTURES.length];
            var gap = index % 48;
            var mobHealth = 5 + index % 95;
            var targetHealth = 5 + (index / 7) % 95;
            var signals = TickSignals.pack(flags, gap, index % 5, index % 5, index % 8, mobHealth, targetHealth,
                    PushDanger.NONE.ordinal(), PushDanger.NONE.ordinal(), PushDanger.NONE.ordinal(),
                    PushDanger.NONE.ordinal());
            var lethal = TickSignals.pack(flags, gap, index % 5, index % 5, index % 8, mobHealth, targetHealth,
                    PushDanger.NONE.ordinal(), PushDanger.NONE.ordinal(), PushDanger.LETHAL.ordinal(),
                    PushDanger.LETHAL.ordinal());
            var now = 100L + index;
            var busy = now - index % 4;
            var reaction = ReactionPolicy.select(signals, now, 0, ReactionScope.ALL, ReactionMask.ALL, true, true,
                    movement, 0.3, MovementSettings.DEFAULTS, true, busy, cooldown, armed, true, false);
            var withHazard = ReactionPolicy.select(lethal, now, 0, ReactionScope.ALL, ReactionMask.ALL, true, true,
                    movement, 0.3, MovementSettings.DEFAULTS, true, busy, cooldown, armed, true, false);
            assertEquals(reaction, withHazard, "situation " + index);
            fired += reaction >= 0 ? 1 : 0;
            var micro = MicroPolicy.choose(movement, signals, reaction, false, true, 3.0, MovementSettings.DEFAULTS);
            assertEquals(micro, MicroPolicy.choose(movement, lethal, withHazard, false, true, 3.0,
                    MovementSettings.DEFAULTS));
            var tactics = List.of(new TacticId("alpha"), new TacticId("bravo"));
            assertEquals(CandidatePolicy.select(index, index, candidates(movement, plain, index), 2, true, tactics),
                    CandidatePolicy.select(index, index, candidates(movement, withVoid, index), 2, true, tactics));
        }
        assertTrue(fired > 1_000, "reactions fired " + fired);
    }

    private static List<CandidateIntent> candidates(Movement movement, double terrain, int index) {
        var factors = new double[] {terrain, UtilityScore.priority(index % 100), UtilityScore.shield(false)};
        var scored = UtilityScore.scoreMillis(factors, (index & 2) == 0);
        return List.of(
                new CandidateIntent(new TacticId("alpha"), movement, Optional.empty(), scored, 0, DamageTier.NONE),
                new CandidateIntent(new TacticId("bravo"), Movement.HOLD, Optional.empty(), scored / 2, 0,
                        DamageTier.NONE));
    }
}
