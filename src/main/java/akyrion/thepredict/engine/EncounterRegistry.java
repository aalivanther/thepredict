package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FightSummary;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.learn.ValueStatDelta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Open encounters, the membership of each session and an O(1) index of participants (M06.3, M06.4). Server
 * thread only; the state lives in {@code engine} (rule A3).
 *
 * <p>Identities come from a monotonic counter that starts at 1 and is never reused (rule I2). There is no
 * global merge: an encounter only gains members through individual changes of target, so every change is
 * O(members). The participant index is keyed by world and participant (a player or any other living entity, M17)
 * and lists every open encounter of that participant
 * in that world, so mobs beyond the member cap or out of range of a group fill the existing encounters in order
 * instead of forming one encounter each. Closing drops the history, the aliases and every index entry of the
 * encounter; a late answer for it is discarded by identity in M10.
 */
public final class EncounterRegistry {
    /** Maximum member sessions per encounter; one more forms another encounter. */
    public static final int MAX_MEMBERS = 16;
    /** Maximum indexed participants per encounter. */
    public static final int MAX_PARTICIPANTS = 16;

    private final Map<EncounterId, Encounter> encounters = new LinkedHashMap<>();
    private final ParticipantIndex index = new ParticipantIndex();
    private final Agenda agenda;
    private final ServerClock clock;
    private final SessionMetrics metrics;
    private DecisionTap tap = DecisionTap.NONE;
    private ZoneControl zones = ZoneControl.NONE;
    private long nextId = 1;

    /**
     * Creates an empty registry.
     *
     * @param agenda agenda that schedules each new encounter and forgets each closed one
     * @param clock server clock, for creation ticks and durations
     * @param metrics counters of opened and closed encounters
     */
    public EncounterRegistry(Agenda agenda, ServerClock clock, SessionMetrics metrics) {
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    private java.util.function.BiConsumer<Encounter, UUID> arrived = (encounter, id) -> {
    };
    private java.util.function.Consumer<Encounter> closing = encounter -> {
    };
    private java.util.function.Consumer<List<ValueStatDelta>> credits = rows -> {
    };
    private akyrion.thepredict.engine.distill.TeachingBoard teaching =
            akyrion.thepredict.engine.distill.TeachingBoard.NONE;
    private java.util.function.Consumer<List<akyrion.thepredict.domain.distill.AgreementStatDelta>> agreementSink =
            rows -> {
            };
    private LearningSettings learning;
    private java.util.function.LongSupplier epochMs = () -> 0L;

    /**
     * Hears a participant added to an open encounter. The callback runs on the server thread.
     *
     * @param consumer the listener
     */
    public void hearArrivals(java.util.function.BiConsumer<Encounter, UUID> consumer) {
        var previous = arrived;
        arrived = (encounter, id) -> {
            previous.accept(encounter, id);
            consumer.accept(encounter, id);
        };
    }

    /**
     * Records learned credit when an encounter closes. Absent settings leave every close unchanged.
     *
     * @param settings the learning section
     * @param sink where {@code m28_value_stats} rows go
     * @param epoch wall time supplied by the adapter
     */
    public void useLearning(LearningSettings settings, java.util.function.Consumer<List<ValueStatDelta>> sink,
                            java.util.function.LongSupplier epoch) {
        learning = settings;
        var previous = credits;
        credits = rows -> {
            previous.accept(rows);
            sink.accept(rows);
        };
        epochMs = epoch;
    }

    /**
     * Wall time of the adapter that {@link #useLearning} installed, so a window closed between two encounter closes
     * carries the same clock as the close (M28.2). {@code 0} until the learning section is installed.
     *
     * @return milliseconds since the epoch, or {@code 0}
     */
    long epochMs() {
        return epochMs.getAsLong();
    }

    /**
     * Records teaching rows when an encounter closes. The sink is the aggregate cache. The board also drops the
     * snapshot of a session that leaves for good, because no window of it will close any more (LD-19).
     *
     * @param board the board the capture noted
     * @param sink where {@code m29_choice_stats} rows go
     */
    public void useTeaching(akyrion.thepredict.engine.distill.TeachingBoard board,
                            java.util.function.Consumer<List<akyrion.thepredict.domain.distill.ChoiceStatDelta>> sink) {
        teaching = Objects.requireNonNull(board, "board");
        board.useSink(Objects.requireNonNull(sink, "sink"));
    }

    /**
     * Sends agreement rows to the aggregate cache. The production sink is {@code acceptAgreements}.
     *
     * @param sink where {@code m29_agreement_stats} rows go
     */
    public void useAgreements(java.util.function.Consumer<List<akyrion.thepredict.domain.distill.AgreementStatDelta>> sink) {
        agreementSink = sink;
        for (var encounter : encounters.values()) {
            for (var member : encounter.memberSessions()) {
                member.learning().distillSession().useAgreements(sink);
            }
        }
    }

    /**
     * Hears an encounter that is about to close, while its members are still attached.
     *
     * @param consumer the listener
     */
    public void hearCloses(java.util.function.Consumer<Encounter> consumer) {
        var previous = closing;
        closing = encounter -> {
            previous.accept(encounter);
            consumer.accept(encounter);
        };
    }

    /**
     * Receives the decision tap used when an encounter opens or closes.
     *
     * @param tap the tap
     */
    public void hear(DecisionTap tap) {
        this.tap = Objects.requireNonNull(tap, "tap");
    }

    /**
     * Opens a zone when an encounter is formed and closes it when the encounter ends.
     *
     * @param zones the zone port; {@link ZoneControl#NONE} builds nothing
     */
    public void useZone(ZoneControl zones) {
        this.zones = Objects.requireNonNull(zones, "zones");
    }

    /**
     * Places an active session according to its target (M06.3, M06.4), called when the target changes and on
     * every heartbeat. A session without a target keeps its encounter, so callers only call this with a target.
     * The session stays if its own encounter, in this world, already has the target; otherwise it joins the first
     * open encounter of the target in this world that has room, does not list the session's own entity as a
     * participant (an encounter is one side, rule I13, M17.6) and has a member within the follow range of the
     * session; otherwise it forms a new encounter with the target as first participant.
     *
     * @param session active session
     * @param worldId world of the mob
     * @param targetId UUID of the target
     * @param withinFollowRange tells whether a member is within the follow range of the session's mob; the
     *                          caller evaluates it with {@code CombatReader} data because {@code engine} has no
     *                          positions (rule D2); since M17 it is also false for a rival member
     * @return the encounter the session belongs to afterwards
     * @throws IllegalStateException if the session is not active
     */
    public Encounter assign(MobSession session, UUID worldId, UUID targetId, Predicate<MobKey> withinFollowRange) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(withinFollowRange, "withinFollowRange");
        if (session.state() != SessionState.ACTIVE) {
            throw new IllegalStateException("only active sessions join encounters, " + session.key() + " is " + session.state());
        }
        var current = session.encounter().map(encounters::get);
        if (current.isPresent() && current.get().worldId().equals(worldId) && current.get().isParticipant(targetId)) {
            return current.get();
        }
        var self = session.key().entityId();
        for (var candidate : index.of(worldId, targetId)) {
            if (candidate.memberCount() < MAX_MEMBERS && !candidate.isParticipant(self)
                    && candidate.members().stream().anyMatch(withinFollowRange)) {
                return move(session, candidate);
            }
        }
        return move(session, create(worldId, targetId));
    }

    /**
     * Removes a session from its encounter; an encounter left without members closes at once. The last member is
     * still attached when the close is heard, so the listeners of {@link #hearCloses} and the learned credit see the
     * whole fight (M27, M28). The teaching board drops the snapshot of the session.
     *
     * @param session session that suspends, closes or changes world
     */
    public void leave(MobSession session) {
        detach(session);
        teaching.release(session.key().entityId().toString());
    }

    private void detach(MobSession session) {
        var encounter = session.encounter().map(encounters::get);
        session.leaveEncounter();
        if (encounter.isEmpty()) {
            return;
        }
        var entityId = session.key().entityId();
        if (encounter.get().memberCount() == 1 && encounter.get().isMember(entityId)) {
            close(encounter.get(), SessionMetrics.EncounterEnd.NO_MEMBERS);
            return;
        }
        encounter.get().removeMember(entityId);
        if (encounter.get().memberCount() == 0) {
            close(encounter.get(), SessionMetrics.EncounterEnd.NO_MEMBERS);
        }
    }

    /**
     * Closes an encounter whose combat ended (M06.4), checked on its heartbeat: no members left, or no member has
     * a target and neither members nor participants are in combat. At most 32 O(1) {@code inCombat} calls.
     *
     * @param id encounter to check
     * @param inCombat {@code CombatReader.inCombat}: vanilla combat tracker or MythicMobs threat table
     * @return {@code true} if the encounter closed or no longer exists
     */
    public boolean closeIfFinished(EncounterId id, Predicate<UUID> inCombat) {
        var encounter = encounters.get(id);
        if (encounter == null) {
            return true;
        }
        if (encounter.memberCount() == 0) {
            close(encounter, SessionMetrics.EncounterEnd.NO_MEMBERS);
            return true;
        }
        if (anyMemberHasTarget(encounter) || anyMemberInCombat(encounter, inCombat)
                || encounter.anyParticipant(inCombat)) {
            return false;
        }
        close(encounter, SessionMetrics.EncounterEnd.COMBAT_ENDED);
        return true;
    }

    /**
     * Adds a participant seen by M07 and indexes the encounter under it.
     *
     * @param id open encounter
     * @param participantId UUID of the participant
     * @return {@code false} if the encounter is closed or already has {@link #MAX_PARTICIPANTS} participants
     */
    public boolean addParticipant(EncounterId id, UUID participantId) {
        var encounter = encounters.get(id);
        if (encounter == null || !encounter.addParticipant(Objects.requireNonNull(participantId, "participantId"))) {
            return false;
        }
        index.add(encounter, participantId);
        arrived.accept(encounter, participantId);
        return true;
    }

    /**
     * Removes a participant; it keeps its alias while the encounter exists.
     *
     * @param id encounter
     * @param participantId UUID of the participant
     */
    public void removeParticipant(EncounterId id, UUID participantId) {
        var encounter = encounters.get(id);
        if (encounter != null) {
            encounter.removeParticipant(participantId);
            index.remove(encounter, participantId);
        }
    }

    /**
     * Open encounters in insertion order. The caller must not modify the view.
     *
     * @return the open encounters
     */
    java.util.Collection<Encounter> open() {
        return encounters.values();
    }

    /**
     * Finds an open encounter.
     *
     * @param id encounter identity
     * @return the encounter, empty once closed
     */
    public Optional<Encounter> find(EncounterId id) {
        return Optional.ofNullable(encounters.get(id));
    }

    /**
     * Finds the open encounters of a participant in a world in O(1), for the events of M07.
     *
     * @param worldId world of the event
     * @param participantId UUID of the participant
     * @return open encounters that list the participant in that world, oldest first
     */
    public List<Encounter> ofParticipant(UUID worldId, UUID participantId) {
        return index.of(worldId, participantId);
    }

    /**
     * Counts the open encounters.
     *
     * @return open encounters
     */
    public int size() {
        return encounters.size();
    }

    /**
     * Closes every encounter without members; the reconciliation's safety net (M06.6).
     *
     * @return number of closed encounters
     */
    public int closeEmpty() {
        var empty = encounters.values().stream().filter(encounter -> encounter.memberCount() == 0).toList();
        empty.forEach(encounter -> close(encounter, SessionMetrics.EncounterEnd.NO_MEMBERS));
        return empty.size();
    }

    /** Counts the open encounters with pending context, for {@code /thepredict status} (M12.1). */
    int contextPending() {
        var pending = 0;
        for (var encounter : encounters.values()) {
            if (encounter.contextPending()) {
                pending++;
            }
        }
        return pending;
    }

    /** Counts the keys of the participant index, for tests that check no key is leaked. */
    int participantKeys() {
        return index.keys();
    }

    private Encounter create(UUID worldId, UUID targetId) {
        var encounter = new Encounter(new EncounterId(nextId++), worldId, clock.currentTick());
        encounter.addParticipant(targetId);
        index.add(encounter, targetId);
        encounters.put(encounter.id(), encounter);
        metrics.encounterOpened();
        return encounter;
    }

    /** Moves a session into an encounter: a new one evaluates at once, an existing one gets pending context. */
    private Encounter move(MobSession session, Encounter target) {
        detach(session);
        var formed = target.memberCount() == 0;
        target.addMember(session);
        session.joinEncounter(target.id());
        session.learning().distillSession().useAgreements(agreementSink);
        if (formed) {
            agenda.schedule(target, clock.currentTick());
            zones.open(target.id(), List.of(session.key()), session.profile().zone());
        } else {
            agenda.markContextPending(target, clock.currentTick());
        }
        return target;
    }

    private void close(Encounter encounter, SessionMetrics.EncounterEnd cause) {
        closing.accept(encounter);
        zones.close(encounter.id());
        emitCredit(encounter);
        if (tap.detail() != TapDetail.NONE) {
            var members = new ArrayList<MobKey>();
            var profiles = new java.util.LinkedHashMap<MobKey, String>();
            for (var member : encounter.memberSessions()) {
                members.add(member.key());
                profiles.put(member.key(), member.profile().id());
            }
            tap.encounterClosed(encounter.id(), new EncounterClosed(encounter.id(), encounter.worldId(),
                    encounter.createdTick(), clock.currentTick(), cause, List.copyOf(members), profiles,
                    encounter.participants(), 0, 0, groupSummary(encounter)));
        }
        for (var member : new ArrayList<>(encounter.memberSessions())) {
            member.leaveEncounter();
            member.invalidateReady();
        }
        encounter.forEachParticipant(participantId -> index.remove(encounter, participantId));
        encounters.remove(encounter.id());
        agenda.remove(encounter);
        encounter.close();
        metrics.encounterClosed(cause, clock.currentTick() - encounter.createdTick());
    }

    private void emitCredit(Encounter encounter) {
        if (learning == null || !learning.collect()) {
            return;
        }
        var rows = new ArrayList<ValueStatDelta>();
        var mode = encounter.groupMode(GroupMode.OFF);
        var epoch = epochMs.getAsLong();
        for (var member : encounter.memberSessions()) {
            member.learning().distillSession().emitAudit(member.profile().id(), epoch);
            noteDrift(member);
            rows.addAll(CreditClose.finish(member, learning, mode, epoch));
        }
        if (!rows.isEmpty()) {
            credits.accept(List.copyOf(rows));
        }
    }

    private static void noteDrift(MobSession session) {
        var state = session.learning().distillSession();
        var fight = session.difficulty().clock();
        if (fight.fought()) {
            var reference = session.difficulty().targetSeconds() * 20;
            var elapsed = fight.summary().map(FightSummary::elapsedTicks).orElse(0);
            if (reference > 0 && elapsed > 0) {
                var ratio = elapsed / (double) reference;
                if (ratio < 0.8 || ratio > 1.25) {
                    state.governor().outcome();
                }
            }
        }
        state.governor().entropy(state.dominantShare());
    }

    private static String groupSummary(Encounter encounter) {
        GroupFightClock clock = null;
        for (var member : encounter.memberSessions()) {
            var found = member.groupClock().orElse(null);
            if (found != null) {
                clock = found;
                break;
            }
        }
        if (clock == null) {
            return "";
        }
        if (clock.open()) {
            clock.close();
        }
        return clock.summaryLine();
    }

    private static boolean anyMemberHasTarget(Encounter encounter) {
        return encounter.memberSessions().stream().anyMatch(member -> member.lastTargetId().isPresent());
    }

    private static boolean anyMemberInCombat(Encounter encounter, Predicate<UUID> inCombat) {
        return encounter.memberSessions().stream().anyMatch(member -> inCombat.test(member.key().entityId()));
    }
}
