package akyrion.thepredict.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A validated tactical profile: the tactics, choices and contexts of one MythicMobs mob type.
 * Immutable and safe to share between threads.
 *
 * <p>The tactic and choice maps keep the declaration order of the author, which breaks ties
 * between equal priorities. Invariants: at least one tactic; each map key equals the ID of its
 * value; no reserved tactic ({@code none} or {@code debug_}); each {@link Tactic#choice()} exists
 * in {@code choices}; each {@link ContextRule} references declared tactics and has a unique ID;
 * {@code id}, {@code mythicMobId} and {@code objective} are not blank.
 *
 * @param id profile name
 * @param mythicMobId exact MythicMobs mob ID
 * @param objective author objective for the model
 * @param tactics tactics in declaration order
 * @param choices skill choices in declaration order
 * @param contexts context rules in declaration order
 * @param movement movement settings
 * @param health health thresholds
 * @param threat threat weights
 * @param baseAi {@code AIGoalSelectors} lines, not interpreted by the domain
 * @param timing decision timing
 * @param participants participant rules of the profile, resolved against the global ones when loaded (M17.2)
 * @param difficulty the {@code Difficulty} section, {@link DifficultySettings#DEFAULT} when the profile omits it (M22)
 * @param stances the {@code Stances} section (M23.2)
 * @param telegraph the {@code Telegraph} section (M23.2)
 * @param poise the {@code Poise} section; empty when the profile has no poise (M23.7)
 * @param zone the {@code Zone} section; {@link ZoneSettings#DEFAULT} when the profile omits it (M24)
 * @param group the {@code Group} section; {@link GroupSettings#DEFAULT} when the profile omits it (M25)
 * @param patterns the {@code Patterns} section; {@link PatternProfile#DEFAULT} when the profile omits it (M26)
 * @param memory the {@code Memory} section; {@link MemoryProfile#DEFAULT} when the profile omits it (M27)
 * @param decision the {@code Decision} section (M29)
 * @param distillMode profile {@code Distill.Mode}; empty when the profile omits it (M29)
 */
public record TacticalProfile(String id, String mythicMobId, String objective,
                              Map<TacticId, Tactic> tactics, Map<ChoiceId, SkillChoice> choices,
                              List<ContextRule> contexts, MovementSettings movement,
                              HealthThresholds health, ThreatWeights threat,
                              List<String> baseAi, DecisionTiming timing, ParticipantRules participants,
                              DifficultySettings difficulty, StanceSettings stances, TelegraphSettings telegraph,
                              Optional<PoiseSettings> poise, ZoneSettings zone, GroupSettings group,
                              PatternProfile patterns, MemoryProfile memory,
                              akyrion.thepredict.domain.distill.ProfileDecision decision,
                              java.util.Optional<akyrion.thepredict.domain.distill.DistillMode> distillMode) {
    /**
     * Validates cross references and copies every collection.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public TacticalProfile {
        DomainChecks.requireText(id, "id");
        DomainChecks.requireText(mythicMobId, "mythicMobId");
        DomainChecks.requireText(objective, "objective");
        tactics = orderedCopy(tactics, "tactics");
        choices = orderedCopy(choices, "choices");
        contexts = List.copyOf(Objects.requireNonNull(contexts, "contexts"));
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(threat, "threat");
        baseAi = List.copyOf(Objects.requireNonNull(baseAi, "baseAi"));
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(stances, "stances");
        Objects.requireNonNull(telegraph, "telegraph");
        Objects.requireNonNull(poise, "poise");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(distillMode, "distillMode");
        validateTactics(tactics, choices);
        validateChoices(choices);
        validateContexts(contexts, tactics);
    }

    /**
     * A profile from before stances and tells (rule W5). Stances and tells use their defaults and poise is absent.
     *
     * @param id profile name
     * @param mythicMobId exact MythicMobs mob ID
     * @param objective author objective
     * @param tactics tactics in declaration order
     * @param choices skill choices in declaration order
     * @param contexts context rules
     * @param movement movement settings
     * @param health health thresholds
     * @param threat threat weights
     * @param baseAi {@code AIGoalSelectors} lines
     * @param timing decision timing
     * @param participants participant rules
     * @param difficulty difficulty section
     */
    public TacticalProfile(String id, String mythicMobId, String objective, Map<TacticId, Tactic> tactics,
                           Map<ChoiceId, SkillChoice> choices, List<ContextRule> contexts, MovementSettings movement,
                           HealthThresholds health, ThreatWeights threat, List<String> baseAi, DecisionTiming timing,
                           ParticipantRules participants, DifficultySettings difficulty) {
        this(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat, baseAi, timing,
                participants, difficulty, StanceSettings.DEFAULT, TelegraphSettings.DEFAULT, Optional.empty(),
                ZoneSettings.DEFAULT);
    }

    /**
     * A profile from before the zone section (rule W5). The zone inherits and does not exploit hazards.
     *
     * @param id profile name
     * @param mythicMobId exact MythicMobs mob ID
     * @param objective author objective
     * @param tactics tactics in declaration order
     * @param choices skill choices in declaration order
     * @param contexts context rules
     * @param movement movement settings
     * @param health health thresholds
     * @param threat threat weights
     * @param baseAi {@code AIGoalSelectors} lines
     * @param timing decision timing
     * @param participants participant rules
     * @param difficulty difficulty section
     * @param stances stance section
     * @param telegraph telegraph section
     * @param poise poise section
     */
    public TacticalProfile(String id, String mythicMobId, String objective, Map<TacticId, Tactic> tactics,
                           Map<ChoiceId, SkillChoice> choices, List<ContextRule> contexts, MovementSettings movement,
                           HealthThresholds health, ThreatWeights threat, List<String> baseAi, DecisionTiming timing,
                           ParticipantRules participants, DifficultySettings difficulty, StanceSettings stances,
                           TelegraphSettings telegraph, Optional<PoiseSettings> poise) {
        this(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat, baseAi, timing,
                participants, difficulty, stances, telegraph, poise, ZoneSettings.DEFAULT, GroupSettings.DEFAULT);
    }

    /**
     * A profile from before the patterns section (rule W5). Patterns inherit and do not lower the global mode.
     *
     * @param id profile name
     * @param mythicMobId exact MythicMobs mob ID
     * @param objective author objective
     * @param tactics tactics in declaration order
     * @param choices skill choices in declaration order
     * @param contexts context rules
     * @param movement movement settings
     * @param health health thresholds
     * @param threat threat weights
     * @param baseAi {@code AIGoalSelectors} lines
     * @param timing decision timing
     * @param participants participant rules
     * @param difficulty difficulty section
     * @param stances stance section
     * @param telegraph telegraph section
     * @param poise poise section
     * @param zone zone section
     * @param group group section
     */
    public TacticalProfile(String id, String mythicMobId, String objective, Map<TacticId, Tactic> tactics,
                           Map<ChoiceId, SkillChoice> choices, List<ContextRule> contexts, MovementSettings movement,
                           HealthThresholds health, ThreatWeights threat, List<String> baseAi, DecisionTiming timing,
                           ParticipantRules participants, DifficultySettings difficulty, StanceSettings stances,
                           TelegraphSettings telegraph, Optional<PoiseSettings> poise, ZoneSettings zone,
                           GroupSettings group) {
        this(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat, baseAi, timing,
                participants, difficulty, stances, telegraph, poise, zone, group, PatternProfile.DEFAULT,
                MemoryProfile.DEFAULT);
    }

    /**
     * A profile from before the memory section (rule W5). Memory inherits and allows facts.
     *
     * @param id profile name
     * @param mythicMobId exact MythicMobs mob ID
     * @param objective author objective
     * @param tactics tactics in declaration order
     * @param choices skill choices in declaration order
     * @param contexts context rules
     * @param movement movement settings
     * @param health health thresholds
     * @param threat threat weights
     * @param baseAi {@code AIGoalSelectors} lines
     * @param timing decision timing
     * @param participants participant rules
     * @param difficulty difficulty section
     * @param stances stance section
     * @param telegraph telegraph section
     * @param poise poise section
     * @param zone zone section
     * @param group group section
     * @param patterns pattern section
     */
    public TacticalProfile(String id, String mythicMobId, String objective, Map<TacticId, Tactic> tactics,
                           Map<ChoiceId, SkillChoice> choices, List<ContextRule> contexts, MovementSettings movement,
                           HealthThresholds health, ThreatWeights threat, List<String> baseAi, DecisionTiming timing,
                           ParticipantRules participants, DifficultySettings difficulty, StanceSettings stances,
                           TelegraphSettings telegraph, Optional<PoiseSettings> poise, ZoneSettings zone,
                           GroupSettings group, PatternProfile patterns) {
        this(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat, baseAi, timing,
                participants, difficulty, stances, telegraph, poise, zone, group, patterns, MemoryProfile.DEFAULT,
                akyrion.thepredict.domain.distill.ProfileDecision.INHERIT, Optional.empty());
    }

    /**
     * A profile from before decision and distill overrides (rule W5). Both inherit.
     *
     * @param id profile name
     * @param mythicMobId exact MythicMobs mob ID
     * @param objective author objective
     * @param tactics tactics in declaration order
     * @param choices skill choices in declaration order
     * @param contexts context rules
     * @param movement movement settings
     * @param health health thresholds
     * @param threat threat weights
     * @param baseAi {@code AIGoalSelectors} lines
     * @param timing decision timing
     * @param participants participant rules
     * @param difficulty difficulty section
     * @param stances stance section
     * @param telegraph telegraph section
     * @param poise poise section
     * @param zone zone section
     * @param group group section
     * @param patterns pattern section
     * @param memory memory section
     */
    public TacticalProfile(String id, String mythicMobId, String objective, Map<TacticId, Tactic> tactics,
                           Map<ChoiceId, SkillChoice> choices, List<ContextRule> contexts, MovementSettings movement,
                           HealthThresholds health, ThreatWeights threat, List<String> baseAi, DecisionTiming timing,
                           ParticipantRules participants, DifficultySettings difficulty, StanceSettings stances,
                           TelegraphSettings telegraph, Optional<PoiseSettings> poise, ZoneSettings zone,
                           GroupSettings group, PatternProfile patterns, MemoryProfile memory) {
        this(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat, baseAi, timing,
                participants, difficulty, stances, telegraph, poise, zone, group, patterns, memory,
                akyrion.thepredict.domain.distill.ProfileDecision.INHERIT, Optional.empty());
    }

    /**
     * A profile from before the group section (rule W5). The group inherits.
     *
     * @param id profile name
     * @param mythicMobId exact MythicMobs mob ID
     * @param objective author objective
     * @param tactics tactics in declaration order
     * @param choices skill choices in declaration order
     * @param contexts context rules
     * @param movement movement settings
     * @param health health thresholds
     * @param threat threat weights
     * @param baseAi {@code AIGoalSelectors} lines
     * @param timing decision timing
     * @param participants participant rules
     * @param difficulty difficulty section
     * @param stances stance section
     * @param telegraph telegraph section
     * @param poise poise section
     * @param zone zone section
     */
    public TacticalProfile(String id, String mythicMobId, String objective, Map<TacticId, Tactic> tactics,
                           Map<ChoiceId, SkillChoice> choices, List<ContextRule> contexts, MovementSettings movement,
                           HealthThresholds health, ThreatWeights threat, List<String> baseAi, DecisionTiming timing,
                           ParticipantRules participants, DifficultySettings difficulty, StanceSettings stances,
                           TelegraphSettings telegraph, Optional<PoiseSettings> poise, ZoneSettings zone) {
        this(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat, baseAi, timing,
                participants, difficulty, stances, telegraph, poise, zone, GroupSettings.DEFAULT);
    }

    /**
     * Creates a profile whose difficulty is inherited (rule W5). The loader passes the resolved participant rules.
     *
     * @param id profile name
     * @param mythicMobId exact MythicMobs mob ID
     * @param objective author objective for the model
     * @param tactics tactics in declaration order
     * @param choices skill choices in declaration order
     * @param contexts context rules in declaration order
     * @param movement movement settings
     * @param health health thresholds
     * @param threat threat weights
     * @param baseAi {@code AIGoalSelectors} lines
     * @param timing decision timing
     * @param participants participant rules
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public TacticalProfile(String id, String mythicMobId, String objective, Map<TacticId, Tactic> tactics,
                           Map<ChoiceId, SkillChoice> choices, List<ContextRule> contexts, MovementSettings movement,
                           HealthThresholds health, ThreatWeights threat, List<String> baseAi, DecisionTiming timing,
                           ParticipantRules participants) {
        this(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat, baseAi, timing,
                participants, DifficultySettings.DEFAULT);
    }

    /**
     * Creates a profile with the participant rules of 0.0.1 and difficulty inherited (rule W5).
     *
     * @param id profile name
     * @param mythicMobId exact MythicMobs mob ID
     * @param objective author objective for the model
     * @param tactics tactics in declaration order
     * @param choices skill choices in declaration order
     * @param contexts context rules in declaration order
     * @param movement movement settings
     * @param health health thresholds
     * @param threat threat weights
     * @param baseAi {@code AIGoalSelectors} lines
     * @param timing decision timing
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public TacticalProfile(String id, String mythicMobId, String objective, Map<TacticId, Tactic> tactics,
                           Map<ChoiceId, SkillChoice> choices, List<ContextRule> contexts, MovementSettings movement,
                           HealthThresholds health, ThreatWeights threat, List<String> baseAi, DecisionTiming timing) {
        this(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat, baseAi, timing,
                ParticipantRules.DEFAULT);
    }

    /**
     * Returns a copy with other contexts, keeping everything else.
     *
     * @param replaced contexts of the copy
     * @return the copy
     */
    public TacticalProfile withContexts(List<ContextRule> replaced) {
        return new TacticalProfile(id, mythicMobId, objective, tactics, choices, replaced, movement, health, threat,
                baseAi, timing, participants, difficulty, stances, telegraph, poise, zone, group, patterns, memory,
                decision, distillMode);
    }

    /**
     * Returns a copy whose overlay sections replace the ones the loader bound. Tactics stay.
     *
     * @param replacedTiming timing after the unfixed keys
     * @param replacedZone zone after the unfixed keys
     * @param replacedGroup group after the unfixed keys
     * @param replacedPatterns patterns after the unfixed keys
     * @param replacedDecision decision after the unfixed keys
     * @param replacedDistill distill mode after the unfixed keys
     * @return the copy
     */
    public TacticalProfile withOverlay(DecisionTiming replacedTiming, ZoneSettings replacedZone,
                                       GroupSettings replacedGroup, PatternProfile replacedPatterns,
                                       akyrion.thepredict.domain.distill.ProfileDecision replacedDecision,
                                       java.util.Optional<akyrion.thepredict.domain.distill.DistillMode> replacedDistill) {
        return new TacticalProfile(id, mythicMobId, objective, tactics, choices, contexts, movement, health, threat,
                baseAi, replacedTiming, participants, difficulty, stances, telegraph, poise, replacedZone,
                replacedGroup, replacedPatterns, memory, replacedDecision, replacedDistill);
    }

    /**
     * Finds a declared tactic.
     *
     * @param tacticId tactic name
     * @return the tactic, or empty when the profile does not declare it
     */
    public Optional<Tactic> tactic(TacticId tacticId) {
        return Optional.ofNullable(tactics.get(Objects.requireNonNull(tacticId, "tacticId")));
    }

    private static <K, V> Map<K, V> orderedCopy(Map<K, V> source, String field) {
        Objects.requireNonNull(source, field);
        var copy = new LinkedHashMap<K, V>();
        source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, field),
                Objects.requireNonNull(value, field)));
        return Collections.unmodifiableMap(copy);
    }

    private static void validateTactics(Map<TacticId, Tactic> tactics, Map<ChoiceId, SkillChoice> choices) {
        if (tactics.isEmpty()) {
            throw new IllegalArgumentException("tactics must declare at least one tactic");
        }
        tactics.forEach((key, tactic) -> {
            if (!key.equals(tactic.id())) {
                throw new IllegalArgumentException("tactics key " + key + " does not match tactic " + tactic.id());
            }
            if (key.isReserved()) {
                throw new IllegalArgumentException("tactics must not declare the reserved tactic " + key);
            }
            tactic.choice().ifPresent(choice -> {
                if (!choices.containsKey(choice)) {
                    throw new IllegalArgumentException("choice " + choice + " of tactic " + key + " is not declared");
                }
            });
        });
    }

    private static void validateChoices(Map<ChoiceId, SkillChoice> choices) {
        choices.forEach((key, choice) -> {
            if (!key.equals(choice.id())) {
                throw new IllegalArgumentException("choices key " + key + " does not match choice " + choice.id());
            }
        });
    }

    private static void validateContexts(List<ContextRule> contexts, Map<TacticId, Tactic> tactics) {
        var seen = new HashSet<ContextId>();
        for (var context : contexts) {
            if (!seen.add(context.id())) {
                throw new IllegalArgumentException("contexts declares " + context.id() + " more than once");
            }
            for (var tactic : context.allowedTactics()) {
                if (!tactics.containsKey(tactic)) {
                    throw new IllegalArgumentException("allowedTactics of context " + context.id()
                            + " references undeclared tactic " + tactic);
                }
            }
        }
    }
}
