package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One primitive of the {@code Intention} line that an author writes in a MythicMobs skill (M19.1, rule I14). Closed and
 * versioned: a primitive never changes its meaning or its family, a retired one stays in
 * {@link IntentionVocabulary#ALIASES}, and each one maps to exactly one value of the closed types of M15. The order of
 * the constants is the order of the published table. A primitive never reaches Jev as text: the binder converts it to
 * those M15 values and Jev reads their fixed phrases (rule I12).
 */
public enum IntentionToken {
    /** Its main end is to take health ({@link SkillRole#DAMAGE}). */
    DAMAGE(Family.ROLE),
    /** Stuns, slows, pushes or roots the target ({@link SkillRole#CONTROL}). */
    CONTROL(Family.ROLE),
    /** Closes the distance at once ({@link SkillRole#GAP_CLOSE}). */
    GAP_CLOSE(Family.ROLE),
    /** Gets the caster away from danger at once ({@link SkillRole#ESCAPE}). */
    ESCAPE(Family.ROLE),
    /** Reduces the damage the caster takes ({@link SkillRole#DEFENSE}). */
    DEFENSE(Family.ROLE),
    /** Restores the caster's health ({@link SkillRole#SUSTAIN}). */
    SUSTAIN(Family.ROLE),
    /** Improves the caster or its allies ({@link SkillRole#BUFF}). */
    BUFF(Family.ROLE),
    /** Weakens the target ({@link SkillRole#DEBUFF}). */
    DEBUFF(Family.ROLE),
    /** Creates allies ({@link SkillRole#SUMMON}). */
    SUMMON(Family.ROLE),
    /** Denies an area for a while ({@link SkillRole#ZONE}). */
    ZONE(Family.ROLE),
    /** Cuts what the target is doing ({@link SkillRole#INTERRUPT}). */
    INTERRUPT(Family.ROLE),
    /** Finishes off a weakened target ({@link SkillRole#FINISHER}). */
    FINISHER(Family.ROLE),
    /** Opens the fight with an advantage ({@link SkillRole#OPENER}). */
    OPENER(Family.ROLE),
    /** A hit on a target within arm's reach ({@link Delivery#MELEE}). */
    MELEE(Family.DELIVERY),
    /** A projectile that travels and may miss ({@link Delivery#PROJECTILE}). */
    PROJECTILE(Family.DELIVERY),
    /** An effect at range that lands at once ({@link Delivery#HITSCAN}). */
    HITSCAN(Family.DELIVERY),
    /** An area centred on the caster ({@link Delivery#AREA_AROUND_SELF}). */
    AREA_AROUND_SELF(Family.DELIVERY),
    /** An area centred on the target ({@link Delivery#AREA_AT_TARGET}). */
    AREA_AT_TARGET(Family.DELIVERY),
    /** A sweep in front of the caster ({@link Delivery#CONE}). */
    CONE(Family.DELIVERY),
    /** Affects only the caster ({@link Delivery#SELF}). */
    SELF(Family.DELIVERY),
    /** Deals little damage ({@link DamageTier#LOW}). */
    DAMAGE_LOW(Family.DAMAGE),
    /** Deals medium damage ({@link DamageTier#MEDIUM}). */
    DAMAGE_MEDIUM(Family.DAMAGE),
    /** Deals a lot of damage ({@link DamageTier#HIGH}). */
    DAMAGE_HIGH(Family.DAMAGE),
    /** Works best when the target's health is critical ({@link UseCondition#TARGET_LOW_HEALTH}). */
    WHEN_TARGET_LOW_HEALTH(Family.BEST_WHEN),
    /** Works best when the target blocks or raises a shield ({@link UseCondition#TARGET_SHIELDING}). */
    WHEN_TARGET_SHIELDING(Family.BEST_WHEN),
    /** Works best when the target moves away ({@link UseCondition#TARGET_FLEEING}). */
    WHEN_TARGET_FLEEING(Family.BEST_WHEN),
    /** Works best when the caster's health is critical ({@link UseCondition#SELF_LOW_HEALTH}). */
    WHEN_SELF_LOW_HEALTH(Family.BEST_WHEN),
    /** Works best when the target has another enemy close by ({@link UseCondition#ENEMIES_GROUPED}). */
    WHEN_ENEMIES_GROUPED(Family.BEST_WHEN),
    /** Works best when the target is beyond melee ({@link UseCondition#TARGET_AT_RANGE}). */
    WHEN_TARGET_AT_RANGE(Family.BEST_WHEN),
    /** Works best when the target is in melee ({@link UseCondition#TARGET_IN_MELEE}). */
    WHEN_TARGET_IN_MELEE(Family.BEST_WHEN),
    /** Leaves the caster exposed afterwards ({@link SkillRisk#LEAVES_CASTER_EXPOSED}). */
    RISK_LEAVES_CASTER_EXPOSED(Family.RISK),
    /** The caster cannot move while it uses it ({@link SkillRisk#ROOTS_CASTER}). */
    RISK_ROOTS_CASTER(Family.RISK),
    /** Hurts the caster ({@link SkillRisk#SELF_DAMAGE}). */
    RISK_SELF_DAMAGE(Family.RISK),
    /** Needs a line of sight ({@link SkillRisk#NEEDS_LINE_OF_SIGHT}). */
    RISK_NEEDS_LINE_OF_SIGHT(Family.RISK),
    /** A moving target may dodge it ({@link SkillRisk#SLOW_PROJECTILE}). */
    RISK_SLOW_PROJECTILE(Family.RISK),
    /** A raised shield blocks it ({@link SkillRisk#BLOCKED_BY_SHIELD}). */
    RISK_BLOCKED_BY_SHIELD(Family.RISK),
    /** May hurt allies ({@link SkillRisk#HITS_ALLIES}). */
    RISK_HITS_ALLIES(Family.RISK),
    /** Goes through shields ({@code ignoresShield} of {@link SkillPurpose}). */
    IGNORES_SHIELD(Family.SHIELD),
    /** The tell asks for a shield ({@link TellAnswer#BLOCK}). */
    ANSWER_BLOCK(Family.ANSWER),
    /** The tell asks for a sidestep ({@link TellAnswer#DODGE}). */
    ANSWER_DODGE(Family.ANSWER),
    /** The tell asks the target to leave reach ({@link TellAnswer#BACK_OFF}). */
    ANSWER_BACK_OFF(Family.ANSWER),
    /** The tell asks the target to break line of sight ({@link TellAnswer#BREAK_SIGHT}). */
    ANSWER_BREAK_SIGHT(Family.ANSWER),
    /** The tell asks the target to hit during the wind-up ({@link TellAnswer#INTERRUPT}). */
    ANSWER_INTERRUPT(Family.ANSWER);

    /** Prefix of the damage tiers. */
    static final String DAMAGE_PREFIX = "DAMAGE_";
    /** Prefix of the best-when conditions. */
    static final String WHEN_PREFIX = "WHEN_";
    /** Prefix of the risks. */
    static final String RISK_PREFIX = "RISK_";
    /** Prefix of the tell answers. */
    static final String ANSWER_PREFIX = "ANSWER_";

    private final Family family;
    private final Enum<?> meaning;

    IntentionToken(Family family) {
        this.family = family;
        this.meaning = meaningOf(family, name());
    }

    /** Family of a primitive: which part of a purpose it fills. */
    public enum Family {
        /** A role; 1 to 3 per line, the first one is the primary role. */
        ROLE,
        /** The delivery; exactly one per line. */
        DELIVERY,
        /** The damage tier; at most one per line. */
        DAMAGE,
        /** A condition in which the skill works best; at most 4 per line. */
        BEST_WHEN,
        /** A drawback; at most 4 per line. */
        RISK,
        /** Whether a raised shield does not stop it. */
        SHIELD,
        /** The answer a tell asks for; at most one per line. */
        ANSWER
    }

    /**
     * Family of the primitive.
     *
     * @return the family
     */
    public Family family() {
        return family;
    }

    /**
     * Version of the vocabulary that introduced the primitive; every primitive of v1 returns 1.
     *
     * @return the version
     */
    public int since() {
        return family == Family.ANSWER ? 2 : 1;
    }

    /**
     * The role of a primitive of the family {@code ROLE}.
     *
     * @return the role, empty for any other family
     */
    public Optional<SkillRole> role() {
        return meaning instanceof SkillRole role ? Optional.of(role) : Optional.empty();
    }

    /**
     * The delivery of a primitive of the family {@code DELIVERY}.
     *
     * @return the delivery, empty for any other family
     */
    public Optional<Delivery> delivery() {
        return meaning instanceof Delivery delivery ? Optional.of(delivery) : Optional.empty();
    }

    /**
     * The damage tier of a primitive of the family {@code DAMAGE}: {@code LOW}, {@code MEDIUM} or {@code HIGH}.
     *
     * @return the tier, empty for any other family
     */
    public Optional<DamageTier> damage() {
        return meaning instanceof DamageTier tier ? Optional.of(tier) : Optional.empty();
    }

    /**
     * The condition of a primitive of the family {@code BEST_WHEN}.
     *
     * @return the condition, empty for any other family
     */
    public Optional<UseCondition> condition() {
        return meaning instanceof UseCondition condition ? Optional.of(condition) : Optional.empty();
    }

    /**
     * The risk of a primitive of the family {@code RISK}.
     *
     * @return the risk, empty for any other family
     */
    public Optional<SkillRisk> risk() {
        return meaning instanceof SkillRisk risk ? Optional.of(risk) : Optional.empty();
    }

    /**
     * The primitive of a role.
     *
     * @param role a role
     * @return its primitive
     */
    public static IntentionToken of(SkillRole role) {
        return valueOf(Objects.requireNonNull(role, "role").name());
    }

    /**
     * The primitive of a delivery.
     *
     * @param delivery a delivery
     * @return its primitive
     */
    public static IntentionToken of(Delivery delivery) {
        return valueOf(Objects.requireNonNull(delivery, "delivery").name());
    }

    /**
     * The primitive of a declared damage tier.
     *
     * @param tier {@code LOW}, {@code MEDIUM} or {@code HIGH}
     * @return its primitive
     * @throws IllegalArgumentException for {@code NONE} or {@code UNRATED}, which a line expresses with no primitive
     */
    public static IntentionToken of(DamageTier tier) {
        if (Objects.requireNonNull(tier, "tier") == DamageTier.NONE || tier == DamageTier.UNRATED) {
            throw new IllegalArgumentException("damage tier " + tier + " has no Intention primitive");
        }
        return valueOf(DAMAGE_PREFIX + tier.name());
    }

    /**
     * The primitive of a use condition.
     *
     * @param condition a condition
     * @return its primitive
     */
    public static IntentionToken of(UseCondition condition) {
        return valueOf(WHEN_PREFIX + Objects.requireNonNull(condition, "condition").name());
    }

    /**
     * The primitive of a risk.
     *
     * @param risk a risk
     * @return its primitive
     */
    public static IntentionToken of(SkillRisk risk) {
        return valueOf(RISK_PREFIX + Objects.requireNonNull(risk, "risk").name());
    }

    /**
     * The primitive of a tell answer.
     *
     * @param answer an answer
     * @return its primitive
     */
    public static IntentionToken of(TellAnswer answer) {
        return valueOf(ANSWER_PREFIX + Objects.requireNonNull(answer, "answer").name());
    }

    /**
     * The answer of a primitive of the family {@code ANSWER}.
     *
     * @return the answer, empty for any other family
     */
    public Optional<TellAnswer> answer() {
        return meaning instanceof TellAnswer found ? Optional.of(found) : Optional.empty();
    }

    private static Enum<?> meaningOf(Family family, String name) {
        return switch (family) {
            case ROLE -> SkillRole.valueOf(name);
            case DELIVERY -> Delivery.valueOf(name);
            case DAMAGE -> DamageTier.valueOf(name.substring(DAMAGE_PREFIX.length()));
            case BEST_WHEN -> UseCondition.valueOf(name.substring(WHEN_PREFIX.length()));
            case RISK -> SkillRisk.valueOf(name.substring(RISK_PREFIX.length()));
            case SHIELD -> Family.SHIELD;
            case ANSWER -> TellAnswer.valueOf(name.substring(ANSWER_PREFIX.length()));
        };
    }
}
