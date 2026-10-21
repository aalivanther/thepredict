package akyrion.thepredict.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The participant rules of one profile (M17.2): which players may take part by game mode, which other living entities
 * the profile allows or denies by native selector, and whether its mobs perceive entities other than their targets.
 * The selectors are opaque text for the domain, like the native conditions (01 §1): {@code config} checks their syntax
 * and the adapter resolves them when it binds. Immutable.
 *
 * @param playerGameModes game modes whose players may be perceived, targeted and fought, in upper case
 * @param entityAllow selectors of non-player living entities this profile adds to the global ones, in order
 * @param entityDeny selectors that never take part, added to the global ones, in order
 * @param perceiveNonTargets whether the mobs of the profile perceive entities other than their targets
 */
public record ParticipantRules(Set<String> playerGameModes, List<String> entityAllow, List<String> entityDeny,
                               boolean perceiveNonTargets) {
    /** Game modes a player may have. */
    public static final Set<String> GAME_MODES = Set.of("SURVIVAL", "ADVENTURE", "CREATIVE", "SPECTATOR");
    /** Largest number of selectors in one list. */
    public static final int MAX_SELECTORS = 16;
    /** Default of whether profiles may add entity selectors of their own, the only place of it (rule C13). */
    public static final boolean DEFAULT_PROFILES_MAY_ALLOW = true;
    /** Default cap of non-target entities kept per member and capture, the only place of it (rule C13). */
    public static final int DEFAULT_MAX_CANDIDATES_PER_MEMBER = 8;
    /** Smallest cap of non-target entities per member and capture. */
    public static final int MIN_CANDIDATES_PER_MEMBER = 1;
    /** Largest cap of non-target entities per member and capture. */
    public static final int MAX_CANDIDATES_PER_MEMBER = 16;
    /**
     * The rules of 0.0.1 and the only place of their defaults (rule C13): players in {@code SURVIVAL} or
     * {@code ADVENTURE}, no other entity, targets only.
     */
    public static final ParticipantRules DEFAULT = new ParticipantRules(orderedModes("SURVIVAL", "ADVENTURE"),
            List.of(), List.of(), false);

    /**
     * Validates the rules and copies the collections, keeping their order.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if a game mode is unknown, a selector is blank or a list has more than
     *                                  {@link #MAX_SELECTORS} selectors
     */
    public ParticipantRules {
        var modes = new LinkedHashSet<String>();
        for (var mode : Objects.requireNonNull(playerGameModes, "playerGameModes")) {
            if (!GAME_MODES.contains(Objects.requireNonNull(mode, "playerGameModes"))) {
                throw new IllegalArgumentException("unknown game mode " + mode);
            }
            modes.add(mode);
        }
        playerGameModes = Collections.unmodifiableSet(modes);
        entityAllow = selectors(entityAllow, "entityAllow");
        entityDeny = selectors(entityDeny, "entityDeny");
    }

    /**
     * Tells whether the profile lets any non-player entity take part.
     *
     * @return {@code true} with at least one selector in {@link #entityAllow()}
     */
    public boolean admitsEntities() {
        return !entityAllow.isEmpty();
    }

    private static List<String> selectors(List<String> values, String field) {
        var copy = List.copyOf(Objects.requireNonNull(values, field));
        DomainChecks.requireMaximumSize(copy, MAX_SELECTORS, field);
        for (var selector : copy) {
            DomainChecks.requireText(selector, field);
        }
        return copy;
    }

    private static Set<String> orderedModes(String... modes) {
        return new LinkedHashSet<>(List.of(modes));
    }
}
