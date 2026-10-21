package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.SessionState;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Where the context listeners of M07.5 hand each native event: it finds the encounters of a participant (a player or
 * any other living entity, M17) or a mob in O(1), records the event in their history and marks their context pending.
 * It never sends a request; the agenda condenses many marks into one earlier evaluation (M06.5). Server thread only
 * (rule T1). An event that records an alias receives the kind of its participant, which the listener knows, so a
 * participant not yet aliased by a capture gets the alias of its kind (M17.1).
 *
 * <p>An event of an entity or mob outside every encounter does no work beyond the index lookup. The same event
 * type from the same participant and mob within {@link #REPEAT_WINDOW_TICKS} ticks is not recorded twice, so two
 * mobs hitting one participant are two events. Raw input is not a context mark (decisión 7, errata E26-1 aplicada).
 */
public final class ContextEvents {
    /** Ticks within which a repeated event of the same type, participant and mob is dropped. */
    public static final int REPEAT_WINDOW_TICKS = 10;

    private final EncounterRegistry encounters;
    private final SessionRegistry sessions;
    private final Agenda agenda;
    private final ServerClock clock;
    private final ContextEventMetrics metrics = new ContextEventMetrics();

    /**
     * Creates the entry point.
     *
     * @param encounters encounters and their participant index
     * @param sessions sessions, to find the encounter of a member mob
     * @param agenda agenda whose pending marks move evaluations earlier
     * @param clock server clock, for event ticks
     */
    public ContextEvents(EncounterRegistry encounters, SessionRegistry sessions, Agenda agenda, ServerClock clock) {
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Tells in O(1) whether an entity takes part in any open encounter of a world, so listeners skip the rest.
     *
     * @param worldId world of the event
     * @param participantId UUID of the entity
     * @return {@code true} if the entity is a participant somewhere in that world
     */
    public boolean isParticipant(UUID worldId, UUID participantId) {
        return !encounters.ofParticipant(worldId, participantId).isEmpty();
    }

    /**
     * Something the mobs could react to changed on a participant: marks context pending in each of its encounters.
     *
     * @param worldId world of the event
     * @param participantId UUID of the participant
     * @return number of encounters marked
     */
    public int participantChanged(UUID worldId, UUID participantId) {
        var found = encounters.ofParticipant(worldId, participantId);
        var tick = clock.currentTick();
        found.forEach(encounter -> mark(encounter, tick));
        return found.size();
    }

    /**
     * A participant caused a recorded event: it enters the history of each of its encounters under its alias,
     * unless repeated, and optionally marks context pending.
     *
     * @param worldId world of the event
     * @param participantId UUID of the participant
     * @param kind kind of the participant
     * @param type kind of event
     * @param markPending whether the event also marks context pending
     * @return number of encounters of the participant
     */
    public int participantEvent(UUID worldId, UUID participantId, ParticipantKind kind, EventType type,
                                boolean markPending) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        var found = encounters.ofParticipant(worldId, participantId);
        var tick = clock.currentTick();
        for (var encounter : found) {
            record(encounter, new EncounterEvent(tick, type, Optional.of(encounter.aliasFor(participantId, kind)),
                    Optional.empty()));
            if (markPending) {
                mark(encounter, tick);
            }
        }
        return found.size();
    }

    /**
     * A participant helped other participants with beneficial effects: records {@code ALLY_ASSISTED} and marks
     * context pending in each encounter that lists one of the affected entities besides the source.
     *
     * @param worldId world of the event
     * @param sourceId UUID of the participant who threw or placed the effect
     * @param sourceKind kind of that participant
     * @param affected UUIDs of the affected entities
     * @return number of encounters where an ally was helped
     */
    public int allyAssisted(UUID worldId, UUID sourceId, ParticipantKind sourceKind, Collection<UUID> affected) {
        Objects.requireNonNull(affected, "affected");
        Objects.requireNonNull(sourceKind, "sourceKind");
        var tick = clock.currentTick();
        var helped = 0;
        for (var encounter : encounters.ofParticipant(worldId, sourceId)) {
            if (affected.stream().anyMatch(ally -> !ally.equals(sourceId) && encounter.isParticipant(ally))) {
                record(encounter, new EncounterEvent(tick, EventType.ALLY_ASSISTED,
                        Optional.of(encounter.aliasFor(sourceId, sourceKind)), Optional.empty()));
                mark(encounter, tick);
                helped++;
            }
        }
        return helped;
    }

    /**
     * An event between a member mob and an entity: recorded with the mob and, if the entity is a participant of
     * the mob's encounter, its alias; optionally marks context pending.
     *
     * @param mobId UUID of the member mob
     * @param participantId UUID of the other entity
     * @param kind kind of the other entity
     * @param type kind of event
     * @param markPending whether the event also marks context pending
     * @return {@code true} if the mob is an active member of an open encounter
     */
    public boolean memberEvent(UUID mobId, UUID participantId, ParticipantKind kind, EventType type,
                               boolean markPending) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        var session = activeSession(mobId);
        var encounter = session.flatMap(MobSession::encounter).flatMap(encounters::find);
        if (encounter.isEmpty()) {
            return false;
        }
        var tick = clock.currentTick();
        if (encounter.get().isParticipant(participantId)) {
            record(encounter.get(), new EncounterEvent(tick, type, Optional.of(encounter.get().aliasFor(participantId,
                    kind)), Optional.of(session.get().key())));
        }
        if (markPending) {
            mark(encounter.get(), tick);
        }
        return true;
    }

    /**
     * Something happened to a member mob (a player started an attack on it): marks its encounter pending.
     *
     * @param mobId UUID of the mob
     * @return {@code true} if the mob is an active member of an open encounter
     */
    public boolean memberChanged(UUID mobId) {
        var encounter = activeSession(mobId).flatMap(MobSession::encounter).flatMap(encounters::find);
        encounter.ifPresent(found -> mark(found, clock.currentTick()));
        return encounter.isPresent();
    }

    /**
     * Counts a path computation of a member mob, a diagnostic metric only (M07.5); it never marks context.
     *
     * @param mobId UUID of the mob
     * @return {@code true} if the mob is an active member of an open encounter
     */
    public boolean memberPathfind(UUID mobId) {
        var member = activeSession(mobId).flatMap(MobSession::encounter).isPresent();
        if (member) {
            metrics.pathfinds++;
        }
        return member;
    }

    /**
     * A participant that is not a player died or left the world (M17.6): marks context pending in each of its
     * encounters, found in O(1) by the participant index; the next capture drops it, as it drops a player who leaves.
     *
     * @param worldId world of the participant
     * @param participantId UUID of the participant
     * @return number of encounters marked
     */
    public int participantGone(UUID worldId, UUID participantId) {
        var found = encounters.ofParticipant(worldId, participantId);
        var tick = clock.currentTick();
        found.forEach(encounter -> mark(encounter, tick));
        return found.size();
    }

    /**
     * Returns the counters of this entry point.
     *
     * @return live counters, read on the server thread
     */
    public ContextEventMetrics metrics() {
        return metrics;
    }

    private Optional<MobSession> activeSession(UUID mobId) {
        return sessions.find(mobId).filter(session -> session.state() == SessionState.ACTIVE);
    }

    private void record(Encounter encounter, EncounterEvent event) {
        if (encounter.recordUnlessRepeated(event, REPEAT_WINDOW_TICKS)) {
            metrics.recorded++;
        } else {
            metrics.repeats++;
        }
    }

    private void mark(Encounter encounter, long tick) {
        metrics.marks++;
        agenda.markContextPending(encounter, tick);
    }
}
