package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An action a tactic may perform: a MythicMobs skill or a vanilla melee attack, with what it is for when that is known.
 * Immutable.
 *
 * <p>Since M19 a skill choice has a purpose only when the profile declares one ({@code DECLARED}) or its skill has a
 * valid {@code Intention} line ({@code SKILL}); without either it is untyped, and every rule that needs the purpose
 * uses the conservative values of {@link PurposeRules#UNTYPED_DEFAULTS} (rule A6, user decision 5). A vanilla melee
 * choice always has its fixed damaging melee purpose. Production code builds choices with {@link #typed},
 * {@link #untyped} and {@link #vanillaMelee}, so no method takes an {@code Optional} (rule C4); the components are the
 * record form.
 *
 * @param id choice name, local to the profile
 * @param kind how the choice is executed
 * @param mythicSkillId exact MythicMobs skill ID; present only for {@code MYTHIC_SKILL}
 * @param values labels of the skill ({@code PredictValues}); kept as history, never the source of a purpose since M19
 * @param requiresTarget whether the choice needs a target to be cast
 * @param extraCooldownTicks extra cooldown in server ticks, {@code >= 0}; kept for compatibility
 * @param purpose what the choice is for; empty for an untyped skill choice (M19.2)
 * @param nativeFacts what MythicMobs enforces for the skill, read when the profile binds (M19.3);
 *        {@link NativeSkillFacts#NONE} before binding and for a vanilla melee choice
 * @param telegraph declared tell; {@link ChoiceTelegraph#NONE} when the choice does not declare one (M23.2)
 * @param displacement author override; empty uses the reading (M24.6)
 * @param targetsAlliesOverride author override of ally targeting; empty uses the reading (M25)
 */
public record SkillChoice(ChoiceId id, ChoiceKind kind, Optional<String> mythicSkillId,
                          Set<PredictValue> values, boolean requiresTarget, int extraCooldownTicks,
                          Optional<SkillPurpose> purpose, NativeSkillFacts nativeFacts,
                          ChoiceTelegraph telegraph, Optional<DisplacementKind> displacement,
                          Optional<Boolean> targetsAlliesOverride) {
    /**
     * Validates the choice and copies the labels.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the skill ID does not match the kind, the cooldown is negative, or a
     *         vanilla melee choice has no purpose or one that is not a damaging {@code MELEE} hit
     */
    public SkillChoice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(mythicSkillId, "mythicSkillId");
        values = Set.copyOf(Objects.requireNonNull(values, "values"));
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(nativeFacts, "nativeFacts");
        Objects.requireNonNull(telegraph, "telegraph");
        Objects.requireNonNull(displacement, "displacement");
        Objects.requireNonNull(targetsAlliesOverride, "targetsAlliesOverride");
        if (extraCooldownTicks < 0) {
            throw new IllegalArgumentException("extraCooldownTicks must be >= 0, was " + extraCooldownTicks);
        }
        if (kind == ChoiceKind.MYTHIC_SKILL) {
            DomainChecks.requireText(mythicSkillId.orElseThrow(() -> new IllegalArgumentException(
                    "mythicSkillId is required for kind MYTHIC_SKILL in choice " + id)), "mythicSkillId");
        } else if (mythicSkillId.isPresent()) {
            throw new IllegalArgumentException("mythicSkillId must be empty for kind VANILLA_MELEE in choice " + id);
        } else if (purpose.filter(found -> found.delivery() == Delivery.MELEE
                && found.roles().contains(SkillRole.DAMAGE)).isEmpty()) {
            throw new IllegalArgumentException("purpose of a VANILLA_MELEE choice must have delivery MELEE and the role"
                    + " DAMAGE in choice " + id);
        }
    }

    /**
     * Compatibility constructor without a declared tell (rule W5). The tell is {@link ChoiceTelegraph#NONE}.
     *
     * @param id choice name, local to the profile
     * @param kind how the choice is executed
     * @param mythicSkillId exact MythicMobs skill ID; present only for {@code MYTHIC_SKILL}
     * @param values labels of the skill
     * @param requiresTarget whether the choice needs a target to be cast
     * @param extraCooldownTicks extra cooldown in server ticks, {@code >= 0}
     * @param purpose what the choice is for; empty for an untyped skill choice
     * @param nativeFacts what MythicMobs enforces
     */
    public SkillChoice(ChoiceId id, ChoiceKind kind, Optional<String> mythicSkillId, Set<PredictValue> values,
                       boolean requiresTarget, int extraCooldownTicks, Optional<SkillPurpose> purpose,
                       NativeSkillFacts nativeFacts) {
        this(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks, purpose, nativeFacts,
                ChoiceTelegraph.NONE, Optional.empty());
    }

    /**
     * Compatibility constructor without a displacement override (rule W5).
     *
     * @param id choice name
     * @param kind how the choice is executed
     * @param mythicSkillId exact MythicMobs skill ID
     * @param values labels of the skill
     * @param requiresTarget whether the choice needs a target
     * @param extraCooldownTicks extra cooldown in ticks
     * @param purpose what the choice is for
     * @param nativeFacts what MythicMobs enforces
     * @param telegraph declared tell
     */
    public SkillChoice(ChoiceId id, ChoiceKind kind, Optional<String> mythicSkillId, Set<PredictValue> values,
                       boolean requiresTarget, int extraCooldownTicks, Optional<SkillPurpose> purpose,
                       NativeSkillFacts nativeFacts, ChoiceTelegraph telegraph) {
        this(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks, purpose, nativeFacts, telegraph,
                Optional.empty());
    }

    /**
     * Compatibility constructor without an ally-targeting override (rule W5).
     *
     * @param id choice name
     * @param kind how the choice is executed
     * @param mythicSkillId exact MythicMobs skill ID
     * @param values labels of the skill
     * @param requiresTarget whether the choice needs a target
     * @param extraCooldownTicks extra cooldown in ticks
     * @param purpose what the choice is for
     * @param nativeFacts what MythicMobs enforces
     * @param telegraph declared tell
     * @param displacement author override
     */
    public SkillChoice(ChoiceId id, ChoiceKind kind, Optional<String> mythicSkillId, Set<PredictValue> values,
                       boolean requiresTarget, int extraCooldownTicks, Optional<SkillPurpose> purpose,
                       NativeSkillFacts nativeFacts, ChoiceTelegraph telegraph,
                       Optional<DisplacementKind> displacement) {
        this(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks, purpose, nativeFacts, telegraph,
                displacement, Optional.empty());
    }

    /**
     * Compatibility constructor of a typed choice without native facts (rule W5): delegates to {@link #typed} for a
     * skill and to {@link #vanillaMelee} for a vanilla melee choice.
     *
     * @param id choice name, local to the profile
     * @param kind how the choice is executed
     * @param mythicSkillId exact MythicMobs skill ID; present only for {@code MYTHIC_SKILL}
     * @param values labels of the skill
     * @param requiresTarget whether the choice needs a target to be cast
     * @param extraCooldownTicks extra cooldown in server ticks, {@code >= 0}
     * @param purpose what the choice is for
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public SkillChoice(ChoiceId id, ChoiceKind kind, Optional<String> mythicSkillId, Set<PredictValue> values,
                       boolean requiresTarget, int extraCooldownTicks, SkillPurpose purpose) {
        this(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks,
                Optional.of(Objects.requireNonNull(purpose, "purpose")), NativeSkillFacts.NONE);
    }

    /**
     * Compatibility constructor of the 0.0.1 (rule W5), without purpose: a skill choice is untyped, because nothing is
     * inferred from {@code PredictValues} any more (M19, user decision 5), and a vanilla melee choice gets its fixed
     * purpose with {@link MovementSettings#DEFAULTS}. Production code uses the factories.
     *
     * @param id choice name, local to the profile
     * @param kind how the choice is executed
     * @param mythicSkillId exact MythicMobs skill ID; present only for {@code MYTHIC_SKILL}
     * @param values labels of the skill
     * @param requiresTarget whether the choice needs a target to be cast
     * @param extraCooldownTicks extra cooldown in server ticks, {@code >= 0}
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the skill ID does not match the kind or the cooldown is negative
     */
    public SkillChoice(ChoiceId id, ChoiceKind kind, Optional<String> mythicSkillId, Set<PredictValue> values,
                       boolean requiresTarget, int extraCooldownTicks) {
        this(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks,
                Objects.requireNonNull(kind, "kind") == ChoiceKind.VANILLA_MELEE
                        ? Optional.of(PurposeRules.vanillaMelee(MovementSettings.DEFAULTS)) : Optional.empty(),
                NativeSkillFacts.NONE);
    }

    /**
     * A MythicMobs skill choice with its purpose.
     *
     * @param id choice name
     * @param mythicSkillId exact MythicMobs skill ID
     * @param values labels of the skill, kept as history
     * @param requiresTarget whether the choice needs a target
     * @param extraCooldownTicks extra cooldown in ticks
     * @param purpose what the choice is for
     * @return the choice, without native facts
     */
    public static SkillChoice typed(ChoiceId id, String mythicSkillId, Set<PredictValue> values,
                                    boolean requiresTarget, int extraCooldownTicks, SkillPurpose purpose) {
        return new SkillChoice(id, ChoiceKind.MYTHIC_SKILL, Optional.of(mythicSkillId), values, requiresTarget,
                extraCooldownTicks, Optional.of(Objects.requireNonNull(purpose, "purpose")), NativeSkillFacts.NONE);
    }

    /**
     * A MythicMobs skill choice without purpose (M19.2).
     *
     * @param id choice name
     * @param mythicSkillId exact MythicMobs skill ID
     * @param values labels of the skill, kept as history
     * @param requiresTarget whether the choice needs a target
     * @param extraCooldownTicks extra cooldown in ticks
     * @return the untyped choice, assumed knockback until a purpose or a reading replaces it
     */
    public static SkillChoice untyped(ChoiceId id, String mythicSkillId, Set<PredictValue> values,
                                      boolean requiresTarget, int extraCooldownTicks) {
        return new SkillChoice(id, ChoiceKind.MYTHIC_SKILL, Optional.of(mythicSkillId), values, requiresTarget,
                extraCooldownTicks, Optional.empty(), NativeSkillFacts.ASSUMED_KNOCKBACK);
    }

    /**
     * A vanilla melee choice with its purpose, a damaging melee hit.
     *
     * @param id choice name
     * @param requiresTarget whether the choice needs a target
     * @param extraCooldownTicks extra cooldown in ticks
     * @param purpose its purpose
     * @return the choice
     */
    public static SkillChoice vanillaMelee(ChoiceId id, boolean requiresTarget, int extraCooldownTicks,
                                           SkillPurpose purpose) {
        return new SkillChoice(id, ChoiceKind.VANILLA_MELEE, Optional.empty(), Set.of(), requiresTarget,
                extraCooldownTicks, Optional.of(Objects.requireNonNull(purpose, "purpose")),
                NativeSkillFacts.VANILLA_KNOCKBACK);
    }

    /**
     * Tells whether the choice has a purpose.
     *
     * @return {@code false} only for an untyped skill choice
     */
    public boolean isTyped() {
        return purpose.isPresent();
    }

    /**
     * The purpose of a choice the caller already knows is typed.
     *
     * @return the purpose
     * @throws IllegalStateException if the choice is untyped
     */
    public SkillPurpose typedPurpose() {
        return purpose.orElseThrow(() -> new IllegalStateException("choice " + id + " is untyped"));
    }

    /**
     * Tells whether the choice has a purpose written by the author ({@code DECLARED} or {@code SKILL}).
     *
     * @return {@code false} for an untyped choice and for the fixed purpose of a vanilla melee choice
     */
    public boolean authored() {
        return purpose.filter(SkillPurpose::authored).isPresent();
    }

    /**
     * The wind-up a cast of this choice keeps the mob busy for: the larger of the purpose's and the one read from
     * MythicMobs (M19.4).
     *
     * @return ticks
     */
    public int busyTicks() {
        return purpose.map(typed -> ImpactTiming.effectiveWindUpTicks(typed, nativeFacts))
                .orElseGet(() -> ImpactTiming.effectiveWindUpTicks(nativeFacts));
    }

    /**
     * Returns a copy with another purpose, keeping everything else.
     *
     * @param newPurpose the purpose
     * @return the copy
     */
    public SkillChoice withPurpose(SkillPurpose newPurpose) {
        return new SkillChoice(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks,
                Optional.of(Objects.requireNonNull(newPurpose, "newPurpose")), nativeFacts, telegraph, displacement,
                targetsAlliesOverride);
    }

    /**
     * Returns a copy without purpose, keeping everything else.
     *
     * @return the untyped copy
     * @throws IllegalArgumentException for a vanilla melee choice, which always has a purpose
     */
    public SkillChoice withoutPurpose() {
        return new SkillChoice(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks, Optional.empty(),
                nativeFacts, telegraph, displacement, targetsAlliesOverride);
    }

    /**
     * Returns a copy with the native facts read when the profile binds.
     *
     * @param facts the facts
     * @return the copy
     */
    public SkillChoice withNativeFacts(NativeSkillFacts facts) {
        return new SkillChoice(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks, purpose,
                Objects.requireNonNull(facts, "facts"), telegraph, displacement, targetsAlliesOverride);
    }

    /**
     * Returns a copy with other labels, keeping everything else.
     *
     * @param newValues the labels
     * @return the copy
     */
    public SkillChoice withValues(Set<PredictValue> newValues) {
        return new SkillChoice(id, kind, mythicSkillId, newValues, requiresTarget, extraCooldownTicks, purpose,
                nativeFacts, telegraph, displacement, targetsAlliesOverride);
    }

    /**
     * Returns a copy with a declared tell.
     *
     * @param declared the tell
     * @return the copy
     */
    public SkillChoice withTelegraph(ChoiceTelegraph declared) {
        return new SkillChoice(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks, purpose,
                nativeFacts, Objects.requireNonNull(declared, "declared"), displacement, targetsAlliesOverride);
    }

    /**
     * Returns a copy with an author displacement (M24.6). The reading no longer decides.
     *
     * @param override the declared displacement
     * @return the copy
     */
    public SkillChoice withDisplacement(DisplacementKind override) {
        return withDisplacement(Optional.of(Objects.requireNonNull(override, "override")));
    }

    /**
     * Returns a copy with an author displacement. Empty keeps the reading. Kept only for the profile parser, which
     * still passes an {@code Optional}; rule C4 asks for {@link #withDisplacement(DisplacementKind)}.
     *
     * @param override the override
     * @return the copy
     */
    public SkillChoice withDisplacement(Optional<DisplacementKind> override) {
        return new SkillChoice(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks, purpose,
                nativeFacts, telegraph, Objects.requireNonNull(override, "override"), targetsAlliesOverride);
    }

    /**
     * Returns a copy with an author override of ally targeting (M25). The reading no longer decides.
     *
     * @param override whether allies are valid targets
     * @return the copy
     */
    public SkillChoice withTargetsAllies(boolean override) {
        return withTargetsAllies(Optional.of(override));
    }

    /**
     * Returns a copy with an author override of ally targeting. Kept only for the profile parser, which still passes
     * an {@code Optional}; rule C4 asks for {@link #withTargetsAllies(boolean)}.
     *
     * @param override empty to use the reading
     * @return the copy
     */
    public SkillChoice withTargetsAllies(Optional<Boolean> override) {
        return new SkillChoice(id, kind, mythicSkillId, values, requiresTarget, extraCooldownTicks, purpose,
                nativeFacts, telegraph, displacement, Objects.requireNonNull(override, "override"));
    }

    /**
     * Whether the choice can target an ally. The override wins. Otherwise the reading does.
     *
     * @return whether allies are valid targets
     */
    public boolean targetsAllies() {
        return targetsAlliesOverride.orElseGet(nativeFacts::targetsAllies);
    }
}
