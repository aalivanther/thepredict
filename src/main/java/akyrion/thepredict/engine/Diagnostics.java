package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.ShortWindUp;
import akyrion.thepredict.domain.StanceState;
import akyrion.thepredict.domain.PlayMode;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TargetRelation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Builds the read-only reports of {@code /thepredict status} and {@code inspect} from what the engine already keeps
 * (01 §8, M12.1, M12.2), and lists the goals of a mob for {@code /thepredict debug goals} (M12.3). Server thread only
 * (rule T1); it changes nothing, starts no request and reads the server only through the ports {@link MobControl} and
 * {@link CombatReader}.
 */
public final class Diagnostics {
    private final Sources sources;
    private final StatusAssembler status;
    private Supplier<Optional<StorageStatus>> storageStatus = Optional::empty;
    private PatternModeGate patterns;
    private PatternDirector patternDirector;
    private AggregateCache aggregates;

    /**
     * Creates the diagnostics over the components of the coordinator.
     *
     * @param sources what the reports read
     */
    public Diagnostics(Sources sources) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.status = new StatusAssembler(sources);
    }

    /**
     * Reports the whole engine without a world budget, for the console.
     *
     * @return the report
     */
    public StatusReport status() {
        var report = status.status();
        if (patterns != null) {
            report = report.withPatterns(new StatusReport.Patterns(patterns.effective().name().toLowerCase(java.util.Locale.ROOT),
                    patterns.cause()));
        }
        var block = storageStatus.get();
        return block.isPresent() ? report.withStorage(block.get()) : report;
    }

    /**
     * Supplies the pattern mode of {@code status}.
     *
     * @param gate the gate
     */
    public void hearPatterns(PatternModeGate gate) {
        patterns = Objects.requireNonNull(gate, "gate");
    }

    /**
     * Supplies the live models of {@code inspect patterns}.
     *
     * @param director the pattern step
     */
    public void hearPatternDirector(PatternDirector director) {
        patternDirector = Objects.requireNonNull(director, "director");
    }

    /**
     * One inspect block taken from the live model. Empty when the mob or the model is absent.
     *
     * @param entityId the mob
     * @return the formatted block
     */
    public Optional<PatternReport.PatternSnapshot> patternInspect(UUID entityId) {
        var session = sources.sessions().find(Objects.requireNonNull(entityId, "entityId")).orElse(null);
        if (session == null || patternDirector == null) {
            return Optional.empty();
        }
        var target = session.lastTargetId().orElse(null);
        if (target == null) {
            return Optional.empty();
        }
        var learned = patternDirector.modelOf(target).orElse(null);
        return learned == null ? Optional.empty() : Optional.of(PatternReport.line(session, learned));
    }

    /**
     * Pattern numbers of one session for {@code inspect patterns}.
     *
     * @param entityId the mob
     * @return empty when the mob has no session
     */
    public Optional<PatternView> patternView(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId")).map(session ->
                new PatternView(session.patternMask(), session.patternForecast()));
    }

    /**
     * Mask and forecast published on a session.
     *
     * @param mask active bits
     * @param forecast action ordinal, or {@code -1}
     */
    public record PatternView(int mask, int forecast) {
    }

    /**
     * Supplies the Storage block of {@code status}. Absent when the supplier returns empty.
     *
     * @param supplier the block, read on the server thread
     */
    public void hearStorage(Supplier<Optional<StorageStatus>> supplier) {
        storageStatus = Objects.requireNonNull(supplier, "supplier");
    }

    /**
     * Supplies {@code outcome_stats} rows for {@code inspect}.
     *
     * @param cache the aggregate cache
     */
    public void hearAggregates(AggregateCache cache) {
        aggregates = Objects.requireNonNull(cache, "cache");
    }

    /**
     * Reports the whole engine with the budget of one world, for a player.
     *
     * @param worldId world of the player who asked
     * @return the report
     */
    public StatusReport status(UUID worldId) {
        return status().withWorld(status.world(Objects.requireNonNull(worldId, "worldId")));
    }

    /**
     * Reports one session.
     *
     * @param entityId UUID of the mob
     * @return the report, empty when the mob has no session
     */
    public Optional<SessionReport> session(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId")).map(this::report);
    }

    /**
     * Packed tick signals of a session (M20.2). Empty when the mob has no session.
     *
     * @param entityId UUID of the mob
     * @return the last packed word
     */
    public OptionalLong tickSignals(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId"))
                .map(session -> OptionalLong.of(session.tickSignals()))
                .orElse(OptionalLong.empty());
    }

    /**
     * Tells whether a mob has a session, for {@code nearest}.
     *
     * @param entityId UUID of an entity
     * @return {@code true} if the entity is a mob with a session
     */
    public boolean bound(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId")).isPresent();
    }

    /**
     * Difficulty block of one session (M22.11). The rung is {@code -1} outside player mode.
     *
     * @param entityId UUID of the mob
     * @return the view, empty when the mob has no session
     */
    public Optional<DifficultyView> difficultyView(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId")).map(session -> {
            var difficulty = session.difficulty();
            var tick = sources.clock().currentTick();
            var rung = difficulty.play() == PlayMode.PLAYER ? difficulty.rung() : -1;
            return new DifficultyView(difficulty.presetName(), difficulty.entropy(), difficulty.reactionNow(tick),
                    difficulty.adaptSeconds(tick), rung);
        });
    }

    /**
     * Whether status should warn that a short wind-up is immediate (M23.4).
     *
     * @return whether a live profile or a counted opt-out says so
     */
    public boolean immediateWindUp() {
        if (sources.metrics().total(DecisionMetrics.TellCounter.UNWARNED_BY_OPT_OUT) > 0) {
            return true;
        }
        for (var session : sources.sessions().all()) {
            if (session.profile().telegraph().shortWindUp() == ShortWindUp.IMMEDIATE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells opened in the current 10-minute self-abort window.
     *
     * @return the count
     */
    public long windowOpened() {
        return sources.metrics().windowOpened();
    }

    /**
     * Self-aborts in the current 10-minute window.
     *
     * @return the count
     */
    public long windowAborted() {
        return sources.metrics().windowAborted();
    }

    /**
     * Stance section and the live stance of one session (M23.8).
     *
     * @param entityId UUID of the mob
     * @return the lines, empty when the mob has no session
     */
    public List<String> stanceLines(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId")).map(session -> {
            var stances = session.profile().stances();
            var visible = CombatStance.visible(session.baseStance().ordinal(), session.actionStance().ordinal());
            return List.of("native " + stances.nativeMode().name().toLowerCase(java.util.Locale.ROOT),
                    "minDwell " + stances.minDwellTicks(),
                    "hysteresis " + stances.hysteresisCycles(),
                    "describe " + stances.describe(),
                    "stance " + visible.name().toLowerCase(java.util.Locale.ROOT),
                    "enraged " + StanceState.enraged(session.stanceState()),
                    "native_stance " + session.authorMirror());
        }).orElse(List.of());
    }

    /**
     * Group section of one session (M25).
     *
     * @param entityId UUID of the mob
     * @return the lines, empty when the mob has no session
     */
    public List<String> groupLines(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId"))
                .map(GroupInspect::lines).orElse(List.of());
    }

    /**
     * Telegraph section and one line per choice (M23.3).
     *
     * @param entityId UUID of the mob
     * @return the lines, empty when the mob has no session
     */
    public List<String> telegraphLines(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId")).map(session -> {
            var settings = session.profile().telegraph();
            var lines = new java.util.ArrayList<String>();
            lines.add("againstPlayers " + settings.againstPlayers());
            lines.add("againstEntities " + settings.againstEntities());
            lines.add("commitment " + settings.commitmentPercent());
            lines.add("entropyCommitment " + settings.entropyCommitmentPercent());
            lines.add("shortWindUp " + settings.shortWindUp().name().toLowerCase(java.util.Locale.ROOT));
            for (var choice : session.profile().choices().values()) {
                var danger = DangerRules.classify(choice);
                var answer = DangerRules.answer(choice);
                var recovery = choice.telegraph().recoveryTicks().orElse(settings.defaultRecoveryTicks());
                lines.add(choice.id() + " danger " + danger.name().toLowerCase(java.util.Locale.ROOT)
                        + " answer " + answer.name().toLowerCase(java.util.Locale.ROOT)
                        + " recovery " + recovery
                        + " signature " + choice.nativeFacts().cueSignature()
                        + " channels " + choice.nativeFacts().cueChannels());
            }
            return List.copyOf(lines);
        }).orElse(List.of());
    }

    /**
     * Forces a debug rung, or clears it when {@code rung} is negative (M22.11).
     *
     * @param entityId UUID of the mob
     * @param rung rung 0 to 8, or {@code -1} for auto
     * @return whether the mob has a session
     */
    public boolean forcePressure(UUID entityId, int rung) {
        var found = sources.sessions().find(Objects.requireNonNull(entityId, "entityId"));
        found.ifPresent(session -> session.difficulty().forceRung(rung));
        return found.isPresent();
    }

    /**
     * What {@code /thepredict eval} prints for one session.
     *
     * @param preset effective preset name
     * @param entropy whether entropy is on
     * @param reactionTicks {@code r(t)}
     * @param adaptSeconds {@code c(t)}
     * @param rung current rung, or {@code -1} on the bench
     */
    public record DifficultyView(String preset, boolean entropy, int reactionTicks, double adaptSeconds, int rung) {
    }

    /**
     * Lists the goals of a mob, with or without a session. {@code MobControl.describeGoals} resolves the mob by its UUID,
     * so a mob without a session is read with generation 0.
     *
     * @param entityId UUID of the mob
     * @return at most 40 goals, empty when the entity is not an active MythicMobs mob
     */
    public List<GoalSummary> goals(UUID entityId) {
        return sources.control().describeGoals(key(entityId));
    }

    /**
     * Reads the movement progress of a mob, with or without a session.
     *
     * @param entityId UUID of the mob
     * @return the progress; nothing running for an entity that is not an active MythicMobs mob
     */
    public MovementProgress progress(UUID entityId) {
        return sources.control().progress(key(entityId));
    }

    private MobKey key(UUID entityId) {
        return sources.sessions().find(Objects.requireNonNull(entityId, "entityId")).map(MobSession::key)
                .orElseGet(() -> new MobKey(entityId, 0));
    }

    private SessionReport report(MobSession session) {
        var now = sources.clock().nanoTime();
        var current = session.intent().filter(intent -> !intent.expired(now));
        var encounter = session.encounter().flatMap(sources.encounters()::find);
        var tick = sources.clock().currentTick();
        var threatAdded = encounter.map(found -> found.threat().pointsFor(session.key(), tick)).orElse(0.0);
        var facts = encounter.flatMap(Encounter::lastFacts);
        var built = new SessionReport(session.key(), session.profile().mythicMobId(), session.profile().id(),
                session.state(), current.map(TacticalIntent::level).orElse(ControlLevel.BASE), session.encounter(),
                current.map(intent -> intent(intent, session.debugIntent(), now)), session.lowConfidenceStreak(),
                session.noProgressStreak(), session.installPending(), session.installedRevision(),
                sources.control().describeGoals(session.key()), sources.control().progress(session.key()),
                session.lastMobFacts(), facts.flatMap(found -> targetFacts(session, found)),
                facts.map(found -> found.participants().size()).orElse(0),
                facts.map(CombatFacts::recentEvents).orElse(List.of()), facts.map(CombatFacts::combatSeconds).orElse(0),
                sources.reader().threatTable(session.key()), threatAdded, session.lastFallbackReason(),
                session.lastOutcome(), purposes(session), session.recentOutcomes(), session.pruned());
        return aggregateLine(session, current).map(built::withAggregate).orElse(built);
    }

    private Optional<String> aggregateLine(MobSession session, Optional<akyrion.thepredict.domain.TacticalIntent> current) {
        if (aggregates == null) {
            return Optional.empty();
        }
        var tactic = current.map(akyrion.thepredict.domain.TacticalIntent::tactic);
        var movement = current.map(akyrion.thepredict.domain.TacticalIntent::movement);
        var level = current.map(akyrion.thepredict.domain.TacticalIntent::level).orElse(ControlLevel.BASE);
        if (tactic.isEmpty() || movement.isEmpty()) {
            return Optional.empty();
        }
        var context = akyrion.thepredict.domain.OutcomeStatKey.context("", "");
        if (!session.recentOutcomes().isEmpty()) {
            var last = session.recentOutcomes().getLast();
            context = akyrion.thepredict.domain.OutcomeStatKey.context(
                    last.startBand().map(Enum::name).orElse(""), last.targetKind().map(Enum::name).orElse(""));
        }
        var key = new akyrion.thepredict.domain.OutcomeStatKey(session.profile().id(), tactic.get(), movement.get(),
                level, context);
        return aggregates.outcome(key).map(stat -> "windows=" + stat.windows() + " effect=" + stat.effectMean());
    }

    private static Map<ChoiceId, Optional<SkillPurpose>> purposes(MobSession session) {
        var purposes = new LinkedHashMap<ChoiceId, Optional<SkillPurpose>>();
        session.profile().choices().forEach((id, choice) -> purposes.put(id, choice.purpose()));
        return purposes;
    }

    private static Optional<ParticipantFacts> targetFacts(MobSession session, CombatFacts facts) {
        var alias = session.lastMobFacts().flatMap(MobFacts::target).map(TargetRelation::target);
        return alias.flatMap(target -> facts.participants().stream()
                .filter(participant -> participant.alias().equals(target)).findFirst());
    }

    private static SessionReport.Intent intent(TacticalIntent intent, boolean debug, long now) {
        var remaining = Math.max(0, intent.expiresAtNanos() - now) / IntentPolicy.NANOS_PER_TICK;
        return new SessionReport.Intent(intent.tactic(), intent.movement(), intent.choice(), intent.confidence(),
                remaining, intent.skillConsumed(), intent.alternatives(), intent.contingencies(), debug);
    }

    /**
     * What the reports read. All of them belong to the coordinator of one plugin start.
     *
     * @param sessions session registry
     * @param encounters encounter registry
     * @param sessionMetrics counters of sessions and encounters
     * @param lifecycle lifecycle, for the pending releases
     * @param contextEvents context events, for their counters
     * @param signals queue of {@code predictordecide}
     * @param metrics latency and counters of the decision path
     * @param threat enriched threat, for its counters
     * @param budget request budget
     * @param settings engine limits
     * @param tracker state of the service
     * @param inFlight requests in flight
     * @param intake result intake, for the model of the last answer
     * @param control goal ownership
     * @param reader combat reader, for the threat table
     * @param clock server clock
     * @param configuredModel configured model as it may be shown
     * @param outcomes totals of the closed outcome windows (M16.8)
     */
    public record Sources(SessionRegistry sessions, EncounterRegistry encounters, SessionMetrics sessionMetrics,
                          SessionLifecycle lifecycle, ContextEvents contextEvents, ContextSignals signals,
                          DecisionMetrics metrics, ThreatEnrichment threat, RequestBudget budget, EngineSettings settings,
                          ServiceStatusTracker tracker, InFlightRegistry inFlight, ResultIntake intake,
                          MobControl control, CombatReader reader, ServerClock clock, String configuredModel,
                          OutcomeMetrics outcomes) {
        /**
         * Validates the components.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Sources {
            for (var component : new Object[]{sessions, encounters, sessionMetrics, lifecycle, contextEvents, signals,
                    metrics, threat, budget, settings, tracker, inFlight, intake, control, reader, clock,
                    configuredModel, outcomes}) {
                Objects.requireNonNull(component, "diagnostic source");
            }
        }
    }
}
