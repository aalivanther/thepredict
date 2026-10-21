package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.ThreatPolicy;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Enriched threat of M11: adds to or removes from the native MythicMobs threat table of each member the threat of
 * facts MythicMobs does not measure, through {@link CombatActions#addThreat}. MythicMobs still chooses the target.
 * Server thread only (rule T1): the listeners call it from their handlers and the coordinator from its evaluation;
 * it never runs from a callback (rule T5). It does not read the control level or the service, so it works the same
 * with or without Jev (plan §8).
 *
 * <p>Each gain is computed by {@link ThreatPolicy} with the weights of the member's profile and limited by the window
 * of its encounter ({@link ThreatWindows}); a zero weight does no work. An event of a mob that is not an active member
 * of an open encounter, or of an entity that is not a participant of it, does nothing beyond the O(1) lookups. The
 * change always carries the key of the current session, checked in the same tick, so a stale generation never receives
 * it (rule I1). A change that throws is counted and dropped, so the table keeps its value (rule A6). Per change lines,
 * with the alias of the participant and never its name, are written only with {@code decision.logDecisions} and within
 * the limit of the execution log that {@link TacticalExecution} shares with it (rule C8).
 */
public final class ThreatEnrichment {
    private final EncounterRegistry encounters;
    private final SessionRegistry sessions;
    private final ServerClock clock;
    private final ThreatMetrics metrics = new ThreatMetrics();
    private final ThreatChanges changes;
    private final UnreachableDecay decay;
    private java.util.function.BiConsumer<UUID, Long> blockedPoise = (mobId, tick) -> {
    };

    /**
     * Creates the enriched threat.
     *
     * @param encounters encounters, their participants and their threat windows
     * @param sessions sessions, for the member of each event and the current generation
     * @param actions port that changes the native threat table
     * @param clock server clock, for the tick of each event
     */
    public ThreatEnrichment(EncounterRegistry encounters, SessionRegistry sessions, CombatActions actions,
                            ServerClock clock) {
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
        changes = new ThreatChanges(sessions, Objects.requireNonNull(actions, "actions"), metrics);
        decay = new UnreachableDecay(changes, metrics);
    }

    /**
     * A member's melee hit was blocked by a participant: {@code MELEE_BLOCKED} adds {@code blockedHits} to that
     * participant in the member's table.
     *
     * @param mobId UUID of the attacking mob
     * @param participantId UUID of the blocking participant, a player or any other living entity (M17.7)
     * @return the threat sent to the table, {@code 0} when nothing was sent
     */
    public double blockedHit(UUID mobId, UUID participantId) {
        var sent = memberFact(mobId, participantId, EventType.MELEE_BLOCKED, 1.0);
        blockedPoise.accept(Objects.requireNonNull(mobId, "mobId"), clock.currentTick());
        return sent;
    }

    /**
     * Hears a blocked melee hit so poise can fill without changing the threat result (M23.7).
     *
     * @param listener mob id and tick
     */
    public void hearBlocked(java.util.function.BiConsumer<UUID, Long> listener) {
        blockedPoise = Objects.requireNonNull(listener, "listener");
    }

    /**
     * A participant's projectile hit a member: {@code RANGED_HIT_ON_MOB} adds {@code rangedHarass} times the final
     * damage to the shooter in the member's table, unless the shooter is the member's target, whose threat for damage
     * taken MythicMobs already counts.
     *
     * @param mobId UUID of the mob that was hit
     * @param shooterId UUID of the shooting participant
     * @param finalDamage final damage of the hit; a negative or non-finite value changes nothing
     * @param shooterIsTarget whether the shooter is the mob's current target
     * @return the threat sent to the table, {@code 0} when nothing was sent
     */
    public double rangedHit(UUID mobId, UUID shooterId, double finalDamage, boolean shooterIsTarget) {
        if (shooterIsTarget || !(Double.isFinite(finalDamage) && finalDamage >= 0.0)) {
            return 0.0;
        }
        return memberFact(mobId, shooterId, EventType.RANGED_HIT_ON_MOB, finalDamage);
    }

    /**
     * A participant's potion or cloud with a beneficial effect reached other participants: in each encounter of the
     * source in that world, {@code ALLY_ASSISTED} adds {@code assistsOthers} per other participant reached to the
     * source in the table of every active member. At most {@link EncounterRegistry#MAX_MEMBERS} changes per encounter.
     *
     * @param worldId world of the event
     * @param sourceId UUID of the participant who threw or placed the effect
     * @param affected UUIDs of the entities reached
     * @return number of member tables the gain was sent to
     */
    public int assisted(UUID worldId, UUID sourceId, Collection<UUID> affected) {
        Objects.requireNonNull(affected, "affected");
        var tick = clock.currentTick();
        var changed = 0;
        for (var encounter : encounters.ofParticipant(worldId, sourceId)) {
            var assisted = assistedIn(encounter, sourceId, affected);
            if (assisted == 0) {
                continue;
            }
            for (var member : List.copyOf(encounter.memberSessions())) {
                if (member.state() == SessionState.ACTIVE
                        && gain(encounter, member, sourceId, EventType.ALLY_ASSISTED, assisted, tick) > 0.0) {
                    changed++;
                }
            }
        }
        return changed;
    }

    /**
     * Once per evaluation of an encounter, after its capture: a member whose target was unreachable at this decision
     * and at the previous one, each after a new capture, removes {@code unreachableDecayPerSecond} times the seconds
     * between them from that target in its table. Unreachable means the navigator follows a live path that does not
     * reach the target; without a path nothing is removed, so the decay is the same at every control level. A weight
     * of zero (also when MythicMobs decays unreachable targets itself, M03) reads nothing. A member that throws only
     * loses its mark.
     *
     * @param encounter encounter evaluated in this tick
     * @param tick current server tick
     * @param control goal ownership, whose progress reading computes no path
     * @return number of losses sent
     */
    public int evaluated(Encounter encounter, long tick, MobControl control) {
        return decay.evaluated(Objects.requireNonNull(encounter, "encounter"), tick,
                Objects.requireNonNull(control, "control"));
    }

    /** Shares the execution log of the tactical execution, so threat lines and decision lines have one limit. */
    void logTo(ExecutionLog executionLog) {
        changes.logTo(executionLog);
    }

    /** Tells the duel recorder of each change handed to the port (M18.4); set once when the execution is wired. */
    void tapTo(DecisionTap tap) {
        changes.tapTo(tap);
    }

    /**
     * Returns the counters of the enriched threat.
     *
     * @return live counters, read on the server thread
     */
    public ThreatMetrics metrics() {
        return metrics;
    }

    private double memberFact(UUID mobId, UUID participantId, EventType fact, double magnitude) {
        var session = sessions.find(mobId).filter(found -> found.state() == SessionState.ACTIVE);
        var encounter = session.flatMap(MobSession::encounter).flatMap(encounters::find);
        if (encounter.isEmpty() || !encounter.get().isParticipant(participantId)) {
            return 0.0;
        }
        return gain(encounter.get(), session.get(), participantId, fact, magnitude, clock.currentTick());
    }

    private double gain(Encounter encounter, MobSession member, UUID participantId, EventType fact, double magnitude,
                        long tick) {
        var weights = member.profile().threat();
        var change = ThreatPolicy.change(fact, magnitude, weights);
        if (change == 0.0) {
            return 0.0;
        }
        var admitted = encounter.threat().admit(member.key(), participantId, fact, change, weights.maxPerWindow(),
                tick);
        if (admitted == 0.0) {
            metrics.limited++;
            return 0.0;
        }
        if (!changes.apply(encounter, member.key(), participantId, admitted, fact.name(), tick)) {
            return 0.0;
        }
        switch (fact) {
            case ALLY_ASSISTED -> metrics.assists++;
            case MELEE_BLOCKED -> metrics.blockedHits++;
            case GROUP_ASSIGNMENT -> {
            }
            default -> metrics.rangedHarass++;
        }
        metrics.pointsAdded += admitted;
        return admitted;
    }

    /**
     * Writes one clipped group assignment through the only threat writer.
     *
     * @param encounter the encounter
     * @param member the mob that owns the table
     * @param participant the assigned participant
     * @param magnitude magnitude already clipped by the window
     * @param tick server tick
     */
    public void assignGroup(Encounter encounter, MobSession member, UUID participant, double magnitude, long tick) {
        var cap = member.profile().threat().maxPerWindow();
        var clipped = Math.min(Math.max(0.0, magnitude), cap);
        if (clipped <= 0.0) {
            return;
        }
        gain(encounter, member, participant, EventType.GROUP_ASSIGNMENT, clipped, tick);
    }

    private static int assistedIn(Encounter encounter, UUID sourceId, Collection<UUID> affected) {
        var assisted = new HashSet<UUID>();
        for (var participantId : affected) {
            if (!participantId.equals(sourceId) && encounter.isParticipant(participantId)) {
                assisted.add(participantId);
            }
        }
        return assisted.size();
    }
}
