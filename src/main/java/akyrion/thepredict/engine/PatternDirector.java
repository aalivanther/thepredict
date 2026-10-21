package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.StyleMemory;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.TickTier;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Step 7 (M26.3). After the group step and before the tick layer it samples kinematics, tokenizes, closes expired
 * windows and publishes the mask. Observe does not touch the body, the candidate set, the signature or a tell extension.
 *
 * <p>The models belong to the encounter ({@link PatternSets}, {@link PatternModels}): one per participant its members
 * fight, empty when the fight starts and dropped when it closes, after the summary (M26.3, A14). A token is read once
 * from the participant's ring and given to every model of that participant. The step walks every active session with
 * a rotating cursor, so the budget leaves out a different tail on each tick. In {@code bias} and {@code full} the mask
 * a session publishes is the one its facts may show ({@link akyrion.thepredict.domain.PatternFactFilter}, M26.7); the
 * local bias reads the raw mask of the model. The effective mode is the lower of the global mode and the profile's
 * {@code Patterns.Mode}.
 */
public final class PatternDirector implements PatternControl {
    private static final UUID NIL = new UUID(0, 0);
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private final PatternInbox inbox;
    private final KinematicBook book;
    private final PatternSettings settings;
    private final PatternModeGate gate;
    private final LongSupplier clock;
    private final PatternBias bias = new PatternBias();
    private final PatternSignals signals = new PatternSignals();
    private final PatternSets sets;
    private MobSession[] visiting = new MobSession[64];
    private SessionRegistry sessions;
    private DecisionMetrics metrics;
    private CombatReader reader;
    private DecisionTap tap = DecisionTap.NONE;
    private PatternDiag diag;
    private EnemyModelSeed modelSeed = EnemyModelSeed.NONE;
    private int minOwnTrials;
    private int cursor;
    private int deferred;
    private long readTick = Long.MIN_VALUE;
    private int reads;

    /**
     * A director with no sessions yet. {@link #bind} attaches the live registries.
     *
     * @param inbox shared ring
     * @param book kinematic words
     * @param settings pattern limits
     * @param gate effective mode; observe does not arm a cast
     * @param clock monotonic clock, the same one the coordinator uses
     */
    public PatternDirector(PatternInbox inbox, KinematicBook book, PatternSettings settings, PatternModeGate gate,
                           LongSupplier clock) {
        this.inbox = inbox;
        this.book = book;
        this.settings = settings;
        this.gate = gate;
        this.clock = clock;
        this.sets = new PatternSets(inbox, book, settings);
    }

    /**
     * Attaches the registries the tick walks.
     *
     * @param sessions active sessions
     * @param encounters open encounters
     * @param metrics pattern counters
     */
    public void bind(SessionRegistry sessions, EncounterRegistry encounters, DecisionMetrics metrics) {
        this.sessions = sessions;
        this.metrics = metrics;
        sets.bind(encounters, metrics);
        encounters.hearArrivals((encounter, participant) -> inbox.admit(participant));
    }

    /**
     * Capture reader used when a kinematic sample is due. Without one the book is left untouched.
     *
     * @param reader the reader
     */
    public void useReader(CombatReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    /**
     * Tap for pattern lines. The default records nothing.
     *
     * @param tap the tap
     */
    public void useTap(DecisionTap tap) {
        this.tap = Objects.requireNonNull(tap, "tap");
        sets.useTap(tap);
    }

    /**
     * The cue port for {@code PREDICTOR_PATTERN_<PATTERN>}: each activation reaches the members of the encounter that
     * target that participant, in {@code bias} and {@code full} only (M26.11, DOC-01). Without it nothing is sent.
     *
     * @param actions the cue port
     */
    public void useCues(CombatActions actions) {
        sets.useCues(Objects.requireNonNull(actions, "actions"),
                session -> session.profile().patterns().effective(gate.effective()));
    }

    /**
     * Air-swing diagnoses flushed at the end of a visit.
     *
     * @param diag the pending diagnosis
     */
    public void useDiag(PatternDiag diag) {
        this.diag = Objects.requireNonNull(diag, "diag");
    }

    /**
     * Seeds models created after this call. {@link EnemyModelSeed#NONE} leaves the pattern step unchanged.
     *
     * @param seed the seed
     * @param ownTrials own trials required when a seed is present
     */
    public void useSeed(EnemyModelSeed seed, int ownTrials) {
        this.modelSeed = seed;
        this.minOwnTrials = ownTrials;
    }

    /**
     * Style of a model that already exists. An unknown player yields an empty style and does not create a model. A
     * participant of two encounters has the same tokens in both models; the older encounter answers.
     *
     * @param target the player
     * @return the fight style
     */
    public Optional<StyleMemory> styleOf(UUID target) {
        var found = sets.find(target);
        return found == null ? Optional.empty() : Optional.of(found.fightStyle());
    }

    /**
     * The model of one enemy, without creating it.
     *
     * @param target the enemy
     * @return the model, or empty
     */
    public Optional<EnemyModel> modelOf(UUID target) {
        return Optional.ofNullable(sets.find(target));
    }

    /**
     * Writes one summary per enemy that already has a model in this encounter. Members are still attached.
     *
     * @param encounter the encounter about to close
     */
    public void summarize(Encounter encounter) {
        sets.summarize(encounter);
    }

    /**
     * Rows of {@code m26_pattern_stats}: each closing encounter hands its rows to {@code sink} (M26.9, JS point 7). The
     * default drops them.
     *
     * @param sink where the rows go, {@code AggregateCache::acceptPatterns} in production
     * @param epochMs wall time supplied by the adapter
     */
    public void useStats(java.util.function.Consumer<java.util.List<PatternStatDelta>> sink, LongSupplier epochMs) {
        sets.useStats(Objects.requireNonNull(sink, "sink"), Objects.requireNonNull(epochMs, "epochMs"));
    }

    /**
     * Closes the patterns of an encounter: the summaries first, then its rows of {@code m26_pattern_stats}, then its
     * models go, and the rings of the participants that no other open encounter keeps (M26.3). Listen after the
     * memory close, which still reads the style.
     *
     * @param encounter the encounter about to close
     */
    public void close(Encounter encounter) {
        sets.summarize(encounter);
        sets.stats(encounter);
        sets.close(encounter);
    }

    /**
     * Forgets a participant that left the server (SP-05): its ring and its kinematic word go, so a slot of the book is
     * never held by a player who quit. The models of its open encounters stay until they close. Server thread only;
     * the composition root calls it from the quit of a player.
     *
     * @param participant the player who quit
     */
    public void forget(UUID participant) {
        inbox.forget(Objects.requireNonNull(participant, "participant"));
        book.forget(participant);
    }

    @Override
    public void step(long tick, long budgetNanos) {
        if (budgetNanos <= 0L) {
            defer();
            return;
        }
        var start = clock.getAsLong();
        var deadline = start + budgetNanos;
        sets.tidy();
        var count = gather();
        var first = count == 0 ? 0 : Math.floorMod(cursor, count);
        for (var visited = 0; visited < count; visited++) {
            if (clock.getAsLong() - start >= budgetNanos) {
                cursor = first + visited;
                defer();
                return;
            }
            visit(visiting[(first + visited) % count], tick, deadline);
        }
        cursor = first;
    }

    /**
     * One session with no deadline. Tests and the timing sample use this.
     *
     * @param session the session
     * @param tick the server tick
     */
    public void visit(MobSession session, long tick) {
        visit(session, tick, Long.MAX_VALUE);
    }

    /**
     * One session that stops when the budget is spent. Leftover tokens stay in the ring.
     *
     * @param session the session
     * @param tick the server tick
     * @param budgetNanos budget of this visit
     */
    void visitWithin(MobSession session, long tick, long budgetNanos) {
        var start = clock.getAsLong();
        visit(session, tick, start + Math.max(0L, budgetNanos));
    }

    /**
     * Steps that waited for a later tick.
     *
     * @return the count
     */
    public int deferred() {
        return deferred;
    }

    /**
     * The word copied onto the model of one enemy.
     *
     * @param target the enemy
     * @return the stored word, {@code 0} when no model has seen the enemy
     */
    public long storedWord(UUID target) {
        var found = sets.find(target);
        return found == null ? 0L : found.kinematics();
    }

    /**
     * Encounters whose models are kept, for the tests of the bound.
     *
     * @return the count
     */
    int encountersTracked() {
        return sets.tracked();
    }

    private void visit(MobSession session, long tick, long deadline) {
        var mode = session.profile().patterns().effective(gate.effective());
        var target = session.lastTargetId().orElse(null);
        var models = target == null ? null : sets.of(session, target);
        var slot = models == null ? -1 : models.admit(target, settings, modelSeed, minOwnTrials, tick);
        if (slot >= 0) {
            observe(session, models, slot, target, tick, deadline);
        }
        var quiet = session.cadence() == TickTier.HOT && !TickSignals.bit(session.tickSignals(), TickSignals.VALID);
        var learned = slot < 0 ? null : models.model(slot);
        publish(session, models, slot, quiet, mode, tick);
        bias.apply(session, learned, settings, mode, quiet, target, tick);
        session.publishStanceView();
        if (diag != null) {
            diag.flush(tap);
        }
    }

    private void observe(MobSession session, PatternModels models, int slot, UUID target, long tick, long deadline) {
        inbox.admit(target);
        var learned = models.model(slot);
        rest(session, models, learned, target, tick);
        var witness = witness(session);
        if (mayRead(target, tick) && signals.sample(reader, book, settings, target, witness, tick, deadline, clock)) {
            reads++;
        }
        learned.kinematics(book.word(target));
        learned.windows().closeExpired((int) tick);
        if (witness != NIL) {
            signals.movement(book, inbox, target, (int) tick);
            signals.edges(session, inbox, learned, target, (int) tick);
        }
        drain(models, slot, target, (int) tick, deadline);
    }

    /**
     * The witness of this visit (M26.1, A14, SP-04): the member itself only while it is hot and its last capture saw
     * its target, otherwise nobody. Without a witness the visit offers no movement token and no shield edge, because
     * the shield bit of the signals is read without a line of sight; {@link PatternSignals} also drops both against an
     * invisible enemy.
     */
    private static UUID witness(MobSession session) {
        return session.cadence() == TickTier.HOT && TickReactions.probeSeen(session) ? session.key().entityId() : NIL;
    }

    /**
     * Closes the rests that have run out (PD-02) and opens the one this session asked for, until the end of its own
     * rest ({@code patterns.restTicks}).
     */
    private void rest(MobSession session, PatternModels models, EnemyModel learned, UUID target, long tick) {
        var changed = learned.endRests(tick);
        if (session.takeRest() && session.patternFocus() >= 0) {
            learned.rest(PATTERNS[session.patternFocus()], session.patternRestUntil());
            changed = true;
        }
        if (changed) {
            sets.emit(learned, target, models.encounter());
        }
    }

    /**
     * Whether a due kinematic read still fits {@code patterns.maxReadsPerTick} (M26.1, A5): the reads of one tick,
     * over every visit, staggered by the rotating cursor. A due read past the cap waits for a later tick and counts
     * {@code PATTERN_READS_DEFERRED}.
     */
    private boolean mayRead(UUID target, long tick) {
        if (readTick != tick) {
            readTick = tick;
            reads = 0;
        }
        if (!signals.due(reader, book, settings, target, tick)) {
            return false;
        }
        if (reads < settings.maxReadsPerTick()) {
            return true;
        }
        if (metrics != null) {
            metrics.add(DecisionMetrics.PatternCounter.PATTERN_READS_DEFERRED, 1);
        }
        return false;
    }

    private int gather() {
        if (sessions == null) {
            return 0;
        }
        var count = sessions.count(SessionState.ACTIVE);
        if (count > visiting.length) {
            visiting = new MobSession[Math.max(count, visiting.length * 2)];
        }
        return sessions.copyActive(visiting);
    }

    private void drain(PatternModels models, int slot, UUID target, int tick, long deadline) {
        while (clock.getAsLong() < deadline) {
            var token = inbox.poll(target);
            if (token < 0) {
                sets.idle(models, slot, tick);
                return;
            }
            sets.feed(target, token, tick, inbox.lastSpear(target));
        }
        if (inbox.size(target) > 0) {
            defer();
        }
    }

    /**
     * Publishes the mask and the forecast of the session's target. In {@code bias} and {@code full} the mask is the one
     * the facts may show, with its hysteresis; {@code off} and {@code observe} keep the raw mask, which only the
     * diagnostics read there.
     */
    private void publish(MobSession session, PatternModels models, int slot, boolean quiet, PatternMode mode,
                         long tick) {
        if (slot < 0 || quiet) {
            session.publishPattern(0, -1);
            return;
        }
        var learned = models.model(slot);
        var shown = models.shown(slot, tick, settings);
        var mask = mode == PatternMode.BIAS || mode == PatternMode.FULL ? shown : learned.activeMask();
        session.publishPattern(mask, learned.forecast());
    }

    private void defer() {
        deferred++;
        if (metrics != null) {
            metrics.add(DecisionMetrics.PatternCounter.PATTERN_STEP_DEFERRED, 1);
        }
    }
}
