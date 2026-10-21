package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CoordinationPolicy;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.FitGate;
import akyrion.thepredict.domain.IntentOutcome;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Acceptance of a finished request (M10.1, M10.2, M10.5, M10.7; plan §7). Package-private; server thread only,
 * called by {@link ExecutionSteps#accept} after {@code ResultIntake} freed the budget and informed the service
 * tracker, which it never informs again.
 *
 * <p>A result is handled only if its encounter is open, its sequence is the last one sent and it was not handled
 * yet; a late, duplicate or cancelled result is discarded with {@code STALE_SEQUENCE}. An answer is then discarded as
 * a whole for a reloaded catalog, another model ({@link ModelExpectation}) or a capture older than
 * {@code decision.maxDecisionAgeMillis}, measured from {@code InFlightRequest.captureNanos}. Each mob is then checked
 * alone: same generation, active, still in this encounter (a session closed and reopened at generation 0 is in
 * another encounter), readable, and with the target of the capture. Its {@code NOW} answer and its contingencies go
 * to {@link IntentPolicy} with the tactics eligible in that capture and, when fit checks were sent, their fits (M16.5). Adopted and renewed intents of the encounter are
 * coordinated ({@link CoordinationPolicy}), applied in encounter order and try their choice in the same tick. A
 * failure creates no intent: sessions keep a valid Jev intent or go through the fallback chain. Every discard is
 * counted by cause; an exception while handling one session drops only that session to level 4.
 *
 * <p>The {@link DecisionTap} hears each discard, of the whole result or of one mob with its {@code NOW} question, and,
 * while something records, the verdict of the intent policy about each mob with its question: adopted, renewed or
 * rejected with its reason, and each contingency stored with the adopted or renewed intent (M18.4).
 */
final class DecisionAcceptor {
    /** Detail of a result whose future was cancelled locally: never a failure of the service (M08). */
    static final String CANCELLED = "cancelled";

    private final SessionRegistry sessions;
    private final EncounterRegistry encounters;
    private final LongSupplier catalogRevision;
    private final IntentControl control;
    private final FallbackStep fallback;
    private final DecisionMetrics metrics;
    private final ExecutionSettings settings;
    private final ModelExpectation model;
    private final ExecutionLog log;
    private final IntentCoordination coordination;
    private final AnswerIdentity identity;
    private final FitGate.FitThresholds fitThresholds;
    private final DecisionTap tap;
    private final AcceptanceTap verdicts;
    private PruningMode pruning = PruningMode.OFF;
    private Runnable coverageStale = () -> {
    };

    void usePruning(PruningMode mode) {
        pruning = Objects.requireNonNull(mode, "mode");
    }

    /**
     * Marks distilled coverage stale when a model change is discarded (errata E29-13 applied).
     *
     * @param hook called once per stale answer; the default does nothing
     */
    void onCoverageStale(Runnable hook) {
        coverageStale = hook == null ? () -> {
        } : hook;
    }

    DecisionAcceptor(SessionRegistry sessions, EncounterRegistry encounters, CombatReader reader,
                     LongSupplier catalogRevision, IntentControl control, FallbackStep fallback, DecisionMetrics metrics,
                     ExecutionSettings settings, ExecutionLog log, FitGate.FitThresholds fitThresholds,
                     DecisionTap tap) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.catalogRevision = Objects.requireNonNull(catalogRevision, "catalogRevision");
        this.control = Objects.requireNonNull(control, "control");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.model = new ModelExpectation(settings.configuredModel());
        this.log = Objects.requireNonNull(log, "log");
        this.coordination = new IntentCoordination(control, fallback);
        this.identity = new AnswerIdentity(reader, control);
        this.fitThresholds = Objects.requireNonNull(fitThresholds, "fitThresholds");
        this.tap = Objects.requireNonNull(tap, "tap");
        this.verdicts = new AcceptanceTap(tap);
    }

    /** Handles one finished request, on time or late. */
    void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
        var request = completed.request();
        var cancelled = completed.result() instanceof DecisionResult.Failed failed && CANCELLED.equals(failed.detail());
        var encounter = encounters.find(request.encounter()).filter(found -> !found.isClosed()
                && found.schedule().sequence() == request.sequence()
                && found.schedule().handledSequence() < request.sequence());
        if (cancelled || encounter.isEmpty()) {
            discard(request, DiscardReason.STALE_SEQUENCE, tick);
            return;
        }
        encounter.get().schedule().handledSequence(request.sequence());
        switch (completed.result()) {
            case DecisionResult.Failed ignored -> failed(request, tick, nowNanos);
            case DecisionResult.Answered answered -> {
                var reason = rejection(request, answered, tick, nowNanos);
                if (reason.isPresent()) {
                    discard(request, reason.get(), tick);
                } else {
                    answered(encounter.get(), request, answered, tick, nowNanos);
                }
            }
        }
    }

    private Optional<DiscardReason> rejection(InFlightRequest request, DecisionResult.Answered answered, long tick,
                                              long nowNanos) {
        if (request.catalogRevision() != catalogRevision.getAsLong()) {
            return Optional.of(DiscardReason.CATALOG_CHANGED);
        }
        if (!model.accepts(answered.effectiveModel(), line -> log.always(tick, line))) {
            if (model.takeCoverageStale()) {
                coverageStale.run();
            }
            return Optional.of(DiscardReason.MODEL_CHANGED);
        }
        if (nowNanos - request.captureNanos() > settings.maxDecisionAgeNanos()) {
            return Optional.of(DiscardReason.TOO_OLD);
        }
        return Optional.empty();
    }

    /** A failure creates no intent: each asked session without a valid Jev intent goes through the fallback chain. */
    private void failed(InFlightRequest request, long tick, long nowNanos) {
        for (var key : AnswerGroups.askedMobs(request)) {
            sessions.find(key).filter(session -> session.state() == SessionState.ACTIVE
                    && session.encounter().equals(Optional.of(request.encounter()))
                    && !control.heldByDebug(session, nowNanos)).ifPresent(session -> guarded(session, tick, () ->
                    fallBackUnlessAnswered(session, request, session.lastTargetId().isPresent(), tick, nowNanos)));
        }
    }

    private void answered(Encounter encounter, InFlightRequest request, DecisionResult.Answered answered, long tick,
                          long nowNanos) {
        answered.group().ifPresent(group -> offerGroup(encounter, request, group, tick));
        var candidates = new ArrayList<CoordinationPolicy.Candidate>();
        var proposing = new ArrayList<MobSession>();
        for (var group : AnswerGroups.of(request, answered, encounter.members(), metrics)) {
            var found = sessions.find(group.mob().entityId());
            var reason = found.isEmpty() ? Optional.of(DiscardReason.SESSION_INACTIVE)
                    : identity.check(found.get(), group.mob(), request, tick, nowNanos);
            if (reason.isPresent()) {
                metrics.increment(reason.get());
                if (tap.recording()) {
                    AnswerGroups.questionOf(request, group.mob(), Situation.NOW).ifPresent(question -> tap
                            .answerDiscarded(request.encounter(), request.sequence(), question, group.mob(),
                                    reason.get()));
                }
                log.decision(tick, () -> "Discarded the answer about mob " + group.mob().entityId() + ": " + reason.get());
                continue;
            }
            var session = found.get();
            guarded(session, tick, () -> offer(session, group, request, tick, nowNanos).ifPresent(intent -> {
                candidates.add(new CoordinationPolicy.Candidate(intent, session.intent(), IntentControl.eligible(session),
                        session.profile()));
                proposing.add(session);
            }));
        }
        coordination.apply(encounter, candidates, proposing, tick, nowNanos);
    }

    /**
     * Offers {@code g0} to the encounter (M25.10). The answer carries the membership epoch of the brief that asked,
     * so the body stays opaque to the engine (01 §8); a live epoch that moved on discards the plan, and a confidence
     * under the leader's floor keeps the local plan, candidate 0.
     */
    private void offerGroup(Encounter encounter, InFlightRequest request, akyrion.thepredict.domain.GroupAnswer group,
                            long tick) {
        var captured = group.membershipEpoch();
        long live = 0;
        var floor = 0.0;
        for (var session : encounter.memberSessions()) {
            live = session.groupCopy().membershipEpoch();
            floor = session.learning().confidenceFloor(session.profile().timing().minimumConfidence());
            break;
        }
        if (live > 0 && captured != live) {
            metrics.increment(DiscardReason.GROUP_EPOCH_CHANGED);
            return;
        }
        var minimum = floor;
        if (group.confidence() < minimum) {
            metrics.increment(FallbackReason.LOW_CONFIDENCE);
            log.decision(tick, () -> "Kept the local plan of encounter " + request.encounter().value()
                    + ": the plan answer had confidence " + group.confidence() + " under " + minimum);
            return;
        }
        encounter.offerGroupAnswer(group.plan(), request.sequence(), captured);
    }

    /** Offers the answers about one mob to {@link IntentPolicy}; returns the adopted or renewed intent. */
    private Optional<TacticalIntent> offer(MobSession session, AnswerGroups.Group group, InFlightRequest request,
                                           long tick, long nowNanos) {
        if (vetoed(session, group, request, tick, nowNanos)) {
            return Optional.empty();
        }
        session.markAnswered();
        var eligible = IntentControl.eligible(session);
        var floor = session.learning().confidenceFloor(session.profile().timing().minimumConfidence());
        var outcome = offered(session, group, eligible, tick, nowNanos, expiryBase(session, request, nowNanos), floor);
        if (tap.recording()) {
            verdicts.told(session, group, request, outcome);
        }
        return switch (outcome) {
            case IntentOutcome.Adopted adopted -> {
                if (adopted.intent().level() == akyrion.thepredict.domain.ControlLevel.JEV) {
                    session.learning().noteAcceptedJev();
                }
                yield proposed(session, adopted.intent());
            }
            case IntentOutcome.Renewed renewed -> proposed(session, renewed.intent());
            case IntentOutcome.Rejected rejected -> {
                rejected(session, rejected.reason(), request, tick, nowNanos);
                yield Optional.empty();
            }
        };
    }

    /**
     * The instant the lifetime of an answered intent counts from (M20.7, AE-09): the capture of its question with
     * {@code REACTIVE}, so an answer that arrives late controls for what remains of its horizon (the extension of
     * {@code IntentControl} still prolongs it while the situation holds), and the acceptance with {@code STANDARD}, as
     * in M19.
     */
    private static long expiryBase(MobSession session, InFlightRequest request, long nowNanos) {
        return session.profile().timing().cadence() == DecisionCadence.REACTIVE ? request.captureNanos() : nowNanos;
    }

    /**
     * Fit checks stay on the raw FitGate. An empty fit map uses the confidence floor, which is the profile minimum
     * until a calibrator installs one.
     */
    private IntentOutcome offered(MobSession session, AnswerGroups.Group group, Set<TacticId> eligible, long tick,
                                  long nowNanos, long expiryBaseNanos, double floor) {
        if (!group.fits().isEmpty()) {
            return session.intent().isPresent()
                    ? IntentPolicy.accept(session.intent().get(), group.now(), group.contingencies(), eligible,
                    session.profile(), tick, nowNanos, expiryBaseNanos, group.fits(), fitThresholds)
                    : IntentPolicy.accept(session.key(), group.now(), group.contingencies(), eligible,
                    session.profile(), tick, nowNanos, expiryBaseNanos, group.fits(), fitThresholds);
        }
        return session.intent().isPresent()
                ? IntentPolicy.accept(session.intent().get(), group.now(), group.contingencies(), eligible,
                session.profile(), tick, nowNanos, expiryBaseNanos, floor)
                : IntentPolicy.accept(session.key(), group.now(), group.contingencies(), eligible, session.profile(),
                tick, nowNanos, expiryBaseNanos, floor);
    }

    private boolean vetoed(MobSession session, AnswerGroups.Group group, InFlightRequest request, long tick,
                           long nowNanos) {
        session.notePendingCaptureNanos(request.captureNanos());
        session.noteLiveSignature(LiveSignature.of(session));
        var best = session.profile().tactics().get(group.now().best());
        var mark = best == null ? SituationGate.classify(session, pruning, control)
                : SituationGate.classify(session, best, pruning, control);
        if (mark == SituationGate.Mark.VETO) {
            discardMob(session, group, request, DiscardReason.SITUATION_CHANGED, tick, nowNanos);
            return true;
        }
        if (mark == SituationGate.Mark.AGED) {
            metrics.layer(DecisionMetrics.LayerCounter.ANSWER_AGED, 1);
        }
        return false;
    }

    private void discardMob(MobSession session, AnswerGroups.Group group, InFlightRequest request, DiscardReason reason,
                            long tick, long nowNanos) {
        metrics.increment(reason);
        if (tap.recording()) {
            AnswerGroups.questionOf(request, group.mob(), Situation.NOW).ifPresent(question -> tap
                    .answerDiscarded(request.encounter(), request.sequence(), question, group.mob(), reason));
        }
        log.decision(tick, () -> "Discarded the answer about mob " + session.key().entityId() + ": " + reason);
        fallBackUnlessAnswered(session, request, session.lastTargetId().isPresent(), tick, nowNanos);
    }

    private Optional<TacticalIntent> proposed(MobSession session, TacticalIntent intent) {
        session.lowConfidenceStreak(0);
        return Optional.of(intent);
    }

    /** A rejected answer leaves the current intent running; a low confidence lengthens the streak (M10.5). */
    private void rejected(MobSession session, FallbackReason reason, InFlightRequest request, long tick, long nowNanos) {
        metrics.increment(reason);
        if (reason == FallbackReason.LOW_CONFIDENCE) {
            session.lowConfidenceStreak(session.lowConfidenceStreak() + 1);
        }
        log.decision(tick, () -> "Rejected the answer about mob " + session.key().entityId() + ": " + reason);
        fallBackUnlessAnswered(session, request, true, tick, nowNanos);
    }

    private void fallBackUnlessAnswered(MobSession session, InFlightRequest request, boolean targetPerceived, long tick,
                                        long nowNanos) {
        var valid = session.intent().filter(intent -> !intent.expired(nowNanos));
        if (valid.filter(intent -> intent.level() == ControlLevel.JEV || intent.level() == ControlLevel.ALTERNATIVE)
                .isEmpty()) {
            fallback.apply(session, request.worldId(), valid.map(TacticalIntent::level).orElse(ControlLevel.BASE),
                    targetPerceived, tick, nowNanos);
        }
    }

    private void discard(InFlightRequest request, DiscardReason reason, long tick) {
        metrics.increment(reason);
        tap.answerDiscarded(request.encounter(), request.sequence(), reason);
        log.decision(tick, () -> "Discarded the result of request " + request.sequence() + " of encounter "
                + request.encounter().value() + ": " + reason);
    }

    private void guarded(MobSession session, long tick, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException exception) {
            if (session.state() == SessionState.ACTIVE) {
                control.fail(session, tick);
            }
        }
    }
}
