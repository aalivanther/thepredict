package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.TickLayerMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Steps 3 to 5 of the coordinator and the upkeep of an encounter before it decides (M09.1, M06.3 to M06.5, M05.4).
 * Server thread only.
 *
 * <p>Signals mark the encounter of their mob. A probe places its session by the target it reports: a target outside
 * the session's encounter goes through {@link EncounterRegistry#assign} with the follow-range predicate of
 * {@link CombatReader#withinFollowRange}; a new target inside it marks context pending. Installations run for the
 * queued sessions within the limit per tick; a {@code Deferred} one waits for the next tick and an {@code Unsupported}
 * one leaves the queue and stays at level 4 until its generation changes. Before an encounter decides, members whose
 * target is not a participant are placed again and the goals of the installed members are checked; after its capture it
 * closes if its combat ended. An exception while handling one session drops only that session to level 4 with
 * {@code INTERNAL_ERROR}.
 */
public final class SessionUpkeep {
    private final SessionRegistry sessions;
    private final EncounterRegistry encounters;
    private final Agenda agenda;
    private final IntentBoard board;
    private final ContextSignals signals;
    private final CombatReader reader;
    private final MobControl control;
    private final LongSupplier catalogRevision;
    private final ExecutionSteps steps;
    private final DecisionMetrics metrics;
    private final TickDegrade degrade = new TickDegrade();
    private final TickBudget budget = new TickBudget(0L, 600, () -> 0L);
    private final List<MobSession> walked = new ArrayList<>();
    private final List<MobSession> probed = new ArrayList<>();
    private final Consumer<MobSession> failure = this::fail;
    private final Consumer<MobSession> pending = this::markPending;
    private final Consumer<MobSession> dueProbe = probed::add;
    private DecisionTap records = DecisionTap.NONE;
    private int readyCursor;
    private int forcedStep;
    private long currentTick;

    /**
     * Creates the upkeep.
     *
     * @param sessions session registry and its installation queue
     * @param encounters encounter registry
     * @param agenda agenda of evaluations and probes
     * @param board intent board, for the fall to level 4
     * @param signals {@code predictordecide} signals
     * @param reader combat reader port
     * @param control goal ownership port
     * @param catalogRevision current catalog revision
     * @param steps reactive cycle of M10
     * @param metrics counter of failed sessions
     */
    public SessionUpkeep(SessionRegistry sessions, EncounterRegistry encounters, Agenda agenda, IntentBoard board,
                         ContextSignals signals, CombatReader reader, MobControl control, LongSupplier catalogRevision,
                         ExecutionSteps steps, DecisionMetrics metrics) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.board = Objects.requireNonNull(board, "board");
        this.signals = Objects.requireNonNull(signals, "signals");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.control = Objects.requireNonNull(control, "control");
        this.catalogRevision = Objects.requireNonNull(catalogRevision, "catalogRevision");
        this.steps = Objects.requireNonNull(steps, "steps");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Receives the records tap. Reactions and micro changes are told only when its detail is full.
     *
     * @param tap the tap
     */
    public void hear(DecisionTap tap) {
        records = Objects.requireNonNull(tap, "tap");
    }

    /**
     * Step 3: marks context pending for the encounter of each signalled mob; a mob without session or encounter is
     * ignored.
     *
     * @param maximum {@code decision.maxSignalsPerTick}
     * @param tick current server tick
     * @return signals drained
     */
    public int signals(int maximum, long tick) {
        return signals.drain(entityId -> sessions.find(entityId).flatMap(MobSession::encounter)
                .flatMap(encounters::find).ifPresent(encounter -> agenda.markContextPending(encounter, tick)), maximum);
    }

    /**
     * Step 4: probes the sessions whose turn falls on this tick, places each by its target and hands it to the
     * reactive cycle of M10. A probe without data leaves the session to the reconciliation of M06.6.
     *
     * @param maximum {@code decision.maxProbesPerTick}
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     * @return sessions probed
     */
    public int probes(int maximum, long tick, long nowNanos) {
        currentTick = tick;
        probed.clear();
        var due = agenda.visitProbes(tick, maximum, dueProbe);
        for (var index = 0; index < probed.size(); index++) {
            probe(probed.get(index), tick, nowNanos);
        }
        probed.clear();
        return due;
    }

    /**
     * Probes one due session of the ring (AE-14): the ring hands the session itself, so no key is looked up again, and
     * the sessions are gathered first in a list this upkeep reuses, so a visit that changes the ring never skips one.
     */
    private void probe(MobSession session, long tick, long nowNanos) {
        if (session.state() != SessionState.ACTIVE) {
            return;
        }
        var key = session.key();
        try {
            var probe = reader.probe(key);
            if (probe.isPresent()) {
                var mode = session.profile().stances().nativeMode();
                if (mode != akyrion.thepredict.domain.StanceNativeMode.OFF) {
                    reader.authorStance(key).ifPresent(stance -> session.authorMirror(stance, tick));
                }
                place(session, probe.get(), tick);
                steps.react(session, probe.get(), tick, nowNanos);
            }
        } catch (RuntimeException exception) {
            fail(session);
        }
    }

    /**
     * Tick layer after the lifecycle and before installations (M20.3). Profiles that only cast take the M19 walk.
     * Any other mode also visits sessions that are not due, still under the same cap. The time of this step is part
     * of {@code maxTickWorkMicros} because the coordinator reads the clock after it.
     *
     * @param maximum {@code decision.tickLayer.maxSessionsPerTick}
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     * @return sessions visited
     */
    public int tickLayer(int maximum, long tick, long nowNanos) {
        return tickLayer(maximum, 600, 4, tick, nowNanos, nowNanos, () -> nowNanos);
    }

    /**
     * Tick layer with its own time cap. {@code nanos} is read after each cast and every eight visits without one.
     * A world of casts-only profiles keeps the M19 walk and does not read the clock here.
     *
     * @param maximum visit cap
     * @param maxMicros time cap
     * @param minHold minimum ticks a published micro stays
     * @param tick current server tick
     * @param nowNanos monotonic time of this tick
     * @param originNanos clock at the start of the pass
     * @param nanos clock supplier
     * @return sessions visited
     */
    public int tickLayer(int maximum, int maxMicros, int minHold, long tick, long nowNanos, long originNanos,
                         java.util.function.LongSupplier nanos) {
        currentTick = tick;
        var active = activeSessions();
        if (castsOnly(active)) {
            visitCastsOnly(active, tick);
            return readyCastsOn(active, maximum, tick, nowNanos);
        }
        var step = Math.max(degrade.step(), forcedStep);
        var walk = TickLayer.pass(active, maximum, readyCursor, tick, nowNanos, board, reader, steps, metrics, failure,
                minHold, step, pending, records, budget.reset(originNanos, maxMicros, nanos));
        readyCursor = TickLayer.Walk.cursor(walk);
        degrade.note(TickLayer.Walk.capped(walk), TickLayer.Walk.underHalf(walk));
        if (Math.max(degrade.step(), forcedStep) > 0) {
            metrics.layer(DecisionMetrics.LayerCounter.TICK_LAYER_DEGRADED, 1);
        }
        return TickLayer.Walk.visits(walk);
    }

    /** The active sessions in opening order, copied into a list this upkeep reuses from tick to tick (rule C12). */
    private List<MobSession> activeSessions() {
        walked.clear();
        sessions.activeInto(walked);
        return walked;
    }

    private static boolean castsOnly(List<MobSession> active) {
        for (var index = 0; index < active.size(); index++) {
            if (active.get(index).profile().timing().tickLayer() != TickLayerMode.CASTS) {
                return false;
            }
        }
        return true;
    }

    /**
     * The visit a casts-only world still owes every active session, as the tick layer pays it: the pressure step of
     * M22 ({@code SessionDifficulty.touch}, its rung, mode, entropy and learning) and the shared fields of the board.
     * Each session runs inside its own boundary (rule C7).
     */
    private void visitCastsOnly(List<MobSession> active, long tick) {
        for (var index = 0; index < active.size(); index++) {
            var session = active.get(index);
            if (session.state() != SessionState.ACTIVE) {
                continue;
            }
            try {
                session.difficulty().touch(session, tick, reader, records, board);
                StanceBoard.syncShared(board, session);
            } catch (RuntimeException exception) {
                fail(session);
            }
        }
    }

    private void markPending(MobSession session) {
        session.encounter().flatMap(encounters::find).ifPresent(encounter -> agenda.markContextPending(encounter,
                currentTick));
    }

    /**
     * Forces step 2 while the previous server tick was over {@code decision.tickLayer.guardMspt}. Zero guard never
     * calls this.
     *
     * @param over whether the previous tick exceeded the guard
     */
    public void noteGuard(boolean over) {
        forcedStep = over ? 2 : 0;
    }

    /**
     * Publishes the profile's waypoint refresh so a micro goal installed earlier still renews on that period.
     *
     * @param ticks {@code decision.tickLayer.waypointRefreshTicks}
     */
    public void noteWaypointRefresh(int ticks) {
        board.noteWaypointRefresh(ticks);
    }

    /**
     * Publishes the server tick on the intent board for the readers off the server thread (SA-10).
     *
     * @param tick the server tick
     */
    public void publishTick(long tick) {
        board.publishTick(tick);
    }

    /**
     * Ready casts of the active sessions, ignoring the tick layer.
     *
     * @param maximum most checks this tick
     * @param tick current server tick
     * @param nowNanos monotonic time of the tick
     * @return checks whose skill was due
     */
    public int readyCasts(int maximum, long tick, long nowNanos) {
        currentTick = tick;
        return readyCastsOn(activeSessions(), maximum, tick, nowNanos);
    }

    private int readyCastsOn(List<MobSession> active, int maximum, long tick, long nowNanos) {
        var count = active.size();
        if (count == 0 || maximum < 1) {
            return 0;
        }
        var index = Math.floorMod(readyCursor, count);
        var scanned = 0;
        var checks = 0;
        for (; scanned < count && checks < maximum; scanned++) {
            var session = active.get(index);
            if (session.nextReadyTick() <= tick) {
                castReady(session, tick, nowNanos);
                checks++;
            }
            index = (index + 1) % count;
        }
        if (checks == maximum) {
            countDeferred(active, index, scanned, tick);
        }
        readyCursor = index;
        return checks;
    }

    private void castReady(MobSession session, long tick, long nowNanos) {
        try {
            steps.castReady(session, tick, nowNanos);
            if (session.consumeContextMiss()) {
                session.encounter().flatMap(encounters::find)
                        .ifPresent(encounter -> agenda.markContextPending(encounter, tick));
            }
        } catch (RuntimeException exception) {
            fail(session);
        }
    }

    /** Counts the due sessions the check cap left for the next tick as {@code READY_DEFERRED}. */
    private void countDeferred(List<MobSession> active, int index, int scanned, long tick) {
        var count = active.size();
        var deferred = 0;
        var cursor = index;
        for (var rest = scanned; rest < count; rest++) {
            if (active.get(cursor).nextReadyTick() <= tick) {
                deferred++;
            }
            cursor = (cursor + 1) % count;
        }
        if (deferred > 0) {
            metrics.add(DecisionMetrics.ReadyCounter.READY_DEFERRED, deferred);
        }
    }

    /**
     * Step 5: runs at most {@code maximum} installations of queued sessions while the work budget lasts.
     *
     * @param maximum {@code decision.maxInstallsPerTick}
     * @param withinBudget whether the tick still has work budget
     * @return installations run
     */
    public int installs(int maximum, BooleanSupplier withinBudget) {
        var revision = catalogRevision.getAsLong();
        var deferred = new ArrayList<MobSession>();
        var done = 0;
        while (done < maximum && withinBudget.getAsBoolean()) {
            var next = sessions.installs().poll();
            if (next.isEmpty()) {
                break;
            }
            done++;
            var session = sessions.find(next.get()).filter(found -> found.state() == SessionState.ACTIVE);
            if (session.isPresent() && session.get().installPending() && install(session.get(), revision)) {
                deferred.add(session.get());
            }
        }
        deferred.forEach(session -> sessions.installs().add(session.key().entityId()));
        return done;
    }

    /**
     * Tells whether sessions wait for their goal installation, so a tick that skips step 5 only counts as deferred when
     * an installation was actually waiting.
     *
     * @return {@code true} while the installation queue is not empty
     */
    public boolean installsPending() {
        return sessions.installs().size() > 0;
    }

    /**
     * Upkeep before an encounter decides: places again the members whose target is not a participant, and checks the
     * goals of the installed members, which costs nothing while their marker is present. The target of the last capture
     * only says that a member may have to move; the move follows the target a fresh probe reads, because a probe may
     * have placed the member here by a newer target after that capture (M13: without it, a mob that switched to a
     * player without encounter went back to its old encounter before its new one was captured, and formed a new one at
     * every probe). At most one extra probe per such member, and none for the others.
     *
     * @param id due encounter
     * @return the encounter if it is still open
     */
    public Optional<Encounter> prepare(EncounterId id) {
        var found = encounters.find(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var revision = catalogRevision.getAsLong();
        for (var member : List.copyOf(found.get().memberSessions())) {
            try {
                member.lastTargetId().filter(target -> !found.get().isParticipant(target))
                        .flatMap(stale -> reader.probe(member.key())).flatMap(MobProbe::targetId)
                        .filter(target -> !found.get().isParticipant(target)).ifPresent(target ->
                                encounters.assign(member, found.get().worldId(), target, withinFollowRange(member)));
                if (!member.installPending() && member.installedRevision().isPresent() && install(member, revision)) {
                    sessions.installs().add(member.key().entityId());
                }
            } catch (RuntimeException exception) {
                fail(member);
            }
        }
        return encounters.find(id);
    }

    /**
     * Closes an evaluated encounter whose combat ended (M06.4): its capture has just recorded the targets, so an
     * encounter never outlives its combat by more than one heartbeat.
     *
     * @param encounter encounter evaluated in this tick
     * @return {@code true} if it is closed
     */
    public boolean closeIfFinished(Encounter encounter) {
        return encounter.isClosed() || encounters.closeIfFinished(encounter.id(), reader::inCombat);
    }

    /** Joins or forms the encounter of a new target, or marks context pending when the target changed inside it. */
    private void place(MobSession session, MobProbe probe, long tick) {
        if (probe.targetId().isEmpty()) {
            return;
        }
        var target = probe.targetId().get();
        var encounter = session.encounter().flatMap(encounters::find);
        if (encounter.isEmpty() || !encounter.get().worldId().equals(probe.worldId())
                || !encounter.get().isParticipant(target)) {
            encounters.assign(session, probe.worldId(), target, withinFollowRange(session));
        } else if (session.lastTargetId().isPresent() && !session.lastTargetId().get().equals(target)) {
            agenda.markContextPending(encounter.get(), tick);
        }
    }

    private Predicate<MobKey> withinFollowRange(MobSession session) {
        var key = session.key();
        return member -> member.equals(key) || reader.withinFollowRange(key, member);
    }

    /**
     * Runs {@code ensureInstalled} and records the result; an {@code Unsupported} session stays at level 4.
     *
     * @return {@code true} if the installation was deferred and must be retried
     */
    private boolean install(MobSession session, long revision) {
        InstallResult result;
        try {
            result = control.ensureInstalled(session.key(), session.profile());
        } catch (RuntimeException exception) {
            fail(session);
            result = new InstallResult.Unsupported("internal error");
        }
        session.recordInstall(result, revision);
        if (result instanceof InstallResult.Unsupported) {
            toBase(session);
        }
        return session.installPending();
    }

    /**
     * Drops one session to level 4 with {@code INTERNAL_ERROR} after an exception (A6, M10.6). With the steps of
     * production the drop is the execution's own ({@link SessionFailure}): the outcome window of the failed tactic
     * closes and the tap hears it. A session that is no longer registered and active only loses its intent.
     */
    private void fail(MobSession session) {
        if (steps instanceof SessionFailure execution && live(session)) {
            execution.failed(session, currentTick);
            return;
        }
        metrics.add(DecisionMetrics.Counter.SESSION_FAILURES, 1);
        metrics.increment(FallbackReason.INTERNAL_ERROR);
        session.fallback(FallbackReason.INTERNAL_ERROR);
        toBase(session);
    }

    private boolean live(MobSession session) {
        return sessions.find(session.key()).filter(found -> found.state() == SessionState.ACTIVE).isPresent();
    }

    private void toBase(MobSession session) {
        session.dropIntent();
        session.invalidateReady();
        if (live(session)) {
            board.publish(session.key(), IntentView.base(session.key().generation()));
        }
    }
}
