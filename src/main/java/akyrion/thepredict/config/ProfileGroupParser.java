package akyrion.thepredict.config;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.RoleCapabilities;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads the {@code Group} section of one profile (M25). An absent section inherits. A mode above the global mode
 * is lowered by the caller, which warns with the file and the path.
 */
final class ProfileGroupParser {
    static final Set<String> GROUP = Set.of("Mode", "Squad", "LeaderPriority", "Weight", "Roles", "OnLeaderDeath",
            "AgainstEntities", "Questions", "PlanQuestion");

    private ProfileGroupParser() {
    }

    /**
     * Reads the section. Capabilities come from the choices already parsed.
     *
     * @param profile the profile
     * @param capabilities aptitudes of this profile
     * @return the settings, still carrying a mode above the global one when the author asked for it
     */
    static GroupSettings parse(ConfigurationSection profile, RoleCapabilities capabilities) {
        var defaults = GroupSettings.DEFAULT;
        var found = ConfigurationValues.section(profile, "Group");
        if (found.isEmpty()) {
            return new GroupSettings(defaults.mode(), defaults.squad(), defaults.leaderPriority(), defaults.weight(),
                    defaults.roles(), defaults.onLeaderDeath(), defaults.againstEntities(), defaults.questions(),
                    defaults.planQuestion(), capabilities);
        }
        var section = found.get();
        ProfileSchema.requireKnown(section, GROUP, Set.of());
        var mode = optional(section, "Mode", GroupMode.class, GroupMode::parse);
        var squad = ConfigurationValues.text(section, "Squad", defaults.squad());
        var priority = ConfigurationValues.intValue(section, "LeaderPriority", defaults.leaderPriority());
        var weight = ConfigurationValues.intValue(section, "Weight", defaults.weight());
        var roles = roles(section);
        var death = ModeKeys.parse(section, "OnLeaderDeath", defaults.onLeaderDeath(), LeaderDeath::parse);
        var bench = ModeKeys.parse(section, "AgainstEntities", defaults.againstEntities(),
                token -> BenchPolicy.parse(token, "Group.AgainstEntities"));
        var questions = optional(section, "Questions", GroupQuestions.class, GroupQuestions::parse);
        var plan = ModeKeys.parse(section, "PlanQuestion", defaults.planQuestion(), PlanQuestion::parse);
        try {
            return new GroupSettings(mode, squad, priority, weight, roles, death, bench, questions, plan, capabilities);
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf(profile.getCurrentPath(), exception, ProfileSchema::yamlName);
        }
    }

    /** A mode key that inherits when absent: empty without the key, the parsed mode with it. */
    private static <E extends Enum<E>> Optional<E> optional(ConfigurationSection section, String key, Class<E> type,
                                                           Function<String, E> parse) {
        return section.contains(key) ? Optional.of(ModeKeys.required(section, key, type, parse)) : Optional.empty();
    }

    private static Set<GroupRole> roles(ConfigurationSection section) {
        var names = ConfigurationValues.textList(section, "Roles");
        var roles = new LinkedHashSet<GroupRole>();
        for (var name : names) {
            GroupRole found = null;
            var token = name.toLowerCase(Locale.ROOT);
            for (var role : GroupRole.values()) {
                if (role.name().toLowerCase(Locale.ROOT).equals(token)) {
                    found = role;
                }
            }
            if (found == null) {
                throw new ConfigurationException(ConfigurationValues.path(section, "Roles"),
                        "names an unknown role '" + name + "'");
            }
            roles.add(found);
        }
        return roles;
    }
}
