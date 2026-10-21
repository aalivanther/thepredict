package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidatePolicyTest {
    @Test
    void priorityKeepsTheHighestScoreFirst() {
        var low = new CandidateIntent(new TacticId("hold_ground"), Movement.HOLD, Optional.empty(), 10, 0);
        var high = new CandidateIntent(new TacticId("press_attack"), Movement.APPROACH, Optional.empty(), 40, 0);
        var set = CandidatePolicy.select(1, 0, List.of(low, high), 8, false, List.of(low.tactic(), high.tactic()));
        assertEquals(high.tactic(), set.candidates().getFirst().tactic());
        assertEquals(List.of(low.tactic(), high.tactic()), set.questionOrder());
    }

    @Test
    void theScaleKeepsTheLocalPlannerFirstAboveAScoreOfOneHundred() {
        var first = DomainFixtures.tacticOf("strong_hit", Movement.APPROACH, "plain", 100);
        var second = DomainFixtures.tacticOf("close_hit", Movement.APPROACH, "close", 90);
        var profile = DomainFixtures.profile(List.of(first, second),
                List.of(DomainFixtures.declared("plain", DomainFixtures.meleePurpose(3)),
                        DomainFixtures.declared("close", DomainFixtures.meleePurpose(3, UseCondition.TARGET_IN_MELEE))),
                List.of(), DomainFixtures.timing());
        var now = Set.of(UseCondition.TARGET_IN_MELEE);
        var local = LocalPlanner.choose(profile, Set.of(), Set.of(first.id(), second.id()), now).orElseThrow();
        assertEquals(second.id(), local.id());
        assertTrue(CandidatePolicy.priorityMillis(profile, second, now)
                > CandidatePolicy.priorityMillis(profile, first, now));
        assertEquals(local.id(), CandidatePolicy.rank(CandidateScoring.PRIORITY, profile, List.of(first, second), now, 0)
                .order().get(0));
    }

    @Test
    void priorityOrderStartsWithTheLocalPlannerForAnyProfile() {
        for (var seed = 1; seed <= 200; seed++) {
            var word = StableRandom.mix(20L ^ seed);
            var count = 2 + (int) (word & 3L);
            var tactics = new ArrayList<Tactic>();
            var choices = new ArrayList<SkillChoice>();
            for (var index = 0; index < count; index++) {
                var bits = StableRandom.mix(word + index);
                var choice = "c" + index;
                choices.add(DomainFixtures.declared(choice, DomainFixtures.meleePurpose(3, conditions(bits >>> 8))));
                tactics.add(DomainFixtures.tacticOf("t" + index + "_x", Movement.APPROACH, choice,
                        (int) Math.floorMod(bits, 101L)));
            }
            var profile = DomainFixtures.profile(tactics, choices, List.of(), DomainFixtures.timing());
            var now = EnumSet.noneOf(UseCondition.class);
            now.addAll(Set.of(conditions(word >>> 32)));
            var eligible = profile.tactics().keySet();
            var local = LocalPlanner.choose(profile, Set.of(), eligible, now).orElseThrow();
            assertEquals(local.id(), CandidatePolicy.rank(CandidateScoring.PRIORITY, profile, tactics, now, 0)
                    .order().get(0), "seed " + seed);
        }
    }

    @Test
    void aPatternFactorStillLiftsATacticOfHighPriority() {
        var plain = new SkillPurpose(SkillRole.CONTROL, Set.of(SkillRole.CONTROL), Delivery.SELF,
                OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE,
                Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        var high = DomainFixtures.tacticOf("keep_back", Movement.KEEP_DISTANCE, "far", 100);
        var hit = DomainFixtures.tacticOf("hold_hit", Movement.HOLD, "hit", 95);
        var profile = DomainFixtures.profile(List.of(high, hit), List.of(DomainFixtures.declared("far", plain),
                DomainFixtures.declared("hit", DomainFixtures.meleePurpose(3))), List.of(), DomainFixtures.timing());
        var mask = EnemyPattern.SPAMS_CLICKS.bit();
        assertTrue(CandidatePolicy.factorFor(CandidateScoring.UTILITY, hit, profile, mask) > 1.0);
        var lifted = CandidatePolicy.rank(CandidateScoring.UTILITY, profile, List.of(high, hit), Set.of(), mask);
        assertEquals(hit.id(), lifted.order().get(0));
        assertTrue(lifted.raisedByPattern());
        assertFalse(CandidatePolicy.rank(CandidateScoring.PRIORITY, profile, List.of(high, hit), Set.of(), mask)
                .raisedByPattern());
    }

    @Test
    void aFailedAnswerPatternLiftsOnlyTheTacticWhoseTellAsksForThatAnswer() {
        var swing = DomainFixtures.tacticOf("hold_swing", Movement.HOLD, "swing", 50);
        var shot = DomainFixtures.tacticOf("hold_shot", Movement.HOLD, "shot", 50);
        var profile = DomainFixtures.profile(List.of(swing, shot),
                List.of(DomainFixtures.declared("swing", DomainFixtures.meleePurpose(3)),
                        DomainFixtures.declared("shot", DomainFixtures.purpose(Delivery.PROJECTILE,
                                OptionalDouble.of(8), OptionalDouble.empty()))),
                List.of(), DomainFixtures.timing());
        var block = EnemyPattern.FAILS_ANSWER_BLOCK.bit();
        assertEquals(CounterMap.of(EnemyPattern.FAILS_ANSWER_BLOCK).factor(),
                CandidatePolicy.factorFor(CandidateScoring.UTILITY, swing, profile, block), 1e-12);
        assertEquals(1.0, CandidatePolicy.factorFor(CandidateScoring.UTILITY, shot, profile, block), 1e-12);
        var lifted = CandidatePolicy.rank(CandidateScoring.UTILITY, profile, List.of(shot, swing), Set.of(), block);
        assertEquals(swing.id(), lifted.order().get(0));
        assertTrue(lifted.raisedByPattern());
    }

    @Test
    void selectKeepsTheLimitAndDiversityPutsEachMovementFirst() {
        var press = candidate("press_attack", Movement.APPROACH, 900);
        var lunge = candidate("lunge_in", Movement.APPROACH, 800);
        var hold = candidate("hold_ground", Movement.HOLD, 700);
        var back = candidate("back_off", Movement.RETREAT, 600);
        var scored = List.of(lunge, hold, press, back);
        var order = List.of(lunge.tactic(), hold.tactic(), press.tactic(), back.tactic());
        assertEquals(List.of(press, lunge, hold), CandidatePolicy.select(1, 0, scored, 3, false, order).candidates());
        assertEquals(List.of(press, hold, back), CandidatePolicy.select(1, 0, scored, 3, true, order).candidates());
        assertEquals(List.of(press, hold, back, lunge), CandidatePolicy.select(1, 0, scored, 4, true, order)
                .candidates());
        assertEquals(order, CandidatePolicy.select(1, 0, scored, 2, true, order).questionOrder());
        assertThrows(IllegalArgumentException.class, () -> CandidatePolicy.select(1, 0, scored, 0, true, order));
    }

    @Test
    void readyInTicksAddsTheBusyRemainderAndSaturates() {
        assertEquals(5, CandidatePolicy.readyInTicks(5, 10, 4, ActionStance.NONE));
        assertEquals(5, CandidatePolicy.readyInTicks(5, 4, 4, ActionStance.WINDUP));
        assertEquals(11, CandidatePolicy.readyInTicks(5, 10, 4, ActionStance.WINDUP));
        assertEquals(Integer.MAX_VALUE, CandidatePolicy.readyInTicks(10, Long.MAX_VALUE, 0, ActionStance.RECOVERY));
        assertEquals(Integer.MAX_VALUE, CandidatePolicy.readyInTicks(Integer.MAX_VALUE, 1, 0, ActionStance.WINDUP));
        assertThrows(IllegalArgumentException.class, () -> CandidatePolicy.readyInTicks(-1, 0, 0, ActionStance.NONE));
    }

    @Test
    void learnedOrderBreaksTiesByDeclarationAndChecksTheScores() {
        var first = DomainFixtures.tacticOf("aaa_move", Movement.HOLD, null, 1);
        var second = DomainFixtures.tacticOf("bbb_move", Movement.HOLD, null, 1);
        var third = DomainFixtures.tacticOf("ccc_move", Movement.HOLD, null, 1);
        var tactics = List.of(first, second, third);
        assertEquals(List.of(second.id(), first.id(), third.id()),
                CandidatePolicy.learnedOrder(tactics, new int[] {5, 9, 5}));
        assertEquals(List.of(first.id(), second.id(), third.id()),
                CandidatePolicy.learnedOrder(tactics, new int[] {7, 7, 7}));
        assertThrows(IllegalArgumentException.class, () -> CandidatePolicy.learnedOrder(tactics, new int[] {1, 2}));
    }

    private static CandidateIntent candidate(String tactic, Movement movement, int score) {
        return new CandidateIntent(new TacticId(tactic), movement, Optional.empty(), score, 0);
    }

    private static UseCondition[] conditions(long bits) {
        var found = new LinkedHashSet<UseCondition>();
        var all = UseCondition.values();
        for (var index = 0; index < all.length && found.size() < SkillPurpose.MAX_BEST_WHEN; index++) {
            if (((bits >>> index) & 1L) != 0L) {
                found.add(all[index]);
            }
        }
        return found.toArray(UseCondition[]::new);
    }
}
