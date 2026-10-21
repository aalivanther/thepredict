package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.adapter.server.context.ParticipantPolicy;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalProfile;
import io.lumine.mythic.api.skills.Skill;

import akyrion.thepredict.domain.DifficultyPreset;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A tactical profile paired with the MythicMobs objects of the current load (M03.4). Replaced as a whole
 * after every reload, because MythicMobs discards its objects when it reloads. Server thread only.
 *
 * <p>Invariant (M15.3, M19.2): a skill choice has its loaded skill. A typed choice has the cooldown class read from the
 * native {@code Cooldown} ({@code UNKNOWN} only when MythicMobs gives no fixed readable value); an untyped choice is
 * accepted with an empty purpose and its cooldown class is not checked. A vanilla melee choice has the class
 * {@code SHORT} or {@code MEDIUM} of its {@code CD}. Nothing is inferred from {@code PredictValues}.
 *
 * @param profile validated profile, with the {@code PredictValues} and the purpose of each choice filled in
 * @param skills loaded skill of every {@code MYTHIC_SKILL} choice
 * @param conditions converted native conditions of every non-shortcut context
 * @param entityType MythicMobs {@code Type} of the mob, in upper case
 * @param brainSpecies whether the species uses a {@code Brain}, so only {@code Movement: NONE} is allowed
 * @param participants participant policy of the profile, compiled when it binds (M17.2)
 * @param declaredPreset mob-file {@code PredictorDifficulty}, empty when the key is absent
 * @param declaredEntropy mob-file {@code PredictorEntropy}
 * @param declaredTraining mob-file {@code PredictorTraining}
 * @param cueHandlers {@code ~onSignal} handlers of the mob's {@code Skills} lines (M23.3), read when it binds, so a
 *                    cue is sent only when the author listens for it
 */
public record BoundProfile(TacticalProfile profile, Map<ChoiceId, Skill> skills,
                           Map<ContextId, NativeConditions> conditions, String entityType, boolean brainSpecies,
                           ParticipantPolicy participants, Optional<DifficultyPreset> declaredPreset,
                           boolean declaredEntropy, boolean declaredTraining, MobCueHandlers cueHandlers) {
    /**
     * Validates the components and copies the maps.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if a choice has no resolved purpose
     */
    public BoundProfile {
        Objects.requireNonNull(profile, "profile");
        skills = Map.copyOf(Objects.requireNonNull(skills, "skills"));
        conditions = Map.copyOf(Objects.requireNonNull(conditions, "conditions"));
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(declaredPreset, "declaredPreset");
        Objects.requireNonNull(cueHandlers, "cueHandlers");
        for (var choice : profile.choices().values()) {
            requireResolved(choice, skills.get(choice.id()));
        }
    }

    /**
     * Creates a bound profile whose mob declares no {@code ~onSignal} handler. Kept for the doubles that bind without
     * mob lines (rule W5); the binder always passes the handlers it read.
     *
     * @param profile validated profile
     * @param skills loaded skill of every {@code MYTHIC_SKILL} choice
     * @param conditions converted native conditions
     * @param entityType MythicMobs {@code Type} of the mob
     * @param brainSpecies whether the species uses a {@code Brain}
     * @param participants participant policy of the profile
     * @param declaredPreset mob-file {@code PredictorDifficulty}
     * @param declaredEntropy mob-file {@code PredictorEntropy}
     * @param declaredTraining mob-file {@code PredictorTraining}
     * @throws IllegalArgumentException if a choice has no resolved purpose
     */
    public BoundProfile(TacticalProfile profile, Map<ChoiceId, Skill> skills,
                        Map<ContextId, NativeConditions> conditions, String entityType, boolean brainSpecies,
                        ParticipantPolicy participants, Optional<DifficultyPreset> declaredPreset,
                        boolean declaredEntropy, boolean declaredTraining) {
        this(profile, skills, conditions, entityType, brainSpecies, participants, declaredPreset, declaredEntropy,
                declaredTraining, MobCueHandlers.NONE);
    }

    /**
     * Creates a bound profile without a mob-file preset. Entropy stays off.
     *
     * @param profile validated profile
     * @param skills loaded skill of every {@code MYTHIC_SKILL} choice
     * @param conditions converted native conditions
     * @param entityType MythicMobs {@code Type} of the mob
     * @param brainSpecies whether the species uses a {@code Brain}
     * @param participants participant policy of the profile
     * @throws IllegalArgumentException if a choice has no resolved purpose
     */
    public BoundProfile(TacticalProfile profile, Map<ChoiceId, Skill> skills,
                        Map<ContextId, NativeConditions> conditions, String entityType, boolean brainSpecies,
                        ParticipantPolicy participants) {
        this(profile, skills, conditions, entityType, brainSpecies, participants, Optional.empty(), false, false);
    }

    /**
     * Creates a bound profile with the participant policy of 0.0.1 ({@link ParticipantPolicy#DEFAULT}). Kept for the
     * tests and doubles that bind without participant rules (rule W5); the binder always passes the compiled policy.
     *
     * @param profile validated profile
     * @param skills loaded skill of every {@code MYTHIC_SKILL} choice
     * @param conditions converted native conditions
     * @param entityType MythicMobs {@code Type} of the mob
     * @param brainSpecies whether the species uses a {@code Brain}
     * @throws IllegalArgumentException if a choice has no resolved purpose
     */
    public BoundProfile(TacticalProfile profile, Map<ChoiceId, Skill> skills,
                        Map<ContextId, NativeConditions> conditions, String entityType, boolean brainSpecies) {
        this(profile, skills, conditions, entityType, brainSpecies, ParticipantPolicy.DEFAULT);
    }

    private static void requireResolved(SkillChoice choice, Skill skill) {
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            requireCooldown(choice, SkillMetadata.vanillaCooldownClass(choice.extraCooldownTicks()));
            return;
        }
        if (skill == null) {
            throw new IllegalArgumentException("skill choice " + choice.id() + " has no loaded skill");
        }
        if (choice.purpose().isEmpty()) {
            return;
        }
        requireCooldown(choice, SkillMetadata.cooldownClass(skill, choice.extraCooldownTicks()));
    }

    private static void requireCooldown(SkillChoice choice, CooldownClass expected) {
        var actual = choice.typedPurpose().cooldown();
        if (actual != expected) {
            throw new IllegalArgumentException("choice " + choice.id() + " has cooldown class " + actual
                    + " instead of " + expected);
        }
    }

    /**
     * Returns a copy that uses another profile and the same MythicMobs objects.
     *
     * @param replaced profile after an overlay
     * @return this binding when the profile is the same instance
     */
    public BoundProfile withProfile(TacticalProfile replaced) {
        if (replaced == profile) {
            return this;
        }
        return new BoundProfile(replaced, skills, conditions, entityType, brainSpecies, participants, declaredPreset,
                declaredEntropy, declaredTraining, cueHandlers);
    }

    /**
     * Returns a copy whose profile no longer declares the given contexts, so they never become active.
     *
     * @param disabled contexts to remove
     * @return this profile when nothing is removed, otherwise the reduced copy
     */
    public BoundProfile withoutContexts(Set<ContextId> disabled) {
        Objects.requireNonNull(disabled, "disabled");
        if (disabled.isEmpty()) {
            return this;
        }
        var contexts = profile.contexts().stream().filter(context -> !disabled.contains(context.id())).toList();
        var remaining = new HashMap<>(conditions);
        remaining.keySet().removeAll(disabled);
        return new BoundProfile(profile.withContexts(contexts), skills, remaining, entityType, brainSpecies,
                participants, declaredPreset, declaredEntropy, declaredTraining, cueHandlers);
    }
}
