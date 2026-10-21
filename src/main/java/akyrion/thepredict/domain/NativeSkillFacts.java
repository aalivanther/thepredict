package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * What MythicMobs already enforces for a skill, read once when the profile binds (M19.3): its cooldown and charges, the
 * wind-up of its top-level {@code delay}, the reach of its {@code targetwithin}, the radius or cone range of its
 * targeter and the options of its damage. No MythicMobs type (rule D1). It completes an {@code Intention} without
 * repeating numbers and never states the purpose of a skill. Immutable.
 *
 * <p>Invariants: distances finite in {@code (0, }{@value SkillPurpose#MAX_REACH_BLOCKS}{@code ]}; the radius in
 * {@code (0, }{@value SkillPurpose#MAX_RADIUS_BLOCKS}{@code ]}; the wind-up in
 * {@code [0, }{@value SkillPurpose#MAX_WIND_UP_TICKS}{@code ]}; the cooldown finite and {@code >= 0};
 * {@code charges >= 1}; {@code cooldownMode} not blank.
 *
 * @param cooldownSeconds the static {@code Cooldown}; empty without a fixed readable value (placeholder or random range)
 * @param charges {@code max(1, Charges)}, as MythicMobs keeps it
 * @param cooldownMode name of the MythicMobs {@code CooldownMode}, opaque text; {@code SEQUENTIAL} by default
 * @param windUpTicks ticks until the first mechanic that is not cosmetic; empty when a delay is a placeholder or a range
 * @param targetWithinBlocks {@code d} of a {@code targetwithin} required in {@code Conditions}; empty for a placeholder
 * @param radiusBlocks {@code r} of the first radius targeter of a mechanic that is not cosmetic
 * @param coneRangeBlocks {@code range} of the first {@code @EntitiesInCone} of a mechanic that is not cosmetic
 * @param damageIgnoresShield whether some damaging mechanic has {@code ignoreshield=true}
 * @param damageIgnoresInvulnerability whether every damaging mechanic has {@code ignoreinvulnerability=true}
 * @param requiresLineOfSight whether {@code Conditions} or {@code TargetConditions} hold {@code lineofsight}
 * @param hasDamagingMechanic whether some top-level mechanic is a damaging mechanic
 * @param recoveryTicks delays after the last non-cosmetic mechanic, empty when unknown (M23.3)
 * @param cueSignature stable hash of the cosmetic prefix, {@code 0} when there is none
 * @param cueChannels {@link CueChannels} bits of that prefix
 * @param hitShape shape of the first damaging mechanic
 * @param targetDisplacement how the choice moves the target (M24.6)
 * @param selfDisplacement how the choice moves the caster
 * @param displacementOrigin where that reading came from
 * @param targetsAllies whether a mechanic targeter names an ally (M25); an incomplete read stays false
 * @param projectileBlocksPerTick declared projectile speed in blocks per tick; empty when unread
 */
public record NativeSkillFacts(OptionalDouble cooldownSeconds, int charges, String cooldownMode,
                               OptionalInt windUpTicks, OptionalDouble targetWithinBlocks,
                               OptionalDouble radiusBlocks, OptionalDouble coneRangeBlocks,
                               boolean damageIgnoresShield, boolean damageIgnoresInvulnerability,
                               boolean requiresLineOfSight, boolean hasDamagingMechanic,
                               OptionalInt recoveryTicks, int cueSignature, int cueChannels, HitShape hitShape,
                               DisplacementKind targetDisplacement, DisplacementKind selfDisplacement,
                               DisplacementOrigin displacementOrigin, boolean targetsAllies,
                               OptionalDouble projectileBlocksPerTick) {
    /** The default {@code CooldownMode} of MythicMobs. */
    public static final String SEQUENTIAL = "SEQUENTIAL";
    /** The facts of a skill that is not a MythicMobs metaskill: nothing read, one charge, {@code SEQUENTIAL}. */
    public static final NativeSkillFacts NONE = new NativeSkillFacts(OptionalDouble.empty(), 1, SEQUENTIAL,
            OptionalInt.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false,
            false, false);
    /**
     * Vanilla melee is a knockback whose branches are not a metaskill. The origin is assumed, not a change of
     * {@link #NONE}.
     */
    public static final NativeSkillFacts VANILLA_KNOCKBACK = new NativeSkillFacts(OptionalDouble.empty(), 1, SEQUENTIAL,
            OptionalInt.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false,
            false, true, OptionalInt.empty(), 0, 0, HitShape.UNKNOWN, DisplacementKind.KNOCKBACK,
            DisplacementKind.NONE, DisplacementOrigin.ASSUMED);
    /**
     * An untyped or unread damaging choice. The branches were not read, so the target is knockback until the author
     * declares otherwise. Damage stays unread so a purpose can still say the hit does not hurt.
     */
    public static final NativeSkillFacts ASSUMED_KNOCKBACK = new NativeSkillFacts(OptionalDouble.empty(), 1, SEQUENTIAL,
            OptionalInt.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false,
            false, false, OptionalInt.empty(), 0, 0, HitShape.UNKNOWN, DisplacementKind.KNOCKBACK,
            DisplacementKind.NONE, DisplacementOrigin.ASSUMED);

    /**
     * Validates the facts.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public NativeSkillFacts {
        Objects.requireNonNull(cooldownSeconds, "cooldownSeconds");
        Objects.requireNonNull(windUpTicks, "windUpTicks");
        Objects.requireNonNull(recoveryTicks, "recoveryTicks");
        Objects.requireNonNull(hitShape, "hitShape");
        Objects.requireNonNull(targetDisplacement, "targetDisplacement");
        Objects.requireNonNull(selfDisplacement, "selfDisplacement");
        Objects.requireNonNull(displacementOrigin, "displacementOrigin");
        Objects.requireNonNull(projectileBlocksPerTick, "projectileBlocksPerTick");
        projectileBlocksPerTick.ifPresent(speed -> {
            if (!(Double.isFinite(speed) && speed > 0.0 && speed <= 64.0)) {
                throw new IllegalArgumentException("projectileBlocksPerTick must be finite, greater than 0 and at most"
                        + " 64, was " + speed);
            }
        });
        recoveryTicks.ifPresent(ticks -> DomainChecks.requireRange(ticks, 0, SkillPurpose.MAX_WIND_UP_TICKS,
                "recoveryTicks"));
        DomainChecks.requireText(Objects.requireNonNull(cooldownMode, "cooldownMode"), "cooldownMode");
        if (charges < 1) {
            throw new IllegalArgumentException("charges must be >= 1, was " + charges);
        }
        cooldownSeconds.ifPresent(seconds -> DomainChecks.requireNonNegative(seconds, "cooldownSeconds"));
        windUpTicks.ifPresent(ticks -> DomainChecks.requireRange(ticks, 0, SkillPurpose.MAX_WIND_UP_TICKS,
                "windUpTicks"));
        requireBlocks(targetWithinBlocks, SkillPurpose.MAX_REACH_BLOCKS, "targetWithinBlocks");
        requireBlocks(radiusBlocks, SkillPurpose.MAX_RADIUS_BLOCKS, "radiusBlocks");
        requireBlocks(coneRangeBlocks, SkillPurpose.MAX_REACH_BLOCKS, "coneRangeBlocks");
    }

    /**
     * The wind-up to wait after a cast: the one read, or 0.
     *
     * @return ticks
     */
    public int windUpOrZero() {
        return windUpTicks.orElse(0);
    }

    /**
     * Facts from before projectile speed was read (rule W5). The speed stays empty.
     *
     * @param cooldownSeconds the static cooldown
     * @param charges charges
     * @param cooldownMode cooldown mode
     * @param windUpTicks wind-up
     * @param targetWithinBlocks target-within
     * @param radiusBlocks radius
     * @param coneRangeBlocks cone range
     * @param damageIgnoresShield whether some hit ignores a shield
     * @param damageIgnoresInvulnerability whether every hit ignores invulnerability
     * @param requiresLineOfSight whether a line of sight is required
     * @param hasDamagingMechanic whether some mechanic deals damage
     * @param recoveryTicks recovery
     * @param cueSignature cue hash
     * @param cueChannels cue bits
     * @param hitShape hit shape
     * @param targetDisplacement how the choice moves the target
     * @param selfDisplacement how the choice moves the caster
     * @param displacementOrigin where that reading came from
     * @param targetsAllies whether a mechanic targeter names an ally
     */
    public NativeSkillFacts(OptionalDouble cooldownSeconds, int charges, String cooldownMode, OptionalInt windUpTicks,
                            OptionalDouble targetWithinBlocks, OptionalDouble radiusBlocks,
                            OptionalDouble coneRangeBlocks, boolean damageIgnoresShield,
                            boolean damageIgnoresInvulnerability, boolean requiresLineOfSight,
                            boolean hasDamagingMechanic, OptionalInt recoveryTicks, int cueSignature, int cueChannels,
                            HitShape hitShape, DisplacementKind targetDisplacement, DisplacementKind selfDisplacement,
                            DisplacementOrigin displacementOrigin, boolean targetsAllies) {
        this(cooldownSeconds, charges, cooldownMode, windUpTicks, targetWithinBlocks, radiusBlocks, coneRangeBlocks,
                damageIgnoresShield, damageIgnoresInvulnerability, requiresLineOfSight, hasDamagingMechanic,
                recoveryTicks, cueSignature, cueChannels, hitShape, targetDisplacement, selfDisplacement,
                displacementOrigin, targetsAllies, OptionalDouble.empty());
    }

    /**
     * Facts from before ally targeting was read (rule W5). {@code targetsAllies} is false.
     *
     * @param cooldownSeconds the static cooldown
     * @param charges charges
     * @param cooldownMode cooldown mode
     * @param windUpTicks wind-up
     * @param targetWithinBlocks target-within
     * @param radiusBlocks radius
     * @param coneRangeBlocks cone range
     * @param damageIgnoresShield whether some hit ignores a shield
     * @param damageIgnoresInvulnerability whether every hit ignores invulnerability
     * @param requiresLineOfSight whether a line of sight is required
     * @param hasDamagingMechanic whether some mechanic deals damage
     * @param recoveryTicks recovery
     * @param cueSignature cue hash
     * @param cueChannels cue bits
     * @param hitShape hit shape
     * @param targetDisplacement how the choice moves the target
     * @param selfDisplacement how the choice moves the caster
     * @param displacementOrigin where that reading came from
     */
    public NativeSkillFacts(OptionalDouble cooldownSeconds, int charges, String cooldownMode, OptionalInt windUpTicks,
                            OptionalDouble targetWithinBlocks, OptionalDouble radiusBlocks,
                            OptionalDouble coneRangeBlocks, boolean damageIgnoresShield,
                            boolean damageIgnoresInvulnerability, boolean requiresLineOfSight,
                            boolean hasDamagingMechanic, OptionalInt recoveryTicks, int cueSignature, int cueChannels,
                            HitShape hitShape, DisplacementKind targetDisplacement, DisplacementKind selfDisplacement,
                            DisplacementOrigin displacementOrigin) {
        this(cooldownSeconds, charges, cooldownMode, windUpTicks, targetWithinBlocks, radiusBlocks, coneRangeBlocks,
                damageIgnoresShield, damageIgnoresInvulnerability, requiresLineOfSight, hasDamagingMechanic,
                recoveryTicks, cueSignature, cueChannels, hitShape, targetDisplacement, selfDisplacement,
                displacementOrigin, false);
    }

    /**
     * Copy that records ally targeting.
     *
     * @param targetsAllies whether a mechanic targeter names an ally
     * @return the copy
     */
    public NativeSkillFacts withTargetsAllies(boolean targetsAllies) {
        return new NativeSkillFacts(cooldownSeconds, charges, cooldownMode, windUpTicks, targetWithinBlocks,
                radiusBlocks, coneRangeBlocks, damageIgnoresShield, damageIgnoresInvulnerability, requiresLineOfSight,
                hasDamagingMechanic, recoveryTicks, cueSignature, cueChannels, hitShape, targetDisplacement,
                selfDisplacement, displacementOrigin, targetsAllies, projectileBlocksPerTick);
    }

    /**
     * Copy that records a declared projectile speed.
     *
     * @param blocksPerTick speed in blocks per tick, finite and in {@code (0, 64]}
     * @return the copy
     */
    public NativeSkillFacts withProjectileSpeed(double blocksPerTick) {
        return new NativeSkillFacts(cooldownSeconds, charges, cooldownMode, windUpTicks, targetWithinBlocks,
                radiusBlocks, coneRangeBlocks, damageIgnoresShield, damageIgnoresInvulnerability, requiresLineOfSight,
                hasDamagingMechanic, recoveryTicks, cueSignature, cueChannels, hitShape, targetDisplacement,
                selfDisplacement, displacementOrigin, targetsAllies, OptionalDouble.of(blocksPerTick));
    }

    /**
     * Facts read before M23 stored no recovery, cue or shape (rule W5).
     *
     * @param cooldownSeconds the static cooldown
     * @param charges charges
     * @param cooldownMode cooldown mode
     * @param windUpTicks wind-up
     * @param targetWithinBlocks target-within
     * @param radiusBlocks radius
     * @param coneRangeBlocks cone range
     * @param damageIgnoresShield whether some hit ignores a shield
     * @param damageIgnoresInvulnerability whether every hit ignores invulnerability
     * @param requiresLineOfSight whether a line of sight is required
     * @param hasDamagingMechanic whether some mechanic deals damage
     */
    public NativeSkillFacts(OptionalDouble cooldownSeconds, int charges, String cooldownMode, OptionalInt windUpTicks,
                            OptionalDouble targetWithinBlocks, OptionalDouble radiusBlocks,
                            OptionalDouble coneRangeBlocks, boolean damageIgnoresShield,
                            boolean damageIgnoresInvulnerability, boolean requiresLineOfSight,
                            boolean hasDamagingMechanic) {
        this(cooldownSeconds, charges, cooldownMode, windUpTicks, targetWithinBlocks, radiusBlocks, coneRangeBlocks,
                damageIgnoresShield, damageIgnoresInvulnerability, requiresLineOfSight, hasDamagingMechanic,
                OptionalInt.empty(), 0, 0, HitShape.UNKNOWN, DisplacementKind.NONE, DisplacementKind.NONE,
                DisplacementOrigin.READ);
    }

    /**
     * Facts from before displacement was read (rule W5). The target and the caster are not moved and the origin is
     * {@link DisplacementOrigin#READ}.
     *
     * @param cooldownSeconds the static cooldown
     * @param charges charges
     * @param cooldownMode cooldown mode
     * @param windUpTicks wind-up
     * @param targetWithinBlocks target-within
     * @param radiusBlocks radius
     * @param coneRangeBlocks cone range
     * @param damageIgnoresShield whether some hit ignores a shield
     * @param damageIgnoresInvulnerability whether every hit ignores invulnerability
     * @param requiresLineOfSight whether a line of sight is required
     * @param hasDamagingMechanic whether some mechanic deals damage
     * @param recoveryTicks recovery
     * @param cueSignature cue hash
     * @param cueChannels cue bits
     * @param hitShape hit shape
     */
    public NativeSkillFacts(OptionalDouble cooldownSeconds, int charges, String cooldownMode, OptionalInt windUpTicks,
                            OptionalDouble targetWithinBlocks, OptionalDouble radiusBlocks,
                            OptionalDouble coneRangeBlocks, boolean damageIgnoresShield,
                            boolean damageIgnoresInvulnerability, boolean requiresLineOfSight,
                            boolean hasDamagingMechanic, OptionalInt recoveryTicks, int cueSignature, int cueChannels,
                            HitShape hitShape) {
        this(cooldownSeconds, charges, cooldownMode, windUpTicks, targetWithinBlocks, radiusBlocks, coneRangeBlocks,
                damageIgnoresShield, damageIgnoresInvulnerability, requiresLineOfSight, hasDamagingMechanic,
                recoveryTicks, cueSignature, cueChannels, hitShape, DisplacementKind.NONE, DisplacementKind.NONE,
                DisplacementOrigin.READ);
    }

    private static void requireBlocks(OptionalDouble value, double maximum, String field) {
        Objects.requireNonNull(value, field);
        if (value.isPresent()) {
            var blocks = value.getAsDouble();
            if (!(Double.isFinite(blocks) && blocks > 0.0 && blocks <= maximum)) {
                throw new IllegalArgumentException(field + " must be finite, greater than 0 and at most " + maximum
                        + ", was " + blocks);
            }
        }
    }
}
