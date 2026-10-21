package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The class and answer tables of M23.4, checked against the literal tables and an independent formulation. */
class DangerRulesTest {
    private static final Delivery[] SINGLE = {Delivery.MELEE, Delivery.PROJECTILE, Delivery.HITSCAN};
    private static final Delivery[] AREA = {Delivery.AREA_AROUND_SELF, Delivery.AREA_AT_TARGET, Delivery.CONE};

    @Test
    void theDeliveryByDamageTableOfM23HoldsCellByCell() {
        for (var delivery : SINGLE) {
            cell(delivery, DamageTier.NONE, DangerClass.NONE);
            cell(delivery, DamageTier.LOW, DangerClass.LIGHT);
            cell(delivery, DamageTier.UNRATED, DangerClass.LIGHT);
            cell(delivery, DamageTier.MEDIUM, DangerClass.MEDIUM);
            cell(delivery, DamageTier.HIGH, DangerClass.SEVERE);
        }
        for (var delivery : AREA) {
            cell(delivery, DamageTier.NONE, DangerClass.NONE);
            cell(delivery, DamageTier.LOW, DangerClass.MEDIUM);
            cell(delivery, DamageTier.UNRATED, DangerClass.MEDIUM);
            cell(delivery, DamageTier.MEDIUM, DangerClass.HEAVY);
            cell(delivery, DamageTier.HIGH, DangerClass.SEVERE);
        }
        cell(Delivery.SELF, DamageTier.NONE, DangerClass.NONE);
        cell(Delivery.SELF, DamageTier.LOW, DangerClass.MEDIUM);
        cell(Delivery.SELF, DamageTier.UNRATED, DangerClass.MEDIUM);
        cell(Delivery.SELF, DamageTier.MEDIUM, DangerClass.HEAVY);
        cell(Delivery.SELF, DamageTier.HIGH, DangerClass.SEVERE);
    }

    @Test
    void theRoleAndShieldRowsGoBeforeTheTable() {
        assertEquals(DangerClass.SEVERE, classify(SkillRole.FINISHER, Delivery.MELEE, DamageTier.MEDIUM, false));
        assertEquals(DangerClass.LIGHT, classify(SkillRole.FINISHER, Delivery.MELEE, DamageTier.LOW, false));
        assertEquals(DangerClass.SEVERE, classify(SkillRole.DAMAGE, Delivery.PROJECTILE, DamageTier.MEDIUM, true));
        assertEquals(DangerClass.MEDIUM, classify(SkillRole.DAMAGE, Delivery.MELEE, DamageTier.LOW, true));
        assertEquals(DangerClass.MEDIUM, classify(SkillRole.DAMAGE, Delivery.HITSCAN, DamageTier.UNRATED, true));
        assertEquals(DangerClass.SEVERE, classify(SkillRole.CONTROL, Delivery.MELEE, DamageTier.MEDIUM, false));
        assertEquals(DangerClass.HEAVY, classify(SkillRole.CONTROL, Delivery.SELF, DamageTier.NONE, false));
        assertEquals(DangerClass.HEAVY, classify(SkillRole.CONTROL, Delivery.CONE, DamageTier.LOW, false));
        assertEquals(DangerClass.MEDIUM, classify(SkillRole.DEBUFF, Delivery.HITSCAN, DamageTier.NONE, false));
        assertEquals(DangerClass.LIGHT, classify(SkillRole.DEBUFF, Delivery.MELEE, DamageTier.LOW, false));
        assertEquals(DangerClass.NONE, classify(SkillRole.BUFF, Delivery.SELF, DamageTier.NONE, false));
    }

    @Test
    void aSelfSkillWithADamagingMechanicIsAnAreaOfUnknownDamage() {
        var buff = purpose(SkillRole.BUFF, Delivery.SELF, DamageTier.NONE, false).orElseThrow();
        assertEquals(DangerClass.NONE, DangerRules.classify(buff, NativeSkillFacts.NONE));
        assertEquals(DangerClass.MEDIUM, DangerRules.classify(buff, damaging()));
        assertEquals(DangerRules.classify(purpose(SkillRole.BUFF, Delivery.AREA_AROUND_SELF, DamageTier.UNRATED, false)
                .orElseThrow(), NativeSkillFacts.NONE), DangerRules.classify(buff, damaging()));
        assertEquals(TellAnswer.BACK_OFF, DangerRules.defaultAnswer(buff, damaging()));
    }

    @Test
    void everyCombinationMatchesTheTableReadByDamageTier() {
        var classified = 0;
        for (var role : SkillRole.values()) {
            for (var delivery : Delivery.values()) {
                for (var damage : DamageTier.values()) {
                    for (var shield : new boolean[] {false, true}) {
                        var purpose = purpose(role, delivery, damage, shield);
                        if (purpose.isEmpty()) {
                            continue;
                        }
                        for (var mechanic : new boolean[] {false, true}) {
                            var facts = mechanic ? damaging() : NativeSkillFacts.NONE;
                            assertEquals(byDamage(role, delivery, damage, shield, mechanic),
                                    DangerRules.classify(purpose.get(), facts),
                                    role + " " + delivery + " " + damage + " shield=" + shield + " mechanic=" + mechanic);
                            classified++;
                        }
                    }
                }
            }
        }
        assertTrue(classified > SkillRole.values().length * Delivery.values().length * 2, "classified " + classified);
    }

    @Test
    void theDefaultAnswerFollowsTheDeliveryAndAnUntypedChoiceIsMediumBackOff() {
        assertEquals(TellAnswer.BLOCK, answer(Delivery.MELEE, false, Set.of()));
        assertEquals(TellAnswer.BLOCK, answer(Delivery.CONE, false, Set.of()));
        assertEquals(TellAnswer.BACK_OFF, answer(Delivery.MELEE, true, Set.of()));
        assertEquals(TellAnswer.BACK_OFF, answer(Delivery.CONE, true, Set.of()));
        assertEquals(TellAnswer.BACK_OFF, answer(Delivery.AREA_AROUND_SELF, false, Set.of()));
        assertEquals(TellAnswer.BACK_OFF, answer(Delivery.SELF, false, Set.of()));
        assertEquals(TellAnswer.DODGE, answer(Delivery.AREA_AT_TARGET, false, Set.of()));
        assertEquals(TellAnswer.DODGE, answer(Delivery.PROJECTILE, false, Set.of()));
        assertEquals(TellAnswer.BREAK_SIGHT, answer(Delivery.HITSCAN, false, Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT)));
        assertEquals(TellAnswer.BACK_OFF, answer(Delivery.HITSCAN, false, Set.of()));
        var choice = SkillChoice.untyped(new ChoiceId("open"), "Open", Set.of(), true, 0);
        assertEquals(DangerClass.MEDIUM, DangerRules.classify(choice));
        assertEquals(TellAnswer.BACK_OFF, DangerRules.answer(choice));
    }

    /**
     * The class table of M23.4 read by damage tier instead of rule by rule: {@code HIGH} is severe; {@code MEDIUM} is
     * severe for a finisher, a shield breaker or a control and otherwise heavy on an area and medium on one target;
     * below that a control is heavy; {@code LOW} and {@code UNRATED} are medium on an area or through a shield and
     * light otherwise; without damage a debuff and a self skill that still hurts are medium, everything else none.
     */
    private static DangerClass byDamage(SkillRole role, Delivery delivery, DamageTier damage, boolean shield,
                                        boolean mechanic) {
        var selfHurts = delivery == Delivery.SELF && (damage != DamageTier.NONE || mechanic);
        var area = delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET
                || delivery == Delivery.CONE || selfHurts;
        if (damage == DamageTier.HIGH) {
            return DangerClass.SEVERE;
        }
        if (damage == DamageTier.MEDIUM) {
            if (role == SkillRole.FINISHER || shield || role == SkillRole.CONTROL) {
                return DangerClass.SEVERE;
            }
            return area ? DangerClass.HEAVY : DangerClass.MEDIUM;
        }
        if (role == SkillRole.CONTROL) {
            return DangerClass.HEAVY;
        }
        if (damage == DamageTier.LOW || damage == DamageTier.UNRATED) {
            return area || shield ? DangerClass.MEDIUM : DangerClass.LIGHT;
        }
        if (role == SkillRole.DEBUFF || selfHurts) {
            return DangerClass.MEDIUM;
        }
        return DangerClass.NONE;
    }

    private static void cell(Delivery delivery, DamageTier damage, DangerClass expected) {
        var role = damage == DamageTier.NONE ? SkillRole.BUFF : SkillRole.DAMAGE;
        assertEquals(expected, classify(role, delivery, damage, false), delivery + " " + damage);
    }

    private static DangerClass classify(SkillRole role, Delivery delivery, DamageTier damage, boolean shield) {
        return DangerRules.classify(purpose(role, delivery, damage, shield).orElseThrow(), NativeSkillFacts.NONE);
    }

    private static TellAnswer answer(Delivery delivery, boolean shield, Set<SkillRisk> risks) {
        var base = purpose(SkillRole.DAMAGE, delivery, DamageTier.MEDIUM, shield).orElseThrow();
        var purpose = new SkillPurpose(base.primary(), base.roles(), base.delivery(), base.reachBlocks(),
                base.radiusBlocks(), 0, CooldownClass.SHORT, base.damage(), Set.of(), risks, shield,
                PurposeOrigin.DECLARED);
        return DangerRules.defaultAnswer(purpose, NativeSkillFacts.NONE);
    }

    private static NativeSkillFacts damaging() {
        return new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false, false, true);
    }

    private static Optional<SkillPurpose> purpose(SkillRole role, Delivery delivery, DamageTier damage,
                                                  boolean shield) {
        if (damage == DamageTier.NONE && (role == SkillRole.DAMAGE || role == SkillRole.FINISHER)) {
            return Optional.empty();
        }
        var reach = OptionalDouble.empty();
        var radius = OptionalDouble.empty();
        if (delivery == Delivery.MELEE || delivery == Delivery.CONE) {
            reach = OptionalDouble.of(3);
        }
        if (delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET) {
            radius = OptionalDouble.of(3);
        }
        try {
            return Optional.of(new SkillPurpose(role, Set.of(role), delivery, reach, radius, 0, CooldownClass.SHORT,
                    damage, Set.of(), Set.of(), shield, PurposeOrigin.DECLARED));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
