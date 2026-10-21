package akyrion.thepredict.domain;

/**
 * Bit mask v1 of what a reaction wants to cast (M20.4). Groups combine with and; values inside a group combine
 * with or; the shield bit is a separate or. Bits 26–31 describe the choice, not its purpose (M24.6, M26.5): only
 * {@link #fits(long, SkillChoice)} reads them, as two more groups that combine with and.
 */
public final class CastPreference {
    /** First bit of {@link SkillRole}. */
    public static final int ROLE_SHIFT = 0;
    /** First bit of {@link Delivery}. */
    public static final int DELIVERY_SHIFT = 13;
    /** First bit of {@link DamageTier}. */
    public static final int DAMAGE_SHIFT = 20;
    /** The choice ignores shields. */
    public static final int IGNORES_SHIELD = 25;
    /** The choice displaces the target. Bit 26, outside the grouped fit of a purpose. */
    public static final int DISPLACES_TARGET = 26;
    /** First bit of {@link TellAnswer}. Bits 27–31. Only {@code EXPLOIT_PATTERN} writes them. */
    public static final int ANSWER_SHIFT = 27;

    private static final long ROLE_BITS = (1L << DELIVERY_SHIFT) - 1;
    private static final long DELIVERY_BITS = (1L << (DAMAGE_SHIFT - DELIVERY_SHIFT)) - 1;
    private static final long DAMAGE_BITS = (1L << (IGNORES_SHIELD - DAMAGE_SHIFT)) - 1;
    private static final long ANSWER_BITS = 31L;
    private static final long CHOICE_BITS = (1L << DISPLACES_TARGET) | (ANSWER_BITS << ANSWER_SHIFT);

    private CastPreference() {
    }

    /**
     * A mask with one role bit.
     *
     * @param role role to set
     * @return the mask
     */
    public static long role(SkillRole role) {
        return 1L << (ROLE_SHIFT + role.ordinal());
    }

    /**
     * A mask with one delivery bit.
     *
     * @param delivery delivery to set
     * @return the mask
     */
    public static long delivery(Delivery delivery) {
        return 1L << (DELIVERY_SHIFT + delivery.ordinal());
    }

    /**
     * A mask with one damage bit.
     *
     * @param damage damage tier to set
     * @return the mask
     */
    public static long damage(DamageTier damage) {
        return 1L << (DAMAGE_SHIFT + damage.ordinal());
    }

    /**
     * The shield clause.
     *
     * @return the mask with the ignores-shield bit
     */
    public static long ignoresShield() {
        return 1L << IGNORES_SHIELD;
    }

    /**
     * The bit of a choice that displaces its target.
     *
     * @return the mask
     */
    public static long displacesTarget() {
        return 1L << DISPLACES_TARGET;
    }

    /**
     * A mask with one tell-answer bit. {@link #fits(long, SkillPurpose)} ignores these bits;
     * {@link #fits(long, SkillChoice)} reads them.
     *
     * @param answer the answer the choice must declare
     * @return the mask
     */
    public static long answer(TellAnswer answer) {
        return 1L << (ANSWER_SHIFT + answer.ordinal());
    }

    /**
     * Whether a declared tell answer is inside the mask. An empty declaration does not fit a mask that asks for one.
     * A mask with no answer bits fits every choice.
     *
     * @param mask preference bits
     * @param answerOrdinal {@link TellAnswer#ordinal()}, or below zero when the tell declares none
     * @return whether the answer clause passes
     */
    public static boolean answerFits(long mask, int answerOrdinal) {
        var bits = (mask >>> ANSWER_SHIFT) & ANSWER_BITS;
        if (bits == 0L) {
            return true;
        }
        return answerOrdinal >= 0 && (bits & (1L << answerOrdinal)) != 0L;
    }

    /**
     * Whether a purpose matches the mask. Bits 26–31 describe a choice and are ignored here.
     *
     * @param mask preference bits
     * @param purpose purpose of the choice
     * @return whether it fits
     */
    public static boolean fits(long mask, SkillPurpose purpose) {
        var anyGroup = (mask & ROLE_BITS) != 0 || ((mask >>> DELIVERY_SHIFT) & DELIVERY_BITS) != 0
                || ((mask >>> DAMAGE_SHIFT) & DAMAGE_BITS) != 0;
        return anyGroup && groupsMatch(mask, purpose) || shieldMatches(mask, purpose);
    }

    /**
     * Whether a choice matches the mask, with the two clauses of the choice itself. Bit 26 asks for a choice that
     * pushes its target: the declared displacement, or else the one read from MythicMobs (assumed knockback when the
     * reading is incomplete), the same displacement rule A12 uses. Bits 27–31 ask for a tell whose answer
     * ({@link DangerRules#answer}, declared or derived) is in the mask. Both are groups: they combine with and with
     * roles, deliveries and damage, and a mask with only them fits on them alone. The shield bit stays a separate or.
     * An untyped choice never fits, as no caller prefers one. Allocation-free.
     *
     * @param mask preference bits
     * @param choice the choice
     * @return whether it fits
     */
    public static boolean fits(long mask, SkillChoice choice) {
        var purpose = choice.purpose();
        if (purpose.isEmpty()) {
            return false;
        }
        if ((mask & CHOICE_BITS) == 0L) {
            return fits(mask, purpose.get());
        }
        return groupsMatch(mask, purpose.get()) && choiceMatches(mask, choice) || shieldMatches(mask, purpose.get());
    }

    private static boolean groupsMatch(long mask, SkillPurpose purpose) {
        var roles = mask & ROLE_BITS;
        var deliveries = (mask >>> DELIVERY_SHIFT) & DELIVERY_BITS;
        var damages = (mask >>> DAMAGE_SHIFT) & DAMAGE_BITS;
        var roleOk = roles == 0 || overlaps(roles, purpose);
        var deliveryOk = deliveries == 0 || (deliveries & (1L << purpose.delivery().ordinal())) != 0;
        var damageOk = damages == 0 || (damages & (1L << purpose.damage().ordinal())) != 0;
        return roleOk && deliveryOk && damageOk;
    }

    private static boolean shieldMatches(long mask, SkillPurpose purpose) {
        return (mask & (1L << IGNORES_SHIELD)) != 0 && purpose.ignoresShield();
    }

    private static boolean choiceMatches(long mask, SkillChoice choice) {
        if ((mask & displacesTarget()) != 0L
                && !choice.displacement().orElse(choice.nativeFacts().targetDisplacement()).pushesTarget()) {
            return false;
        }
        return ((mask >>> ANSWER_SHIFT) & ANSWER_BITS) == 0L || answerFits(mask, DangerRules.answer(choice).ordinal());
    }

    private static boolean overlaps(long roles, SkillPurpose purpose) {
        for (var role : purpose.roles()) {
            if ((roles & (1L << role.ordinal())) != 0) {
                return true;
            }
        }
        return false;
    }
}
