package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.choice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillChoiceTest {
    @Test
    void mythicSkillRequiresASkillId() {
        var error = assertThrows(IllegalArgumentException.class, () -> new SkillChoice(choice("strike"),
                ChoiceKind.MYTHIC_SKILL, Optional.empty(), Set.of(), true, 0));
        assertTrue(error.getMessage().contains("mythicSkillId"));
        assertThrows(IllegalArgumentException.class, () -> new SkillChoice(choice("strike"),
                ChoiceKind.MYTHIC_SKILL, Optional.of(" "), Set.of(), true, 0));
    }

    @Test
    void vanillaMeleeRejectsASkillId() {
        var error = assertThrows(IllegalArgumentException.class, () -> new SkillChoice(choice("punch"),
                ChoiceKind.VANILLA_MELEE, Optional.of("Punch"), Set.of(), true, 0));
        assertTrue(error.getMessage().contains("mythicSkillId"));
        assertEquals(ChoiceKind.VANILLA_MELEE, new SkillChoice(choice("punch"), ChoiceKind.VANILLA_MELEE,
                Optional.empty(), Set.of(PredictValue.MELEE_ATTACK), true, 0).kind());
    }

    @Test
    void negativeExtraCooldownIsRejected() {
        var error = assertThrows(IllegalArgumentException.class, () -> new SkillChoice(choice("strike"),
                ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"), Set.of(), true, -1));
        assertTrue(error.getMessage().contains("extraCooldownTicks"));
    }

    @Test
    void valuesAreCopied() {
        var values = EnumSet.of(PredictValue.DEFENSE);
        var skill = new SkillChoice(choice("guard"), ChoiceKind.MYTHIC_SKILL, Optional.of("Guard"), values, false, 5);
        values.add(PredictValue.HIGH_DAMAGE);
        assertEquals(Set.of(PredictValue.DEFENSE), skill.values());
    }

    @Test
    void aVanillaMeleeChoiceMustBeADamagingMeleeHit() {
        var projectile = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.PROJECTILE,
                OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.LOW, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        var error = assertThrows(IllegalArgumentException.class, () -> new SkillChoice(choice("punch"),
                ChoiceKind.VANILLA_MELEE, Optional.empty(), Set.of(), true, 0, projectile));
        assertTrue(error.getMessage().contains("delivery MELEE"));
        var control = new SkillPurpose(SkillRole.CONTROL, Set.of(SkillRole.CONTROL), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.LOW, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        assertThrows(IllegalArgumentException.class, () -> new SkillChoice(choice("punch"), ChoiceKind.VANILLA_MELEE,
                Optional.empty(), Set.of(), true, 0, control));
        new SkillChoice(choice("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"), Set.of(), true, 0, projectile);
    }

    @Test
    void theCompatibilityConstructorLeavesASkillUntypedAndTypesVanillaMelee() {
        var skill = new SkillChoice(choice("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"),
                Set.of(PredictValue.MELEE_ATTACK, PredictValue.HIGH_DAMAGE), true, 0);
        assertTrue(skill.purpose().isEmpty(), "PredictValues no longer infer a purpose (M19, decision 5)");
        var punch = new SkillChoice(choice("punch"), ChoiceKind.VANILLA_MELEE, Optional.empty(), Set.of(), true, 0);
        assertEquals(Delivery.MELEE, punch.typedPurpose().delivery());
        assertEquals(DamageTier.UNRATED, punch.typedPurpose().damage());
        assertEquals(PurposeOrigin.INFERRED, punch.typedPurpose().origin());
    }

    @Test
    void withPurposeKeepsTheRestOfTheChoice() {
        var skill = new SkillChoice(choice("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"),
                Set.of(PredictValue.MELEE_ATTACK), true, 4);
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.LOW, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        var resolved = skill.withPurpose(purpose.withCooldown(CooldownClass.SHORT));
        assertEquals(CooldownClass.SHORT, resolved.typedPurpose().cooldown());
        assertEquals(skill.values(), resolved.values());
        assertEquals(4, resolved.extraCooldownTicks());
        assertEquals(skill.withPurpose(purpose), resolved.withPurpose(purpose));
    }

    @Test
    void anUntypedChoiceHasNoPurposeAndATypedOneDoes() {
        var raw = SkillChoice.untyped(choice("raw"), "Raw", Set.of(PredictValue.DEFENSE), false, 2);
        assertFalse(raw.isTyped());
        assertFalse(raw.authored());
        assertEquals(Optional.empty(), raw.purpose());
        assertTrue(assertThrows(IllegalStateException.class, raw::typedPurpose).getMessage().contains("untyped"));
        var declared = raw.withPurpose(melee(PurposeOrigin.DECLARED));
        var skill = raw.withPurpose(melee(PurposeOrigin.SKILL));
        var inferred = raw.withPurpose(melee(PurposeOrigin.INFERRED));
        assertTrue(declared.isTyped());
        assertTrue(declared.authored());
        assertTrue(skill.authored());
        assertFalse(inferred.authored());
        assertEquals(melee(PurposeOrigin.DECLARED), declared.typedPurpose());
    }

    @Test
    void busyTicksIsTheLargerWindUpAndVanillaMeleeCannotDropItsPurpose() {
        var facts = new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.of(15),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false, false, false);
        var typed = SkillChoice.typed(choice("slam"), "Slam", Set.of(), true, 0,
                melee(PurposeOrigin.DECLARED)).withNativeFacts(facts);
        assertEquals(15, typed.busyTicks());
        var longer = typed.withPurpose(new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(2.5), OptionalDouble.empty(), 20, CooldownClass.UNKNOWN, DamageTier.LOW, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED));
        assertEquals(20, longer.busyTicks());
        var raw = SkillChoice.untyped(choice("raw"), "Raw", Set.of(), false, 0).withNativeFacts(facts);
        assertEquals(15, raw.busyTicks());
        assertEquals(0, SkillChoice.untyped(choice("raw"), "Raw", Set.of(), false, 0).busyTicks());
        assertFalse(raw.withoutPurpose().isTyped());
        var punch = SkillChoice.vanillaMelee(choice("punch"), true, 0, melee(PurposeOrigin.INFERRED));
        assertFalse(punch.authored());
        assertThrows(IllegalArgumentException.class, punch::withoutPurpose);
    }

    @Test
    void authorOverridesNeedNoOptionalAndAbsentOverridesAreEmptyNeverNull() {
        var base = SkillChoice.typed(choice("throw"), "Throw", Set.of(), true, 0, melee(PurposeOrigin.DECLARED));
        assertEquals(base.withDisplacement(Optional.of(DisplacementKind.STRONG_PUSH)),
                base.withDisplacement(DisplacementKind.STRONG_PUSH));
        assertEquals(Optional.of(DisplacementKind.STRONG_PUSH),
                base.withDisplacement(DisplacementKind.STRONG_PUSH).displacement());
        assertTrue(base.withTargetsAllies(true).targetsAllies());
        assertEquals(base.withTargetsAllies(Optional.of(false)), base.withTargetsAllies(false));
        assertThrows(NullPointerException.class, () -> base.withDisplacement((DisplacementKind) null));
        assertThrows(NullPointerException.class, () -> new SkillChoice(base.id(), base.kind(), base.mythicSkillId(),
                base.values(), base.requiresTarget(), base.extraCooldownTicks(), base.purpose(), base.nativeFacts(),
                base.telegraph(), null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new SkillChoice(base.id(), base.kind(), base.mythicSkillId(),
                base.values(), base.requiresTarget(), base.extraCooldownTicks(), base.purpose(), base.nativeFacts(),
                base.telegraph(), Optional.empty(), null));
    }

    private static SkillPurpose melee(PurposeOrigin origin) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(2.5),
                OptionalDouble.empty(), 5, CooldownClass.UNKNOWN, DamageTier.LOW, Set.of(), Set.of(), false, origin);
    }
}
