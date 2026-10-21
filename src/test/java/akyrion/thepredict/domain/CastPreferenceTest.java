package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CastPreferenceTest {
    @Test
    void groupsCombineWithAndAndTheShieldBitIsSeparate() {
        var sustain = purpose(Set.of(SkillRole.SUSTAIN), Delivery.SELF, DamageTier.NONE, false);
        var escape = purpose(Set.of(SkillRole.ESCAPE), Delivery.SELF, DamageTier.NONE, false);
        var shield = purpose(Set.of(SkillRole.CONTROL), Delivery.MELEE, DamageTier.LOW, true);
        assertTrue(CastPreference.fits(CastPreference.role(SkillRole.SUSTAIN), sustain));
        assertFalse(CastPreference.fits(CastPreference.role(SkillRole.ESCAPE), sustain));
        assertTrue(CastPreference.fits(ReactionPolicy.mask(Reaction.EVADE_CRITICAL), escape));
        assertTrue(CastPreference.fits(CastPreference.ignoresShield(), shield));
        assertFalse(CastPreference.fits(0L, sustain));
        var both = CastPreference.role(SkillRole.DAMAGE) | CastPreference.delivery(Delivery.MELEE);
        assertTrue(CastPreference.fits(both, purpose(Set.of(SkillRole.DAMAGE), Delivery.MELEE, DamageTier.HIGH, false)));
        assertFalse(CastPreference.fits(both, purpose(Set.of(SkillRole.DAMAGE), Delivery.PROJECTILE, DamageTier.HIGH,
                false)));
    }

    @Test
    void withoutChoiceBitsAChoiceFitsExactlyAsItsPurpose() {
        var masks = new long[] {0L, CastPreference.role(SkillRole.DAMAGE), CastPreference.ignoresShield(),
                CastPreference.damage(DamageTier.MEDIUM) | CastPreference.delivery(Delivery.MELEE),
                ReactionPolicy.mask(Reaction.EVADE_CRITICAL), ReactionPolicy.mask(Reaction.RESPECT_SHIELD)};
        var choices = new SkillChoice[] {choice("swing", melee(false)), choice("bolt", shot()),
                choice("breaker", purpose(Set.of(SkillRole.DAMAGE), Delivery.MELEE, DamageTier.LOW, true)),
                choice("mend", purpose(Set.of(SkillRole.SUSTAIN), Delivery.SELF, DamageTier.NONE, false))};
        for (var mask : masks) {
            for (var choice : choices) {
                assertEquals(CastPreference.fits(mask, choice.typedPurpose()), CastPreference.fits(mask, choice),
                        choice.id().value() + " " + Long.toBinaryString(mask));
            }
        }
        assertFalse(CastPreference.fits(CastPreference.role(SkillRole.DAMAGE),
                SkillChoice.untyped(new ChoiceId("open"), "Open", Set.of(), true, 0)), "an untyped choice never fits");
    }

    @Test
    void theDisplacementBitAsksForAChoiceThatPushesItsTarget() {
        var control = purpose(Set.of(SkillRole.CONTROL), Delivery.MELEE, DamageTier.LOW, false);
        var slow = choice("slow", control).withDisplacement(Optional.of(DisplacementKind.NONE));
        var bash = choice("bash", control).withDisplacement(Optional.of(DisplacementKind.STRONG_PUSH));
        var shove = choice("shove", melee(false)).withDisplacement(Optional.of(DisplacementKind.KNOCKBACK));
        var hazard = ReactionPolicy.mask(Reaction.HAZARD_PUSH);
        assertTrue(CastPreference.fits(hazard, slow.typedPurpose()), "the purpose rule cannot see the push");
        assertFalse(CastPreference.fits(hazard, slow));
        assertTrue(CastPreference.fits(hazard, bash));
        assertFalse(CastPreference.fits(hazard, shove), "the CONTROL group still applies");
        assertTrue(CastPreference.fits(CastPreference.displacesTarget(), shove));
        assertFalse(CastPreference.fits(CastPreference.displacesTarget(), slow));
        var unread = choice("unread", melee(false)).withNativeFacts(NativeSkillFacts.ASSUMED_KNOCKBACK);
        assertTrue(CastPreference.fits(CastPreference.displacesTarget(), unread),
                "a damaging choice the binder could not read is assumed to knock back, as rule A12 assumes");
        assertFalse(CastPreference.fits(CastPreference.displacesTarget(), choice("unbound", melee(false))),
                "before binding nothing was read and nothing is assumed");
    }

    @Test
    void theAnswerBitsAskForAChoiceWhoseTellAsksForThatAnswer() {
        var swing = choice("swing", melee(false));
        var bolt = choice("bolt", shot());
        var feint = choice("feint", shot()).withTelegraph(new ChoiceTelegraph(Optional.empty(),
                Optional.of(TellAnswer.BLOCK), OptionalInt.empty()));
        var block = CastPreference.answer(TellAnswer.BLOCK);
        assertFalse(CastPreference.fits(block, swing.typedPurpose()), "a purpose has no tell");
        assertTrue(CastPreference.fits(block, swing));
        assertFalse(CastPreference.fits(block, bolt));
        assertTrue(CastPreference.fits(block, feint), "a declared answer wins over the derived one");
        assertTrue(CastPreference.fits(CounterMap.of(EnemyPattern.FAILS_ANSWER_DODGE).preference(), bolt));
        var mixed = CastPreference.damage(DamageTier.MEDIUM) | block;
        assertTrue(CastPreference.fits(mixed, swing));
        assertFalse(CastPreference.fits(mixed, bolt), "groups combine with and");
        var breaker = choice("breaker", purpose(Set.of(SkillRole.DAMAGE), Delivery.MELEE, DamageTier.LOW, true));
        assertFalse(CastPreference.fits(block, breaker), "a shield breaker asks to back off");
        assertTrue(CastPreference.fits(block | CastPreference.ignoresShield(), breaker), "the shield bit stays an or");
    }

    private static SkillChoice choice(String id, SkillPurpose purpose) {
        return SkillChoice.typed(new ChoiceId(id), "Skill_" + id, Set.of(), true, 0, purpose);
    }

    private static SkillPurpose melee(boolean shield) {
        return purpose(Set.of(SkillRole.DAMAGE), Delivery.MELEE, DamageTier.MEDIUM, shield);
    }

    private static SkillPurpose shot() {
        return purpose(Set.of(SkillRole.DAMAGE), Delivery.PROJECTILE, DamageTier.MEDIUM, false);
    }

    private static SkillPurpose purpose(Set<SkillRole> roles, Delivery delivery, DamageTier damage, boolean shield) {
        var reach = delivery == Delivery.SELF || delivery == Delivery.AREA_AROUND_SELF
                ? OptionalDouble.empty() : OptionalDouble.of(3);
        return new SkillPurpose(roles.iterator().next(), roles, delivery, reach, OptionalDouble.empty(),
                0, CooldownClass.UNKNOWN, damage, Set.of(), Set.of(), shield, PurposeOrigin.DECLARED);
    }
}
