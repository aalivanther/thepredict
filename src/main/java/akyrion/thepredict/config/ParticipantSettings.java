package akyrion.thepredict.config;

import akyrion.thepredict.domain.ParticipantRules;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Objects;

/**
 * The optional {@code participants} section of {@code config.yml} (M17.2): which players and which other living
 * entities may be perceived, targeted and fought, by native selector. An absent section, or one with the values of the
 * bundled file, is the behavior of 0.0.1: players in {@code SURVIVAL} or {@code ADVENTURE} and no other entity. The
 * root translates it into the settings of the adapter (rule D10).
 *
 * @param global game modes, allowed and denied selectors and {@code perceiveNonTargets} of every profile
 * @param profilesMayAllow whether profiles may add entity selectors of their own
 * @param maxCandidatesPerMember most non-target entities kept per member and capture with {@code perceiveNonTargets}
 */
public record ParticipantSettings(ParticipantRules global, boolean profilesMayAllow, int maxCandidatesPerMember) {
    /** Default of {@code participants.entities.profilesMayAllow}, from {@link ParticipantRules} (rule C13). */
    public static final boolean DEFAULT_PROFILES_MAY_ALLOW = ParticipantRules.DEFAULT_PROFILES_MAY_ALLOW;
    /** Default of {@code participants.entities.maxCandidatesPerMember}, from {@link ParticipantRules} (rule C13). */
    public static final int DEFAULT_MAX_CANDIDATES_PER_MEMBER = ParticipantRules.DEFAULT_MAX_CANDIDATES_PER_MEMBER;
    /** Largest {@code participants.entities.maxCandidatesPerMember}, from {@link ParticipantRules}. */
    public static final int MAX_CANDIDATES_PER_MEMBER = ParticipantRules.MAX_CANDIDATES_PER_MEMBER;

    /**
     * Validates the settings.
     *
     * @throws NullPointerException if {@code global} is {@code null}
     * @throws ConfigurationException if {@code maxCandidatesPerMember} is outside 1 to 16
     */
    public ParticipantSettings {
        Objects.requireNonNull(global, "global");
        SettingsChecks.requireRange(maxCandidatesPerMember, ParticipantRules.MIN_CANDIDATES_PER_MEMBER,
                MAX_CANDIDATES_PER_MEMBER,
                "participants.entities.maxCandidatesPerMember");
    }

    /**
     * Returns the settings of a fresh installation, the behavior of 0.0.1.
     *
     * @return the defaults
     */
    public static ParticipantSettings standard() {
        return new ParticipantSettings(ParticipantRules.DEFAULT, DEFAULT_PROFILES_MAY_ALLOW,
                DEFAULT_MAX_CANDIDATES_PER_MEMBER);
    }

    /** Reads the {@code participants} section; absent fields take their defaults. */
    static ParticipantSettings from(ConfigurationSection configuration) {
        var section = ConfigurationValues.section(configuration, "participants");
        if (section.isEmpty()) {
            return standard();
        }
        var participants = section.get();
        var modes = participants.contains("playerGameModes")
                ? ParticipantsParser.gameModes(participants, "playerGameModes", ParticipantRules.GAME_MODES)
                : ParticipantRules.DEFAULT.playerGameModes();
        var entities = ConfigurationValues.section(participants, "entities");
        var allow = entities.map(value -> ParticipantsParser.selectors(value, "allow")).orElse(List.of());
        var deny = entities.map(value -> ParticipantsParser.selectors(value, "deny")).orElse(List.of());
        var profilesMayAllow = entities.map(value -> ConfigurationValues.booleanValue(value, "profilesMayAllow",
                DEFAULT_PROFILES_MAY_ALLOW)).orElse(DEFAULT_PROFILES_MAY_ALLOW);
        var perceive = entities.map(value -> ConfigurationValues.booleanValue(value, "perceiveNonTargets",
                ParticipantRules.DEFAULT.perceiveNonTargets())).orElse(ParticipantRules.DEFAULT.perceiveNonTargets());
        var candidates = entities.map(value -> ConfigurationValues.intValue(value, "maxCandidatesPerMember",
                DEFAULT_MAX_CANDIDATES_PER_MEMBER)).orElse(DEFAULT_MAX_CANDIDATES_PER_MEMBER);
        return new ParticipantSettings(new ParticipantRules(modes, allow, deny, perceive), profilesMayAllow,
                candidates);
    }
}
