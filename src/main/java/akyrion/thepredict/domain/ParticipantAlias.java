package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * Stable alias of a participant inside one encounter (rule I6, M17.1): {@code player_1} for a player,
 * {@code entity_1} for any other living entity, with one counter per kind. The state sent to the model never
 * carries names, MythicMobs IDs, factions or UUIDs, because they do not improve the decision and cost tokens and
 * precision. Immutable.
 *
 * @param kind kind of the participant
 * @param index one-based index within its kind, {@code >= 1}
 */
public record ParticipantAlias(ParticipantKind kind, int index) {
    /** Text prefix of the alias of a player. */
    public static final String PLAYER_PREFIX = "player_";
    /** Text prefix of the alias of any other living entity. */
    public static final String ENTITY_PREFIX = "entity_";

    /**
     * Validates the alias.
     *
     * @throws NullPointerException if {@code kind} is {@code null}
     * @throws IllegalArgumentException if {@code index < 1}
     */
    public ParticipantAlias {
        Objects.requireNonNull(kind, "kind");
        if (index < 1) {
            throw new IllegalArgumentException("participant index must be >= 1, was " + index);
        }
    }

    /**
     * Creates the alias of a player, the only kind before M17. Kept for the tests and doubles that build player
     * aliases (rule W5); production code passes the kind.
     *
     * @param index one-based index, {@code >= 1}
     * @throws IllegalArgumentException if {@code index < 1}
     */
    public ParticipantAlias(int index) {
        this(ParticipantKind.PLAYER, index);
    }

    /**
     * Returns the alias as it appears in the encounter state.
     *
     * @return {@code "player_" + index} or {@code "entity_" + index}
     */
    public String text() {
        return (kind == ParticipantKind.PLAYER ? PLAYER_PREFIX : ENTITY_PREFIX) + index;
    }

    /**
     * Returns the alias text so error messages stay readable.
     *
     * @return the same value as {@link #text()}
     */
    @Override
    public String toString() {
        return text();
    }
}
