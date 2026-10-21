package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Stable participant aliases of one encounter (rule I6, M06.3, M17.1): the first player seen is {@code player_1},
 * the next {@code player_2}, the first other living entity {@code entity_1}, with one counter per kind, and an index
 * is never given to another participant of the same kind. Server thread only; the UUIDs never leave {@code engine}.
 * {@link #aliasFor} is the {@code CaptureContext.aliasFor} function of M07.
 *
 * <p>At most {@link #CAPACITY} participants of both kinds together keep an alias (rule A5). A new one beyond that
 * drops the oldest alias of a participant who is no longer one; indices keep growing, so no alias is ever reassigned.
 * Because an encounter has at most 16 participants, a current participant never loses its alias.
 */
public final class ParticipantAliases {
    /** Maximum number of participants with an alias, both kinds together. */
    public static final int CAPACITY = 32;

    private final Map<UUID, ParticipantAlias> aliases = new LinkedHashMap<>();
    private final Predicate<UUID> participant;
    private final Map<ParticipantKind, Integer> nextIndex = new EnumMap<>(ParticipantKind.class);

    /**
     * Creates an empty alias table.
     *
     * @param participant tells whether a UUID is a current participant, whose alias must be kept
     */
    public ParticipantAliases(Predicate<UUID> participant) {
        this.participant = Objects.requireNonNull(participant, "participant");
    }

    /**
     * Returns the alias of a participant, assigning the next index of its kind the first time. A UUID that already has
     * an alias keeps it, whatever kind is asked: the entity behind a UUID never changes kind.
     *
     * @param participantId UUID of the participant
     * @param kind kind of the participant
     * @return the stable alias of the participant in this encounter
     */
    public ParticipantAlias aliasFor(UUID participantId, ParticipantKind kind) {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(kind, "kind");
        var existing = aliases.get(participantId);
        if (existing != null) {
            return existing;
        }
        if (aliases.size() >= CAPACITY) {
            dropOldestFormerParticipant();
        }
        var index = nextIndex.getOrDefault(kind, 1);
        nextIndex.put(kind, index + 1);
        var alias = new ParticipantAlias(kind, index);
        aliases.put(participantId, alias);
        return alias;
    }

    /**
     * Returns the alias of a participant without assigning one.
     *
     * @param participantId UUID of the participant
     * @return the alias, if the participant already has one
     */
    public Optional<ParticipantAlias> find(UUID participantId) {
        return Optional.ofNullable(aliases.get(participantId));
    }

    /**
     * Counts the participants with an alias.
     *
     * @return at most {@link #CAPACITY}
     */
    public int size() {
        return aliases.size();
    }

    /** Forgets every alias when the encounter closes. */
    void clear() {
        aliases.clear();
    }

    /** Removes the oldest alias whose owner is not a participant; the oldest of all if every one is. */
    private void dropOldestFormerParticipant() {
        var iterator = aliases.keySet().iterator();
        while (iterator.hasNext()) {
            if (!participant.test(iterator.next())) {
                iterator.remove();
                return;
            }
        }
        aliases.remove(aliases.keySet().iterator().next());
    }
}
