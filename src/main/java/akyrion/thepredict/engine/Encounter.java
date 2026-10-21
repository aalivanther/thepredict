package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One combat encounter: the unit of request, heartbeat and budget (01 §8, M06.3). Server thread only.
 *
 * <p>Members are the sessions themselves, so their keys follow every generation change; at most
 * {@link EncounterRegistry#MAX_MEMBERS} members and {@link EncounterRegistry#MAX_PARTICIPANTS} indexed
 * participants. The history is a ring of {@link #HISTORY_SIZE} events. Only {@link EncounterRegistry} changes
 * membership; the scheduling state lives in its {@link EncounterSchedule}, changed by {@link Agenda}, {@link InFlightRegistry}
 * and {@link DecisionDispatcher}; the threat windows of M11 live in its {@link ThreatWindows}, changed by
 * {@link ThreatEnrichment} and dropped on close.
 */
public final class Encounter {
    /** Number of events kept in the history ring. */
    public static final int HISTORY_SIZE = 16;

    private final EncounterId id;
    private final UUID worldId;
    private final long createdTick;
    private final Map<UUID, MobSession> members = new LinkedHashMap<>();
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final ParticipantAliases aliases = new ParticipantAliases(participants::contains);
    private final EventHistory history = new EventHistory(HISTORY_SIZE);
    private final EncounterSchedule schedule = new EncounterSchedule();
    private final ThreatWindows threat = new ThreatWindows();
    private Optional<CombatFacts> lastFacts = Optional.empty();
    private boolean closed;
    private boolean answerReady;
    private GroupPlan answerPlan;
    private long answerSequence;
    private long answerMembership;
    private boolean captureReady;
    private boolean captureAsks;
    private boolean groupQuestion;
    private long captureSequence;
    private final GroupPlan[] capturePlans = new GroupPlan[8];
    private int captureCount;
    private GroupMode notedMode;

    Encounter(EncounterId id, UUID worldId, long createdTick) {
        this.id = Objects.requireNonNull(id, "id");
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.createdTick = createdTick;
    }

    /**
     * Returns the identity of the encounter.
     *
     * @return monotonic identity, never reused during one start (rule I2)
     */
    public EncounterId id() {
        return id;
    }

    /**
     * Returns the world of the encounter, for the per-world budget and the agenda.
     *
     * @return UUID of the world where it formed
     */
    public UUID worldId() {
        return worldId;
    }

    long createdTick() {
        return createdTick;
    }

    /**
     * Returns the current keys of the members.
     *
     * @return member keys in joining order
     */
    public List<MobKey> members() {
        var keys = new ArrayList<MobKey>(members.size());
        members.values().forEach(member -> keys.add(member.key()));
        return keys;
    }

    /**
     * Counts the members.
     *
     * @return number of member sessions
     */
    public int memberCount() {
        return members.size();
    }

    /**
     * Returns a copy of the indexed participants. Their UUIDs never leave {@code engine} (rule I6). The copy keeps
     * the order of arrival, so a tie between participants breaks the same way on every start (rule C16).
     *
     * @return participant UUIDs in order of arrival
     */
    public Set<UUID> participants() {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(participants));
    }

    /**
     * Visits the indexed participants in order of arrival without copying them, for the steps that run every tick
     * (rule C12).
     *
     * @param visitor receives each participant UUID
     */
    public void forEachParticipant(java.util.function.Consumer<UUID> visitor) {
        participants.forEach(visitor);
    }

    /**
     * Counts the indexed participants.
     *
     * @return number of participants
     */
    public int participantCount() {
        return participants.size();
    }

    /**
     * Whether some participant passes the test, in order of arrival and without copying the set (AE-05).
     *
     * @param test the test
     * @return {@code true} at the first participant that passes
     */
    public boolean anyParticipant(java.util.function.Predicate<UUID> test) {
        java.util.Objects.requireNonNull(test, "test");
        for (var participant : participants) {
            if (test.test(participant)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether an entity is an indexed participant.
     *
     * @param participantId UUID of the entity
     * @return {@code true} if the entity is a participant
     */
    public boolean isParticipant(UUID participantId) {
        return participants.contains(participantId);
    }

    /**
     * Returns the stable alias of a participant, assigning the next one of its kind the first time (the
     * {@code CaptureContext.aliasFor} function of M07 and M17.1).
     *
     * @param participantId UUID of the participant
     * @param kind kind of the participant
     * @return the alias of the participant in this encounter
     */
    public ParticipantAlias aliasFor(UUID participantId, ParticipantKind kind) {
        return aliases.aliasFor(participantId, kind);
    }

    ParticipantAliases aliases() {
        return aliases;
    }

    /**
     * Adds an event to the history ring, replacing the oldest one when full.
     *
     * @param event event observed in the encounter
     */
    public void record(EncounterEvent event) {
        history.record(event);
    }

    /**
     * Adds an event unless the same type from the same participant and mob is already in the history less than
     * {@code windowTicks} earlier (M07.5).
     *
     * @return {@code true} if the event was recorded
     */
    boolean recordUnlessRepeated(EncounterEvent event, long windowTicks) {
        if (history.hasRepeat(event, windowTicks)) {
            return false;
        }
        history.record(event);
        return true;
    }

    /**
     * Returns a copy of the history, oldest first.
     *
     * @return at most {@link #HISTORY_SIZE} events
     */
    public List<EncounterEvent> recentEvents() {
        return history.recent();
    }

    /**
     * Tells whether something relevant happened since the last decision (M06.5, M07.5).
     *
     * @return {@code true} while context is pending
     */
    public boolean contextPending() {
        return schedule.contextPending();
    }

    /**
     * Cause of the pending mark. The first mark wins until the encounter decides.
     *
     * @return the cause stored with the mark
     */
    public Agenda.PendingCause pendingCause() {
        return schedule.pendingCause();
    }

    void pendingCause(Agenda.PendingCause cause) {
        schedule.pendingCause(cause);
    }

    /**
     * Stores {@code g0} until the group step. A membership that no longer matches is dropped there.
     *
     * @param plan the plan Jev named
     * @param sequence capture sequence
     * @param membership membership epoch stamped on the capture
     */
    void offerGroupAnswer(GroupPlan plan, long sequence, long membership) {
        answerPlan = plan;
        answerSequence = sequence;
        answerMembership = membership;
        answerReady = plan != null;
    }

    boolean hasGroupAnswer() {
        return answerReady;
    }

    GroupPlan groupAnswerPlan() {
        return answerPlan;
    }

    long groupAnswerSequence() {
        return answerSequence;
    }

    long groupAnswerMembership() {
        return answerMembership;
    }

    void clearGroupAnswer() {
        answerReady = false;
        answerPlan = null;
    }

    /**
     * Stores one capture's candidates. Candidate 0 is first. A capture that does not ask does not, by itself,
     * replace a live plan.
     *
     * @param sequence capture sequence
     * @param asks whether the request carries {@code g0}
     * @param plans candidates
     */
    void offerGroupCapture(long sequence, boolean asks, List<GroupPlan> plans) {
        captureSequence = sequence;
        captureAsks = asks;
        captureCount = 0;
        if (plans != null) {
            for (var plan : plans) {
                if (plan == null || captureCount == capturePlans.length) {
                    continue;
                }
                capturePlans[captureCount++] = plan;
            }
        }
        captureReady = true;
    }

    boolean hasGroupCapture() {
        return captureReady;
    }

    boolean groupCaptureAsks() {
        return captureAsks;
    }

    /**
     * Notes that the production capture saw a group brief other than {@code NONE}.
     *
     * @param asked whether the brief asks a group question
     */
    void noteGroupQuestion(boolean asked) {
        groupQuestion = asked;
    }

    /**
     * Whether this encounter's capture carried a group brief other than {@code NONE}.
     *
     * @return the flag
     */
    boolean groupQuestion() {
        return groupQuestion;
    }

    long groupCaptureSequence() {
        return captureSequence;
    }

    List<GroupPlan> groupCapturePlans() {
        var copy = new ArrayList<GroupPlan>(captureCount);
        for (var index = 0; index < captureCount; index++) {
            copy.add(capturePlans[index]);
        }
        return copy;
    }

    void clearGroupCapture() {
        captureReady = false;
    }

    /** Returns the scheduling state, for the agenda, the requests in flight and the dispatcher. */
    EncounterSchedule schedule() {
        return schedule;
    }

    /** Returns the threat windows and unreachable marks of M11.3. */
    ThreatWindows threat() {
        return threat;
    }

    /**
     * Returns the facts of the last capture, for {@code /thepredict inspect} (M12.2); only aliases, bands and cues.
     *
     * @return the facts, empty before the first capture
     */
    public Optional<CombatFacts> lastFacts() {
        return lastFacts;
    }

    void lastFacts(CombatFacts facts) {
        lastFacts = Optional.of(facts);
    }

    /**
     * Returns the heartbeat: the smallest {@code heartbeatTicks} among the member profiles.
     *
     * @return ticks between evaluations when nothing happens, at least 1
     */
    public int heartbeatTicks() {
        return members.values().stream().mapToInt(member -> member.profile().timing().heartbeatTicks()).min().orElse(1);
    }

    /**
     * Returns the minimum interval: the largest {@code minimumDecisionIntervalTicks} among the member profiles.
     *
     * @return ticks between two decisions at least, at least 1
     */
    public int minimumDecisionIntervalTicks() {
        return members.values().stream().mapToInt(member -> member.profile().timing().minimumDecisionIntervalTicks())
                .max().orElse(1);
    }

    /**
     * Returns the reactive interval (M20.7): the largest {@code reactiveIntervalTicks} among the member profiles when
     * every member asks with {@code REACTIVE}, or {@code -1} when a member is {@code STANDARD} or there is none. The
     * saving mode of one member governs the whole encounter.
     *
     * @return ticks a pending mark waits after the last decision, or {@code -1}
     */
    public int reactiveIntervalTicks() {
        var interval = -1;
        for (var member : members.values()) {
            var timing = member.profile().timing();
            if (timing.cadence() != DecisionCadence.REACTIVE) {
                return -1;
            }
            interval = Math.max(interval, timing.reactiveIntervalTicks());
        }
        return interval;
    }

    /**
     * Entropy question gap, the smaller {@code min(c(t), ReactiveInterval)} among entropy members.
     *
     * @param tick current tick
     * @return the gap, empty when no member is in entropy
     */
    public java.util.OptionalInt entropyGap(long tick) {
        var gap = Integer.MAX_VALUE;
        var found = false;
        for (var member : members.values()) {
            if (!member.difficulty().entropy()) {
                continue;
            }
            found = true;
            gap = Math.min(gap, member.difficulty().effectiveInterval(
                    member.profile().timing().reactiveIntervalTicks(), tick));
        }
        return found ? java.util.OptionalInt.of(Math.max(0, gap)) : java.util.OptionalInt.empty();
    }

    /**
     * Tells whether the encounter closed; a closed encounter is never reopened.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Stores the effective group mode of this encounter for the close.
     *
     * @param mode the mode from the group step
     */
    void noteGroupMode(GroupMode mode) {
        if (mode != null) {
            notedMode = mode;
        }
    }

    /**
     * Effective group mode stamped by the group step, or the fallback when this encounter was never published.
     *
     * @param fallback global mode
     * @return the mode to use at close
     */
    public GroupMode groupMode(GroupMode fallback) {
        return notedMode == null ? fallback : notedMode;
    }

    boolean isMember(UUID entityId) {
        return members.containsKey(entityId);
    }

    Collection<MobSession> memberSessions() {
        return members.values();
    }

    void addMember(MobSession session) {
        members.put(session.key().entityId(), session);
    }

    void removeMember(UUID entityId) {
        members.remove(entityId);
    }

    boolean addParticipant(UUID participantId) {
        return participants.contains(participantId)
                || participants.size() < EncounterRegistry.MAX_PARTICIPANTS && participants.add(participantId);
    }

    void removeParticipant(UUID participantId) {
        participants.remove(participantId);
    }

    /** Marks the encounter closed and drops its history, participants, aliases and threat windows. */
    void close() {
        closed = true;
        members.clear();
        participants.clear();
        aliases.clear();
        history.clear();
        threat.clear();
    }
}
