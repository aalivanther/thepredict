package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyPatternConsiderationTest {
    @Test
    void thePatternFactorCanChangeTheUtilityOrder() {
        var plainPurpose = new SkillPurpose(SkillRole.CONTROL, Set.of(SkillRole.CONTROL), Delivery.SELF,
                OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE,
                Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        var high = DomainFixtures.tacticOf("keep_back", Movement.KEEP_DISTANCE, "far", 50);
        var low = DomainFixtures.tacticOf("hold_hit", Movement.HOLD, "hit", 46);
        var profile = DomainFixtures.profile(List.of(high, low),
                List.of(DomainFixtures.declared("far", plainPurpose),
                        DomainFixtures.declared("hit", DomainFixtures.meleePurpose(3))),
                List.of(), DomainFixtures.timing());
        var mask = EnemyPattern.SPAMS_CLICKS.bit();
        var now = Set.<UseCondition>of();
        var tactics = List.of(high, low);
        var factor = CandidatePolicy.factorFor(CandidateScoring.UTILITY, low, profile, mask);
        assertTrue(factor > 1.0 && factor <= 1.2);
        assertEquals(1.0, CandidatePolicy.factorFor(CandidateScoring.UTILITY, high, profile, mask));
        assertEquals(1.0, CandidatePolicy.factorFor(CandidateScoring.PRIORITY, low, profile, mask));
        assertTrue(CandidatePolicy.priorityMillis(profile, low, now, factor)
                > CandidatePolicy.priorityMillis(profile, high, now, 1.0));
        var lifted = CandidatePolicy.rank(CandidateScoring.UTILITY, profile, tactics, now, mask);
        var plain = CandidatePolicy.rank(CandidateScoring.UTILITY, profile, tactics, now, 0);
        var priority = CandidatePolicy.rank(CandidateScoring.PRIORITY, profile, tactics, now, mask);
        assertEquals(low.id(), lifted.order().get(0));
        assertEquals(high.id(), plain.order().get(0));
        assertEquals(high.id(), priority.order().get(0));
        assertNotEquals(lifted.order().get(0), plain.order().get(0));
        assertTrue(lifted.raisedByPattern());
        assertFalse(plain.raisedByPattern());
        assertFalse(priority.raisedByPattern());
        assertTrue(CandidatePolicy.lifts(lifted, profile, low.choice().orElseThrow()));
        assertFalse(CandidatePolicy.lifts(plain, profile, low.choice().orElseThrow()));
        assertFalse(CandidatePolicy.lifts(lifted, profile, high.choice().orElseThrow()));
    }

    @Test
    void thePatternFactorNeverDropsAScore() {
        assertEquals(Consideration.LEARNED_VALUE, Consideration.values()[Consideration.values().length - 1]);
        assertEquals(Consideration.ENEMY_PATTERN,
                Consideration.values()[Consideration.LEARNED_VALUE.ordinal() - 1]);
        for (var pattern : EnemyPattern.values()) {
            var factor = CounterMap.of(pattern).factor();
            for (var score = 0; score <= 1000; score += 25) {
                var lifted = UtilityScore.withPattern(score, factor);
                assertTrue(lifted >= score && lifted <= 1000, pattern + " " + score + " -> " + lifted);
            }
        }
        assertEquals(1000, UtilityScore.withPattern(900, 1.2));
        assertEquals(500, UtilityScore.withPattern(500, 0.5), "a factor below one never lowers a score");
    }
}
