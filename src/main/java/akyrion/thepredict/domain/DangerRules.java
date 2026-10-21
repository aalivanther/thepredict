package akyrion.thepredict.domain;

import java.util.Optional;

/**
 * Danger class and the answer a tell asks for (M23.4). The first matching rule wins. A {@code SELF} skill that still
 * hurts through a damaging mechanic while its tier is {@code NONE} is an area of unknown damage ({@code UNRATED}), so
 * with the v1 enums every case meets a rule of the table and the final capture is unreachable; it stays for a value
 * a later enum adds.
 */
public final class DangerRules {
    private DangerRules() {
    }

    /**
     * Class of a purpose. {@code SELF} with damage is classified as an area around the caster.
     *
     * @param purpose the purpose
     * @param facts native facts; damage on {@code SELF} is also read from {@link NativeSkillFacts#hasDamagingMechanic()}
     * @return the class
     */
    public static DangerClass classify(SkillPurpose purpose, NativeSkillFacts facts) {
        boolean shield = purpose.ignoresShield();
        boolean control = purpose.roles().contains(SkillRole.CONTROL);
        boolean finisher = purpose.roles().contains(SkillRole.FINISHER);
        boolean debuff = purpose.roles().contains(SkillRole.DEBUFF);
        boolean selfDamage = purpose.delivery() == Delivery.SELF
                && (purpose.damage() != DamageTier.NONE || facts.hasDamagingMechanic());
        var damage = selfDamage && purpose.damage() == DamageTier.NONE ? DamageTier.UNRATED : purpose.damage();
        if (damage == DamageTier.HIGH || finisher && atLeastMedium(damage) || shield && atLeastMedium(damage)
                || control && atLeastMedium(damage)) {
            return DangerClass.SEVERE;
        }
        if (damage == DamageTier.MEDIUM && area(purpose.delivery(), selfDamage)
                || control && (damage == DamageTier.NONE || low(damage))) {
            return DangerClass.HEAVY;
        }
        if (damage == DamageTier.MEDIUM && single(purpose.delivery()) || low(damage) && area(purpose.delivery(), selfDamage)
                || shield && low(damage) || debuff && damage == DamageTier.NONE) {
            return DangerClass.MEDIUM;
        }
        if (low(damage) && single(purpose.delivery()) && !shield) {
            return DangerClass.LIGHT;
        }
        if (damage == DamageTier.NONE && !control && !debuff && !selfDamage) {
            return DangerClass.NONE;
        }
        return DangerClass.MEDIUM;
    }

    /**
     * Class of a choice. A declaration wins. An untyped choice is {@link DangerClass#MEDIUM}.
     *
     * @param choice the choice
     * @return the class
     */
    public static DangerClass classify(SkillChoice choice) {
        Optional<DangerClass> declared = choice.telegraph().danger();
        if (declared.isPresent()) {
            return declared.get();
        }
        if (choice.purpose().isEmpty()) {
            return DangerClass.MEDIUM;
        }
        return classify(choice.purpose().get(), choice.nativeFacts());
    }

    /**
     * Answer of a purpose when the choice does not declare one.
     *
     * @param purpose the purpose
     * @param facts native facts
     * @return the answer
     */
    public static TellAnswer defaultAnswer(SkillPurpose purpose, NativeSkillFacts facts) {
        if (purpose.ignoresShield()) {
            return TellAnswer.BACK_OFF;
        }
        var delivery = purpose.delivery();
        if (delivery == Delivery.MELEE || delivery == Delivery.CONE) {
            return TellAnswer.BLOCK;
        }
        boolean selfDamage = delivery == Delivery.SELF
                && (purpose.damage() != DamageTier.NONE || facts.hasDamagingMechanic());
        if (delivery == Delivery.AREA_AROUND_SELF || selfDamage) {
            return TellAnswer.BACK_OFF;
        }
        if (delivery == Delivery.AREA_AT_TARGET || delivery == Delivery.PROJECTILE) {
            return TellAnswer.DODGE;
        }
        if (delivery == Delivery.HITSCAN) {
            return purpose.risks().contains(SkillRisk.NEEDS_LINE_OF_SIGHT) || facts.requiresLineOfSight()
                    ? TellAnswer.BREAK_SIGHT : TellAnswer.BACK_OFF;
        }
        return TellAnswer.BACK_OFF;
    }

    /**
     * Answer of a choice. A declaration wins. An untyped choice is {@link TellAnswer#BACK_OFF}.
     *
     * @param choice the choice
     * @return the answer
     */
    public static TellAnswer answer(SkillChoice choice) {
        if (choice.telegraph().answer().isPresent()) {
            return choice.telegraph().answer().get();
        }
        if (choice.purpose().isEmpty()) {
            return TellAnswer.BACK_OFF;
        }
        return defaultAnswer(choice.purpose().get(), choice.nativeFacts());
    }

    private static boolean atLeastMedium(DamageTier damage) {
        return damage == DamageTier.MEDIUM || damage == DamageTier.HIGH;
    }

    private static boolean low(DamageTier damage) {
        return damage == DamageTier.LOW || damage == DamageTier.UNRATED;
    }

    private static boolean single(Delivery delivery) {
        return delivery == Delivery.MELEE || delivery == Delivery.PROJECTILE || delivery == Delivery.HITSCAN;
    }

    private static boolean area(Delivery delivery, boolean selfDamage) {
        return delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET || delivery == Delivery.CONE
                || selfDamage;
    }
}
