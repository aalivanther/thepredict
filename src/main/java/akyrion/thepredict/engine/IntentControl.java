package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.PurposeRules;
import akyrion.thepredict.domain.ReadyCastOrder;
import akyrion.thepredict.domain.ReadyIn;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TelegraphPolicy;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.EscapeGuard;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.ShortWindUp;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.UseCondition;
import akyrion.thepredict.domain.ZoneCastOrder;
import akyrion.thepredict.domain.ZoneGuardKind;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The one place where M10 changes the intent of an active session and what the world sees of it (M10.2, M10.3,
 * M10.6; rules A3, A6, T1). Package-private; server thread only.
 *
 * <p>Applying an intent stores it, publishes its view and, only when its tactic differs from the previous intent,
 * sends the signal of the tactic once and forgets the progress of the previous tactic: a renewal, a change of level
 * with the same tactic and a drop to level 4 never signal (M04.6). A cast attempt and its consumed mark happen in the
 * same call (plan §7). Changing level never installs goals. A new tactic or level opens the outcome window of the new
 * intent and closes the previous one; the end of an intent closes it (M16.8).
 *
 * <p>While something records, the {@link DecisionTap} hears each change that no answer explains (an answer is told by
 * the acceptance with its question): a fired contingency, a new local plan with the reason of the drop, a change of
 * level or of tactic of an alternative, and each drop to level 4; and each cast outcome once, when it is counted
 * (M18.4). No event is built while nothing records.
 */
class IntentControl {
    /**
     * Ticks between two vanilla melee hits of a mob, as {@code MeleeAttackGoal} spaces them: {@code resetAttackCooldown}
     * sets {@code adjustedTickDelay(20)}, which is 20 because the goal updates every tick, and the goal hits again once
     * the counter, lowered by one per tick, reaches 0 (javap of Paper 26.2). A {@code VANILLA_MELEE} choice keeps that
     * rhythm, so real-time readiness never hits faster than vanilla.
     */
    static final int VANILLA_MELEE_INTERVAL_TICKS = 20;

    private final IntentBoard board;
    private final CombatActions actions;
    private final DecisionMetrics metrics;
    private final ExecutionLog log;
    private final OutcomeEvents outcomes;
    private final IntentTap tap;
    private final long[] stanceStep = new long[5];
    private HazardNotes hazards = (mob, target, kind, tick) -> {
    };
    private PatternRaise.PatternUse patternUse = PatternRaise::captured;
    private ZoneCastAdvisor zone = IntentControl::zoneStep;

    /**
     * The default cast order of A12 (M24.6): the limits are those of the attached zone ({@code zone.pushDeferMaxTicks}
     * and {@code zone.answerMaxWaitTicks}, stored on the session), and a lethal push against a player passes only behind
     * a tell that shows its minimum, measured with the same wind-up as {@link TellOpening#hold} (E-1, E-12).
     *
     * @param session the caster
     * @param choice the choice about to cast
     * @param tick server tick
     * @return the step
     */
    private static ZoneCastOrder.Step zoneStep(MobSession session, SkillChoice choice, long tick) {
        var displacement = choice.displacement().orElse(choice.nativeFacts().targetDisplacement());
        var danger = DangerRules.classify(choice);
        var answer = DangerRules.answer(choice);
        var entropy = session.difficulty().entropy();
        var shows = TelegraphPolicy.showsMinimum(session.playerTarget(), entropy, danger, answer, choice.busyTicks(),
                session.profile().telegraph());
        return ZoneCastOrder.step(session.playerTarget(), session.zoneMode(), entropy, danger,
                session.feasibility(answer), displacement, targetDanger(session, displacement),
                session.pushDeferredTicks(), session.pushDeferMaxTicks(), session.answerWaitedTicks(),
                session.answerMaxWaitTicks(), shows);
    }

    /**
     * Replaces the cast-order advisor. The default is {@link #zoneStep}. Server thread only.
     *
     * @param advisor the advisor
     */
    void useZone(ZoneCastAdvisor advisor) {
        this.zone = java.util.Objects.requireNonNull(advisor, "advisor");
    }

    /**
     * Hears accepted casts that move a target. The default ignores them.
     *
     * @param notes the ledger
     */
    void useHazard(HazardNotes notes) {
        this.hazards = java.util.Objects.requireNonNull(notes, "notes");
    }

    /**
     * Notes an immediate cast whose target displacement is not none, against the perceived target.
     *
     * @param session the caster
     * @param choice the choice that was cast
     * @param tick the server tick
     */
    void noteHazard(MobSession session, SkillChoice choice, long tick) {
        var target = session.lastTargetId().orElse(null);
        if (target == null) {
            return;
        }
        noteHazard(session, choice, target, tick);
    }

    /**
     * Notes a cast whose target displacement is not none, against the victim of that cast.
     *
     * @param session the caster
     * @param choice the choice that was cast
     * @param target the entity the cast was released against
     * @param tick the server tick
     */
    void noteHazard(MobSession session, SkillChoice choice, UUID target, long tick) {
        var kind = choice.displacement().orElse(choice.nativeFacts().targetDisplacement());
        if (kind == DisplacementKind.NONE) {
            return;
        }
        hazards.acceptedCast(session.key(), Objects.requireNonNull(target, "target"), kind, tick);
    }

    /** The tap, so a candidate cast can be told without going through the intent choice. */
    IntentTap tap() {
        return tap;
    }

    IntentControl(IntentBoard board, CombatActions actions, DecisionMetrics metrics, ExecutionLog log,
                  OutcomeEvents outcomes, DecisionTap tap) {
        this.board = Objects.requireNonNull(board, "board");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.log = Objects.requireNonNull(log, "log");
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes");
        this.tap = new IntentTap(tap);
    }

    /**
     * Conditions {@code raisedTactic} ranks with. The default is the stored capture, without the probe overlay.
     *
     * @param use the encounter lookup, or the capture default
     */
    void usePatternConditions(PatternRaise.PatternUse use) {
        patternUse = Objects.requireNonNull(use, "use");
    }

    /**
     * Conditions of the set that decides whether a cast was raised by the pattern factor.
     *
     * @param session the session
     * @param facts the capture
     * @return the conditions, which the caller must not modify
     */
    Set<UseCondition> patternConditions(MobSession session, MobFacts facts) {
        return patternUse.of(session, facts);
    }

    /**
     * Makes an intent current: stores and publishes it, signals and resets progress on a new tactic, and logs only a
     * change of tactic or level, never a renewal or a local replan of the same tactic. The tactic that
     * {@code NO_PROGRESS} has just dropped to level 4 is not signalled again when it is planned once more within
     * {@code minimumDecisionIntervalTicks} (PZ-07): the author's {@code ~onSignal} skills do not fire twice for one
     * engagement.
     */
    void apply(MobSession session, TacticalIntent intent, long tick) {
        apply(session, intent, tick, IntentTap.Change.ALTERNATIVE, Optional.empty());
    }

    /** Makes the intent of an accepted answer current, like {@link #apply}; the acceptance already told the tap. */
    void applyAnswered(MobSession session, TacticalIntent intent, long tick) {
        apply(session, intent, tick, IntentTap.Change.ANSWER, Optional.empty());
    }

    /** Makes a local intent of the fallback chain current, like {@link #apply}, with the reason of the drop if any. */
    void applyLocal(MobSession session, TacticalIntent intent, Optional<FallbackReason> reason, long tick) {
        apply(session, intent, tick, IntentTap.Change.LOCAL, reason);
    }

    /**
     * Makes a fired contingency current (M10.4) and counts it; its outcome window is marked, so a positive effect counts
     * the contingency as effective (M16.6).
     */
    void applyContingency(MobSession session, TacticalIntent intent, long tick) {
        metrics.add(DecisionMetrics.Counter.CONTINGENCIES_FIRED, 1);
        apply(session, intent, tick, IntentTap.Change.CONTINGENCY, Optional.empty());
    }

    /** Feeds the probe of the reactive cycle to the open outcome window (M16.8). */
    void observe(MobSession session, MobProbe probe, long tick) {
        session.noteProbe(probe, tick);
        outcomes.probed(session, probe, tick);
    }

    private void apply(MobSession session, TacticalIntent intent, long tick, IntentTap.Change change,
                       Optional<FallbackReason> reason) {
        var contingency = change == IntentTap.Change.CONTINGENCY;
        var newTactic = session.intent().map(previous -> !previous.tactic().equals(intent.tactic())).orElse(true);
        var newLevel = session.intent().map(previous -> previous.level() != intent.level()).orElse(true);
        tap.applied(session, intent, change, newTactic, newLevel, reason);
        if (newTactic || newLevel || contingency) {
            outcomes.adopted(session, intent, tick, contingency);
        }
        session.adoptIntent(intent);
        session.debugIntent(false);
        if (change == IntentTap.Change.ANSWER && intent.level() == ControlLevel.JEV && session.captureNoted()) {
            session.holdIntentCapture(session.captureSignature(), session.pendingCaptureNanos());
        } else {
            session.releaseIntentCapture();
        }
        StanceBoard.sync(board, session);
        if (newTactic) {
            startTactic(session, intent, tick);
        }
        if (newTactic || newLevel) {
            log.decision(tick, () -> "Mob " + session.key().entityId() + " " + intent.level() + " "
                    + intent.tactic().value());
        }
        session.scheduleReady(tick);
    }

    /**
     * Starts a new tactic: its progress counts from now, and the tactic is signalled unless {@code NO_PROGRESS} dropped
     * this same tactic within the decision interval, so a replan does not send it again (PZ-07).
     */
    private void startTactic(MobSession session, TacticalIntent intent, long tick) {
        session.tacticStarted(tick);
        session.resetProgress();
        if (!session.resumesStuck(intent.tactic(), tick, session.profile().timing().minimumDecisionIntervalTicks())) {
            actions.signalTactic(session.key(), intent.tactic());
        }
    }

    /**
     * Drops a controlled session to level 4 with its reason; a session already at level 4 does not change, so
     * {@code BASE -> BASE} is never counted as a drop.
     */
    void toBase(MobSession session, FallbackReason reason, long tick) {
        if (session.intent().isEmpty()) {
            return;
        }
        tap.dropped(session, reason);
        session.dropIntent();
        session.invalidateReady();
        session.resetProgress();
        outcomes.close(session, tick);
        board.publish(session.key(), IntentView.base(session.key().generation()));
        dropped(session, reason, tick);
    }

    /** Records a change of level with its reason (A7). */
    void dropped(MobSession session, FallbackReason reason, long tick) {
        session.fallback(reason);
        metrics.increment(reason);
        log.decision(tick, () -> "Mob " + session.key().entityId() + " fell back: " + reason);
    }

    /** Drops one session to level 4 after an exception while processing it (M10.6, rule C7). */
    void fail(MobSession session, long tick) {
        metrics.add(DecisionMetrics.Counter.SESSION_FAILURES, 1);
        tap.dropped(session, FallbackReason.INTERNAL_ERROR);
        session.dropIntent();
        session.invalidateReady();
        session.resetProgress();
        outcomes.close(session, tick);
        board.publish(session.key(), IntentView.base(session.key().generation()));
        dropped(session, FallbackReason.INTERNAL_ERROR, tick);
    }

    /**
     * Tries the choice of the current intent once the ready step has admitted the session (M19.4). A debug intent is
     * not re-armed. A second start on this tick, or a start while {@code busyUntilTick} still holds, is deferred
     * before any cast. After the attempt the session is scheduled again from the outcome.
     */
    void cast(MobSession session, long tick, long nowNanos) {
        if (!castStartAllowed(session, tick)) {
            deferStart(session, tick);
            return;
        }
        if (!session.debugIntent()) {
            rearm(session, tick, nowNanos);
        }
        var current = session.intent().filter(intent -> !intent.skillConsumed() && intent.choice().isPresent());
        if (current.isEmpty()) {
            session.scheduleReady(closedChoiceTick(session, tick));
            return;
        }
        var intent = current.get();
        if (intent.expired(nowNanos)) {
            expireChoice(session, tick, nowNanos);
            session.invalidateReady();
            return;
        }
        var choice = session.profile().choices().get(intent.choice().get());
        attemptChoice(session, intent, choice, tick, false);
    }

    /**
     * Next visit when the intent has no open choice: the tick at which the choice it still names can be re-armed and
     * cast ({@link #rearmTick}), or two ticks ahead when it names none (M19.4). A ready tick that has already passed
     * waits one tick, so a consumed choice is not started twice on the tick that consumed it.
     */
    private long closedChoiceTick(MobSession session, long tick) {
        var choice = intentChoice(session);
        if (choice == null) {
            return tick + ReactiveLayer.CYCLE_TICKS;
        }
        var at = rearmTick(session, choice, readyTick(session, choice, tick));
        return at <= tick ? tick + 1 : at;
    }

    /**
     * The first tick at which the consumed choice of the current intent can be cast again, which is never before it is
     * re-armed (EFO-11): a re-arm happens in a later tick than the consumption, after a consumption that was not a
     * {@code CAST} only once one local intent lifetime has passed ({@link #rearmWhenReady}), and for a local intent only
     * in its reactive cycle, the next probe of the session. Waking the session earlier only polls a choice that cannot
     * fire. Any other choice, an open choice and a debug intent keep their ready tick. No allocation.
     *
     * @param session the session
     * @param choice a choice of the profile
     * @param at its ready tick
     * @return {@code at}, or the later tick at which the choice can be re-armed
     */
    long rearmTick(MobSession session, SkillChoice choice, long at) {
        var intent = session.intent().orElse(null);
        if (intent == null || !intent.skillConsumed() || session.debugIntent() || intent.choice().isEmpty()
                || !intent.choice().get().equals(choice.id())) {
            return at;
        }
        var consumed = session.choiceConsumedTick();
        var after = consumed.isEmpty() ? at : consumed.getAsLong() + (session.choiceConsumedByCast() ? 1L
                : FallbackStep.localIntentTicks(session.profile().timing()));
        if (intent.level() == ControlLevel.LOCAL) {
            after = Math.max(after, session.nextProbeTick());
        }
        return Math.max(at, after);
    }

    /**
     * Whether a cast may start now: this tick has not already started one, and the wind-up in progress has ended or
     * the target is an entity (M19.4, M19.5, {@link #overlapAllowed}).
     *
     * @param session the session
     * @param tick current tick
     * @return whether a start is allowed
     */
    boolean castStartAllowed(MobSession session, long tick) {
        return (tick >= session.busyUntilTick() || overlapAllowed(session)) && session.lastCastStartTick() != tick;
    }

    /**
     * Whether this choice may start now (E-3, decision 6 of M23): as {@link #castStartAllowed(MobSession, long)}, and
     * also before the busy deadline when the mob is in {@code RECOVERY} and the choice is a non-damaging escape or
     * defense ({@link ReadyCastOrder#startsWhileBusy}), so a mob that recovers can still evade. Still one start per
     * tick. The ready step of {@code ANY_READY} asks this per candidate; {@link #cast} keeps the plain guard.
     *
     * @param session the session
     * @param tick current tick
     * @param choice the candidate about to start
     * @return whether a start of this choice is allowed
     */
    boolean castStartAllowed(MobSession session, long tick, SkillChoice choice) {
        var free = tick >= session.busyUntilTick() || overlapAllowed(session)
                || ReadyCastOrder.startsWhileBusy(choice, session.actionStance());
        return free && session.lastCastStartTick() != tick;
    }

    /**
     * Whether a cast may start during the wind-up of another one (2026-10-14): only against an entity, as MythicMobs
     * itself lets a mob run several skills at once, so a ThePredict mob is not slower than the same file without it.
     * Against a player, or while the kind of the target is unknown, one wind-up at a time keeps the tells of A11
     * readable. Both the target of this tick and the target of the last capture must be entities. No allocation.
     *
     * @param session the session
     * @return whether the wind-up in progress may be overlapped
     */
    static boolean overlapAllowed(MobSession session) {
        if (session.playerTarget()) {
            return false;
        }
        var facts = session.lastMobFacts();
        if (facts.isEmpty()) {
            return false;
        }
        var target = facts.get().target();
        return target.isPresent() && target.get().target().kind() == ParticipantKind.ENTITY;
    }

    /**
     * Reschedules a start that {@link #castStartAllowed} refused, without calling MythicMobs.
     *
     * @param session the session
     * @param tick current tick
     */
    void deferStart(MobSession session, long tick) {
        if (tick < session.busyUntilTick() && !overlapAllowed(session)) {
            metrics.add(DecisionMetrics.ReadyCounter.BUSY_BLOCKED, 1);
            session.scheduleReady(session.busyUntilTick());
        } else {
            session.scheduleReady(tick + 1);
        }
    }

    /**
     * Fires one choice and schedules the next try from the outcome (M19.4). {@code keepTactic} is the {@code ANY_READY}
     * choice of the intent: a shield then leaves the tactic in place so another candidate can run this tick.
     *
     * @param session the session
     * @param intent intent that owns the choice
     * @param choice choice to try
     * @param tick current tick
     * @param keepTactic whether {@code SKIPPED_SHIELD} must not change the tactic
     * @return the outcome, {@code NOT_ELIGIBLE} when the choice is not ready yet and MythicMobs was not called
     */
    ExecutionOutcome attemptChoice(MobSession session, TacticalIntent intent, SkillChoice choice, long tick,
                                   boolean keepTactic) {
        var index = choiceIndex(session, choice);
        var at = slackedReady(session, choice, index, tick);
        var isReady = ready(session, choice, index, tick);
        if (!isReady || !admitted(session, choice, intent.movement()) || at > tick) {
            if (!isReady) {
                record(session, intent, ExecutionOutcome.NOT_ELIGIBLE, tick, false);
            }
            session.scheduleReady(at > tick ? at : tick + 1L);
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        var outcome = fire(session, choice, intent.movement(), tick);
        if (session.consumeTellHeld()) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        record(session, intent, outcome, tick, false);
        LearnedCast.settle(session, choice, outcome);
        afterAttempt(session, intent, choice, index, outcome, tick, keepTactic);
        return outcome;
    }

    /** Schedules the next try from the outcome of an attempt that reached MythicMobs or the zone (M19.4). */
    private void afterAttempt(MobSession session, TacticalIntent intent, SkillChoice choice, int index,
                              ExecutionOutcome outcome, long tick, boolean keepTactic) {
        switch (outcome) {
            case CAST -> finishCast(session, intent, choice, index, tick);
            case REJECTED_BY_MYTHICMOBS, EXPIRED -> {
                consume(session, intent, tick, false);
                session.scheduleReady(tick + FallbackStep.localIntentTicks(session.profile().timing()));
            }
            case SKIPPED_SHIELD -> {
                if (!keepTactic) {
                    shieldAlternative(session, intent, tick);
                }
            }
            case NOT_ELIGIBLE, DEFERRED_INVULNERABLE -> {
                var delay = session.takeZoneDelay();
                if (delay > 0 && index >= 0) {
                    session.holdChoice(index, tick + delay);
                }
                session.scheduleReady(tick + (delay > 0 ? delay : 1L));
            }
        }
    }

    private void finishCast(MobSession session, TacticalIntent intent, SkillChoice choice, int index, long tick) {
        noteHazard(session, choice, tick);
        outcomes.cast(session, damaging(choice), tick);
        consume(session, intent, tick, true);
        session.nextChoiceTick(tick + choice.extraCooldownTicks());
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            session.nextMeleeTick(tick + VANILLA_MELEE_INTERVAL_TICKS);
        }
        session.noteCastStarted(tick);
        session.lastDanger(akyrion.thepredict.domain.DangerRules.classify(choice).ordinal());
        session.busyUntil(Math.max(session.busyUntilTick(), tick + (long) choice.busyTicks()));
        noteImpact(session, choice, tick);
        TellOpening.afterCast(session, tick);
        tap.posture(session, tick);
        session.difficulty().clearAnchors();
        metrics.add(DecisionMetrics.ReadyCounter.READY_CASTS, 1);
        metrics.readyLatency(session.takeReadyLatency(index, tick));
        session.scheduleReady(readyTick(session, choice, index, tick));
        ReadyCasts.noteRaised(this, session, intent, choice, tick);
    }

    private void shieldAlternative(MobSession session, TacticalIntent intent, long tick) {
        IntentPolicy.nextAlternative(intent, eligible(session), session.profile(), tick)
                .ifPresentOrElse(alternative -> apply(session, alternative, tick), () -> {
                    consume(session, intent, tick, false);
                    session.scheduleReady(tick + FallbackStep.localIntentTicks(session.profile().timing()));
                });
    }

    /**
     * Fires a choice unless a tell holds it (M23.4). With a captured target the four-argument cast checks it.
     *
     * @param session the caster
     * @param choice the choice
     * @param movement movement of the intent
     * @param tick server tick
     * @return the outcome; the caller drops it when {@link MobSession#consumeTellHeld()} is true
     */
    ExecutionOutcome fire(MobSession session, SkillChoice choice, akyrion.thepredict.domain.Movement movement, long tick) {
        var index = choiceIndex(session, choice);
        session.armChoice(index);
        try {
            var ended = session.tellOpen() ? null : beforeTell(session, choice, index, movement, tick);
            return ended != null ? ended : throughTell(session, choice, movement, tick);
        } finally {
            session.armChoice(-1);
        }
    }

    /**
     * The steps of a choice that holds no tell yet (M24.6, M25): the escape guard, the cast order of the zone and the
     * group gate refuse with {@code NOT_ELIGIBLE}, and a class that casts at once without a tell is cast here. The
     * choice is armed, so the deferral and retry counts of the zone are its own (EFO-10).
     *
     * @return the outcome when the choice ends here, or {@code null} when it goes on through the tell
     */
    private ExecutionOutcome beforeTell(MobSession session, SkillChoice choice, int index,
                                        akyrion.thepredict.domain.Movement movement, long tick) {
        if (EscapeGuard.blocked(session.zoneMode(), choice.nativeFacts().selfDisplacement(), session.tickSignals())) {
            session.noteEscape();
            tap.zoneGuard(session.key(), ZoneGuardKind.ESCAPE_BLOCKED);
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        var step = zone.advise(session, choice, tick);
        noteZoneStep(session, step, index, tick);
        var opens = TellOpening.opens(session, choice);
        if (step != ZoneCastOrder.Step.OPEN && step != ZoneCastOrder.Step.CAST
                || GroupCastGate.admit(session, choice, tick, opens).isPresent()) {
            tap.posture(session, tick);
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        if (step != ZoneCastOrder.Step.CAST || opens) {
            return null;
        }
        var outcome = castNow(session, choice, movement);
        GroupTellClose.untold(session, outcome, tick);
        noteOptOut(session, choice);
        tap.posture(session, tick);
        return outcome;
    }

    /**
     * Counts what a refusing step of the zone asks: a deferral and a retry wait their step, and a drop gives the tell
     * up for the whiff cooldown, as the step promises (EFO-10).
     */
    private static void noteZoneStep(MobSession session, ZoneCastOrder.Step step, int index, long tick) {
        switch (step) {
            case DEFER -> session.notePushDefer();
            case RETRY -> session.noteAnswerWait();
            case DROP -> session.dropAnswer(index, tick + session.profile().telegraph().whiffCooldownTicks());
            default -> {
            }
        }
    }

    /** Opens or keeps the tell of a choice, or casts it, and publishes the posture (M23.4). */
    private ExecutionOutcome throughTell(MobSession session, SkillChoice choice,
                                         akyrion.thepredict.domain.Movement movement, long tick) {
        ExecutionOutcome outcome;
        var openedBefore = session.tellOpen();
        if (TellOpening.hold(session, choice, movement, tick, actions, stanceStep)) {
            if (!openedBefore) {
                noteOpened(session, choice, tick);
            }
            outcome = ExecutionOutcome.NOT_ELIGIBLE;
        } else {
            if (!openedBefore && session.tellOpen()) {
                noteOpened(session, choice, tick);
            } else if (!openedBefore) {
                noteOptOut(session, choice);
            }
            outcome = castNow(session, choice, movement);
            if (!openedBefore && !session.tellOpen()) {
                GroupTellClose.untold(session, outcome, tick);
            }
        }
        StanceClock.flushConflict(session, tick, log, metrics);
        StanceBoard.sync(board, session);
        tap.posture(session, tick);
        return outcome;
    }

    private static PushDanger targetDanger(MobSession session, DisplacementKind displacement) {
        if (displacement == DisplacementKind.STRONG_PUSH) {
            return dangerAt(session, TickSignals.TARGET_PUSH_FAR);
        }
        if (displacement == DisplacementKind.KNOCKBACK) {
            return dangerAt(session, TickSignals.TARGET_PUSH_NEAR);
        }
        return PushDanger.NONE;
    }

    private static PushDanger dangerAt(MobSession session, int shift) {
        return switch (TickSignals.push(session.tickSignals(), shift)) {
            case 1 -> PushDanger.HARM;
            case 2 -> PushDanger.LETHAL;
            case 3 -> PushDanger.UNKNOWN;
            default -> PushDanger.NONE;
        };
    }

    private void noteOpened(MobSession session, SkillChoice choice, long tick) {
        var lead = 0;
        if (session.tellHeld() && session.releaseTick() != MobSession.NEVER) {
            lead = (int) Math.max(0L, session.releaseTick() - tick);
        }
        metrics.noteTellOpen(tick, lead);
        var settings = session.profile().telegraph();
        if (settings.shortWindUp() != ShortWindUp.IMMEDIATE) {
            return;
        }
        var danger = DangerRules.classify(choice);
        if (danger.enforced() && settings.minimumTicks(danger, DangerRules.answer(choice)) > choice.busyTicks()) {
            metrics.add(DecisionMetrics.TellCounter.UNWARNED_BY_OPT_OUT, 1);
            session.learning().distillSession().governor().fairness();
        }
    }

    private void noteOptOut(MobSession session, SkillChoice choice) {
        var settings = session.profile().telegraph();
        var excluded = settings.shortWindUp() == ShortWindUp.IMMEDIATE
                || session.playerTarget() && !settings.againstPlayers()
                || !session.playerTarget() && !settings.againstEntities();
        if (excluded && DangerRules.classify(choice).enforced()) {
            metrics.add(DecisionMetrics.TellCounter.UNWARNED_BY_OPT_OUT, 1);
            session.learning().distillSession().governor().fairness();
        }
    }

    /** Casts now; a tell open at this point opened in this cast with a lead of 0 and lends its interrupt (SA-02). */
    private ExecutionOutcome castNow(MobSession session, SkillChoice choice,
                                     akyrion.thepredict.domain.Movement movement) {
        if (session.tellOpen()) {
            return TellOpening.castTold(session, choice, movement, actions);
        }
        var expected = session.lastTargetId();
        if (expected.isPresent()) {
            return actions.cast(session.key(), choice, movement, expected.get());
        }
        if (choice.requiresTarget()) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        return actions.cast(session.key(), choice, movement);
    }

    /** Cheap admission of a candidate, before any line-of-sight read (M19.5). */
    boolean admitted(MobSession session, SkillChoice choice, akyrion.thepredict.domain.Movement movement) {
        return actions.cheaplyAdmitted(session.key(), choice, movement);
    }

    /** The one line-of-sight read of this mob for the current ready step (M19.5). */
    boolean sees(MobSession session) {
        return actions.seesTarget(session.key());
    }

    /**
     * The choice of the intent may fire now: it is not consumed, and its tactic is still eligible once the cooldown
     * ends, or the intent is a debug mark (M19.4). A consumed choice is re-armed before this is asked.
     */
    boolean intentChoiceOpen(MobSession session, TacticalIntent intent) {
        return !intent.skillConsumed() && intentTacticAllowed(session, intent);
    }

    /**
     * Tick at which a choice may be tried again: the native wait, the extra cooldown and, for vanilla melee, the
     * attack interval. An unknown wait is 0 for vanilla melee and two ticks for every other choice. The wait of the
     * group ledgers holds only a choice that asks for a token ({@link GroupCastGate#asksToken}): a refused attack never
     * delays a heal, an escape or a defense of the same mob.
     */
    long readyTick(MobSession session, SkillChoice choice, long tick) {
        return readyTick(session, choice, choiceIndex(session, choice), tick);
    }

    /**
     * {@link #readyTick(MobSession, SkillChoice, long)} for a caller that already knows the declaration index, so a
     * loop over the choices does not search the index again for each one (EFO-19).
     *
     * @param session the session
     * @param choice the choice
     * @param index declaration index of {@code choice} in {@link MobSession#listedChoices()}, or {@code -1}
     * @param tick current tick
     * @return the tick at which the choice may be tried
     */
    long readyTick(MobSession session, SkillChoice choice, int index, long tick) {
        var at = readyTickUnnoted(session, choice, index, tick);
        session.noteReadyRead(index, at, tick);
        return at;
    }

    /**
     * {@link #readyTick(MobSession, SkillChoice, int, long)} without noting the read for the ready latency: a look at
     * another choice of the mob that is not about to be cast now (E-1, {@link StrongerFirst}).
     *
     * @param session the session
     * @param choice the choice
     * @param index declaration index of {@code choice}, or {@code -1}
     * @param tick current tick
     * @return the tick at which the choice may be tried
     */
    long readyTickUnnoted(MobSession session, SkillChoice choice, int index, long tick) {
        var wait = actions.ticksUntilReady(session.key(), choice);
        long at;
        if (wait.isEmpty()) {
            at = choice.kind() == ChoiceKind.VANILLA_MELEE ? tick : tick + ReactiveLayer.CYCLE_TICKS;
        } else {
            at = tick + wait.getAsInt();
        }
        at = Math.max(at, session.nextChoiceTick());
        if (index >= 0) {
            at = Math.max(at, session.whiffUntil(index));
        }
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            at = Math.max(at, session.nextMeleeTick());
        }
        if (index >= 0 ? session.asksToken(index) : GroupCastGate.asksToken(choice)) {
            at = Math.max(at, session.ledgerWaitUntilTick());
        }
        return at;
    }

    /**
     * Native ready tick plus damaging slack. The anchor is stored only here, and only while reach still holds.
     *
     * @param session the session
     * @param choice the choice
     * @param index declaration index, or {@code -1}
     * @param tick current tick
     * @return the tick at which the cast may start
     */
    long slackedReady(MobSession session, SkillChoice choice, int index, long tick) {
        return slack(session, choice, tick, readyTick(session, choice, index, tick), index);
    }

    private long slack(MobSession session, SkillChoice choice, long tick, long at, int index) {
        var slackTicks = session.levers().damagingCastSlackTicks();
        if (slackTicks <= 0 || index < 0) {
            return at;
        }
        var damaging = akyrion.thepredict.domain.CastSlack.damaging(choice);
        var reach = admitted(session, choice, intentMovement(session));
        return session.difficulty().ready(index, at, tick, at <= tick && reach, slackTicks, damaging);
    }

    /**
     * Ready tick of a choice, reusing a cached tick that is more than two ticks ahead when {@code nearOnly} is set
     * (M19.4) and the cached tick was read less than {@link ReactiveLayer#CYCLE_TICKS} ago, so each reactive cycle
     * reads it again: below 20 TPS the wall-clock cooldown of MythicMobs ends before the tick read earlier (SA-13).
     * The due path passes {@code nearOnly} false and always reads. The consumed choice of the intent answers
     * the tick it can be re-armed on ({@link #rearmTick}), so neither the recalibration nor the soonest candidate wakes
     * the session for a choice that cannot fire yet; the cache keeps the plain ready tick.
     *
     * @param session the session
     * @param choice the choice
     * @param index declaration index, or {@code -1} when the choice is not in the cached list
     * @param tick current tick
     * @param nearOnly whether a far cached tick skips {@link CombatActions#ticksUntilReady}
     * @return the tick at which the choice may be tried
     */
    long cachedReady(MobSession session, SkillChoice choice, int index, long tick, boolean nearOnly) {
        if (nearOnly && index >= 0 && admitted(session, choice, intentMovement(session))) {
            var known = session.freshChoiceReady(index, tick);
            if (known != Long.MIN_VALUE && known - tick > 2L) {
                return rearmTick(session, choice, known);
            }
        }
        var at = readyTick(session, choice, index, tick);
        at = slack(session, choice, tick, at, index);
        if (index >= 0) {
            session.noteChoiceReady(index, at, tick);
        }
        return rearmTick(session, choice, at);
    }

    private static void consume(MobSession session, TacticalIntent intent, long tick, boolean cast) {
        session.adoptIntent(intent.withSkillConsumed());
        session.choiceConsumed(tick, cast);
    }

    /**
     * Re-arms the choice of a renewed Jev or alternative intent as soon as it is ready again, never because of an answer
     * (M10, real-time readiness; DT-28): the unexpired intent may try its choice once more in the first reactive cycle
     * after its last consumption in which the native cooldown and the extra cooldown let it be cast, so it casts at the
     * cadence of the skill's own {@code Cooldown}, as in the local mode.
     */
    private void rearm(MobSession session, long tick, long nowNanos) {
        var intent = session.intent().orElse(null);
        if (intent != null && (intent.level() == ControlLevel.JEV || intent.level() == ControlLevel.ALTERNATIVE)
                && !intent.expired(nowNanos)) {
            rearmWhenReady(session, intent, tick);
        }
    }

    /**
     * The choice the current intent names, or {@code null} when there is no intent or it names none. No allocation,
     * so the ready step reads it on every visit (C12).
     */
    private static SkillChoice intentChoice(MobSession session) {
        var intent = session.intent().orElse(null);
        if (intent == null || intent.choice().isEmpty()) {
            return null;
        }
        return session.profile().choices().get(intent.choice().get());
    }

    /** The movement of the current intent, {@code NONE} without one. No allocation. */
    private static akyrion.thepredict.domain.Movement intentMovement(MobSession session) {
        var intent = session.intent().orElse(null);
        return intent == null ? akyrion.thepredict.domain.Movement.NONE : intent.movement();
    }

    /**
     * Gives a consumed choice of the current intent back once it is ready again (M10, real-time readiness): in a tick
     * after its last consumption, with the native and the extra cooldown passed; after a consumption that was not a
     * {@code CAST} one local intent lifetime must also have passed ({@link FallbackStep#localIntentTicks}). Nothing is
     * published or signalled, and the start tick that holds the hysteresis and the minimum hold does not change; the
     * epoch of the choice grows by one, so it casts at most once per epoch.
     *
     * @return whether the choice was re-armed
     */
    boolean rearmWhenReady(MobSession session, TacticalIntent intent, long tick) {
        var consumedTick = session.choiceConsumedTick();
        if (!intent.skillConsumed() || intent.choice().isEmpty() || consumedTick.isEmpty()
                || tick <= consumedTick.getAsLong()) {
            return false;
        }
        var retryTicks = FallbackStep.localIntentTicks(session.profile().timing());
        if (!session.choiceConsumedByCast() && tick - consumedTick.getAsLong() < retryTicks) {
            return false;
        }
        var choice = session.profile().choices().get(intent.choice().get());
        if (choice == null || !ready(session, choice, tick)) {
            return false;
        }
        session.adoptIntent(new TacticalIntent(intent.mob(), intent.tactic(), intent.movement(), intent.choice(),
                intent.confidence(), intent.alternatives(), intent.contingencies(), intent.level(),
                intent.startTick(), intent.expiresAtNanos(), false));
        session.rearmChoice();
        return true;
    }

    /**
     * Tells whether a choice could be cast now as far as its cooldowns go: the extra cooldown of compatibility has
     * passed and the native cooldown of its skill too ({@link CombatActions#ready}, O(1)).
     */
    boolean ready(MobSession session, SkillChoice choice, long tick) {
        return ready(session, choice, choiceIndex(session, choice), tick);
    }

    /**
     * {@link #ready(MobSession, SkillChoice, long)} for a caller that already knows the declaration index (EFO-19).
     *
     * @param session the session
     * @param choice the choice
     * @param index declaration index of {@code choice} in {@link MobSession#listedChoices()}, or {@code -1}
     * @param tick current tick
     * @return whether its cooldowns let it be cast now
     */
    boolean ready(MobSession session, SkillChoice choice, int index, long tick) {
        if (index >= 0 && tick < session.whiffUntil(index)) {
            return false;
        }
        return tick - session.nextChoiceTick() >= 0 && offCooldown(session, choice, tick);
    }

    /**
     * Tells whether the native rhythm lets a choice be cast now: the {@code Cooldown} of a skill
     * ({@link CombatActions#ready}, O(1)), and for a vanilla melee choice also the vanilla attack interval
     * ({@link #VANILLA_MELEE_INTERVAL_TICKS}) since the last vanilla hit.
     */
    boolean offCooldown(MobSession session, SkillChoice choice, long tick) {
        return meleeIntervalPassed(session, choice, tick) && actions.ready(session.key(), choice);
    }

    /** A vanilla melee choice waits the vanilla attack interval after the last vanilla hit; any other choice never. */
    private static boolean meleeIntervalPassed(MobSession session, SkillChoice choice, long tick) {
        return choice.kind() != ChoiceKind.VANILLA_MELEE || tick - session.nextMeleeTick() >= 0;
    }

    /**
     * Records {@code EXPIRED} once for an expired Jev or alternative intent whose choice was never cast, and consumes it.
     * A local intent that expires is only planned again, so its choice is not lost and nothing is recorded.
     */
    void expireChoice(MobSession session, long tick, long nowNanos) {
        var intent = session.intent().orElse(null);
        if (intent != null && intent.expired(nowNanos) && !intent.skillConsumed() && intent.choice().isPresent()
                && intent.level() != ControlLevel.LOCAL) {
            record(session, intent, ExecutionOutcome.EXPIRED, tick, false);
            consume(session, intent, tick, false);
        }
    }

    /** The tactics eligible now: those of the last capture (M07), empty before the first one. */
    static Set<TacticId> eligible(MobSession session) {
        var facts = session.lastMobFacts().orElse(null);
        return facts == null ? Set.of() : facts.eligibleTactics();
    }

    /** Whether a cast of the choice counts as damaging for its window: its purpose, or the untyped default. */
    private static boolean damaging(SkillChoice choice) {
        var purpose = choice.purpose().orElse(null);
        return purpose == null ? PurposeRules.UNTYPED_DEFAULTS.damaging() : purpose.damaging();
    }

    /**
     * Makes an intent of {@code /thepredict debug intent} current like {@link #apply} and marks it, so the coordinator
     * leaves the session alone until it expires or is released (M12.3).
     */
    void applyDebug(MobSession session, TacticalIntent intent, long tick) {
        apply(session, intent, tick);
        session.debugIntent(true);
    }

    /**
     * Ends an expired debug intent: the session drops to level 4 with {@code EXPIRED} for {@code inspect}; it is not
     * counted as a decision of the fallback chain, which takes over again on the next reactive cycle.
     */
    void endDebug(MobSession session, long tick) {
        tap.dropped(session, FallbackReason.EXPIRED);
        session.dropIntent();
        session.invalidateReady();
        session.resetProgress();
        outcomes.close(session, tick);
        board.publish(session.key(), IntentView.base(session.key().generation()));
        session.fallback(FallbackReason.EXPIRED);
        log.decision(tick, () -> "Mob " + session.key().entityId() + " debug intent expired");
    }

    /**
     * Drops the intent of a session to level 4 at the request of {@code /thepredict debug release}; no fallback reason is
     * recorded because nothing failed. The fallback chain decides again on the next reactive cycle.
     */
    void release(MobSession session, long tick) {
        tap.dropped(session);
        session.dropIntent();
        session.invalidateReady();
        session.resetProgress();
        outcomes.close(session, tick);
        board.publish(session.key(), IntentView.base(session.key().generation()));
        log.decision(tick, () -> "Mob " + session.key().entityId() + " released by the debug command");
    }

    /**
     * Moves a reactive Jev deadline forward without adopting or renewing (M20.7). Only a reactive Jev intent whose
     * live signature still matches the signature saved at adoption, and whose expiry is inside two ticks, moves.
     * The saved capture time is the one of that adoption, never the clock of this visit and never a later capture.
     *
     * @param session session whose intent may be extended
     * @param nowNanos monotonic time
     */
    void extend(MobSession session, long nowNanos) {
        if (!extensionDue(session, nowNanos)) {
            return;
        }
        var intent = session.intent().orElseThrow();
        var timing = session.profile().timing();
        var horizon = timing.intentHorizonTicks() * IntentPolicy.NANOS_PER_TICK;
        var maximum = timing.maximumIntentTicks() * IntentPolicy.NANOS_PER_TICK;
        var proposed = Math.min(nowNanos + horizon, session.intentCaptureNanos() + maximum);
        var next = Math.max(intent.expiresAtNanos(), proposed);
        if (next == intent.expiresAtNanos()) {
            return;
        }
        var replaced = intent.withExpiresAtNanos(next);
        session.adoptIntent(replaced);
        StanceBoard.sync(board, session);
        metrics.layer(DecisionMetrics.LayerCounter.INTENT_EXTENDED, 1);
    }

    private boolean extensionDue(MobSession session, long nowNanos) {
        if (session.profile().timing().cadence() != DecisionCadence.REACTIVE) {
            return false;
        }
        var intent = session.intent().orElse(null);
        if (intent == null || intent.level() != ControlLevel.JEV || session.debugIntent()) {
            return false;
        }
        if (!session.intentCaptureHeld() || session.liveSignature() != session.intentCaptureSignature()) {
            return false;
        }
        return intent.expiresAtNanos() - nowNanos < 2L * IntentPolicy.NANOS_PER_TICK;
    }

    /** Readiness of a choice for rule 8. A vanilla melee is zero and does not ask further. */
    ReadyIn readyIn(MobKey mob, SkillChoice choice) {
        return actions.readyIn(mob, choice);
    }

    void showIntent(MobSession session, long nowNanos) {
        var intent = session.intent().orElse(null);
        if (intent == null || intent.expired(nowNanos)) {
            return;
        }
        StanceBoard.sync(board, session);
    }

    /**
     * Tells whether an unexpired intent of the debug command controls the mob; M10 then leaves the session alone
     * (M12.3).
     */
    boolean heldByDebug(MobSession session, long nowNanos) {
        var intent = session.intent().orElse(null);
        return session.debugIntent() && intent != null && !intent.expired(nowNanos);
    }

    /**
     * Counts an outcome once per intent and logs it only when the outcome, the tactic or the level changes, never on
     * every retry or local replan of the same tactic (rules C8, C12).
     */
    void record(MobSession session, TacticalIntent intent, ExecutionOutcome outcome, long tick, boolean readyCast) {
        var counted = session.newOutcomeForIntent(outcome, intent.tactic(), intent.startTick());
        var noteworthy = session.noteworthyOutcome(outcome, intent.tactic(), intent.level());
        session.outcome(outcome, intent.tactic(), intent.level(), intent.startTick());
        if (counted) {
            metrics.increment(outcome);
            if (readyCast) {
                tap.readyCast(session, intent, outcome);
            } else {
                tap.cast(session, intent, outcome);
            }
        }
        if (!noteworthy) {
            return;
        }
        log.decision(tick, () -> "Mob " + session.key().entityId() + " " + intent.tactic().value() + " choice "
                + intent.choice().orElseThrow().value() + ": " + outcome);
    }

    /**
     * Runs one due session (M19.4). The caller already compared {@code nextReadyTick}. Preconditions that fail
     * reschedule or invalidate and do not cast.
     */
    void castIfReady(MobSession session, long tick, long nowNanos) {
        metrics.add(DecisionMetrics.ReadyCounter.READY_CHECKS, 1);
        var intent = controlling(session, nowNanos);
        if (intent == null) {
            session.invalidateReady();
            return;
        }
        if (!targetStillExpected(session, intent)) {
            if (intent.choice().isPresent()) {
                record(session, intent, ExecutionOutcome.NOT_ELIGIBLE, tick, false);
            }
            session.noteContextMiss();
            session.scheduleReady(tick + 1);
            return;
        }
        var anyReady = !session.debugIntent() && session.profile().timing().castMode() == CastMode.ANY_READY;
        if (!anyReady && !intentTacticAllowed(session, intent)) {
            session.scheduleReady(tick + ReactiveLayer.CYCLE_TICKS);
            return;
        }
        if (!startsNow(session, tick, anyReady)) {
            return;
        }
        if (anyReady) {
            rearm(session, tick, nowNanos);
            ReadyCasts.fire(this, session, tick, nowNanos);
            return;
        }
        cast(session, tick, nowNanos);
    }

    /**
     * The intent that may cast in the ready step: active session of the scheduled generation, a controlling level and
     * not expired (M19.4, preconditions 1 and 2).
     *
     * @return the intent, or {@code null} when the scheduled cast must be dropped
     */
    private static TacticalIntent controlling(MobSession session, long nowNanos) {
        if (session.state() != SessionState.ACTIVE || session.readyGeneration() != session.key().generation()) {
            return null;
        }
        var intent = session.intent().orElse(null);
        if (intent == null || intent.level() == ControlLevel.BASE || intent.expired(nowNanos)) {
            return null;
        }
        return intent;
    }

    /**
     * The start guards of the ready step: one start per tick, and no start before the busy deadline unless the
     * wind-up may be overlapped ({@link #overlapAllowed}) or, in {@code ANY_READY}, a recovering mob has an escape or
     * a defense ready ({@link #escapeReady}, E-3). A refusal reschedules.
     *
     * @return whether the ready step may try a choice now
     */
    private boolean startsNow(MobSession session, long tick, boolean anyReady) {
        var busy = tick < session.busyUntilTick() && !overlapAllowed(session);
        if (busy && !(anyReady && session.actionStance() == akyrion.thepredict.domain.ActionStance.RECOVERY
                && escapeReady(session, tick))) {
            metrics.add(DecisionMetrics.ReadyCounter.BUSY_BLOCKED, 1);
            session.scheduleReady(session.busyUntilTick());
            return false;
        }
        if (session.lastCastStartTick() == tick) {
            session.scheduleReady(tick + 1);
            return false;
        }
        return true;
    }

    /**
     * Whether a choice that may start during {@code RECOVERY} is ready now: a non-damaging escape or defense
     * ({@link ReadyCastOrder#startsWhileBusy}) off its cooldowns. Only then does a recovering session of
     * {@code ANY_READY} go past the busy guard to the ready step; otherwise it waits for the busy deadline, as before.
     * Read only while a recovery holds the mob, with no allocation.
     */
    private boolean escapeReady(MobSession session, long tick) {
        var choices = session.listedChoices();
        for (var index = 0; index < choices.length; index++) {
            var choice = choices[index];
            if (ReadyCastOrder.startsWhileBusy(choice, akyrion.thepredict.domain.ActionStance.RECOVERY)
                    && !session.choiceHeld(index, tick) && ready(session, choice, index, tick)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pulls {@code nextReadyTick} earlier when a native cooldown ends between reactive cycles (M19.4). A choice whose
     * cached ready tick is more than two ticks ahead is not read again inside the reactive cycle of its read; the next
     * cycle reads it again (SA-13). A held choice of the intent, such as one a zone deferral holds for its step, does
     * not pull before its hold ends (EFO-10).
     */
    void recalibrate(MobSession session, long tick) {
        if (session.state() != SessionState.ACTIVE || session.intent().isEmpty()) {
            return;
        }
        var at = Long.MAX_VALUE;
        var choice = intentChoice(session);
        if (choice != null) {
            var index = choiceIndex(session, choice);
            at = cachedReady(session, choice, index, tick, true);
            if (index >= 0) {
                at = Math.max(at, session.choiceFreeTick(index));
            }
        }
        if (!session.debugIntent() && session.profile().timing().castMode() == CastMode.ANY_READY) {
            at = Math.min(at, ReadyCasts.soonest(this, session, tick, true));
        }
        if (at <= tick && at < session.nextReadyTick()) {
            session.scheduleReady(tick);
        }
    }

    private static int choiceIndex(MobSession session, SkillChoice choice) {
        var choices = session.listedChoices();
        for (var index = 0; index < choices.length; index++) {
            if (choices[index] == choice || choices[index].id().equals(choice.id())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * The captured target is still the live one (M19.4, precondition 3). Without a captured target the per-choice
     * rule in {@link #fire} stays in force. A native {@code targetwithin} on the intent choice also requires the AI
     * target. Neither check casts.
     */
    private boolean targetStillExpected(MobSession session, TacticalIntent intent) {
        var expected = session.lastTargetId();
        if (expected.isEmpty()) {
            return true;
        }
        if (!actions.liveTargetMatches(session.key(), expected.get())) {
            return false;
        }
        var choice = intent.choice().isPresent() ? session.profile().choices().get(intent.choice().get()) : null;
        return choice == null || choice.nativeFacts().targetWithinBlocks().isEmpty()
                || actions.aiTargetMatches(session.key(), expected.get());
    }

    /**
     * Counts a cast of a candidate that is not the choice of the intent: the window, the wind-up and the tap, without
     * consuming the intent choice or signalling (M19.5).
     */
    void noteCandidateCast(MobSession session, SkillChoice choice, long tick) {
        outcomes.cast(session, damaging(choice), tick, true);
        session.noteCastStarted(tick);
        session.lastDanger(akyrion.thepredict.domain.DangerRules.classify(choice).ordinal());
        session.busyUntil(Math.max(session.busyUntilTick(), tick + (long) choice.busyTicks()));
        noteImpact(session, choice, tick);
        TellOpening.afterCast(session, tick);
        tap.posture(session, tick);
        session.difficulty().clearAnchors();
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            session.nextMeleeTick(tick + VANILLA_MELEE_INTERVAL_TICKS);
        }
        metrics.add(DecisionMetrics.ReadyCounter.READY_CASTS, 1);
        metrics.readyLatency(session.takeReadyLatency(choiceIndex(session, choice), tick));
        metrics.increment(ExecutionOutcome.CAST);
    }

    /**
     * Notes the hit of a cast that started now in the ring of own hits in flight (E-1): it lands after the wind-up of
     * the choice, on the captured target. A cast without a damaging hit, or without a captured target, notes nothing.
     *
     * @param session the caster
     * @param choice the choice that was cast
     * @param tick the server tick of the start
     */
    static void noteImpact(MobSession session, SkillChoice choice, long tick) {
        var target = session.lastTargetId();
        if (target.isPresent() && OwnImpacts.hits(choice)) {
            session.ownImpacts().note(target.get(), tick + choice.busyTicks(), OwnImpacts.weight(choice), tick);
        }
    }

    /** The intent tactic may fire when there is no capture yet, or it is eligible once its cooldown ends. */
    private static boolean intentTacticAllowed(MobSession session, TacticalIntent intent) {
        if (session.debugIntent() || intent.skillConsumed()) {
            return true;
        }
        var facts = session.lastMobFacts();
        return facts.isEmpty() || facts.get().eligibleWhenReady().contains(intent.tactic());
    }

    /**
     * Releases a due tell. Does not call {@link #castIfReady}.
     *
     * @param session the caster
     * @param tick server tick
     * @return the outcome
     */
    akyrion.thepredict.domain.ReleaseOutcome releaseTell(MobSession session, long tick) {
        var open = session.tellOpen();
        var index = session.tellChoice();
        var choices = session.listedChoices();
        var choice = index >= 0 && index < choices.length ? choices[index] : null;
        var outcome = TelegraphRelease.release(session, tick, actions, stanceStep, this::noteHazard);
        if (open) {
            noteRelease(session, choice, tick, outcome);
        }
        StanceBoard.sync(board, session);
        StanceClock.flushConflict(session, tick, log, metrics);
        tap.posture(session, tick);
        return outcome;
    }

    private void noteRelease(MobSession session, SkillChoice choice, long tick,
                             akyrion.thepredict.domain.ReleaseOutcome outcome) {
        switch (outcome) {
            case RELEASED, WHIFFED, TARGET_LOST -> metrics.noteTellEnd(tick);
            case REJECTED -> {
                metrics.noteTellEnd(tick);
                metrics.noteSelfAbort(tick);
                if (choice != null && !actions.ready(session.key(), choice)) {
                    metrics.add(DecisionMetrics.TellCounter.WHIFF_COOLDOWN_BLOCKED, 1);
                }
            }
            case SESSION_INVALID -> {
                metrics.noteTellEnd(tick);
                metrics.noteSelfAbort(tick);
            }
            case DEFERRED_INVULNERABLE, RELEASE_DEFERRED ->
                    metrics.add(DecisionMetrics.TellCounter.RELEASE_DEFERRED, 1);
        }
    }

    /**
     * Advances a due action stance.
     *
     * @param session the mob
     * @param tick server tick
     */
    void advanceStance(MobSession session, long tick) {
        StanceClock.advance(session, tick, actions, stanceStep);
        StanceBoard.sync(board, session);
        StanceClock.flushConflict(session, tick, log, metrics);
        tap.posture(session, tick);
    }

    /**
     * Settles a motivated cast whose hit window passed without a hit and emits its {@code pattern_exploit} event, on
     * the reactive cycle (E-10). A cast still inside its window keeps waiting.
     *
     * @param session the mob
     * @param tick server tick
     */
    void settleExploit(MobSession session, long tick) {
        if (session.pendingExploit().waiting()) {
            session.expireMotivated(tick);
        }
        tap.flushExploit(session);
    }

    /**
     * Re-evaluates the base on the reactive cycle.
     *
     * @param session the mob
     * @param tick server tick
     */
    void evaluateStance(MobSession session, long tick) {
        StanceClock.evaluateBase(session, tick, actions, stanceStep);
        StanceBoard.sync(board, session);
        StanceClock.flushConflict(session, tick, log, metrics);
        tap.posture(session, tick);
    }
}
