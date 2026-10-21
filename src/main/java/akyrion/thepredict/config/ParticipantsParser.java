package akyrion.thepredict.config;

import akyrion.thepredict.domain.ParticipantRules;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the participant rules of {@code config.yml} and of the {@code Participants} section of a profile (M17.2).
 * Only the syntax of each selector is checked here, because {@code config} cannot read the registries of Bukkit (rule
 * D3); the adapter resolves the values when it binds and warns about the unknown ones. Every error names the file
 * and the full path (rule C13).
 */
final class ParticipantsParser {
    /**
     * Names of {@code org.bukkit.entity.SpawnCategory}, copied as text because {@code config} does not read Bukkit; a
     * test of the adapter checks that they match {@code SpawnCategory.values()}.
     */
    static final List<String> SPAWN_CATEGORIES = List.of("MONSTER", "ANIMAL", "WATER_ANIMAL", "WATER_AMBIENT",
            "WATER_UNDERGROUND_CREATURE", "AMBIENT", "AXOLOTL", "MISC");
    /** Values of the {@code kind:} selector. */
    static final List<String> KINDS = List.of("LIVING", "MOB", "ENEMY", "MONSTER", "ANIMAL");
    /** Selector that matches the mobs bound to ThePredict. */
    static final String PREDICTOR = "predictor";

    private static final Pattern NAMESPACED_KEY = Pattern.compile("([a-z0-9._-]+:)?[a-z0-9/._-]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.+-]{1,64}");
    private static final Pattern MYTHIC_ID = Pattern.compile("\\*|\\S{1,64}");

    private ParticipantsParser() {
    }

    /**
     * Reads the {@code Participants} section of a profile against the global rules: the profile's game modes must be
     * among the global ones, {@code PerceiveNonTargets} can only be true while the global value is, and with
     * {@code profilesMayAllow: false} the profile's {@code Allow} is ignored with a warning.
     *
     * @param profile section of one profile
     * @param global the {@code participants} section of {@code config.yml}
     * @param file profile file, for warnings
     * @param warnings receives the warning of an ignored {@code Allow}
     * @return the resolved rules of the profile; the global game modes and {@code perceiveNonTargets} without a section
     * @throws ConfigurationException with the full path of the first invalid value
     */
    static ParticipantRules profile(ConfigurationSection profile, ParticipantSettings global, String file,
                                    List<ConfigProblem> warnings) {
        var rules = global.global();
        var section = ConfigurationValues.section(profile, "Participants");
        if (section.isEmpty()) {
            return new ParticipantRules(rules.playerGameModes(), List.of(), List.of(), rules.perceiveNonTargets());
        }
        var participants = section.get();
        ProfileSchema.requireKnown(participants, ProfileSchema.PARTICIPANTS);
        var modes = participants.contains("PlayerGameModes")
                ? gameModes(participants, "PlayerGameModes", rules.playerGameModes()) : rules.playerGameModes();
        var entities = ConfigurationValues.section(participants, "Entities");
        entities.ifPresent(value -> ProfileSchema.requireKnown(value, ProfileSchema.PARTICIPANT_ENTITIES));
        var allow = entities.map(value -> selectors(value, "Allow")).orElse(List.of());
        if (!allow.isEmpty() && !global.profilesMayAllow()) {
            warnings.add(ConfigProblem.warning(file, ConfigurationValues.path(entities.get(), "Allow"),
                    "is ignored: participants.entities.profilesMayAllow is false"));
            allow = List.of();
        }
        var deny = entities.map(value -> selectors(value, "Deny")).orElse(List.of());
        var perceive = entities.map(value -> ConfigurationValues.booleanValue(value, "PerceiveNonTargets",
                rules.perceiveNonTargets())).orElse(rules.perceiveNonTargets());
        if (perceive && !rules.perceiveNonTargets()) {
            throw new ConfigurationException(ConfigurationValues.path(entities.orElseThrow(), "PerceiveNonTargets"),
                    "can only be true while participants.entities.perceiveNonTargets is true");
        }
        return new ParticipantRules(modes, allow, deny, perceive);
    }

    /**
     * Reads a list of game modes, each one among {@code allowed}.
     *
     * @param section section of the list
     * @param key key of the list
     * @param allowed modes the list may use: every mode globally, the global list in a profile
     * @return the modes in order, in upper case
     */
    static Set<String> gameModes(ConfigurationSection section, String key, Set<String> allowed) {
        var values = ConfigurationValues.textList(section, key);
        var modes = new LinkedHashSet<String>();
        for (var index = 0; index < values.size(); index++) {
            var mode = values.get(index).trim().toUpperCase(Locale.ROOT);
            if (!ParticipantRules.GAME_MODES.contains(mode)) {
                throw new ConfigurationException(ConfigurationValues.path(section, key) + "[" + index + "]",
                        "must be SURVIVAL, ADVENTURE, CREATIVE or SPECTATOR, was '" + values.get(index) + "'");
            }
            if (!allowed.contains(mode)) {
                throw new ConfigurationException(ConfigurationValues.path(section, key) + "[" + index + "]",
                        "must be one of the global participants.playerGameModes " + allowed + ", was '" + mode + "'");
            }
            modes.add(mode);
        }
        return modes;
    }

    /**
     * Reads a list of selectors and checks the syntax of each one.
     *
     * @param section section of the list
     * @param key key of the list
     * @return the selectors in order, trimmed
     */
    static List<String> selectors(ConfigurationSection section, String key) {
        var values = ConfigurationValues.textList(section, key);
        var path = ConfigurationValues.path(section, key);
        if (values.size() > ParticipantRules.MAX_SELECTORS) {
            throw new ConfigurationException(path, "must have at most " + ParticipantRules.MAX_SELECTORS
                    + " selectors, had " + values.size());
        }
        var selectors = new ArrayList<String>(values.size());
        for (var index = 0; index < values.size(); index++) {
            var selector = values.get(index).trim();
            check(selector, path + "[" + index + "]");
            selectors.add(selector);
        }
        return List.copyOf(selectors);
    }

    /**
     * Checks the syntax of one selector: {@code type:}, {@code tag:}, {@code category:}, {@code kind:},
     * {@code mythic:}, {@code faction:}, {@code team:}, {@code tagged:} or {@code predictor}.
     *
     * @param selector text of the selector
     * @param path full path, for the error
     * @throws ConfigurationException if the prefix is unknown or the value has the wrong form
     */
    static void check(String selector, String path) {
        if (selector.equals(PREDICTOR)) {
            return;
        }
        var colon = selector.indexOf(':');
        if (colon <= 0) {
            throw new ConfigurationException(path, "must be predictor or prefix:value with the prefix type, tag, "
                    + "category, kind, mythic, faction, team or tagged, was '" + selector + "'");
        }
        var prefix = selector.substring(0, colon);
        var value = selector.substring(colon + 1);
        var valid = switch (prefix) {
            case "type", "tag" -> NAMESPACED_KEY.matcher(value).matches();
            case "category" -> SPAWN_CATEGORIES.contains(value.toUpperCase(Locale.ROOT));
            case "kind" -> KINDS.contains(value.toUpperCase(Locale.ROOT));
            case "mythic" -> MYTHIC_ID.matcher(value).matches();
            case "faction", "team", "tagged" -> NAME.matcher(value).matches();
            default -> throw new ConfigurationException(path, "has the unknown prefix '" + prefix + "'; use type, tag,"
                    + " category, kind, mythic, faction, team, tagged or predictor");
        };
        if (!valid) {
            throw new ConfigurationException(path, "has an invalid value for " + prefix + ": '" + value + "'"
                    + hint(prefix));
        }
    }

    private static String hint(String prefix) {
        return switch (prefix) {
            case "type", "tag" -> " (a namespaced key such as minecraft:zombie)";
            case "category" -> " (one of " + SPAWN_CATEGORIES + ")";
            case "kind" -> " (one of " + KINDS + ")";
            case "mythic" -> " (* or a MythicMobs mob ID)";
            default -> " (1 to 64 letters, digits or _ . + -)";
        };
    }
}
