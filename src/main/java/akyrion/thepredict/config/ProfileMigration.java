package akyrion.thepredict.config;

import akyrion.thepredict.domain.TacticId;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts a schema 1 profile file into schema 2 (M03.6): each choice becomes a tactic without movement
 * that keeps its restrictions; {@link OperationMigration} converts the operations. Ambiguous combinations
 * are left for manual correction.
 */
final class ProfileMigration {
    private static final Set<String> KNOWN = Set.of("MythicID", "TypeOfMythicID", "Type", "Objective", "Choices",
            "Behaviors", "Combinators", "Operations", "DecisionIntervalTicks", "LowHealthRatio", "AttackRangeBlocks");

    private ProfileMigration() {
    }

    /** Writes the schema 2 text of a schema 1 file and records every decision. */
    static String convert(String file, ConfigurationSection v1, MigrationNotes notes) {
        var text = new StringBuilder("schemaVersion: ").append(ProfileSchema.SCHEMA_VERSION).append('\n');
        for (var id : v1.getKeys(false)) {
            var profile = v1.getConfigurationSection(id);
            if (profile == null) {
                notes.manual(file + ": " + id + " is not a profile section and was skipped");
            } else {
                convertProfile(file + ": " + id, id, profile, text, notes);
            }
        }
        return text.toString();
    }

    private static void convertProfile(String where, String id, ConfigurationSection profile, StringBuilder text,
                                       MigrationNotes notes) {
        text.append(id).append(":\n");
        text.append("  MythicID: ").append(YamlText.quote(profile.getString("MythicID", ""))).append('\n');
        text.append("  TypeOfMythicID: MOB\n  Type: NORMAL\n");
        text.append("  Objective: ").append(YamlText.quote(profile.getString("Objective", ""))).append('\n');
        profile.getKeys(false).stream().filter(key -> !KNOWN.contains(key)).sorted()
                .forEach(key -> notes.manual(where + "." + key + " is not a schema 1 field and was ignored"));
        var tactics = convertChoices(where, profile, text, notes);
        OperationMigration.convert(where, profile, tactics, text, notes);
        convertThresholds(where, profile, text, notes);
    }

    private static Map<String, String> convertChoices(String where, ConfigurationSection profile, StringBuilder text,
                                                      MigrationNotes notes) {
        var choices = profile.getConfigurationSection("Choices");
        var tacticsByChoice = new LinkedHashMap<String, String>();
        if (choices == null) {
            notes.manual(where + " has no Choices; add Tactics by hand");
            return tacticsByChoice;
        }
        var choiceText = new StringBuilder("  Choices:\n");
        var tacticText = new StringBuilder("  Tactics:\n");
        var used = new HashSet<String>();
        for (var original : choices.getKeys(false)) {
            var choice = choices.getConfigurationSection(original);
            var tacticId = tacticName(identifier(original));
            if (choice == null || !used.add(tacticId)) {
                notes.manual(where + ".Choices." + original + " could not be converted (not a section or a name clash)");
            } else {
                convertChoice(where + ".Choices." + original, original, choice, choiceText, tacticText, notes);
                tacticsByChoice.put(original, tacticId);
            }
        }
        text.append(choiceText).append(tacticText);
        return tacticsByChoice;
    }

    private static void convertChoice(String where, String original, ConfigurationSection choice, StringBuilder choiceText,
                                      StringBuilder tacticText, MigrationNotes notes) {
        var choiceId = identifier(original);
        var tacticId = tacticName(choiceId);
        if (!choiceId.equals(original)) {
            notes.decision(where + " was renamed to " + choiceId + " (schema 2 names)");
        }
        writeChoice(choiceText, choiceId, choice);
        tacticText.append("    ").append(tacticId).append(":\n      Movement: NONE\n      Choice: ").append(choiceId)
                .append('\n');
        if (choice.get("Priority") instanceof Integer priority) {
            tacticText.append("      Priority: ").append(priority).append('\n');
        }
        notes.decision(where + " became tactic " + tacticId
                + " with Movement NONE; skill, CD, RequiresTarget and Priority are kept");
    }

    private static void writeChoice(StringBuilder text, String choiceId, ConfigurationSection choice) {
        text.append("    ").append(choiceId).append(":\n");
        text.append("      MythicID: ").append(YamlText.quote(choice.getString("MythicID", ""))).append('\n');
        text.append("      TypeOfMythicID: SKILL\n");
        if (choice.get("RequiresTarget") instanceof Boolean requiresTarget) {
            text.append("      RequiresTarget: ").append(requiresTarget).append('\n');
        }
        if (choice.get("CD") instanceof Integer cooldown) {
            text.append("      CD: ").append(cooldown).append('\n');
        }
    }

    private static void convertThresholds(String where, ConfigurationSection profile, StringBuilder text,
                                          MigrationNotes notes) {
        if (profile.contains("DecisionIntervalTicks")) {
            notes.decision(where + ".DecisionIntervalTicks was dropped: profileDefaults.minimumDecisionIntervalTicks applies");
        }
        if (profile.contains("AttackRangeBlocks")) {
            notes.decision(where + ".AttackRangeBlocks was dropped: attacking_phase uses the melee band (Movement.MeleeReach)");
        }
        if (!profile.contains("LowHealthRatio")) {
            return;
        }
        var wounded = ProfileDefaults.standard().woundedHealthRatio();
        if (profile.get("LowHealthRatio") instanceof Number ratio && ratio.doubleValue() > 0 && ratio.doubleValue() < wounded) {
            text.append("  Health:\n    Critical: ").append(YamlText.number(ratio.doubleValue())).append('\n');
            notes.decision(where + ".LowHealthRatio " + ratio + " became Health.Critical");
        } else {
            notes.manual(where + ".LowHealthRatio (" + ConfigurationValues.safeValue(profile.get("LowHealthRatio"))
                    + ") does not fit Health.Critical"
                    + " (above 0 and below Health.Wounded " + wounded + "); set it by hand");
        }
    }

    private static String identifier(String name) {
        var normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (normalized.isEmpty() || !Character.isLetter(normalized.charAt(0))) {
            normalized = "c_" + normalized;
        }
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private static String tacticName(String choiceId) {
        var name = choiceId.length() < 3 || choiceId.equals(TacticId.NONE.value())
                || choiceId.startsWith(TacticId.DEBUG_PREFIX) ? "use_" + choiceId : choiceId;
        return name.length() > 40 ? name.substring(0, 40) : name;
    }
}
