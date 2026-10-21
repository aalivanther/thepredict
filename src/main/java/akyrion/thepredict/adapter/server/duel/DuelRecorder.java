package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.DuelRecording;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.SwitchableDecisionTap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The duel recorder of {@code /thepredict duel} (M18.4): a diagnostic function of the plugin, off by default, behind
 * {@code thepredict.debug}. While it records it attaches itself to the {@link SwitchableDecisionTap} of the engine, runs
 * one sample every {@value DuelSummary#SAMPLE_TICKS} ticks and hears the listeners of {@link DuelListener}; everything
 * runs on the server thread (rule T1) except the one writer thread of {@link DuelWriter} (rule T1-bis). Without a
 * recording the tap forwards nothing and every listener returns after one field read.
 *
 * <p>Files go to {@code plugins/ThePredict/duels/<label>-<UTC yyyyMMdd-HHmmss>/}; a label matches
 * {@link DuelRecording.DuelRequest#LABEL} and the resolved folder must stay inside {@code duels/}. The recording stops by
 * itself at {@value #MAX_ROUNDS} rounds, after {@value #MAX_HOURS} hours, when a file reaches 128 MB or the recording
 * 512 MB, or when a write fails: the queue is written, then {@code summary.md} with {@code stopped_by}, the player who
 * started it is told if connected and the log gets {@code Duel recording '<label>' stopped: <limit> reached}. A new
 * recording waits until the writer of the previous one ended, so there is never more than one writer thread, and its
 * writer thread starts last, once everything else of the recording exists. A start whose folder exists is refused. A
 * call of the engine tap that throws detaches the recorder and stops it with {@code stopped_by: tap failure}; a round
 * still running when the recording stops is written as {@code unfinished}.
 */
public final class DuelRecorder implements DuelRecording, DecisionTap {
    /** Most rounds of one recording. */
    public static final int MAX_ROUNDS = 500;
    /** Longest recording, in hours. */
    public static final int MAX_HOURS = 4;
    /** Longest wait for the writer when the plugin shuts down (rule T7). */
    public static final long SHUTDOWN_WAIT_MILLIS = 2000;
    /** Stop reason of {@code /thepredict duel stop}. */
    public static final String STOPPED_BY_COMMAND = "command";
    /** Stop reason of the plugin shutdown. */
    public static final String STOPPED_BY_SHUTDOWN = "shutdown";
    /** Stop reason of a call of the engine tap that threw. */
    public static final String STOPPED_BY_TAP_FAILURE = "tap failure";
    private static final long MAX_NANOS = MAX_HOURS * 3_600_000_000_000L;
    private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    /** Runs a task every few ticks on the server thread until the returned handle is closed. */
    @FunctionalInterface
    public interface Repeater {
        /**
         * Schedules a repeating task.
         *
         * @param periodTicks ticks between two runs
         * @param task the task
         * @return the handle that cancels it
         */
        AutoCloseable every(long periodTicks, Runnable task);
    }

    /** Tells the log, and the player who started a recording if connected, that it stopped by itself. */
    public interface Notifier {
        /**
         * Tells that a recording started from the console stopped.
         *
         * @param line the line for the log
         */
        void stopped(String line);

        /**
         * Tells that a recording started by a player stopped.
         *
         * @param requester player who started it, told if connected
         * @param line the line, also written to the log
         */
        void stopped(UUID requester, String line);
    }

    /**
     * What the recorder needs from the composition root.
     *
     * @param duelsFolder {@code plugins/ThePredict/duels}
     * @param clock server clock of the ticks and of {@code t_nanos}
     * @param utc clock of the UTC time in the folder name
     * @param scene what the recorder reads of the world
     * @param board intent board, for the level, tactic, movement and confidence of each sample
     * @param tap the switchable tap of the engine, which the recorder attaches itself to while it records
     * @param repeater scheduler of the sample task
     * @param notifier receiver of the line of a recording that stopped by itself
     * @param writers starts the writer of a folder
     */
    public record Parts(Path duelsFolder, ServerClock clock, Clock utc, DuelScene scene, IntentBoard board,
                        SwitchableDecisionTap tap, Repeater repeater, Notifier notifier,
                        Function<Path, DuelWriter> writers) {
        /**
         * Validates the parts.
         *
         * @throws NullPointerException if a part is {@code null}
         */
        public Parts {
            Objects.requireNonNull(duelsFolder, "duelsFolder");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(utc, "utc");
            Objects.requireNonNull(scene, "scene");
            Objects.requireNonNull(board, "board");
            Objects.requireNonNull(tap, "tap");
            Objects.requireNonNull(repeater, "repeater");
            Objects.requireNonNull(notifier, "notifier");
            Objects.requireNonNull(writers, "writers");
        }
    }

    private final Parts parts;
    private Optional<Active> active = Optional.empty();
    private volatile boolean recording;
    private Optional<DuelWriter> lastWriter = Optional.empty();
    private DuelStatus last = DuelStatus.IDLE;

    private record Active(DuelSession session, DuelTap tap, DuelCombat combat, AutoCloseable task) {
    }

    /**
     * Creates the recorder; nothing records until {@link #start}.
     *
     * @param parts what the root provides; {@code writers} is {@link DuelWriter#create} on a server (T1-bis: the
     *              writer thread starts with {@code begin()}, not when the recorder is built)
     */
    public DuelRecorder(Parts parts) {
        this.parts = Objects.requireNonNull(parts, "parts");
    }

    @Override
    public DuelStatus start(DuelRequest request) {
        Objects.requireNonNull(request, "request");
        var root = parts.duelsFolder().toAbsolutePath().normalize();
        var directory = root.resolve(request.label() + "-" + FOLDER_TIME.format(parts.utc().instant())).normalize();
        var refusal = refusal(request, root, directory);
        if (refusal.isPresent()) {
            return DuelStatus.refused(request.label(), refusal.get());
        }
        var writer = parts.writers().apply(directory);
        var session = new DuelSession(request, directory, writer, parts.clock(), parts.scene(), parts.board());
        var combat = new DuelCombat(session, parts.board());
        var task = parts.repeater().every(DuelSummary.SAMPLE_TICKS, this::tick);
        active = Optional.of(new Active(session, new DuelTap(session, combat), combat, task));
        parts.tap().attach(this, () -> stop(STOPPED_BY_TAP_FAILURE, true));
        recording = true;
        writer.begin();
        lastWriter = Optional.of(writer);
        last = status();
        return last;
    }

    /** Why a recording cannot start now, checked before anything is created. */
    private Optional<String> refusal(DuelRequest request, Path root, Path directory) {
        if (active.isPresent()) {
            return Optional.of("recording '" + active.get().session().request().label() + "' is running; stop it first");
        }
        if (lastWriter.filter(DuelWriter::running).isPresent()) {
            return Optional.of("the previous recording is still being written; try again");
        }
        if (!parts.scene().worldLoaded(request.worldId())) {
            return Optional.of("the world of the arena is not loaded");
        }
        if (!directory.startsWith(root) || directory.equals(root)) {
            return Optional.of("the folder would leave plugins/ThePredict/duels");
        }
        if (Files.exists(directory)) {
            return Optional.of("the folder " + directory.getFileName() + " already exists; use another label or wait a "
                    + "second");
        }
        return Optional.empty();
    }

    @Override
    public DuelStatus status() {
        return active.map(running -> status(running.session(), true, Optional.empty())).orElse(last);
    }

    @Override
    public DuelStatus stop() {
        return stop(STOPPED_BY_COMMAND, false);
    }

    /**
     * Stops a recording while the plugin shuts down, waiting at most {@link #SHUTDOWN_WAIT_MILLIS} for its writer
     * (rule T7, before the HTTP client closes).
     *
     * @return whether the writer ended in time; {@code true} when nothing recorded
     */
    public boolean shutdown() {
        stop(STOPPED_BY_SHUTDOWN, false);
        return awaitWriter(SHUTDOWN_WAIT_MILLIS);
    }

    /**
     * Waits for the writer of the last recording to end, after a stop.
     *
     * @param millis longest wait
     * @return {@code true} when it ended or none ran
     */
    public boolean awaitWriter(long millis) {
        return lastWriter.map(writer -> writer.awaitStopped(millis)).orElse(true);
    }

    /** Runs one sample and checks every limit; the recorder schedules it every 5 ticks while it records. */
    void tick() {
        var running = active.orElse(null);
        if (running == null) {
            return;
        }
        var session = running.session();
        var writer = session.writer();
        var limit = writer.limitReached().or(() -> writer.failure().map(failure -> "a write failure (" + failure + ")"))
                .or(() -> session.rounds().started() >= MAX_ROUNDS ? Optional.of(MAX_ROUNDS + " rounds")
                        : Optional.empty())
                .or(() -> parts.clock().nanoTime() - session.startNanos() >= MAX_NANOS ? Optional.of(MAX_HOURS + " h")
                        : Optional.empty());
        if (limit.isPresent()) {
            stop(limit.get(), true);
        } else {
            session.sample(parts.clock().currentTick());
        }
    }

    private DuelStatus stop(String reason, boolean notify) {
        if (active.isEmpty()) {
            return last;
        }
        var running = active.get();
        active = Optional.empty();
        recording = false;
        parts.tap().detach();
        try {
            running.task().close();
        } catch (Exception exception) {
            reason = reason + "; the sample task could not be cancelled: " + exception.getClass().getSimpleName();
        }
        var session = running.session();
        session.unfinished(parts.clock().currentTick());
        session.writer().finish(session.summaryText(reason));
        last = status(session, false, Optional.of(reason));
        if (notify) {
            var line = "Duel recording '" + session.request().label() + "' stopped: " + reason + " reached";
            var requester = session.request().requester();
            if (requester.isPresent()) {
                parts.notifier().stopped(requester.get(), line);
            } else {
                parts.notifier().stopped(line);
            }
        }
        return last;
    }

    private static DuelStatus status(DuelSession session, boolean recording, Optional<String> stoppedBy) {
        var writer = session.writer();
        return new DuelStatus(recording, session.request().label(), session.rounds().started(),
                session.rounds().tracked(), writer.dropped(), writer.bytesWritten(), stoppedBy,
                session.directory().toString(), session.summary().requests(), session.summary().inputTokens());
    }

    /**
     * Tells whether a recording runs; the listeners read this {@code volatile} flag first, from any thread, and return
     * at once without one.
     *
     * @return {@code true} while recording
     */
    @Override
    public boolean recording() {
        return recording;
    }

    /**
     * Tells whether a recording follows an entity.
     *
     * @param entityId the entity
     * @return {@code true} for a followed duelist
     */
    public boolean follows(UUID entityId) {
        return active.filter(running -> running.session().rounds().duelist(entityId).isPresent()).isPresent();
    }

    /**
     * A damage dealt by an entity to another, one of them followed, from {@link DuelListener}.
     *
     * @param attacker the damager, or the living shooter of a projectile
     * @param victim the damaged entity
     * @param cause the damage cause
     * @param finalDamage final damage
     * @param healthFraction final damage over the victim's maximum health
     * @param victimBlocking whether the victim blocked with a shield
     */
    public void damaged(UUID attacker, UUID victim, String cause, double finalDamage, double healthFraction,
                        boolean victimBlocking) {
        Objects.requireNonNull(attacker, "attacker");
        active.ifPresent(running -> running.combat().damaged(Optional.of(attacker), victim, cause, finalDamage,
                healthFraction, victimBlocking, parts.clock().currentTick()));
    }

    /**
     * A damage without an entity as its source taken by a followed duelist, from {@link DuelListener}.
     *
     * @param victim the damaged entity
     * @param cause the damage cause
     * @param finalDamage final damage
     * @param healthFraction final damage over the victim's maximum health
     * @param victimBlocking whether the victim blocked with a shield
     */
    public void damaged(UUID victim, String cause, double finalDamage, double healthFraction, boolean victimBlocking) {
        active.ifPresent(running -> running.combat().damaged(Optional.empty(), victim, cause, finalDamage,
                healthFraction, victimBlocking, parts.clock().currentTick()));
    }

    /**
     * An entity died or was removed, from {@link DuelListener}; only a followed duelist matters.
     *
     * @param entityId the entity
     * @param died {@code true} for a death, {@code false} for a removal
     */
    public void gone(UUID entityId, boolean died) {
        active.ifPresent(running -> {
            running.combat().forget(entityId);
            running.session().gone(entityId, died, parts.clock().currentTick());
        });
    }

    /**
     * A {@code MythicSkillEvent} not cancelled, from {@link DuelListener}.
     *
     * @param caster the caster
     * @param skill internal name of the skill
     */
    public void skillEvent(UUID caster, String skill) {
        active.ifPresent(running -> running.combat().skillEvent(caster, skill, parts.clock().currentTick()));
    }

    @Override
    public void questionsPlanned(EncounterId encounter, long sequence, List<QuestionSpec> planned,
                                 Preparation preparation, DecisionDispatcher.Outcome dispatch, CombatFacts facts) {
        active.ifPresent(running -> running.tap().planned(encounter, sequence, planned, preparation, dispatch, facts));
    }

    @Override
    public void requestBody(EncounterId encounter, long sequence, String body) {
        active.ifPresent(running -> running.tap().body(encounter, sequence, body));
    }

    @Override
    public void resultReceived(EncounterId encounter, long sequence, DecisionResult result, long roundTripNanos) {
        active.ifPresent(running -> running.tap().result(encounter, sequence, result, roundTripNanos));
    }

    @Override
    public void answerDiscarded(EncounterId encounter, long sequence, DiscardReason reason) {
        active.ifPresent(running -> running.tap().discarded(encounter, sequence, Optional.empty(), Optional.empty(),
                reason));
    }

    @Override
    public void answerDiscarded(EncounterId encounter, long sequence, QuestionKey question, MobKey mob,
                                DiscardReason reason) {
        active.ifPresent(running -> running.tap().discarded(encounter, sequence, Optional.of(question),
                Optional.of(mob.entityId()), reason));
    }

    @Override
    public void intentEvent(MobKey mob, IntentEvent event) {
        active.ifPresent(running -> running.tap().intent(mob, event));
    }

    @Override
    public void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome) {
        active.ifPresent(running -> running.tap().cast(mob, tactic, choice, outcome));
    }

    @Override
    public void threatChanged(MobKey mob, UUID participant, double amount, String cause) {
        active.ifPresent(running -> running.tap().threat(mob, participant, amount, cause));
    }

    @Override
    public void outcomeClosed(MobKey mob, OutcomeWindow.Summary summary) {
        active.ifPresent(running -> running.tap().outcome(mob, summary));
    }
}
