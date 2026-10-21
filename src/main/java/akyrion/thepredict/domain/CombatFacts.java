package akyrion.thepredict.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Semantic state of one encounter at capture time, built only from what the mobs could perceive.
 * Immutable, with copied lists, so it may travel to other threads (rule A4).
 *
 * <p>Invariants: at most {@value #MAX_MOBS} mobs, {@value #MAX_PARTICIPANTS} participants and
 * {@value #MAX_EVENTS} events; unique mob sessions, aliases and threat ranks; every target alias
 * is a participant; {@code captureTick >= 0}; {@code combatSeconds >= 0}.
 *
 * @param encounter encounter identity
 * @param captureTick server tick of the capture
 * @param captureNanos monotonic time of the capture
 * @param mobs facts of each mob
 * @param participants facts of each participant: a player or any other admitted living entity (M17.1)
 * @param recentEvents recent events, oldest first
 * @param combatSeconds seconds since the encounter started
 * @param relations parent, owner and children read on capture (M25); {@link GroupRelations#NONE} when unread
 * @param brief the group question offered with this capture; {@link GroupBrief#NONE} when the body has no {@code g0}
 */
public record CombatFacts(EncounterId encounter, long captureTick, long captureNanos,
                          List<MobFacts> mobs, List<ParticipantFacts> participants,
                          List<EncounterEvent> recentEvents, int combatSeconds, GroupRelations relations,
                          GroupBrief brief) {
    /** Largest number of mobs in one capture. */
    public static final int MAX_MOBS = 32;
    /** Largest number of participants in one capture. */
    public static final int MAX_PARTICIPANTS = 16;
    /** Largest number of recent events in one capture. */
    public static final int MAX_EVENTS = 32;

    /**
     * Validates the facts and copies the lists.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public CombatFacts {
        Objects.requireNonNull(encounter, "encounter");
        mobs = List.copyOf(Objects.requireNonNull(mobs, "mobs"));
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        recentEvents = List.copyOf(Objects.requireNonNull(recentEvents, "recentEvents"));
        if (captureTick < 0) {
            throw new IllegalArgumentException("captureTick must be >= 0, was " + captureTick);
        }
        if (combatSeconds < 0) {
            throw new IllegalArgumentException("combatSeconds must be >= 0, was " + combatSeconds);
        }
        Objects.requireNonNull(relations, "relations");
        Objects.requireNonNull(brief, "brief");
        DomainChecks.requireMaximumSize(mobs, MAX_MOBS, "mobs");
        DomainChecks.requireMaximumSize(participants, MAX_PARTICIPANTS, "participants");
        DomainChecks.requireMaximumSize(recentEvents, MAX_EVENTS, "recentEvents");
        var aliases = validateParticipants(participants);
        validateMobs(mobs, aliases);
    }

    /**
     * Facts from before a group brief was attached (rule W5). Relations and the brief stay absent.
     *
     * @param encounter encounter identity
     * @param captureTick server tick of the capture
     * @param captureNanos monotonic time of the capture
     * @param mobs facts of each mob
     * @param participants facts of each participant
     * @param recentEvents recent events, oldest first
     * @param combatSeconds seconds since the encounter started
     */
    public CombatFacts(EncounterId encounter, long captureTick, long captureNanos, List<MobFacts> mobs,
                       List<ParticipantFacts> participants, List<EncounterEvent> recentEvents, int combatSeconds) {
        this(encounter, captureTick, captureNanos, mobs, participants, recentEvents, combatSeconds,
                GroupRelations.NONE, GroupBrief.NONE);
    }

    /**
     * Finds the facts of one mob session.
     *
     * @param mob mob session
     * @return the facts, or empty when the mob is not part of the capture
     */
    public Optional<MobFacts> mob(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        for (var facts : mobs) {
            if (facts.mob().equals(mob)) {
                return Optional.of(facts);
            }
        }
        return Optional.empty();
    }

    /**
     * Replaces the mob list. The other components stay.
     *
     * @param replacement mob facts
     * @return the copy
     */
    public CombatFacts withMobs(List<MobFacts> replacement) {
        return new CombatFacts(encounter, captureTick, captureNanos, replacement, participants, recentEvents,
                combatSeconds, relations, brief);
    }

    /**
     * Replaces the group attachment. The other components stay.
     *
     * @param relations relations read on capture
     * @param brief the group question, or {@link GroupBrief#NONE}
     * @return the copy
     */
    public CombatFacts withGroup(GroupRelations relations, GroupBrief brief) {
        return new CombatFacts(encounter, captureTick, captureNanos, mobs, participants, recentEvents, combatSeconds,
                relations, brief);
    }

    private static HashSet<ParticipantAlias> validateParticipants(List<ParticipantFacts> participants) {
        var aliases = new HashSet<ParticipantAlias>();
        var ranks = new HashSet<Integer>();
        for (var participant : participants) {
            if (!aliases.add(participant.alias())) {
                throw new IllegalArgumentException("participants repeats alias " + participant.alias());
            }
            if (!ranks.add(participant.threatRank())) {
                throw new IllegalArgumentException("participants repeats threatRank " + participant.threatRank());
            }
        }
        return aliases;
    }

    private static void validateMobs(List<MobFacts> mobs, HashSet<ParticipantAlias> aliases) {
        var keys = new HashSet<MobKey>();
        for (var facts : mobs) {
            if (!keys.add(facts.mob())) {
                throw new IllegalArgumentException("mobs repeats session " + facts.mob());
            }
            facts.target().ifPresent(relation -> {
                if (!aliases.contains(relation.target())) {
                    throw new IllegalArgumentException("target " + relation.target() + " of mob "
                            + facts.mob() + " is not a participant");
                }
            });
        }
    }
}
