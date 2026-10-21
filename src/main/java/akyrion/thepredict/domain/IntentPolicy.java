package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure rule that turns an answer into an intent: adopt, renew or reject. Stateless; time enters
 * as parameters; safe on any thread.
 *
 * <p>A tactic is applicable only if its probability is positive, it is eligible now and the profile
 * declares it; contingency tactics must have a positive probability and be declared. An answer whose confidence is below {@link DecisionTiming#minimumConfidence()} is rejected with
 * {@code LOW_CONFIDENCE}; an answer without an applicable tactic is rejected with
 * {@code INVALID_RESPONSE}. A rejection leaves the current intent running. A current Jev intent is
 * replaced only if the best tactic beats it by {@link DecisionTiming#switchMargin()} and it was
 * held for {@link DecisionTiming#minimumDecisionIntervalTicks()}, with a strictly positive gain even
 * when the margin is zero; otherwise it is renewed. A
 * current intent of another level, expired or no longer eligible is replaced at once. Intents keep
 * up to {@value TacticalIntent#MAX_ALTERNATIVES} applicable alternatives and the
 * contingencies whose answers reach the minimum confidence.
 *
 * <p>With fit checks (M16.5) the policy delegates to {@link FitGate}: an option that is not applicable counts as vetoed
 * whatever the veto, the verdict chooses the tactic and its level ({@code JEV} or {@code ALTERNATIVE}), and a vetoed option is
 * never kept as an alternative. Without fits the rule above applies unchanged.
 */
public final class IntentPolicy {
    /** Nominal duration of one server tick, used to turn intent lifetimes into deadlines. */
    public static final long NANOS_PER_TICK = 50_000_000L;

    private static final List<Situation> CONTINGENCY_ORDER =
            List.of(Situation.IF_OWN_HEALTH_CRITICAL, Situation.IF_TARGET_IN_MELEE);

    private IntentPolicy() {
    }

    /**
     * Offers an answer to a mob without a current intent.
     *
     * @param mob mob session
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions; never keyed by {@code NOW}
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @return {@code Adopted} or {@code Rejected}
     */
    public static IntentOutcome accept(MobKey mob, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos) {
        return accept(mob, now, contingencies, eligibleNow, profile, tick, nowNanos,
                profile.timing().minimumConfidence());
    }

    /**
     * Offers an answer using an explicit confidence floor. The floor is the profile minimum unless a calibrator
     * installed one. Fit checks do not use this overload.
     *
     * @param mob mob session
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @param confidenceFloor raw confidence required to adopt
     * @return {@code Adopted} or {@code Rejected}
     */
    public static IntentOutcome accept(MobKey mob, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos,
                                       double confidenceFloor) {
        return accept(mob, now, contingencies, eligibleNow, profile, tick, nowNanos, nowNanos, confidenceFloor);
    }

    /**
     * Offers an answer to a mob without a current intent, with the instant its lifetime counts from (M20.7, AE-09):
     * the capture of the question with {@code REACTIVE}, the acceptance with {@code STANDARD}. The intent expires
     * {@code lifetime} ticks after {@code expiryBaseNanos}.
     *
     * @param mob mob session
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time; a mob without a current intent has nothing to compare with it
     * @param expiryBaseNanos monotonic instant the lifetime of the intent counts from
     * @param confidenceFloor raw confidence required to adopt
     * @return {@code Adopted} or {@code Rejected}
     */
    public static IntentOutcome accept(MobKey mob, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos,
                                       long expiryBaseNanos, double confidenceFloor) {
        Objects.requireNonNull(mob, "mob");
        var ranked = rankedApplicable(now, eligibleNow, profile);
        var rejection = rejection(now, ranked, confidenceFloor);
        if (rejection.isPresent()) {
            return new IntentOutcome.Rejected(rejection.orElseThrow());
        }
        return new IntentOutcome.Adopted(build(mob, ranked.getFirst(), now, contingencies, ranked, profile,
                tick, expiryBaseNanos, false));
    }

    /**
     * Offers an answer to a mob that has a current intent.
     *
     * @param current current intent of the mob
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions; never keyed by {@code NOW}
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @return {@code Adopted}, {@code Renewed} or {@code Rejected}
     */
    public static IntentOutcome accept(TacticalIntent current, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos) {
        return accept(current, now, contingencies, eligibleNow, profile, tick, nowNanos,
                profile.timing().minimumConfidence());
    }

    /**
     * Offers an answer that has a current intent, using an explicit confidence floor.
     *
     * @param current current intent of the mob
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @param confidenceFloor raw confidence required to adopt
     * @return {@code Adopted}, {@code Renewed} or {@code Rejected}
     */
    public static IntentOutcome accept(TacticalIntent current, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos,
                                       double confidenceFloor) {
        return accept(current, now, contingencies, eligibleNow, profile, tick, nowNanos, nowNanos, confidenceFloor);
    }

    /**
     * Offers an answer to a mob that has a current intent, with the instant the lifetime of the adopted or renewed
     * intent counts from (M20.7, AE-09). The current intent is judged against {@code nowNanos}.
     *
     * @param current current intent of the mob
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @param expiryBaseNanos monotonic instant the lifetime of the intent counts from
     * @param confidenceFloor raw confidence required to adopt
     * @return {@code Adopted}, {@code Renewed} or {@code Rejected}
     */
    public static IntentOutcome accept(TacticalIntent current, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos,
                                       long expiryBaseNanos, double confidenceFloor) {
        Objects.requireNonNull(current, "current");
        var ranked = rankedApplicable(now, eligibleNow, profile);
        var rejection = rejection(now, ranked, confidenceFloor);
        if (rejection.isPresent()) {
            return new IntentOutcome.Rejected(rejection.orElseThrow());
        }
        var best = ranked.getFirst();
        var currentUsable = !current.expired(nowNanos) && eligibleNow.contains(current.tactic())
                && profile.tactics().containsKey(current.tactic());
        if (!currentUsable) {
            return new IntentOutcome.Adopted(build(current.mob(), best, now, contingencies, ranked, profile,
                    tick, expiryBaseNanos, false));
        }
        if (!best.equals(current.tactic()) && (current.level() != ControlLevel.JEV
                || switchJustified(current, now, best, profile.timing(), tick))) {
            return new IntentOutcome.Adopted(build(current.mob(), best, now, contingencies, ranked, profile,
                    tick, expiryBaseNanos, false));
        }
        return new IntentOutcome.Renewed(build(current.mob(), current.tactic(), now, contingencies, ranked, profile,
                current.startTick(), expiryBaseNanos, current.skillConsumed()));
    }

    /**
     * Offers an answer with fit checks to a mob without a current intent (M16.5); without fits it is
     * {@link #accept(MobKey, Answer, Map, Set, TacticalProfile, long, long)}.
     *
     * @param mob mob session
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions; never keyed by {@code NOW}
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @param fits probability of {@code true} of the fit check of each option; empty without fit checks
     * @param thresholds veto and rescue thresholds
     * @return {@code Adopted} or {@code Rejected}
     */
    public static IntentOutcome accept(MobKey mob, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos,
                                       Map<TacticId, Double> fits, FitGate.FitThresholds thresholds) {
        return accept(mob, now, contingencies, eligibleNow, profile, tick, nowNanos, nowNanos, fits, thresholds);
    }

    /**
     * Offers an answer with fit checks to a mob without a current intent, with the instant the lifetime of the intent
     * counts from (M20.7, AE-09).
     *
     * @param mob mob session
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @param expiryBaseNanos monotonic instant the lifetime of the intent counts from
     * @param fits probability of {@code true} of the fit check of each option; empty without fit checks
     * @param thresholds veto and rescue thresholds
     * @return {@code Adopted} or {@code Rejected}
     */
    public static IntentOutcome accept(MobKey mob, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos,
                                       long expiryBaseNanos, Map<TacticId, Double> fits,
                                       FitGate.FitThresholds thresholds) {
        if (fits.isEmpty()) {
            return accept(mob, now, contingencies, eligibleNow, profile, tick, nowNanos, expiryBaseNanos,
                    profile.timing().minimumConfidence());
        }
        var ranked = rankedApplicable(now, eligibleNow, profile);
        return switch (verdict(now, ranked, profile, fits, thresholds)) {
            case FitGate.FitVerdict.Reject reject -> new IntentOutcome.Rejected(reject.reason());
            case FitGate.FitVerdict.Adopt adopt -> new IntentOutcome.Adopted(build(mob, adopt.tactic(), now,
                    contingencies, backed(ranked, fits, thresholds), profile, tick, expiryBaseNanos, false,
                    adopt.level()));
        };
    }

    /**
     * Offers an answer with fit checks to a mob that has a current intent (M16.5); without fits it is
     * {@link #accept(TacticalIntent, Answer, Map, Set, TacticalProfile, long, long)}. A verdict for the current tactic
     * renews it at the level of the verdict, so a {@code LOCAL} intent that Jev confirms becomes {@code JEV} as it does
     * without fits; a {@code JEV} verdict that was not rescued replaces a current {@code JEV} intent only past the switch
     * margin and the minimum interval, as without fits, and that hysteresis keeps the current level; any other verdict
     * replaces it at once.
     *
     * @param current current intent of the mob
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions; never keyed by {@code NOW}
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @param fits probability of {@code true} of the fit check of each option; empty without fit checks
     * @param thresholds veto and rescue thresholds
     * @return {@code Adopted}, {@code Renewed} or {@code Rejected}
     */
    public static IntentOutcome accept(TacticalIntent current, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos,
                                       Map<TacticId, Double> fits, FitGate.FitThresholds thresholds) {
        return accept(current, now, contingencies, eligibleNow, profile, tick, nowNanos, nowNanos, fits, thresholds);
    }

    /**
     * Offers an answer with fit checks to a mob that has a current intent, with the instant the lifetime of the adopted
     * or renewed intent counts from (M20.7, AE-09). The current intent is judged against {@code nowNanos}.
     *
     * @param current current intent of the mob
     * @param now answer to the {@code NOW} question
     * @param contingencies answers to contingency questions
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick
     * @param nowNanos current monotonic time
     * @param expiryBaseNanos monotonic instant the lifetime of the intent counts from
     * @param fits probability of {@code true} of the fit check of each option; empty without fit checks
     * @param thresholds veto and rescue thresholds
     * @return {@code Adopted}, {@code Renewed} or {@code Rejected}
     */
    public static IntentOutcome accept(TacticalIntent current, Answer now, Map<Situation, Answer> contingencies,
                                       Set<TacticId> eligibleNow, TacticalProfile profile, long tick, long nowNanos,
                                       long expiryBaseNanos, Map<TacticId, Double> fits,
                                       FitGate.FitThresholds thresholds) {
        if (fits.isEmpty()) {
            return accept(current, now, contingencies, eligibleNow, profile, tick, nowNanos, expiryBaseNanos,
                    profile.timing().minimumConfidence());
        }
        var ranked = rankedApplicable(now, eligibleNow, profile);
        var verdict = verdict(now, ranked, profile, fits, thresholds);
        if (verdict instanceof FitGate.FitVerdict.Reject reject) {
            return new IntentOutcome.Rejected(reject.reason());
        }
        var adopt = (FitGate.FitVerdict.Adopt) verdict;
        var alternatives = backed(ranked, fits, thresholds);
        var currentUsable = !current.expired(nowNanos) && eligibleNow.contains(current.tactic())
                && profile.tactics().containsKey(current.tactic());
        var hysteresis = current.level() == ControlLevel.JEV && adopt.level() == ControlLevel.JEV && !adopt.rescued();
        var confirmed = adopt.tactic().equals(current.tactic());
        if (currentUsable && (confirmed
                || hysteresis && !switchJustified(current, now, adopt.tactic(), profile.timing(), tick))) {
            var level = confirmed ? adopt.level() : current.level();
            return new IntentOutcome.Renewed(build(current.mob(), current.tactic(), now, contingencies, alternatives,
                    profile, current.startTick(), expiryBaseNanos, current.skillConsumed(), level));
        }
        return new IntentOutcome.Adopted(build(current.mob(), adopt.tactic(), now, contingencies, alternatives, profile,
                tick, expiryBaseNanos, false, adopt.level()));
    }

    /**
     * Moves to the first alternative that is still eligible (control level 2).
     *
     * @param intent current intent
     * @param eligibleNow tactics eligible now
     * @param profile tactical profile of the mob
     * @param tick current server tick, the start of the alternative
     * @return an {@code ALTERNATIVE} intent with the same deadline, or empty when the alternatives are exhausted
     */
    public static Optional<TacticalIntent> nextAlternative(TacticalIntent intent, Set<TacticId> eligibleNow,
                                                           TacticalProfile profile, long tick) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(eligibleNow, "eligibleNow");
        Objects.requireNonNull(profile, "profile");
        var alternatives = intent.alternatives();
        for (var index = 0; index < alternatives.size(); index++) {
            var candidate = profile.tactic(alternatives.get(index)).filter(tactic -> eligibleNow.contains(tactic.id()));
            if (candidate.isPresent()) {
                var tactic = candidate.orElseThrow();
                var remaining = new EnumMap<Situation, TacticId>(Situation.class);
                intent.contingencies().forEach((situation, planned) -> {
                    if (!planned.equals(tactic.id())) {
                        remaining.put(situation, planned);
                    }
                });
                return Optional.of(new TacticalIntent(intent.mob(), tactic.id(), tactic.movement(), tactic.choice(),
                        intent.confidence(), alternatives.subList(index + 1, alternatives.size()), remaining,
                        ControlLevel.ALTERNATIVE, tick, intent.expiresAtNanos(), false));
            }
        }
        return Optional.empty();
    }

    /**
     * The verdict of {@link FitGate} with every option that is not applicable excluded, like a vetoed option, whatever
     * the veto: with {@code decision.fitVeto: 0} it is skipped and the next valid option is judged.
     */
    private static FitGate.FitVerdict verdict(Answer now, List<TacticId> applicable, TacticalProfile profile,
                                              Map<TacticId, Double> fits, FitGate.FitThresholds thresholds) {
        var excluded = new HashSet<TacticId>(now.probabilities().keySet());
        excluded.removeAll(applicable);
        return FitGate.judge(now, fits, excluded, profile.timing().minimumConfidence(), thresholds);
    }

    /** The applicable tactics in rank order that no fit vetoes, the only ones kept as alternatives. */
    private static List<TacticId> backed(List<TacticId> ranked, Map<TacticId, Double> fits,
                                         FitGate.FitThresholds thresholds) {
        return ranked.stream().filter(tactic -> !fits.containsKey(tactic) || fits.get(tactic) >= thresholds.veto())
                .toList();
    }

    private static List<TacticId> rankedApplicable(Answer answer, Set<TacticId> eligibleNow, TacticalProfile profile) {
        Objects.requireNonNull(answer, "now");
        Objects.requireNonNull(eligibleNow, "eligibleNow");
        Objects.requireNonNull(profile, "profile");
        var applicable = new ArrayList<TacticId>();
        for (var tactic : answer.ranked()) {
            if (answer.probabilityOf(tactic) > 0.0 && eligibleNow.contains(tactic)
                    && profile.tactics().containsKey(tactic)) {
                applicable.add(tactic);
            }
        }
        return applicable;
    }

    private static Optional<FallbackReason> rejection(Answer now, List<TacticId> ranked, double confidenceFloor) {
        if (now.confidence() < confidenceFloor) {
            return Optional.of(FallbackReason.LOW_CONFIDENCE);
        }
        if (ranked.isEmpty()) {
            return Optional.of(FallbackReason.INVALID_RESPONSE);
        }
        return Optional.empty();
    }

    private static boolean switchJustified(TacticalIntent current, Answer now, TacticId best,
                                           DecisionTiming timing, long tick) {
        var held = tick - current.startTick() >= timing.minimumDecisionIntervalTicks();
        var gain = now.probabilityOf(best) - now.probabilityOf(current.tactic());
        return held && gain > 0.0 && gain >= timing.switchMargin() - Answer.ROUNDING_EPSILON;
    }

    private static TacticalIntent build(MobKey mob, TacticId tacticId, Answer now, Map<Situation, Answer> contingencies,
                                        List<TacticId> ranked, TacticalProfile profile, long startTick,
                                        long expiryBaseNanos, boolean skillConsumed) {
        return build(mob, tacticId, now, contingencies, ranked, profile, startTick, expiryBaseNanos, skillConsumed,
                ControlLevel.JEV);
    }

    private static TacticalIntent build(MobKey mob, TacticId tacticId, Answer now, Map<Situation, Answer> contingencies,
                                        List<TacticId> ranked, TacticalProfile profile, long startTick,
                                        long expiryBaseNanos, boolean skillConsumed, ControlLevel level) {
        var tactic = profile.tactic(tacticId).orElseThrow();
        var alternatives = new ArrayList<TacticId>();
        for (var candidate : ranked) {
            if (alternatives.size() < TacticalIntent.MAX_ALTERNATIVES && !candidate.equals(tacticId)) {
                alternatives.add(candidate);
            }
        }
        var lifetime = profile.timing().cadence() == DecisionCadence.REACTIVE
                ? profile.timing().intentHorizonTicks() : profile.timing().maximumIntentTicks();
        var expiresAtNanos = expiryBaseNanos + lifetime * NANOS_PER_TICK;
        return new TacticalIntent(mob, tacticId, tactic.movement(), tactic.choice(), now.confidence(), alternatives,
                plannedContingencies(contingencies, tacticId, profile), level, startTick, expiresAtNanos,
                skillConsumed);
    }

    private static Map<Situation, TacticId> plannedContingencies(Map<Situation, Answer> answers, TacticId tactic,
                                                                 TacticalProfile profile) {
        Objects.requireNonNull(answers, "contingencies");
        if (answers.containsKey(Situation.NOW)) {
            throw new IllegalArgumentException("contingencies must not use situation NOW");
        }
        var planned = new EnumMap<Situation, TacticId>(Situation.class);
        for (var situation : CONTINGENCY_ORDER) {
            var answer = answers.get(situation);
            if (planned.size() >= profile.timing().contingenciesPerMob() || answer == null
                    || answer.confidence() < profile.timing().minimumConfidence()) {
                continue;
            }
            answer.ranked().stream()
                    .filter(candidate -> answer.probabilityOf(candidate) > 0.0 && profile.tactics().containsKey(candidate))
                    .findFirst()
                    .filter(candidate -> !candidate.equals(tactic))
                    .ifPresent(candidate -> planned.put(situation, candidate));
        }
        return planned;
    }
}
