package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentionTokenTest {
    @Test
    void constantsFollowThePublishedTable() {
        assertEquals(List.of(
                IntentionToken.DAMAGE, IntentionToken.CONTROL, IntentionToken.GAP_CLOSE, IntentionToken.ESCAPE,
                IntentionToken.DEFENSE, IntentionToken.SUSTAIN, IntentionToken.BUFF, IntentionToken.DEBUFF,
                IntentionToken.SUMMON, IntentionToken.ZONE, IntentionToken.INTERRUPT, IntentionToken.FINISHER,
                IntentionToken.OPENER, IntentionToken.MELEE, IntentionToken.PROJECTILE, IntentionToken.HITSCAN,
                IntentionToken.AREA_AROUND_SELF, IntentionToken.AREA_AT_TARGET, IntentionToken.CONE,
                IntentionToken.SELF, IntentionToken.DAMAGE_LOW, IntentionToken.DAMAGE_MEDIUM,
                IntentionToken.DAMAGE_HIGH, IntentionToken.WHEN_TARGET_LOW_HEALTH,
                IntentionToken.WHEN_TARGET_SHIELDING, IntentionToken.WHEN_TARGET_FLEEING,
                IntentionToken.WHEN_SELF_LOW_HEALTH, IntentionToken.WHEN_ENEMIES_GROUPED,
                IntentionToken.WHEN_TARGET_AT_RANGE, IntentionToken.WHEN_TARGET_IN_MELEE,
                IntentionToken.RISK_LEAVES_CASTER_EXPOSED, IntentionToken.RISK_ROOTS_CASTER,
                IntentionToken.RISK_SELF_DAMAGE, IntentionToken.RISK_NEEDS_LINE_OF_SIGHT,
                IntentionToken.RISK_SLOW_PROJECTILE, IntentionToken.RISK_BLOCKED_BY_SHIELD,
                IntentionToken.RISK_HITS_ALLIES, IntentionToken.IGNORES_SHIELD, IntentionToken.ANSWER_BLOCK,
                IntentionToken.ANSWER_DODGE, IntentionToken.ANSWER_BACK_OFF, IntentionToken.ANSWER_BREAK_SIGHT,
                IntentionToken.ANSWER_INTERRUPT), List.of(IntentionToken.values()));
    }

    @Test
    void theVocabularyHas43PrimitivesOfVersionTwo() {
        assertEquals(43, IntentionToken.values().length);
        for (var token : IntentionToken.values()) {
            assertEquals(token.family() == IntentionToken.Family.ANSWER ? 2 : 1, token.since(), token.name());
        }
        assertEquals(2, IntentionVocabulary.VERSION);
    }

    @Test
    void everyFamilyHasItsSize() {
        assertEquals(13, IntentionVocabulary.family(IntentionToken.Family.ROLE).size());
        assertEquals(7, IntentionVocabulary.family(IntentionToken.Family.DELIVERY).size());
        assertEquals(3, IntentionVocabulary.family(IntentionToken.Family.DAMAGE).size());
        assertEquals(7, IntentionVocabulary.family(IntentionToken.Family.BEST_WHEN).size());
        assertEquals(7, IntentionVocabulary.family(IntentionToken.Family.RISK).size());
        assertEquals(EnumSet.of(IntentionToken.IGNORES_SHIELD), IntentionVocabulary.family(IntentionToken.Family.SHIELD));
        assertEquals(5, IntentionVocabulary.family(IntentionToken.Family.ANSWER).size());
    }

    @Test
    void everyValueOfTheClosedTypesOfM15HasExactlyOnePrimitiveAndBack() {
        for (var role : SkillRole.values()) {
            var token = IntentionToken.of(role);
            assertEquals(IntentionToken.Family.ROLE, token.family());
            assertEquals(role, token.role().orElseThrow());
        }
        for (var delivery : Delivery.values()) {
            assertEquals(delivery, IntentionToken.of(delivery).delivery().orElseThrow());
        }
        for (var condition : UseCondition.values()) {
            assertEquals(condition, IntentionToken.of(condition).condition().orElseThrow());
        }
        for (var risk : SkillRisk.values()) {
            assertEquals(risk, IntentionToken.of(risk).risk().orElseThrow());
        }
        for (var tier : EnumSet.of(DamageTier.LOW, DamageTier.MEDIUM, DamageTier.HIGH)) {
            assertEquals(tier, IntentionToken.of(tier).damage().orElseThrow());
        }
        assertEquals(SkillRole.values().length + Delivery.values().length + UseCondition.values().length
                + SkillRisk.values().length + 3 + 1 + TellAnswer.values().length, IntentionToken.values().length);
    }

    @Test
    void eachPrimitiveMeansOnlyItsOwnFamily() {
        for (var token : IntentionToken.values()) {
            var meanings = (token.role().isPresent() ? 1 : 0) + (token.delivery().isPresent() ? 1 : 0)
                    + (token.damage().isPresent() ? 1 : 0) + (token.condition().isPresent() ? 1 : 0)
                    + (token.risk().isPresent() ? 1 : 0) + (token.answer().isPresent() ? 1 : 0);
            assertEquals(token.family() == IntentionToken.Family.SHIELD ? 0 : 1, meanings, token.name());
        }
    }

    @Test
    void unratedAndNoneHaveNoPrimitive() {
        assertThrows(IllegalArgumentException.class, () -> IntentionToken.of(DamageTier.UNRATED));
        assertThrows(IllegalArgumentException.class, () -> IntentionToken.of(DamageTier.NONE));
    }

    @Test
    void thePrefixesSeparateTheFamiliesInAFlatList() {
        for (var token : IntentionToken.values()) {
            var name = token.name();
            switch (token.family()) {
                case DAMAGE -> assertTrue(name.startsWith("DAMAGE_"));
                case BEST_WHEN -> assertTrue(name.startsWith("WHEN_"));
                case RISK -> assertTrue(name.startsWith("RISK_"));
                case ANSWER -> assertTrue(name.startsWith("ANSWER_"));
                default -> assertTrue(!name.startsWith("WHEN_") && !name.startsWith("RISK_")
                        && !name.startsWith("DAMAGE_") && !name.startsWith("ANSWER_"), name);
            }
        }
    }
}
