package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One event of the encounter history. Immutable.
 *
 * @param tick server tick of the event, {@code >= 0}
 * @param type kind of event
 * @param participant participant involved, a player or any other living entity, if any
 * @param mob mob involved, if any
 */
public record EncounterEvent(long tick, EventType type, Optional<ParticipantAlias> participant,
                             Optional<MobKey> mob) {
    /**
     * Validates the event.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if {@code tick < 0}
     */
    public EncounterEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(mob, "mob");
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0, was " + tick);
        }
    }
}
