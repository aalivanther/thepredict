package akyrion.thepredict.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Fields of a schema 2 profile; rejects unknown and schema 1 fields before they can be ignored (M03.2). */
final class ProfileSchema {
    /** Schema version of profile files. */
    static final int SCHEMA_VERSION = 2;
    /** Largest number of contexts per profile (M07.7). */
    static final int MAX_CONTEXTS = 16;
    static final Set<String> PROFILE = Set.of("MythicID", "TypeOfMythicID", "Type", "Objective", "Choices", "Tactics",
            "Contexts", "Movement", "Health", "Threat", "BaseAI", "Timing", "Participants", "Reactions", "Difficulty",
            "Stances", "Telegraph", "Poise", "Zone", "Group", "Patterns", "Memory", "Decision", "Distill");
    static final Set<String> VERSION_ONE_PROFILE = Set.of("Behaviors", "Combinators", "Operations",
            "DecisionIntervalTicks", "LowHealthRatio", "AttackRangeBlocks");
    static final Set<String> CHOICE = Set.of("Kind", "MythicID", "TypeOfMythicID", "RequiresTarget", "CD", "Purpose",
            "Telegraph", "Displacement", "TargetsAllies");
    static final Set<String> PURPOSE = Set.of("Roles", "Delivery", "Reach", "Radius", "WindUp", "Damage", "BestWhen",
            "Risks", "IgnoresShield");
    static final Set<String> VERSION_ONE_CHOICE = Set.of("Priority");
    static final Set<String> TACTIC = Set.of("Movement", "Choice", "Description", "Priority", "Stance");
    static final Set<String> CONTEXT = Set.of("Conditions", "Priority", "Tactics", "DirectTactic");
    static final Set<String> MOVEMENT = Set.of("Band", "Retreat", "Approach", "Arena", "MeleeReach", "RangedReach");
    static final Set<String> BAND = Set.of("Inner", "Outer");
    static final Set<String> RETREAT = Set.of("Distance", "Speed");
    static final Set<String> APPROACH = Set.of("Speed");
    static final Set<String> ARENA = Set.of("Radius");
    static final Set<String> HEALTH = Set.of("Critical", "Wounded");
    static final Set<String> THREAT = Set.of("AssistsOthers", "RangedHarass", "BlockedHits",
            "UnreachableDecayPerSecond", "MaxPerWindow");
    static final Set<String> TIMING = Set.of("MinimumConfidence", "SwitchMargin", "QuestionDetail", "FitChecks",
            "CastMode", "ReadyHorizon", "TickLayer", "Reactions", "ReactionDelay", "Cadence", "ReactiveInterval",
            "IntentHorizon");
    /** Fields of the {@code Reactions} section of a profile (M20.4). */
    static final Set<String> REACTIONS = Set.of("Ahead");
    /** Fields of the {@code Participants} section of a profile (M17.2). */
    static final Set<String> PARTICIPANTS = Set.of("PlayerGameModes", "Entities");
    /** Fields of {@code Participants.Entities}. */
    static final Set<String> PARTICIPANT_ENTITIES = Set.of("Allow", "Deny", "PerceiveNonTargets");
    /** Fields of the {@code Difficulty} section of a profile (M22.11). */
    static final Set<String> DIFFICULTY = Set.of("Mode", "TargetSeconds", "Tolerance", "MinRung", "MaxRung",
            "StartRung", "InFight", "Learn", "NativeLevel");

    private static final Map<String, String> DOMAIN_NAMES = Map.ofEntries(
            Map.entry("meleeReachBlocks", "MeleeReach"), Map.entry("bandInnerBlocks", "Band.Inner"),
            Map.entry("bandOuterBlocks", "Band.Outer"), Map.entry("rangedReachBlocks", "RangedReach"),
            Map.entry("retreatDistanceBlocks", "Retreat.Distance"), Map.entry("retreatSpeed", "Retreat.Speed"),
            Map.entry("approachSpeed", "Approach.Speed"), Map.entry("arenaRadiusBlocks", "Arena.Radius"),
            Map.entry("criticalRatio", "Critical"), Map.entry("woundedRatio", "Wounded"),
            Map.entry("assistsOthers", "AssistsOthers"), Map.entry("rangedHarass", "RangedHarass"),
            Map.entry("blockedHits", "BlockedHits"), Map.entry("unreachableDecayPerSecond", "UnreachableDecayPerSecond"),
            Map.entry("maxPerWindow", "MaxPerWindow"), Map.entry("reachBlocks", "Reach"),
            Map.entry("radiusBlocks", "Radius"), Map.entry("windUpTicks", "WindUp"),
            Map.entry("commitmentPercent", "Commitment"), Map.entry("entropyCommitmentPercent", "EntropyCommitment"),
            Map.entry("defaultRecoveryTicks", "DefaultRecoveryTicks"), Map.entry("minDwellTicks", "MinDwellTicks"),
            Map.entry("hysteresisCycles", "HysteresisCycles"), Map.entry("targetSeconds", "TargetSeconds"),
            Map.entry("tolerance", "Tolerance"), Map.entry("rungs", "MinRung"), Map.entry("startRung", "StartRung"));

    private ProfileSchema() {
    }

    /** Rejects fields of a section that are not in {@code allowed}. */
    static void requireKnown(ConfigurationSection section, Set<String> allowed) {
        requireKnown(section, allowed, Set.of());
    }

    /** Rejects unknown fields, explaining the ones that belong to schema 1. */
    static void requireKnown(ConfigurationSection section, Set<String> allowed, Set<String> versionOne) {
        for (var field : new TreeSet<>(section.getKeys(false))) {
            if (versionOne.contains(field)) {
                throw new ConfigurationException(ConfigurationValues.path(section, field),
                        "is a schema 1 field; review the migration proposal under migration/");
            }
            if (!allowed.contains(field)) {
                throw new ConfigurationException(ConfigurationValues.path(section, field), "is not a known field");
            }
        }
    }

    /**
     * The YAML key of one domain record field: the mapped name when the key differs from the field, otherwise the
     * field with its first letter in upper case, which is how every other profile key is spelled.
     *
     * @param field record field named by a domain error
     * @return the profile key
     */
    static String yamlName(String field) {
        var mapped = DOMAIN_NAMES.get(field);
        if (mapped != null) {
            return mapped;
        }
        return field.isEmpty() ? field : Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    /** Replaces domain field names in a domain error with the YAML names of the profile. */
    static String yamlNames(String domainMessage) {
        var message = domainMessage;
        for (var entry : DOMAIN_NAMES.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return message;
    }
}
