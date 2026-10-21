package akyrion.thepredict.adapter.server.binding;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Builds the binder doubles of {@link MythicFixtures} from MythicMobs YAML files, so that documents and test packs can
 * be bound as MythicMobs would load them. A top-level entry with {@code Type}, itself or through its {@code Template}, is
 * a mob; any other entry is a skill. A {@code Template} is resolved as MythicMobs 5.13.1 does (javap of
 * {@code TemplateUtil.processTemplate} and {@code MythicConfigImpl.merge}, M18.1 and DT-40): the variant merges its
 * parent, a list the variant also has becomes the parent's lines followed by its own, and a value the variant sets wins.
 *
 * <p>A skill that declares {@code Intention} also gets a prepared reading (M19.3). The MythicMobs classes cannot be
 * doubled, so the lines are turned into a {@link NativeSkillView} and read once. A skill without that line stays unread,
 * which is what a double that is not a metaskill is.
 */
public final class MythicYaml {
    private MythicYaml() {
    }

    /**
     * Reads every top-level entry of the given files as a mob or a skill.
     *
     * @param files parsed MythicMobs mob and skill files
     * @return a lookup with one double per entry
     */
    public static MythicFixtures.Definitions definitions(Collection<? extends ConfigurationSection> files) {
        var entries = new LinkedHashMap<String, ConfigurationSection>();
        for (var file : files) {
            for (var id : file.getKeys(false)) {
                var entry = file.getConfigurationSection(id);
                if (entry != null) {
                    entries.put(id, entry);
                }
            }
        }
        var definitions = MythicFixtures.definitions();
        entries.forEach((id, entry) -> {
            var lineage = lineage(id, entries);
            var type = lineage.stream().map(section -> section.getString("Type")).filter(Objects::nonNull)
                    .reduce((parent, child) -> child);
            if (type.isPresent()) {
                definitions.add(MythicFixtures.mob(id, type.get(), mob(lineage)));
            } else {
                attach(definitions, id, entry);
            }
        });
        return definitions;
    }

    /**
     * Adds a skill double and, when the entry declares {@code Intention}, the reading of its lines.
     *
     * @param definitions lookup to fill
     * @param id skill ID
     * @param entry skill section
     */
    public static void attach(MythicFixtures.Definitions definitions, String id, ConfigurationSection entry) {
        definitions.add(MythicFixtures.skill(id, skill(entry)));
        if (entry.isSet("Intention")) {
            definitions.prepare(id, view(entry));
        }
    }

    /**
     * The entry and its templates, the farthest ancestor first; an unknown or repeated template ends the chain.
     *
     * @param id the entry
     * @param entries every top-level entry by id
     * @return the chain of sections
     */
    public static List<ConfigurationSection> lineage(String id, Map<String, ConfigurationSection> entries) {
        var chain = new ArrayList<ConfigurationSection>();
        var seen = new HashSet<String>();
        var current = id;
        while (current != null && seen.add(current) && entries.containsKey(current)) {
            var section = entries.get(current);
            chain.addFirst(section);
            current = section.getString("Template");
        }
        return chain;
    }

    private static Map<String, Object> mob(List<ConfigurationSection> lineage) {
        var config = new HashMap<String, Object>();
        var skills = new ArrayList<String>();
        for (var entry : lineage) {
            if (entry.isSet("Predictor")) {
                config.put("Predictor", entry.get("Predictor"));
            }
            skills.addAll(entry.getStringList("Skills"));
            if (entry.isSet("ThreatTable.DecayUnreachable")) {
                config.put("ThreatTable.DecayUnreachable", entry.getBoolean("ThreatTable.DecayUnreachable"));
            }
        }
        config.put("Skills", skills);
        return config;
    }

    private static Map<String, Object> skill(ConfigurationSection entry) {
        var config = new HashMap<String, Object>();
        copy(entry, config, "PredictValues");
        copy(entry, config, "Intention");
        if (entry.isSet("Cooldown")) {
            config.put("Cooldown", entry.getString("Cooldown"));
        }
        return config;
    }

    private static void copy(ConfigurationSection entry, Map<String, Object> config, String key) {
        if (entry.isList(key)) {
            config.put(key, entry.getStringList(key));
        } else if (entry.isSet(key)) {
            config.put(key, entry.getString(key));
        }
    }

    private static NativeSkillView view(ConfigurationSection entry) {
        return new View(entry.isSet("Cooldown"), seconds(entry.getString("Cooldown")),
                entry.isSet("Charges") ? Math.max(1, entry.getInt("Charges")) : 1, entry.getString("CooldownMode"),
                conditions(entry.getStringList("Conditions"), true),
                conditions(entry.getStringList("TargetConditions"), false),
                mechanics(entry.getStringList("Skills")));
    }

    private static OptionalDouble seconds(String text) {
        if (text == null || text.isBlank()) {
            return OptionalDouble.empty();
        }
        try {
            var value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    private static List<NativeSkillView.Line> conditions(List<String> lines, boolean required) {
        var parsed = new ArrayList<NativeSkillView.Line>(lines.size());
        for (var line : lines) {
            var body = action(line);
            parsed.add(new NativeSkillView.Line(typeName(body), body, false, NativeSkillView.Targeter.OTHER, required));
        }
        return List.copyOf(parsed);
    }

    private static List<NativeSkillView.Line> mechanics(List<String> lines) {
        var parsed = new ArrayList<NativeSkillView.Line>(lines.size());
        for (var line : lines) {
            var type = typeName(NativeSkillReader.withoutTargeter(line).trim());
            parsed.add(new NativeSkillView.Line(type, line, "damage".equalsIgnoreCase(type), targeter(line), false,
                    NESTED_TYPES.contains(type.toLowerCase(Locale.ROOT))));
        }
        return List.copyOf(parsed);
    }

    /** Type names MythicMobs maps to {@code MetaSkillMechanic}, {@code RandomSkillMechanic}, a projectile or an aura. */
    private static final java.util.Set<String> NESTED_TYPES = java.util.Set.of("skill", "metaskill", "randomskill",
            "randommeta", "projectile", "missile", "orbital", "aura", "onattack", "ondamaged", "onshoot");

    /** Drops a trailing {@code true} or {@code false} action so the option parser sees only the condition. */
    private static String action(String line) {
        var trimmed = line.trim();
        if (trimmed.endsWith(" true")) {
            return trimmed.substring(0, trimmed.length() - 5).trim();
        }
        if (trimmed.endsWith(" false")) {
            return trimmed.substring(0, trimmed.length() - 6).trim();
        }
        return trimmed;
    }

    private static String typeName(String body) {
        var brace = body.indexOf('{');
        var space = body.indexOf(' ');
        var end = brace < 0 ? space : space < 0 ? brace : Math.min(brace, space);
        return end < 0 ? body : body.substring(0, end);
    }

    private static NativeSkillView.Targeter targeter(String line) {
        var text = NativeSkillReader.targeterText(line);
        var brace = text.indexOf('{');
        var name = (brace < 0 ? text : text.substring(0, brace)).toLowerCase(Locale.ROOT);
        if (name.equals("playersinradius") || name.equals("mobsinradius") || name.equals("entitiesinradius")
                || name.equals("entitiesnearby") || name.equals("pir")) {
            return NativeSkillView.Targeter.RADIUS;
        }
        return name.equals("entitiesincone") || name.equals("eic") ? NativeSkillView.Targeter.CONE
                : NativeSkillView.Targeter.OTHER;
    }

    private record View(boolean declared, OptionalDouble seconds, int charges, String mode,
                        List<NativeSkillView.Line> conditions, List<NativeSkillView.Line> targetConditions,
                        List<NativeSkillView.Line> mechanics) implements NativeSkillView {
        @Override
        public boolean cooldownDeclared() {
            return declared;
        }

        @Override
        public OptionalDouble staticCooldownSeconds() {
            return seconds;
        }

        @Override
        public String cooldownMode() {
            return mode;
        }

        @Override
        public OptionalInt ticks(String text) {
            return LiveNativeSkill.staticTicks(text);
        }
    }
}
