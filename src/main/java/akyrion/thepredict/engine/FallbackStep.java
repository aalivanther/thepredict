package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActiveUseConditions;
import akyrion.thepredict.domain.CandidatePolicy;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.FallbackPolicy;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.HeldIntentRevalidation;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.LocalPlanner;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.learn.PatternExploit;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.UseCondition;
import akyrion.thepredict.engine.learn.LearnedLocalPick;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Levels 3 and 4 of the fallback chain for one session without a valid Jev intent (M10.6, plan §8). Package-private;
 * server thread only; it never waits for the network.
 *
 * <p>{@link FallbackPolicy} chooses the level from the service state, the budget, the low-confidence streak and the
 * valid intent. The local planner has a tactic only for a mob with installed goals whose last capture and current
 * reading perceive a target: a mob out of combat, or whose target lies beyond every perception radius, keeps its base
 * AI (M07). With a healthy service and no low-confidence streak, the local planner also covers the wait for an answer
 * (the {@code REQUESTING} state of plan §8): until Jev answers about a generation, and afterwards while the session is
 * in an open encounter, whose next answer is always on its way, so an intent that expires between two answers never
 * hands the mob to the base AI for a heartbeat; a Jev or alternative intent that ends this way records
 * {@code EXPIRED}. After a low-confidence rejection the mob yields to the base AI instead (plan §6) until the streak
 * reaches {@code decision.lowConfidenceStreakForLocal}, when {@link FallbackPolicy} gives {@code LOCAL} with
 * {@code LOW_CONFIDENCE}. A local intent has confidence 0 and lives
 * {@code min(maximumIntentTicks, minimumDecisionIntervalTicks)} ticks; it is planned again when it expires while the
 * local mode lasts.
 *
 * <p>Real-time readiness (M10, user decision of 2026-09-30): the local planner also runs over the tactics of
 * {@code MobFacts.eligibleWhenReady} whose skill is off its native cooldown now ({@link CombatActions#ready}); when it
 * picks a tactic whose choice is ready (the extra cooldown passed too), a session without a valid intent plans that
 * tactic, and a valid local intent whose choice is consumed or not ready does not wait for its expiry: the same tactic
 * gets its choice back, and another tactic replaces it once the current one has lasted
 * {@code minimumDecisionIntervalTicks} or is no longer eligible, so readiness never switches tactics faster than the
 * minimum hold allows. Without a ready pick, an expired local intent whose tactic the planner still picks with every
 * skill off cooldown keeps that tactic while its skill cools down; otherwise the planner's tactic of the last capture is
 * planned, as before. When no tactic of the governing context can cast now, because every skill it allows is cooling
 * down, the local plan waits with the tactic the planner picks among {@code MobFacts.eligibleWhenReady}: its movement
 * holds and its skill is cast as soon as it is ready, instead of handing the mob to the base AI until a skill of the
 * context comes back (2026-10-14). The level rule of {@link FallbackPolicy} does not change.
 */
final class FallbackStep {
    /** The reason of a drop by expiry, built once so the fallback allocates nothing for it. */
    private static final Optional<FallbackReason> EXPIRED = Optional.of(FallbackReason.EXPIRED);
    /** The reason of a retreat refused by the coordination, built once. */
    private static final Optional<FallbackReason> RETREAT_LIMIT = Optional.of(FallbackReason.RETREAT_LIMIT);
    private final ServiceStatusTracker tracker;
    private final EncounterRegistry encounters;
    private final RequestBudget budget;
    private final IntentControl control;
    private final ExecutionSettings settings;

    FallbackStep(ServiceStatusTracker tracker, EncounterRegistry encounters, RequestBudget budget, IntentControl control,
                 ExecutionSettings settings) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.control = Objects.requireNonNull(control, "control");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Chooses and applies the level of an active session. The local tactic is, in order, the ready pick, the kept tactic
     * and the tactic of the last capture; with a valid local intent only whether one exists matters, so the cheaper
     * tactic of the capture is tried before the kept one. A retreat that the kill window ({@link KillWindow}) or the
     * absence of a pursuer ({@link Pursuit}) rules out gives way to the best tactic of the chain that stays.
     *
     * @param validLevel level of the intent that is still valid (unexpired, same target), {@code BASE} when none is
     * @param targetPerceived whether the current reading still perceives the target of the last capture
     */
    void apply(MobSession session, UUID worldId, ControlLevel validLevel, boolean targetPerceived, long tick,
               long nowNanos) {
        var preferred = preferredTactic(session, targetPerceived, tick);
        var stay = KillWindow.open(session) && KillWindow.retreating(session) || Pursuit.absent(session);
        var ready = (preferred.isPresent() ? Optional.<Tactic>empty()
                : waitingForReadiness(session, validLevel, tick) ? readyTactic(session, targetPerceived, tick)
                : Optional.<Tactic>empty()).filter(tactic -> !stay || KillWindow.stays(tactic));
        var chained = chainTactic(session, validLevel, preferred, ready, targetPerceived, tick);
        var local = stay && chained.isPresent() && !KillWindow.stays(chained.get())
                ? KillWindow.press(chained, stayingTactic(session, targetPerceived, tick)) : chained;
        var service = tracker.state();
        var budgetAvailable = service != ServiceState.READY || budget.available(worldId, nowNanos);
        var decision = FallbackPolicy.level(service, budgetAvailable, session.lowConfidenceStreak(),
                settings.lowConfidenceStreakForLocal(), true, validLevel, local.isPresent());
        var awaiting = decision.level() == ControlLevel.BASE && local.isPresent()
                && session.lowConfidenceStreak() == 0 && (!session.jevAnswered() || answerComing(session));
        var level = awaiting ? ControlLevel.LOCAL : decision.level();
        if (level == ControlLevel.BASE) {
            control.toBase(session, decision.reason().orElse(FallbackReason.EXPIRED), tick);
        } else if (level == ControlLevel.LOCAL && validLevel != ControlLevel.LOCAL) {
            planFromChain(session, local.orElseThrow(), awaiting ? Optional.empty() : decision.reason(), awaiting,
                    tick, nowNanos);
        } else if (level == ControlLevel.LOCAL && ready.isPresent()) {
            replanForReadiness(session, ready.get(), tick, nowNanos);
        } else if (level == ControlLevel.LOCAL && stay && KillWindow.retreating(session)
                && local.filter(KillWindow::stays).isPresent()) {
            planLocal(session, local.get(), Optional.empty(), tick, nowNanos);
        }
    }

    /**
     * The local tactic of the chain: the preferred one, else, with a valid local intent, the ready pick, the tactic of
     * the capture and the kept one, and without it the ready pick, the kept one and the tactic of the capture; the wait
     * of the context last.
     */
    private Optional<Tactic> chainTactic(MobSession session, ControlLevel validLevel, Optional<Tactic> preferred,
                                         Optional<Tactic> ready, boolean targetPerceived, long tick) {
        if (preferred.isPresent()) {
            return preferred;
        }
        return (validLevel == ControlLevel.LOCAL
                ? ready.or(() -> localTactic(session, targetPerceived, false, tick))
                        .or(() -> keptTactic(session, targetPerceived))
                : ready.or(() -> keptTactic(session, targetPerceived))
                        .or(() -> localTactic(session, targetPerceived, false, tick)))
                .or(() -> waitingTactic(session, targetPerceived));
    }

    /** The best local tactic that does not retreat, for the kill window: the capture's, else the context's wait. */
    private Optional<Tactic> stayingTactic(MobSession session, boolean targetPerceived, long tick) {
        return localTactic(session, targetPerceived, true, tick).or(() -> stayingWait(session, targetPerceived));
    }

    /**
     * The wait of {@link #waitingTactic} among the tactics that do not retreat, for the kill window: the planner's pick
     * of the governing context without its retreats, empty when every one of them retreats.
     */
    private Optional<Tactic> stayingWait(MobSession session, boolean targetPerceived) {
        if (!targetPerceived || session.installPending() || session.installedRevision().isEmpty()) {
            return Optional.empty();
        }
        var facts = session.lastMobFacts().filter(found -> found.target().isPresent());
        if (facts.isEmpty()) {
            return Optional.empty();
        }
        var captured = facts.get();
        var staying = new LinkedHashSet<TacticId>();
        for (var tacticId : captured.eligibleWhenReady()) {
            if (session.profile().tactic(tacticId).filter(KillWindow::stays).isPresent()) {
                staying.add(tacticId);
            }
        }
        return LocalPlanner.choose(session.profile(), captured.activeContexts(), staying,
                useConditions(session, captured));
    }

    /**
     * Plans a local intent for a session without a valid one and records the drop that brought it there: none while it
     * waits for a first answer, {@code EXPIRED} when a Jev or alternative intent ended, and otherwise the reason of the
     * level rule; a local intent planned again records nothing.
     */
    private void planFromChain(MobSession session, Tactic tactic, Optional<FallbackReason> ruleReason, boolean awaiting,
                               long tick, long nowNanos) {
        var previous = session.intent().map(TacticalIntent::level).orElse(ControlLevel.BASE);
        var answered = previous == ControlLevel.JEV || previous == ControlLevel.ALTERNATIVE;
        var reason = awaiting ? (answered ? EXPIRED : Optional.<FallbackReason>empty()) : ruleReason;
        planLocal(session, tactic, previous == ControlLevel.LOCAL ? Optional.empty() : reason, tick, nowNanos);
        if (previous != ControlLevel.LOCAL) {
            reason.ifPresent(cause -> control.dropped(session, cause, tick));
        }
    }

    /**
     * The tactic of the current local intent when the local planner still picks it with every skill off cooldown (M10,
     * real-time readiness): a local tactic whose skill only cools down is kept, as a renewed Jev intent is, instead of
     * handing the mob to another tactic for the cooldown and coming back once the minimum hold of that tactic allows it.
     * Empty when the current intent is not local or the planner prefers another tactic for any other reason.
     */
    private Optional<Tactic> keptTactic(MobSession session, boolean targetPerceived) {
        var current = session.intent().filter(intent -> intent.level() == ControlLevel.LOCAL);
        if (current.isEmpty() || !targetPerceived || session.installPending()
                || session.installedRevision().isEmpty()) {
            return Optional.empty();
        }
        var facts = session.lastMobFacts().filter(found -> found.target().isPresent());
        var resolved = session.resolvedTactic();
        if (resolved.isPresent()) {
            return resolved.get().equals(current.get().tactic()) ? session.profile().tactic(current.get().tactic())
                    : Optional.empty();
        }
        var prepared = session.candidates().filter(set -> !set.candidates().isEmpty());
        if (prepared.isPresent()) {
            return session.profile().tactic(prepared.get().candidates()
                    .get(prepared.get().localPick(session.levers())).tactic());
        }
        return facts.flatMap(found -> {
            var eligible = found.eligibleWhenReady();
            var chosen = LocalPlanner.choose(session.profile(), found.activeContexts(), eligible,
                    useConditions(session, found));
            return raisedTactic(session, found, eligible).or(() -> chosen)
                    .filter(tactic -> tactic.id().equals(current.get().tactic()));
        });
    }

    /**
     * The tactic the local planner picks with every skill off cooldown, among the tactics of the governing context of
     * the last capture: the wait of a mob whose allowed skills are all cooling down. Empty without installed goals, a
     * perceived target or such a tactic, which leaves the session to the base AI as before.
     */
    private Optional<Tactic> waitingTactic(MobSession session, boolean targetPerceived) {
        if (!targetPerceived || session.installPending() || session.installedRevision().isEmpty()) {
            return Optional.empty();
        }
        var facts = session.lastMobFacts().filter(found -> found.target().isPresent());
        if (facts.isEmpty()) {
            return Optional.empty();
        }
        var captured = facts.get();
        return LocalPlanner.choose(session.profile(), captured.activeContexts(), captured.eligibleWhenReady(),
                useConditions(session, captured));
    }

    /**
     * Tells whether the local plan may need a tactic whose choice is ready: always without a valid local intent, and
     * with one only while its choice cannot be cast now (none, consumed, or held by a cooldown), so a local intent whose
     * choice is armed and ready costs nothing more.
     */
    private boolean waitingForReadiness(MobSession session, ControlLevel validLevel, long tick) {
        if (validLevel != ControlLevel.LOCAL) {
            return true;
        }
        var intent = session.intent().orElseThrow();
        var choice = intent.choice().isPresent() ? session.profile().choices().get(intent.choice().get()) : null;
        return intent.skillConsumed() || choice == null || !control.ready(session, choice, tick)
                || !whenReady(session).contains(intent.tactic());
    }

    /**
     * The tactic the local planner picks with the cooldowns read now, when its choice is ready (M10, real-time
     * readiness): the planner runs over {@code MobFacts.eligibleWhenReady} of the last capture without the tactics whose
     * skill is on its native cooldown now, with the same use conditions; the pick counts only if it has a choice and the
     * extra cooldown has passed. No capture, path or measure is made: one O(1) cooldown read per eligible tactic, and
     * the planner runs, allocating its set, only when some tactic with a choice is ready.
     */
    private Optional<Tactic> readyTactic(MobSession session, boolean targetPerceived, long tick) {
        if (!targetPerceived || session.installPending() || session.installedRevision().isEmpty()
                || tick - session.nextChoiceTick() < 0) {
            return Optional.empty();
        }
        var facts = session.lastMobFacts().orElse(null);
        if (facts == null || facts.target().isEmpty() || !anyChoiceReady(session, facts.eligibleWhenReady(), tick)) {
            return Optional.empty();
        }
        var profile = session.profile();
        var ready = new LinkedHashSet<TacticId>();
        for (var tacticId : facts.eligibleWhenReady()) {
            var choice = choiceOf(profile, tacticId);
            if (choice == null || control.offCooldown(session, choice, tick)) {
                ready.add(tacticId);
            }
        }
        var resolved = session.resolvedTactic();
        if (resolved.isPresent()) {
            ready.clear();
            if (facts.eligibleWhenReady().contains(resolved.get())) {
                var choice = choiceOf(profile, resolved.get());
                if (choice == null || control.offCooldown(session, choice, tick)) {
                    ready.add(resolved.get());
                }
            }
        }
        var chosen = LocalPlanner.choose(profile, facts.activeContexts(), ready, useConditions(session, facts))
                .filter(tactic -> tactic.choice().isPresent());
        var lifted = raisedTactic(session, facts, ready).filter(tactic -> tactic.choice().isPresent());
        return lifted.isPresent() ? lifted : chosen;
    }

    /** Tells, without allocating, whether a tactic with a choice off its native cooldown is among {@code tactics}. */
    private boolean anyChoiceReady(MobSession session, Set<TacticId> tactics, long tick) {
        for (var tacticId : tactics) {
            var choice = choiceOf(session.profile(), tacticId);
            if (choice != null && control.offCooldown(session, choice, tick)) {
                return true;
            }
        }
        return false;
    }

    /** The choice of a tactic of the profile, {@code null} for a tactic without one. */
    private static SkillChoice choiceOf(TacticalProfile profile, TacticId tacticId) {
        var tactic = profile.tactics().get(tacticId);
        return tactic == null || tactic.choice().isEmpty() ? null : profile.choices().get(tactic.choice().get());
    }

    /**
     * Acts on a ready pick for a valid local intent (M10, real-time readiness): the same tactic gets its consumed
     * choice back once it is ready; another tactic replaces the current one only once it has lasted
     * {@code minimumDecisionIntervalTicks} since it started, or when it is no longer eligible, so readiness never makes
     * the local tactic flap. The replan is a new local intent without a drop reason. While the minimum hold keeps the
     * current tactic, its own choice is still given back as soon as it is ready (EFO-05): the hold protects the
     * movement, not an idle mob. Entropy paces only the change of tactic ({@code SessionDifficulty.localDue}), and only
     * a change that happens spends its interval; the same tactic gets its ready choice back at once, so entropy never
     * reacts more slowly than the normal mode (E22-4, PZ-11).
     */
    private void replanForReadiness(MobSession session, Tactic ready, long tick, long nowNanos) {
        var intent = session.intent().orElseThrow();
        if (ready.id().equals(intent.tactic())) {
            control.rearmWhenReady(session, intent, tick);
            return;
        }
        var held = tick - session.tacticStartTick() >= session.profile().timing().minimumDecisionIntervalTicks();
        if ((held || !whenReady(session).contains(intent.tactic()))
                && session.difficulty().localDue(tick, localIntentTicks(session.profile().timing()))) {
            planLocal(session, ready, Optional.empty(), tick, nowNanos);
            return;
        }
        control.rearmWhenReady(session, intent, tick);
    }

    /** The tactics of the last capture that would be eligible with every skill off cooldown, empty before one. */
    private static Set<TacticId> whenReady(MobSession session) {
        return session.lastMobFacts().map(MobFacts::eligibleWhenReady).orElse(Set.of());
    }

    /**
     * Tells whether an answer is on its way: the session belongs to an open encounter, which always has its request in
     * flight or its next evaluation scheduled within one heartbeat (M09.5), so an expired intent waits locally, not in
     * the base AI, as while waiting for the first answer.
     */
    private boolean answerComing(MobSession session) {
        return session.encounter().flatMap(encounters::find).filter(encounter -> !encounter.isClosed()).isPresent();
    }

    /**
     * Plans locally without retreating, for a retreat the coordination of the encounter refused (M10.7); without
     * such a tactic the session goes to level 4. Both record {@code RETREAT_LIMIT}.
     */
    void withoutRetreat(MobSession session, long tick, long nowNanos) {
        var local = localTactic(session, true, true, tick);
        if (local.isEmpty()) {
            control.toBase(session, FallbackReason.RETREAT_LIMIT, tick);
            return;
        }
        var previous = session.intent().map(TacticalIntent::level).orElse(ControlLevel.BASE);
        if (previous == ControlLevel.LOCAL) {
            planLocal(session, local.get(), Optional.empty(), tick, nowNanos);
            return;
        }
        planLocal(session, local.get(), RETREAT_LIMIT, tick, nowNanos);
        control.dropped(session, FallbackReason.RETREAT_LIMIT, tick);
    }

    /** Applies a new local intent; {@code reason} is the reason of the drop that the tap hears with it (M18.4). */
    private void planLocal(MobSession session, Tactic tactic, Optional<FallbackReason> reason, long tick,
                           long nowNanos) {
        var lifetimeTicks = localIntentTicks(session.profile().timing());
        control.applyLocal(session, new TacticalIntent(session.key(), tactic.id(), tactic.movement(), tactic.choice(),
                0.0, List.of(), Map.of(), ControlLevel.LOCAL, tick,
                nowNanos + lifetimeTicks * IntentPolicy.NANOS_PER_TICK, false), reason, tick);
    }

    /**
     * Lifetime of a local intent, {@code min(maximumIntentTicks, minimumDecisionIntervalTicks)}: the cadence at which
     * the local mode plans again when no ready choice asks for it sooner, and the wait before a choice consumed by
     * anything but a {@code CAST} is re-armed (M10, real-time readiness).
     */
    static int localIntentTicks(DecisionTiming timing) {
        return Math.min(timing.maximumIntentTicks(), timing.minimumDecisionIntervalTicks());
    }

    /**
     * The local tactic of a session with installed goals and a perceived target, if any; the declared {@code BestWhen}
     * of each skill breaks ties with the use conditions of the last capture (M15.8).
     */
    private Optional<Tactic> localTactic(MobSession session, boolean targetPerceived, boolean withoutRetreat,
                                         long tick) {
        if (!targetPerceived || session.installPending() || session.installedRevision().isEmpty()) {
            return Optional.empty();
        }
        var facts = session.lastMobFacts().filter(found -> found.target().isPresent());
        if (facts.isEmpty()) {
            return Optional.empty();
        }
        var eligible = facts.get().eligibleTactics();
        if (withoutRetreat) {
            var kept = new LinkedHashSet<>(eligible);
            kept.removeIf(tactic -> session.profile().tactic(tactic).map(Tactic::movement).orElse(Movement.NONE)
                    == Movement.RETREAT);
            eligible = kept;
        }
        var resolved = session.resolvedTactic();
        if (resolved.isPresent() && eligible.contains(resolved.get())) {
            eligible = Set.of(resolved.get());
        }
        var prepared = session.candidates().filter(set -> !set.candidates().isEmpty());
        if (prepared.isPresent()) {
            var lead = prepared.get().candidates().get(prepared.get().localPick(session.levers())).tactic();
            if (eligible.contains(lead)) {
                return session.profile().tactic(lead);
            }
        }
        var captured = facts.get();
        var chosen = LocalPlanner.choose(session.profile(), captured.activeContexts(), eligible,
                useConditions(session, captured));
        return raisedTactic(session, captured, eligible).or(() -> chosen);
    }

    /**
     * A held skip, then the learned pick. Both win over a ready skill, so a table can select a tactic with no cast
     * and a pattern query can select the cast. Absence leaves the ready path unchanged.
     */
    private Optional<Tactic> preferredTactic(MobSession session, boolean targetPerceived, long tick) {
        if (!targetPerceived || session.installPending() || session.installedRevision().isEmpty()) {
            return Optional.empty();
        }
        var facts = session.lastMobFacts().filter(found -> found.target().isPresent());
        if (facts.isEmpty()) {
            return Optional.empty();
        }
        var eligible = facts.get().eligibleTactics();
        var held = heldTactic(session, eligible, tick);
        return held.isPresent() ? held : learnedTactic(session, eligible, tick);
    }

    /**
     * A held skip wins over the prepared set (errata G5 and E28-13 applied). Absence leaves the chain unchanged.
     * A failed revalidation drops the hold and falls through.
     */
    private Optional<Tactic> heldTactic(MobSession session, Set<TacticId> eligible, long tick) {
        var found = session.learning().held();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var intent = found.get();
        var failed = !eligible.contains(intent.tactic());
        var result = HeldIntentRevalidation.check(tick, intent.expiresTick(), intent.captureSignature(),
                session.liveSignature(), failed);
        if (result == HeldIntentRevalidation.Result.KEEP || result == HeldIntentRevalidation.Result.AGED) {
            return session.profile().tactic(intent.tactic());
        }
        if (result == HeldIntentRevalidation.Result.SITUATION_CHANGED) {
            session.learning().noteSkip(heldCause(intent.behavior(), true));
        } else if (result == HeldIntentRevalidation.Result.EXPIRED) {
            session.learning().noteSkip(heldCause(intent.behavior(), false));
        }
        session.learning().release();
        return Optional.empty();
    }

    private static akyrion.thepredict.domain.learn.LearnCause heldCause(
            akyrion.thepredict.domain.BehaviorPolicy behavior, boolean changed) {
        if (behavior == akyrion.thepredict.domain.BehaviorPolicy.DISTILLED) {
            return changed ? akyrion.thepredict.domain.learn.LearnCause.DISTILLED_SITUATION_CHANGED
                    : akyrion.thepredict.domain.learn.LearnCause.DISTILLED_EXPIRED;
        }
        return changed ? akyrion.thepredict.domain.learn.LearnCause.VOI_SKIP_SITUATION_CHANGED
                : akyrion.thepredict.domain.learn.LearnCause.VOI_SKIP_EXPIRED;
    }

    /**
     * Learned leader. Absent unless scoring is {@code learned} and a value table is active, so the default chain
     * is unchanged.
     */
    private Optional<Tactic> learnedTactic(MobSession session, Set<TacticId> eligible, long tick) {
        if (session.candidateScoring() != CandidateScoring.LEARNED || !session.learning().tableActive()) {
            return Optional.empty();
        }
        var tactics = eligibleTactics(session, eligible);
        var learning = session.learning();
        var ones = new double[tactics.size()];
        java.util.Arrays.fill(ones, 1.0);
        var none = CellLookup.lead(session, tactics, false, 20, 20, ones);
        var weights = learning.localWeights(session.entropyActive(), ids(tactics));
        var local = !CellLookup.patternContextDiffers(session) && uniform(weights) ? none
                : CellLookup.lead(session, tactics, true, 20, 20, weights);
        notePattern(session, none, local, tick);
        var executed = local.isPresent() && session.patternMode() != akyrion.thepredict.domain.PatternMode.OBSERVE
                ? local : none;
        return executed.flatMap(id -> session.profile().tactic(id));
    }

    private static boolean uniform(double[] weights) {
        for (var weight : weights) {
            if (weight != 1.0) {
                return false;
            }
        }
        return true;
    }

    /** The ids of the tactics, in order, so each local weight is read by the tactic it belongs to (LD-16). */
    private static List<String> ids(List<Tactic> tactics) {
        var ids = new ArrayList<String>(tactics.size());
        for (var tactic : tactics) {
            ids.add(tactic.id().value());
        }
        return ids;
    }

    private static List<Tactic> eligibleTactics(MobSession session, Set<TacticId> eligible) {
        var tactics = new ArrayList<Tactic>();
        for (var tactic : session.profile().tactics().values()) {
            if (eligible.contains(tactic.id())) {
                tactics.add(tactic);
            }
        }
        return tactics;
    }

    /**
     * The pattern query moves only {@code localPick}. Observe leaves every decision. A moved pick spends the budget
     * on the server tick. The pattern of the pick, and its group, is the held pattern of lowest ordinal (A7, C16,
     * LD-33): the held bits are a mask, not an ordinal. Without a held pattern (the pattern came with the context of
     * the learned table) it is the first ordinal, as before.
     */
    private void notePattern(MobSession session, Optional<TacticId> none, Optional<TacticId> local, long tick) {
        if (!session.learning().learnedSource().isEmpty() || none.isEmpty() || local.isEmpty()
                || local.get().equals(none.get())
                || session.patternMode() == akyrion.thepredict.domain.PatternMode.OBSERVE) {
            return;
        }
        var held = session.patternHeld();
        var pattern = akyrion.thepredict.domain.EnemyPattern.values()[held == 0 ? 0
                : Integer.numberOfTrailingZeros(held)];
        var group = pattern.group();
        var quota = session.exploitBudget().allows((int) tick, session.patternWindow(), session.patternQuota());
        var allows = akyrion.thepredict.domain.ExploitPolicy.allowsNow(session.patternMode(),
                session.patternResting(tick), session.learning().openPatterns(), session.patternVariety(), quota);
        var decision = PatternExploit.decide(group, allows, 1, 0);
        if (!decision.motivated()) {
            return;
        }
        session.noteCastQuota((int) tick, true);
        session.learning().noteOpenPattern();
        session.learning().noteLearned(decision.source(), akyrion.thepredict.engine.learn.LearnedResult.NEUTRAL);
        var target = session.lastTargetId().orElse(session.key().entityId());
        control.tap().patternExploited(session.key(), target,
                new akyrion.thepredict.domain.PatternExploit(pattern,
                        akyrion.thepredict.domain.CounterBias.OPPORTUNITY, false, decision.source()),
                "", local.get().value(), session.exploitBudget().motivated((int) tick, session.patternWindow()));
    }

    /** The tactic the pattern factor alone raises, empty unless utility scoring changes the leader. */
    private Optional<Tactic> raisedTactic(MobSession session, MobFacts facts, Set<TacticId> eligible) {
        if (session.candidateScoring() != akyrion.thepredict.domain.CandidateScoring.UTILITY
                || session.patternHeld() == 0 || eligible.isEmpty()) {
            return Optional.empty();
        }
        var tactics = new ArrayList<Tactic>();
        for (var tactic : session.profile().tactics().values()) {
            if (eligible.contains(tactic.id())) {
                tactics.add(tactic);
            }
        }
        if (tactics.isEmpty()) {
            return Optional.empty();
        }
        var rank = CandidatePolicy.rank(session.candidateScoring(), session.profile(), tactics,
                useConditions(session, facts), session.patternHeld());
        if (!rank.raisedByPattern()) {
            return Optional.empty();
        }
        return session.profile().tactic(rank.order().get(0));
    }

    /** Use conditions of the last capture: the mob facts and its target among the participants of the encounter. */
    Set<UseCondition> useConditions(MobSession session, MobFacts facts) {
        var participants = session.encounter().flatMap(encounters::find).flatMap(Encounter::lastFacts)
                .map(CombatFacts::participants).orElse(List.of());
        return ActiveUseConditions.of(facts, participants);
    }
}
