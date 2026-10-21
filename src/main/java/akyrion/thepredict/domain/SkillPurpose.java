package akyrion.thepredict.domain;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * What a skill choice is for, in closed types and without raw numbers toward Jev (M15.1). Immutable and safe on any
 * thread.
 *
 * <p>Invariants: {@code roles} holds {@code primary} and has 1 to {@value #MAX_ROLES} entries; {@code reachBlocks},
 * when present, is finite in {@code (0, }{@value #MAX_REACH_BLOCKS}{@code ]}, required for {@code MELEE} and
 * {@code CONE} and absent for {@code SELF} and {@code AREA_AROUND_SELF}; {@code radiusBlocks}, when present, is finite
 * in {@code (0, }{@value #MAX_RADIUS_BLOCKS}{@code ]} and present exactly for {@code AREA_AROUND_SELF} and
 * {@code AREA_AT_TARGET}; {@code windUpTicks} in {@code [0, }{@value #MAX_WIND_UP_TICKS}{@code ]}; {@code damage} is not
 * {@code NONE} when {@code roles} has {@code DAMAGE} or {@code FINISHER}; at most {@value #MAX_BEST_WHEN} entries in
 * {@code bestWhen} and {@value #MAX_RISKS} in {@code risks}; {@code ignoresShield} excludes the risk
 * {@code BLOCKED_BY_SHIELD}; {@code AREA_AROUND_SELF} is measured with {@code POSITIONS_STRICT}, a purpose that does not
 * come from the skill uses {@link ReachMeasure#defaultFor} its delivery, and {@code POSITIONS} only measures a
 * {@code CONE} (M19.3). Error messages name the domain field, which the profile loader translates to its YAML key.
 *
 * @param primary main role
 * @param roles every role, including {@code primary}
 * @param delivery how the skill reaches its target
 * @param reachBlocks reach in blocks, measured with {@code reachMeasure}: by default the gap between bounding boxes for
 *        {@code MELEE} and {@code CONE} and the distance between centres for the ranged deliveries
 * @param radiusBlocks radius of the area in blocks: the {@code r} of the MythicMobs radius targeter the skill uses,
 *        measured between the positions of the entities as MythicMobs measures it
 * @param windUpTicks ticks between the cast and the hit
 * @param cooldown cooldown class, filled in by the binder from the native {@code Cooldown}
 * @param damage damage tier
 * @param bestWhen situations in which the skill works best
 * @param risks drawbacks of the skill
 * @param ignoresShield whether a raised shield does not stop it
 * @param origin where the purpose comes from
 * @param reachMeasure how {@code reachBlocks} or {@code radiusBlocks} is measured (M19.3); never reaches Jev
 */
public record SkillPurpose(SkillRole primary, Set<SkillRole> roles, Delivery delivery,
                           OptionalDouble reachBlocks, OptionalDouble radiusBlocks, int windUpTicks,
                           CooldownClass cooldown, DamageTier damage, Set<UseCondition> bestWhen,
                           Set<SkillRisk> risks, boolean ignoresShield, PurposeOrigin origin,
                           ReachMeasure reachMeasure) {
    /** Most roles a skill may declare. */
    public static final int MAX_ROLES = 3;
    /** Largest reach in blocks. */
    public static final double MAX_REACH_BLOCKS = 64.0;
    /** Largest area radius in blocks. */
    public static final double MAX_RADIUS_BLOCKS = 16.0;
    /** Longest wind-up in ticks. */
    public static final int MAX_WIND_UP_TICKS = 100;
    /** Most {@code bestWhen} conditions. */
    public static final int MAX_BEST_WHEN = 4;
    /** Most risks. */
    public static final int MAX_RISKS = 4;

    /**
     * Validates the purpose and copies the sets.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public SkillPurpose {
        Objects.requireNonNull(primary, "primary");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(reachBlocks, "reachBlocks");
        Objects.requireNonNull(radiusBlocks, "radiusBlocks");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(damage, "damage");
        bestWhen = Set.copyOf(Objects.requireNonNull(bestWhen, "bestWhen"));
        risks = Set.copyOf(Objects.requireNonNull(risks, "risks"));
        Objects.requireNonNull(origin, "origin");
        requireMeasure(delivery, origin, Objects.requireNonNull(reachMeasure, "reachMeasure"));
        if (!roles.contains(primary)) {
            throw new IllegalArgumentException("roles must contain the primary role " + primary);
        }
        if (roles.size() > MAX_ROLES) {
            throw new IllegalArgumentException("roles must have 1 to " + MAX_ROLES + " entries, had " + roles.size());
        }
        requireReach(delivery, reachBlocks);
        requireRadius(delivery, radiusBlocks);
        DomainChecks.requireRange(windUpTicks, 0, MAX_WIND_UP_TICKS, "windUpTicks");
        if (damage == DamageTier.NONE && (roles.contains(SkillRole.DAMAGE) || roles.contains(SkillRole.FINISHER))) {
            throw new IllegalArgumentException("damage must not be NONE when roles has DAMAGE or FINISHER");
        }
        DomainChecks.requireMaximumSize(bestWhen, MAX_BEST_WHEN, "bestWhen");
        DomainChecks.requireMaximumSize(risks, MAX_RISKS, "risks");
        if (ignoresShield && risks.contains(SkillRisk.BLOCKED_BY_SHIELD)) {
            throw new IllegalArgumentException("ignoresShield cannot be true while risks has BLOCKED_BY_SHIELD");
        }
    }

    /**
     * Creates a purpose measured with the default measure of its delivery ({@link ReachMeasure#defaultFor}), the form
     * of every purpose before M19 (rule W5): every declared purpose and every test that does not read a skill.
     *
     * @param primary main role
     * @param roles every role, including {@code primary}
     * @param delivery how the skill reaches its target
     * @param reachBlocks reach in blocks
     * @param radiusBlocks radius of the area in blocks
     * @param windUpTicks ticks between the cast and the hit
     * @param cooldown cooldown class
     * @param damage damage tier
     * @param bestWhen situations in which the skill works best
     * @param risks drawbacks of the skill
     * @param ignoresShield whether a raised shield does not stop it
     * @param origin where the purpose comes from
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public SkillPurpose(SkillRole primary, Set<SkillRole> roles, Delivery delivery, OptionalDouble reachBlocks,
                        OptionalDouble radiusBlocks, int windUpTicks, CooldownClass cooldown, DamageTier damage,
                        Set<UseCondition> bestWhen, Set<SkillRisk> risks, boolean ignoresShield, PurposeOrigin origin) {
        this(primary, roles, delivery, reachBlocks, radiusBlocks, windUpTicks, cooldown, damage, bestWhen, risks,
                ignoresShield, origin, ReachMeasure.defaultFor(Objects.requireNonNull(delivery, "delivery")));
    }

    /**
     * Tells whether the skill deals damage: {@code roles} has {@code DAMAGE} or {@code FINISHER}, or {@code damage} is
     * {@code UNRATED}, {@code LOW}, {@code MEDIUM} or {@code HIGH}. Only the pre-cast checks of a purpose written by the
     * author use it (M15.6).
     *
     * @return whether it is a damaging skill
     */
    public boolean damaging() {
        return roles.contains(SkillRole.DAMAGE) || roles.contains(SkillRole.FINISHER) || damage != DamageTier.NONE;
    }

    /**
     * Tells whether the author wrote this purpose ({@code DECLARED} or {@code SKILL}): only then do the lint, the
     * eligibility rule, the pre-cast checks by delivery and the local tie-break of M15 apply.
     *
     * @return whether the origin is {@code DECLARED} or {@code SKILL}
     */
    public boolean authored() {
        return origin != PurposeOrigin.INFERRED;
    }

    /**
     * Tells whether the skill is aimed at the target: every delivery except {@code SELF} and {@code AREA_AROUND_SELF}.
     *
     * @return whether it is aimed at the target
     */
    public boolean aimedAtTarget() {
        return delivery != Delivery.SELF && delivery != Delivery.AREA_AROUND_SELF;
    }

    /**
     * Effective reach of the skill: {@code radiusBlocks} for {@code AREA_AROUND_SELF}, otherwise {@code reachBlocks}.
     *
     * @return the reach in blocks, empty when the skill has no reach limit ({@code SELF}, or a ranged delivery without
     *         {@code reachBlocks})
     */
    public OptionalDouble effectiveReachBlocks() {
        return delivery == Delivery.AREA_AROUND_SELF ? radiusBlocks : reachBlocks;
    }

    /**
     * Tells whether another purpose means the same to Jev: every component is equal except the {@code origin} and the
     * {@code reachMeasure}, which Jev never reads (M19.3, user decision 11). {@code OptionSet.distinct} (M16.1) uses
     * it to merge tactics identical in everything.
     *
     * @param other another purpose
     * @return whether the two differ at most in their origin and their measure
     */
    public boolean semanticallyEqual(SkillPurpose other) {
        Objects.requireNonNull(other, "other");
        return primary == other.primary && roles.equals(other.roles) && delivery == other.delivery
                && reachBlocks.equals(other.reachBlocks) && radiusBlocks.equals(other.radiusBlocks)
                && windUpTicks == other.windUpTicks && cooldown == other.cooldown && damage == other.damage
                && bestWhen.equals(other.bestWhen) && risks.equals(other.risks) && ignoresShield == other.ignoresShield;
    }

    /**
     * Returns a copy with another cooldown class, which the binder reads from the native {@code Cooldown}.
     *
     * @param cooldownClass the resolved class
     * @return the copy, or this purpose when the class is the same
     */
    public SkillPurpose withCooldown(CooldownClass cooldownClass) {
        if (cooldownClass == cooldown) {
            return this;
        }
        return new SkillPurpose(primary, roles, delivery, reachBlocks, radiusBlocks, windUpTicks, cooldownClass, damage,
                bestWhen, risks, ignoresShield, origin, reachMeasure);
    }

    /**
     * Returns a copy that also ignores shields. Production binding never calls this from {@code PredictValues}
     * (M19, errata E19-1 DP5): a declared purpose keeps the flag the profile wrote, and a skill purpose ORs the native
     * {@code ignoreshield} inside {@code SkillIntentions}. Tests use this copy.
     *
     * @return the copy, or this purpose when it already ignores shields
     */
    public SkillPurpose ignoringShields() {
        if (ignoresShield) {
            return this;
        }
        return new SkillPurpose(primary, roles, delivery, reachBlocks, radiusBlocks, windUpTicks, cooldown, damage,
                bestWhen, risks, true, origin, reachMeasure);
    }

    /**
     * The roles for presentation: {@code primary} first, then the others in the order of {@link SkillRole}.
     *
     * @return a new ordered set
     */
    public Set<SkillRole> orderedRoles() {
        var ordered = new LinkedHashSet<SkillRole>();
        ordered.add(primary);
        ordered.addAll(EnumSet.copyOf(roles));
        return ordered;
    }

    private static void requireMeasure(Delivery delivery, PurposeOrigin origin, ReachMeasure measure) {
        if (delivery == Delivery.AREA_AROUND_SELF && measure != ReachMeasure.POSITIONS_STRICT) {
            throw new IllegalArgumentException("reachMeasure must be POSITIONS_STRICT for delivery AREA_AROUND_SELF");
        }
        if (origin != PurposeOrigin.SKILL && measure != ReachMeasure.defaultFor(delivery)) {
            throw new IllegalArgumentException("reachMeasure of a purpose with origin " + origin + " must be "
                    + ReachMeasure.defaultFor(delivery));
        }
        if (measure == ReachMeasure.POSITIONS && delivery != Delivery.CONE) {
            throw new IllegalArgumentException("reachMeasure POSITIONS only measures delivery CONE");
        }
    }

    private static void requireReach(Delivery delivery, OptionalDouble reachBlocks) {
        if (reachBlocks.isPresent()) {
            requireBlocks(reachBlocks.getAsDouble(), MAX_REACH_BLOCKS, "reachBlocks");
        }
        var required = delivery == Delivery.MELEE || delivery == Delivery.CONE;
        var forbidden = delivery == Delivery.SELF || delivery == Delivery.AREA_AROUND_SELF;
        if (required && reachBlocks.isEmpty()) {
            throw new IllegalArgumentException("reachBlocks is required for delivery " + delivery);
        }
        if (forbidden && reachBlocks.isPresent()) {
            throw new IllegalArgumentException("reachBlocks must be absent for delivery " + delivery);
        }
    }

    private static void requireRadius(Delivery delivery, OptionalDouble radiusBlocks) {
        if (radiusBlocks.isPresent()) {
            requireBlocks(radiusBlocks.getAsDouble(), MAX_RADIUS_BLOCKS, "radiusBlocks");
        }
        var area = delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET;
        if (area && radiusBlocks.isEmpty()) {
            throw new IllegalArgumentException("radiusBlocks is required for delivery " + delivery);
        }
        if (!area && radiusBlocks.isPresent()) {
            throw new IllegalArgumentException("radiusBlocks must be absent for delivery " + delivery);
        }
    }

    private static void requireBlocks(double value, double maximum, String field) {
        if (!(Double.isFinite(value) && value > 0.0 && value <= maximum)) {
            throw new IllegalArgumentException(field + " must be finite, greater than 0 and at most " + maximum
                    + ", was " + value);
        }
    }
}
