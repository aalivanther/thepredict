package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.EncounterClosed;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.TapDetail;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Builds immutable {@link RecordEvent}s and offers them (M21.1). It does not touch a file. A level of {@code off}
 * builds nothing. A request body is stored as it was sent: it never carries the key (rule W6). With
 * {@code bodies: dedup}, a body is written in full the first time its hash is seen on a UTC day, so every day of
 * {@code bodies-*} stands alone against the retention, and at most {@link #MAX_SEEN_BODIES} hashes are remembered
 * (A5). The server thread calls it.
 */
public final class RecordTap implements DecisionTap {
    /** Most body hashes remembered for {@code dedup}; the oldest is forgotten and its body written again. */
    public static final int MAX_SEEN_BODIES = 65_536;
    /** Most encounters whose pattern summaries are being counted at once. */
    static final int MAX_COUNTED_ENCOUNTERS = 1024;
    private static final long DAY_MS = 86_400_000L;

    private final RecordLevel level;
    private final RecordBodies bodies;
    private final String run;
    private final Clock clock;
    private final LongSupplier ticks;
    private final LongSupplier nanos;
    private final Consumer<RecordEvent> sink;
    private final Consumer<RecordEvent> bodySink;
    private final Set<String> seenBodies = Collections.newSetFromMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_SEEN_BODIES;
        }
    });
    private final Map<EncounterId, Integer> patternSummaries = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<EncounterId, Integer> eldest) {
            return size() > MAX_COUNTED_ENCOUNTERS;
        }
    };
    private long seenDay = Long.MIN_VALUE;
    private volatile String tag = "";
    private volatile String failure = "";
    private int memoryDigests = -1;

    /**
     * Creates the tap.
     *
     * @param level record level
     * @param bodies body policy
     * @param run run id
     * @param clock wall clock for {@code t_ms}
     * @param ticks server tick
     * @param nanos monotonic time
     * @param sink where events go; must not block
     */
    public RecordTap(RecordLevel level, RecordBodies bodies, String run, Clock clock, LongSupplier ticks,
                     LongSupplier nanos, Consumer<RecordEvent> sink) {
        this(level, bodies, run, clock, ticks, nanos, sink, sink);
    }

    /**
     * Creates the tap. A deduplicated {@code body} line goes to {@code bodySink}, not to {@code sink}.
     *
     * @param level record level
     * @param bodies body policy
     * @param run run id
     * @param clock wall clock for {@code t_ms}
     * @param ticks server tick
     * @param nanos monotonic time
     * @param sink where events go; must not block
     * @param bodySink where the first {@code dedup} body goes; must not block
     */
    public RecordTap(RecordLevel level, RecordBodies bodies, String run, Clock clock, LongSupplier ticks,
                     LongSupplier nanos, Consumer<RecordEvent> sink, Consumer<RecordEvent> bodySink) {
        this.level = level;
        this.bodies = bodies;
        this.run = run;
        this.clock = clock;
        this.ticks = ticks;
        this.nanos = nanos;
        this.sink = sink;
        this.bodySink = bodySink;
    }

    /**
     * Sets the operator tag.
     *
     * @param tag the tag, or empty
     */
    public void tag(String tag) {
        this.tag = tag == null ? "" : tag;
    }

    /**
     * Exception class that stopped this tap, or empty.
     *
     * @return the class name
     */
    public String failure() {
        return failure;
    }

    /**
     * Marks the tap failed. Later events are not built.
     *
     * @param exception the failure
     */
    public void fail(RuntimeException exception) {
        failure = exception.getClass().getName();
    }

    @Override
    public TapDetail detail() {
        if (!failure.isEmpty()) {
            return TapDetail.NONE;
        }
        return switch (level) {
            case OFF -> TapDetail.NONE;
            case DECISIONS -> TapDetail.DECISIONS;
            case FULL -> TapDetail.FULL;
        };
    }

    private String annotationVoi = "";
    private String annotationHint = "";
    private String distillNote = "";
    private String patternFactsNote = "";

    @Override
    public void noteAnnotation(String voi, String jevHint) {
        annotationVoi = voi == null ? "" : voi;
        annotationHint = jevHint == null ? "" : jevHint;
    }

    @Override
    public void noteDistill(String distill, String patternFacts) {
        distillNote = distill == null ? "" : distill;
        patternFactsNote = patternFacts == null ? "" : patternFacts;
    }

    @Override
    public void regimeChanged(MobKey mob, String from, String to, String cause, int dwellTicks, String blockedBy) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("from", JsonText.quote(from));
        fields.put("to", JsonText.quote(to));
        fields.put("cause", JsonText.quote(cause));
        fields.put("dwell_ticks", Integer.toString(dwellTicks));
        fields.put("blocked_by", blockedBy.isEmpty() ? "null" : JsonText.quote(blockedBy));
        offer("regime", fields);
    }

    @Override
    public void distillEvent(String kind, String event, String detailText) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("kind", JsonText.quote(kind));
        fields.put("event", JsonText.quote(event));
        fields.put("detail", JsonText.quote(detailText));
        offer("learn", fields);
    }

    @Override
    public void questionsPlanned(EncounterId encounter, long sequence, List<QuestionSpec> planned,
                                 Preparation preparation, DecisionDispatcher.Outcome dispatch, CombatFacts facts) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("sequence", Long.toString(sequence));
        fields.put("questions", DecisionLines.questions(planned));
        fields.put("dispatch", JsonText.quote(dispatch.name()));
        fields.put("dispatch_detail", preparation instanceof Preparation.Invalid invalid
                ? JsonText.quote(scrub(invalid.detail())) : "null");
        fields.put("sent_options", DecisionLines.sentOptions(preparation));
        fields.put("dropped_questions", DecisionLines.dropped(planned, preparation));
        fields.put("local_mobs", DecisionLines.localMobs(planned, preparation));
        takeAnnotations(fields);
        offer("planned", fields);
    }

    /** Moves the notes of the next {@code planned} line into its fields; they are used once. */
    private void takeAnnotations(LinkedHashMap<String, String> fields) {
        if (!annotationVoi.isEmpty()) {
            fields.put("voi", JsonText.quote(annotationVoi));
        }
        if (!annotationHint.isEmpty()) {
            fields.put("jev_hint", JsonText.quote(annotationHint));
        }
        if (!distillNote.isEmpty()) {
            fields.put("distill", distillNote);
        }
        if (!patternFactsNote.isEmpty()) {
            fields.put("pattern_facts", patternFactsNote);
        }
        annotationVoi = "";
        annotationHint = "";
        distillNote = "";
        patternFactsNote = "";
    }

    @Override
    public void requestBody(EncounterId encounter, long sequence, String body) {
        if (detail() == TapDetail.NONE || bodies == RecordBodies.NONE) {
            return;
        }
        var digest = RunIds.bodySha256(body);
        if (bodies == RecordBodies.ALL) {
            offer("body", bodyFields(encounter, sequence, digest, body));
            return;
        }
        var ref = new LinkedHashMap<String, String>();
        ref.put("encounter", Long.toString(encounter.value()));
        ref.put("sequence", Long.toString(sequence));
        ref.put("chars", Integer.toString(body.length()));
        ref.put("body_sha256", JsonText.quote(digest));
        offer("body_ref", ref);
        if (bodies == RecordBodies.DEDUP && firstToday(digest)) {
            bodySink.accept(new RecordEvent("body", run, clock.millis(), nanos.getAsLong(), ticks.getAsLong(), tag,
                    bodyFields(encounter, sequence, digest, body)));
        }
    }

    private static LinkedHashMap<String, String> bodyFields(EncounterId encounter, long sequence, String digest,
                                                            String body) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("sequence", Long.toString(sequence));
        fields.put("chars", Integer.toString(body.length()));
        fields.put("body_sha256", JsonText.quote(digest));
        fields.put("body", JsonText.quote(body));
        return fields;
    }

    /** Whether a body hash is new on this UTC day. The remembered hashes are forgotten when the day changes. */
    private boolean firstToday(String digest) {
        var day = Math.floorDiv(clock.millis(), DAY_MS);
        if (day != seenDay) {
            seenBodies.clear();
            seenDay = day;
        }
        return seenBodies.add(digest);
    }

    @Override
    public void resultReceived(EncounterId encounter, long sequence, DecisionResult result, long roundTripNanos) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("sequence", Long.toString(sequence));
        switch (result) {
            case DecisionResult.Answered answered -> {
                fields.put("outcome", JsonText.quote("answered"));
                fields.put("model", JsonText.quote(answered.effectiveModel()));
                fields.put("input_tokens", Integer.toString(answered.inputTokens()));
                fields.put("source", JsonText.quote(answered.source().name().toLowerCase(java.util.Locale.ROOT)));
                fields.put("round_trip_ms", JsonText.number(roundTripNanos / 1_000_000.0));
                fields.put("answers", DecisionLines.answers(answered));
                fields.put("fits", DecisionLines.fits(answered));
            }
            case DecisionResult.Failed failed -> {
                fields.put("outcome", JsonText.quote("failed"));
                fields.put("failure", JsonText.quote(failed.kind().name()));
                fields.put("source", JsonText.quote("live"));
            }
        }
        offer("result", fields);
    }

    @Override
    public void answerDiscarded(EncounterId encounter, long sequence, DiscardReason reason) {
        acceptance(encounter, sequence, reason.name(), "");
    }

    @Override
    public void answerDiscarded(EncounterId encounter, long sequence, QuestionKey question, MobKey mob,
                                DiscardReason reason) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("sequence", Long.toString(sequence));
        fields.put("decision", JsonText.quote("discarded"));
        fields.put("question", JsonText.quote(question.value()));
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("reason", JsonText.quote(reason.name()));
        offer("acceptance", fields);
    }

    @Override
    public void intentEvent(MobKey mob, IntentEvent event) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("kind", JsonText.quote(event.kind().name()));
        fields.put("tactic", JsonText.quote(event.tactic().value()));
        fields.put("level", JsonText.quote(event.to().name()));
        if (event.kind() == IntentEventKind.LOCAL_PLANNED) {
            fields.put("encounter", "null");
            fields.put("sequence", "-1");
            offer("local", fields);
        } else {
            offer("intent", fields);
        }
    }

    @Override
    public void outcomeClosed(MobKey mob, OutcomeWindow.Summary summary) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("level", JsonText.quote(summary.level().name()));
        fields.put("tactic", JsonText.quote(summary.tactic().value()));
        fields.put("movement", JsonText.quote(summary.movement().name()));
        fields.put("dealt", JsonText.number(summary.dealt()));
        fields.put("taken", JsonText.number(summary.taken()));
        fields.put("reached", summary.reached() ? "true" : "false");
        fields.put("casts", Integer.toString(summary.casts()));
        fields.put("hits", Integer.toString(summary.hits()));
        fields.put("mob_died", summary.mobDied() ? "true" : "false");
        fields.put("target_died", summary.targetDied() ? "true" : "false");
        fields.put("effect", JsonText.number(summary.effect()));
        fields.put("start_band", JsonText.quote(summary.startBand().map(Enum::name).orElse("")));
        fields.put("target_kind", JsonText.quote(summary.targetKind().map(Enum::name).orElse("")));
        fields.put("reactions", "null");
        fields.put("micro_ticks", "null");
        fields.put("ready_casts", Integer.toString(summary.readyCasts()));
        fields.put("start_tick", Long.toString(summary.startTick()));
        offer("outcome", fields);
    }

    @Override
    public void patternChanged(EncounterId encounter, java.util.UUID participant,
                               akyrion.thepredict.domain.PatternChange change) {
        if (detail().ordinal() < TapDetail.DECISIONS.ordinal()) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("participant", JsonText.quote(participant.toString()));
        fields.put("participant_kind", "null");
        fields.put("pattern", JsonText.quote(change.pattern().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("from", "null");
        fields.put("to", JsonText.quote(change.state().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("cause", JsonText.quote(change.cause().name()));
        fields.put("confidence_millis", "null");
        fields.put("evidence_s", "null");
        fields.put("evidence_f", "null");
        fields.put("trials", "null");
        offer("pattern", fields);
    }

    /**
     * A cast motivated by a pattern whose participant the caller does not name: the line says {@code null} for it,
     * never the mob.
     */
    @Override
    public void patternExploited(MobKey mob, akyrion.thepredict.domain.PatternExploit exploit) {
        exploited(mob, "null", exploit, "", "", 0);
    }

    @Override
    public void patternExploited(MobKey mob, java.util.UUID participant,
                                 akyrion.thepredict.domain.PatternExploit exploit, String choice, String tactic,
                                 int quotaUsed) {
        exploited(mob, JsonText.quote(participant.toString()), exploit, choice, tactic, quotaUsed);
    }

    private void exploited(MobKey mob, String participant, akyrion.thepredict.domain.PatternExploit exploit,
                           String choice, String tactic, int quotaUsed) {
        if (detail() != TapDetail.FULL) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("participant", participant);
        fields.put("pattern", JsonText.quote(exploit.pattern().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("bias", JsonText.quote(exploit.bias().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("choice", JsonText.quote(choice));
        fields.put("tactic", JsonText.quote(tactic));
        fields.put("result", JsonText.quote(exploit.landed() ? "landed" : "miss"));
        fields.put("source", JsonText.quote(exploit.source()));
        fields.put("quota_used", Integer.toString(quotaUsed));
        offer("pattern_exploit", fields);
    }

    @Override
    public void patternDiag(java.util.UUID participant, String weaponClass, int chargeMillis, boolean leftClickAir,
                            String blockedBy, boolean wouldToken) {
        if (detail() != TapDetail.FULL) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("participant", JsonText.quote(participant.toString()));
        fields.put("kind", JsonText.quote("AIR_SWING"));
        fields.put("weapon_class", JsonText.quote(weaponClass));
        fields.put("charge_millis", Integer.toString(chargeMillis));
        fields.put("left_click_air", leftClickAir ? "true" : "false");
        fields.put("blocked_by", JsonText.quote(blockedBy));
        fields.put("would_token", wouldToken ? "true" : "false");
        offer("pattern_diag", fields);
    }

    @Override
    public void patternSummary(EncounterId encounter, java.util.UUID participant,
                               akyrion.thepredict.domain.EnemyProfileSummary summary) {
        if (detail().ordinal() < TapDetail.DECISIONS.ordinal()) {
            return;
        }
        patternSummaries.merge(encounter, 1, Integer::sum);
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("participant", JsonText.quote(participant.toString()));
        fields.put("participant_kind", "null");
        fields.put("species", "null");
        var encoded = akyrion.thepredict.domain.EnemyProfileCodec.encode(summary);
        fields.put("summary_v", Integer.toString(akyrion.thepredict.domain.EnemyProfileSummary.VERSION));
        fields.put("summary_bytes", Integer.toString(encoded.length));
        fields.put("summary", akyrion.thepredict.domain.EnemyProfileCodec.toJson(summary));
        offer("pattern_summary", fields);
    }

    @Override
    public void encounterClosed(EncounterId encounter, EncounterClosed closed) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("world", JsonText.quote(closed.worldId().toString()));
        fields.put("reason", JsonText.quote(closed.reason().name()));
        fields.put("start_tick", Long.toString(closed.startTick()));
        fields.put("end_tick", Long.toString(closed.endTick()));
        fields.put("ttk", Long.toString(closed.endTick() - closed.startTick()));
        fields.put("mobs_died", Integer.toString(closed.mobsDied()));
        fields.put("participants_died", Integer.toString(closed.participantsDied()));
        fields.put("memory_digests", memoryDigests < 0 ? "null" : Integer.toString(memoryDigests));
        fields.put("pattern_summaries", Integer.toString(patternSummaries.getOrDefault(encounter, 0)));
        fields.put("group_summary", JsonText.quote(closed.groupSummary()));
        memoryDigests = -1;
        patternSummaries.remove(encounter);
        offer("encounter_close", fields);
    }

    /**
     * The digest count the next {@code encounter_close} line will carry. Without it the line says {@code null}.
     *
     * @param count digests of the encounter
     */
    public void noteMemoryDigests(int count) {
        memoryDigests = count;
    }

    @Override
    public void memoryAdmitted(EncounterId encounter, java.util.UUID subject,
                               akyrion.thepredict.domain.MemoryAdmission admission) {
        if (detail().ordinal() < TapDetail.DECISIONS.ordinal()) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("subject", JsonText.quote(subject.toString()));
        fields.put("cause", JsonText.quote(admission.cause().name()));
        fields.put("start_rung", Integer.toString(admission.startRung()));
        fields.put("mode", JsonText.quote(admission.mode().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("seeded", Integer.toString(admission.seededPatterns()));
        offer("memory_seed", fields);
    }

    @Override
    public void memoryApplied(java.util.UUID subject, akyrion.thepredict.domain.MemoryUpdate update) {
        if (detail() != TapDetail.FULL) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("subject", JsonText.quote(subject.toString()));
        fields.put("style_breaks", Integer.toString(update.styleBreaks()));
        fields.put("anchor_changed", update.anchorChanged() ? "true" : "false");
        fields.put("bytes_before", Integer.toString(update.bytesBefore()));
        fields.put("bytes_after", Integer.toString(update.bytesAfter()));
        fields.put("digests", Integer.toString(update.digests()));
        offer("memory_update", fields);
    }

    /**
     * A load finished.
     *
     * @param subject the player
     * @param state the cache state name
     */
    public void memoryLoad(java.util.UUID subject, String state) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("subject", JsonText.quote(subject.toString()));
        fields.put("state", JsonText.quote(state));
        offer("memory_load", fields);
    }

    /**
     * An administrator command finished.
     *
     * @param action the command
     * @param detail the result
     */
    public void memoryAdmin(String action, String detail) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("action", JsonText.quote(action));
        fields.put("detail", JsonText.quote(detail));
        offer("memory_admin", fields);
    }

    @Override
    public void answerCache(EncounterId encounter, long sequence,
                            akyrion.thepredict.domain.AnswerCacheEvent event) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("sequence", Long.toString(sequence));
        fields.put("mode", JsonText.quote(event.mode().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("hit", event.hit() ? "true" : "false");
        fields.put("age_ticks", Long.toString(event.ageTicks()));
        fields.put("agree_best", event.agreeBest() ? "true" : "false");
        fields.put("confidence_delta", JsonText.number(event.confidenceDelta()));
        fields.put("tokens_saved", Long.toString(event.tokensSaved()));
        offer("cache", fields);
    }

    @Override
    public void castAttempted(MobKey mob, akyrion.thepredict.domain.TacticId tactic,
                              akyrion.thepredict.domain.ChoiceId choice,
                              akyrion.thepredict.domain.ExecutionOutcome outcome, boolean readyCast) {
        if (detail() != TapDetail.FULL) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("tactic", JsonText.quote(tactic.value()));
        fields.put("choice", JsonText.quote(choice.value()));
        fields.put("outcome", JsonText.quote(outcome.name()));
        fields.put("ready_cast", readyCast ? "true" : "false");
        offer("cast", fields);
    }

    @Override
    public void threatChanged(MobKey mob, java.util.UUID participant, double amount, String cause) {
        if (detail() != TapDetail.FULL) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("participant", JsonText.quote(participant.toString()));
        fields.put("amount", JsonText.number(amount));
        fields.put("cause", JsonText.quote(scrub(cause)));
        offer("threat", fields);
    }

    @Override
    public void reactionFired(MobKey mob, akyrion.thepredict.domain.Reaction reaction) {
        if (detail() != TapDetail.FULL) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("reaction", JsonText.quote(reaction.name()));
        offer("reaction", fields);
    }

    @Override
    public void microChanged(MobKey mob, akyrion.thepredict.domain.MicroBehaviour from,
                             akyrion.thepredict.domain.MicroBehaviour to) {
        if (detail() != TapDetail.FULL) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("from", JsonText.quote(from.name()));
        fields.put("to", JsonText.quote(to.name()));
        offer("micro", fields);
    }

    @Override
    public void optionsPruned(EncounterId encounter, long sequence, MobKey mob,
                              akyrion.thepredict.domain.OptionPruning.PruneResult pruning) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("sequence", Long.toString(sequence));
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("removed", DecisionLines.removed(pruning));
        fields.put("undone", pruning.vetoesUndone() ? "true" : "false");
        offer("pruned", fields);
    }

    /**
     * A simulator round. {@code ttk} is the same duration the encounter close reports.
     *
     * @param seed the round seed
     * @param ttk ticks to the kill
     */
    public void round(long seed, long ttk) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("seed", Long.toString(seed));
        fields.put("ttk", Long.toString(ttk));
        offer("round", fields);
    }

    /**
     * A writer statistic line.
     *
     * @param dropped events dropped
     * @param bytes bytes written
     */
    public void writerStats(long dropped, long bytes) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("dropped", Long.toString(dropped));
        fields.put("bytes", Long.toString(bytes));
        offer("writer_stats", fields);
    }

    /**
     * The {@code run_start} line (M21.1), with its fields in the order of the schema. The writer writes
     * {@code run_stop} itself as it closes.
     *
     * @param pluginVersion the plugin version
     * @param model the configured model; only a versioned id is written, see {@link RunIds#modelLabel}
     * @param configSha256 the fingerprint of {@code config.yml} without its {@code apiKey}
     * @param questionDetail the configured {@code decision.questionDetail}
     * @param answerCache the configured {@code decision.answerCache}
     */
    public void runStart(String pluginVersion, String model, String configSha256, QuestionDetail questionDetail,
                         AnswerCacheMode answerCache) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("plugin_version", JsonText.quote(pluginVersion));
        fields.put("model", JsonText.quote(RunIds.modelLabel(model)));
        fields.put("question_detail", JsonText.quote(questionDetail.name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("config_sha256", JsonText.quote(configSha256));
        fields.put("records_level", JsonText.quote(level.name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("bodies", JsonText.quote(bodies.name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("answer_cache", JsonText.quote(answerCache.name().toLowerCase(java.util.Locale.ROOT)));
        offer("run_start", fields);
    }

    private void acceptance(EncounterId encounter, long sequence, String reason, String mob) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", Long.toString(encounter.value()));
        fields.put("sequence", Long.toString(sequence));
        fields.put("decision", JsonText.quote("discarded"));
        fields.put("reason", JsonText.quote(reason));
        if (!mob.isEmpty()) {
            fields.put("mob", JsonText.quote(mob));
        }
        offer("acceptance", fields);
    }

    @Override
    public void fightEvent(akyrion.thepredict.domain.MobKey mob, akyrion.thepredict.domain.FightEventKind event,
                           long elapsedTicks, int rung, String mode, int targetSeconds,
                           akyrion.thepredict.engine.FightFacts facts) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var line = java.util.Objects.requireNonNull(facts, "facts");
        var fields = new LinkedHashMap<String, String>();
        fields.put("encounter", JsonText.quote(line.encounter()));
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("profile", JsonText.quote(line.profile()));
        fields.put("target", JsonText.quote(line.target()));
        fields.put("target_kind", JsonText.quote(line.targetKind()));
        fields.put("event", JsonText.quote(event.name()));
        fields.put("elapsed_ticks", Long.toString(elapsedTicks));
        fields.put("target_health", Double.toString(line.targetHealth()));
        fields.put("rung", Integer.toString(rung));
        fields.put("mode", JsonText.quote(mode));
        fields.put("target_seconds", Integer.toString(targetSeconds));
        fields.put("world_difficulty", JsonText.quote(line.worldDifficulty()));
        fields.put("natural_regeneration", line.naturalRegeneration() ? "true" : "false");
        fields.put("party_size", Integer.toString(line.partySize()));
        offer("fight", fields);
    }

    @Override
    public void pressureChanged(akyrion.thepredict.domain.MobKey mob, int from, int to,
                                akyrion.thepredict.domain.PressureCause cause, double projectedSeconds,
                                int targetSeconds, int deferredTicks) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("from", Integer.toString(from));
        fields.put("to", Integer.toString(to));
        fields.put("cause", JsonText.quote(cause.name()));
        fields.put("ttk_hat_seconds", Double.toString(projectedSeconds));
        fields.put("target_seconds", Integer.toString(targetSeconds));
        fields.put("deferred_ticks", Integer.toString(deferredTicks));
        offer("pressure", fields);
    }

    @Override
    public void stanceChanged(MobKey mob, akyrion.thepredict.domain.StanceChange change) {
        if (detail() != TapDetail.FULL) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("from", JsonText.quote(change.from().name()));
        fields.put("to", JsonText.quote(change.to().name()));
        fields.put("base", JsonText.quote(change.base().name()));
        fields.put("action", JsonText.quote(change.action().name()));
        fields.put("enraged", change.enraged() ? "true" : "false");
        fields.put("cause", JsonText.quote(change.cause().name()));
        fields.put("dwell_ticks", Long.toString(change.dwellTicks()));
        fields.put("native_mode", JsonText.quote(change.nativeMode().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("native_stance", JsonText.quote(change.nativeStance()));
        fields.put("variable_written", change.variableWritten() ? "true" : "false");
        fields.put("native_written", change.nativeWritten() ? "true" : "false");
        offer("stance", fields);
    }

    @Override
    public void hazardEvent(MobKey mob, akyrion.thepredict.domain.HazardRecord record) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        fields.put("damage", Integer.toString(record.damage()));
        fields.put("died", record.died() ? "true" : "false");
        fields.put("ticks", Integer.toString(record.ticks()));
        fields.put("kind", JsonText.quote(record.kind().name()));
        fields.put("outcome", JsonText.quote(record.outcome().name()));
        offer("hazard", fields);
    }

    @Override
    public void telegraphClosed(MobKey mob, akyrion.thepredict.domain.TelegraphSummary summary) {
        if (detail() == TapDetail.NONE) {
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("mob", JsonText.quote(mob.entityId().toString()));
        tellIdentity(fields, summary);
        tellTiming(fields, summary);
        tellOutcome(fields, summary);
        offer("telegraph", fields);
    }

    private static void tellIdentity(LinkedHashMap<String, String> fields,
                                     akyrion.thepredict.domain.TelegraphSummary summary) {
        fields.put("profile", JsonText.quote(summary.profileId()));
        fields.put("choice", JsonText.quote(summary.choiceId()));
        fields.put("danger", JsonText.quote(summary.danger().name()));
        fields.put("answer", JsonText.quote(summary.answer().name()));
        fields.put("target", summary.target().isEmpty() ? JsonText.quote("") : JsonText.quote(summary.target()));
        fields.put("target_kind", JsonText.quote(summary.targetKind()));
    }

    private static void tellTiming(LinkedHashMap<String, String> fields,
                                   akyrion.thepredict.domain.TelegraphSummary summary) {
        fields.put("start_tick", Long.toString(summary.startTick()));
        fields.put("lead_ticks", Integer.toString(summary.leadTicks()));
        fields.put("windup_ticks", Integer.toString(summary.windupTicks()));
        fields.put("telegraph_ticks", Integer.toString(summary.telegraphTicks()));
        fields.put("min_ticks", Integer.toString(summary.minTicks()));
        fields.put("short_windup", JsonText.quote(summary.shortWindUp().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("release_deferred", summary.releaseDeferred() ? "true" : "false");
    }

    private static void tellOutcome(LinkedHashMap<String, String> fields,
                                    akyrion.thepredict.domain.TelegraphSummary summary) {
        fields.put("end", JsonText.quote(summary.end().name()));
        fields.put("result", JsonText.quote(summary.result().name()));
        fields.put("damage_ratio", JsonText.number(summary.damageRatio()));
        fields.put("recovery_ticks", Integer.toString(summary.recoveryTicks()));
        fields.put("punished", summary.punished() ? "true" : "false");
        fields.put("punish_damage_ratio", JsonText.number(summary.punishDamageRatio()));
        fields.put("cue_channels", Integer.toString(summary.cueChannels()));
        fields.put("rung", Integer.toString(summary.rung()));
    }

    private void offer(String type, LinkedHashMap<String, String> fields) {
        sink.accept(new RecordEvent(type, run, clock.millis(), nanos.getAsLong(), ticks.getAsLong(), tag, fields));
    }

    /**
     * Hides an {@code Authorization} header or a bearer token in a free diagnostic text, word by word: the rest of the
     * text stays. Request bodies are not scrubbed: they never carry the key and must keep their hash.
     */
    private static String scrub(String value) {
        var text = value.replace("Authorization", "redacted");
        var lower = text.toLowerCase(java.util.Locale.ROOT);
        var out = new StringBuilder(text.length());
        var from = 0;
        var at = lower.indexOf("bearer ");
        while (at >= 0) {
            var start = at + "bearer ".length();
            var end = start;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != '"') {
                end++;
            }
            out.append(text, from, start).append("redacted");
            from = end;
            at = lower.indexOf("bearer ", end);
        }
        return out.append(text, from, text.length()).toString();
    }
}
