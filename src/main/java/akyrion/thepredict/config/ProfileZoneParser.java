package akyrion.thepredict.config;

import akyrion.thepredict.domain.HazardPush;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.ZoneSettings;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Reads the {@code Zone} section of one profile (M24.11). An absent section inherits. A mode above
 * {@code Zone.Mode} is lowered by the caller, which warns with the file and the path.
 */
final class ProfileZoneParser {
    static final Set<String> ZONE = Set.of("Mode", "Radius", "Facts", "HazardPush");

    private ProfileZoneParser() {
    }

    /**
     * Reads the section. Empty components inherit.
     *
     * @param profile the profile
     * @return the settings, still carrying a mode above the global one when the author asked for it
     */
    static ZoneSettings parse(ConfigurationSection profile) {
        var found = ConfigurationValues.section(profile, "Zone");
        if (found.isEmpty()) {
            return ZoneSettings.DEFAULT;
        }
        var section = found.get();
        ProfileSchema.requireKnown(section, ZONE, Set.of());
        var defaults = ZoneSettings.DEFAULT;
        Optional<ZoneMode> mode = Optional.empty();
        if (section.contains("Mode")) {
            mode = Optional.of(ModeKeys.required(section, "Mode", ZoneMode.class, ZoneMode::parse));
        }
        OptionalInt radius = OptionalInt.empty();
        if (section.contains("Radius")) {
            radius = OptionalInt.of(ConfigurationValues.intValue(section, "Radius", 0));
        }
        var facts = ConfigurationValues.booleanValue(section, "Facts", defaults.facts());
        var push = ModeKeys.parse(section, "HazardPush", defaults.hazardPush(), HazardPush::parse);
        try {
            return new ZoneSettings(mode, radius, facts, push);
        } catch (IllegalArgumentException exception) {
            throw SettingsChecks.leaf(profile.getCurrentPath(), exception, ProfileSchema::yamlName);
        }
    }
}
