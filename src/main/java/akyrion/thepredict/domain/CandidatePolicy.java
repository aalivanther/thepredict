package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Orders prepared intentions (M20.6). Priority scoring is {@link LocalPlanner#score} scaled to 0–1000 over its
 * largest value ({@link #MAX_SCORE}), so two different scores never share a scaled value and the first candidate is
 * the tactic the local planner already picks. Diversity keeps the best of each movement first. Ties break by
 * declaration order, never by a draw.
 */
public final class CandidatePolicy {
    /** Largest {@link LocalPlanner#score}: the top priority plus every {@code BestWhen} bonus. */
    public static final int MAX_SCORE = Tactic.MAX_PRIORITY + SkillPurpose.MAX_BEST_WHEN * LocalPlanner.BEST_WHEN_BONUS;

    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private CandidatePolicy() {
    }

    /**
     * Score of the priority mode, 0–1000.
     *
     * @param profile profile of the mob
     * @param tactic tactic being scored
     * @param now conditions that hold
     * @return the scaled score
     */
    public static int priorityMillis(TacticalProfile profile, Tactic tactic, Set<UseCondition> now) {
        return priorityMillis(profile, tactic, now, 1.0);
    }

    /**
     * Priority score multiplied by a pattern factor after the scale. A factor of {@code 1} matches
     * {@link #priorityMillis(TacticalProfile, Tactic, Set)}. The scale is {@code score × 1000 / MAX_SCORE}, rounded
     * down: two integer scores stay at least five thousandths apart, so the scale keeps the order of
     * {@link LocalPlanner#choose} and leaves room for the factor below 1000.
     *
     * @param profile profile of the mob
     * @param tactic tactic being scored
     * @param now conditions that hold
     * @param patternFactor factor in {@code [1, 1.2]}
     * @return the scaled score
     */
    public static int priorityMillis(TacticalProfile profile, Tactic tactic, Set<UseCondition> now,
                                     double patternFactor) {
        var score = Math.max(0, LocalPlanner.score(profile, tactic, now));
        var scaled = (int) Math.min(1000L, score * 1000L / MAX_SCORE);
        return UtilityScore.withPattern(scaled, patternFactor);
    }

    /**
     * Keeps at most {@code limit} candidates. Index 0 is the highest score.
     *
     * @param captureTick tick of the capture
     * @param signature situation signature
     * @param scored scored candidates, in declaration order
     * @param limit {@code decision.maxOptionsPerQuestion}
     * @param diversity whether distinct movements go first
     * @param questionOrder order already chosen for the question; with priority and diversity off it is the order of M19
     * @return the set
     */
    public static CandidateSet select(long captureTick, int signature, List<CandidateIntent> scored, int limit,
                                      boolean diversity, List<TacticId> questionOrder) {
        Objects.requireNonNull(scored, "scored");
        Objects.requireNonNull(questionOrder, "questionOrder");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1, was " + limit);
        }
        var ranked = new ArrayList<>(scored);
        ranked.sort(Comparator.comparingInt(CandidateIntent::scoreMillis).reversed());
        List<CandidateIntent> chosen;
        if (!diversity) {
            chosen = ranked.size() > limit ? ranked.subList(0, limit) : ranked;
        } else {
            chosen = diverse(ranked, limit);
        }
        return new CandidateSet(captureTick, signature, List.copyOf(chosen), Map.of(), questionOrder);
    }

    /**
     * Ticks until a candidate is ready. A live action adds the busy remainder (M23.9 amends M20.6 with the sum, for the
     * local order and the {@code READINESS} consideration). {@code NONE} does not.
     *
     * @param readyIn ticks the choice already needs, {@code >= 0}
     * @param busyUntil busy deadline
     * @param now current tick
     * @param action current action
     * @return the adjusted wait, saturated at {@link Integer#MAX_VALUE}
     * @throws IllegalArgumentException if {@code readyIn} is negative
     */
    public static int readyInTicks(int readyIn, long busyUntil, long now, ActionStance action) {
        if (readyIn < 0) {
            throw new IllegalArgumentException("readyIn must be >= 0, was " + readyIn);
        }
        if (action == ActionStance.NONE || busyUntil <= now) {
            return readyIn;
        }
        var extra = busyUntil - now;
        if (extra > Integer.MAX_VALUE - readyIn) {
            return Integer.MAX_VALUE;
        }
        return readyIn + (int) extra;
    }

    /**
     * Order after {@code ENEMY_PATTERN}. Utility scoring multiplies only a tactic that fits a pattern in the mask.
     * {@code raisedByPattern} is true when that factor alone changes the first tactic.
     *
     * @param scoring candidate scoring
     * @param profile profile of the mob
     * @param tactics eligible tactics, in declaration order
     * @param now conditions that hold
     * @param patternMask bits of {@link EnemyPattern#bit()}
     * @return the order and whether the factor changed the leader
     */
    public static PatternRank rank(CandidateScoring scoring, TacticalProfile profile, List<Tactic> tactics,
                                   Set<UseCondition> now, int patternMask) {
        Objects.requireNonNull(scoring, "scoring");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(tactics, "tactics");
        Objects.requireNonNull(now, "now");
        if (tactics.isEmpty()) {
            throw new IllegalArgumentException("tactics must not be empty");
        }
        var utility = scoring == CandidateScoring.UTILITY;
        var mask = utility ? patternMask : 0;
        var lifted = ordered(scoring, profile, tactics, now, mask);
        var raised = utility && mask != 0
                && !lifted.get(0).equals(ordered(scoring, profile, tactics, now, 0).get(0));
        return new PatternRank(lifted, raised);
    }

    /**
     * Pattern factor of one tactic. It stays {@code 1} outside utility scoring and for a tactic that fits nothing.
     * The tactic fits a pattern through its movement or through its choice, with the choice-level rule of
     * {@link CastPreference#fits(long, SkillChoice)}.
     *
     * @param scoring candidate scoring
     * @param tactic tactic being scored
     * @param profile profile of the mob
     * @param patternMask bits of {@link EnemyPattern#bit()}
     * @return a factor in {@code [1, 1.2]}
     */
    public static double factorFor(CandidateScoring scoring, Tactic tactic, TacticalProfile profile, int patternMask) {
        if (scoring != CandidateScoring.UTILITY || patternMask == 0) {
            return 1.0;
        }
        var best = 1.0;
        for (var pattern : PATTERNS) {
            if ((patternMask & pattern.bit()) == 0 || !fits(tactic, profile, pattern)) {
                continue;
            }
            var factor = CounterMap.of(pattern).factor();
            if (factor > best) {
                best = factor;
            }
        }
        return best > 1.2 ? 1.2 : best;
    }

    /**
     * Whether the lead of a raised order casts this choice.
     *
     * @param rank order from {@link #rank}
     * @param profile profile of the mob
     * @param choice choice that was cast
     * @return {@code true} when the factor alone put this choice first
     */
    public static boolean lifts(PatternRank rank, TacticalProfile profile, ChoiceId choice) {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(choice, "choice");
        if (!rank.raisedByPattern() || rank.order().isEmpty()) {
            return false;
        }
        var lead = profile.tactics().get(rank.order().get(0));
        return lead != null && choice.equals(lead.choice().orElse(null));
    }

    /**
     * One utility ordering.
     *
     * @param order tactic ids, highest score first
     * @param raisedByPattern whether the pattern factor changed the first id
     */
    public record PatternRank(List<TacticId> order, boolean raisedByPattern) {
    }

    /**
     * Learned order. The pattern mask does not move it. Higher score first; ties keep the declaration order.
     *
     * @param tactics tactics in declaration order
     * @param scoreMillis score of each tactic, by position
     * @return the order Jev sees
     * @throws IllegalArgumentException if there is not exactly one score per tactic
     */
    public static List<TacticId> learnedOrder(List<Tactic> tactics, int[] scoreMillis) {
        Objects.requireNonNull(tactics, "tactics");
        Objects.requireNonNull(scoreMillis, "scoreMillis");
        if (scoreMillis.length != tactics.size()) {
            throw new IllegalArgumentException("scoreMillis must hold one score per tactic, had " + scoreMillis.length
                    + " for " + tactics.size());
        }
        return ids(tactics, descending(scoreMillis));
    }

    private static List<TacticId> ordered(CandidateScoring scoring, TacticalProfile profile, List<Tactic> tactics,
                                          Set<UseCondition> now, int patternMask) {
        var scores = new int[tactics.size()];
        for (var index = 0; index < scores.length; index++) {
            var tactic = tactics.get(index);
            scores[index] = priorityMillis(profile, tactic, now, factorFor(scoring, tactic, profile, patternMask));
        }
        return ids(tactics, descending(scores));
    }

    /** Positions sorted by score, highest first; a stable insertion sort, so equal scores keep their positions. */
    private static int[] descending(int[] scores) {
        var order = new int[scores.length];
        for (var index = 0; index < order.length; index++) {
            var value = index;
            var slot = index - 1;
            while (slot >= 0 && scores[order[slot]] < scores[value]) {
                order[slot + 1] = order[slot];
                slot--;
            }
            order[slot + 1] = value;
        }
        return order;
    }

    private static List<TacticId> ids(List<Tactic> tactics, int[] order) {
        var ids = new ArrayList<TacticId>(order.length);
        for (var position : order) {
            ids.add(tactics.get(position).id());
        }
        return List.copyOf(ids);
    }

    /**
     * Whether a tactic counters a pattern (M26.4): through its movement, the micro of the counter of
     * {@link CounterMap}, or through its choice, with the choice-level rule of
     * {@link CastPreference#fits(long, SkillChoice)}. The utility factor and the pattern shift of a distilled pick
     * (M29.4, AE-08) read the same rule.
     *
     * @param tactic the tactic
     * @param profile profile that declares its choice
     * @param pattern the pattern
     * @return whether the tactic counters it
     */
    public static boolean counters(Tactic tactic, TacticalProfile profile, EnemyPattern pattern) {
        Objects.requireNonNull(tactic, "tactic");
        Objects.requireNonNull(profile, "profile");
        return fits(tactic, profile, Objects.requireNonNull(pattern, "pattern"));
    }

    private static boolean fits(Tactic tactic, TacticalProfile profile, EnemyPattern pattern) {
        var bias = CounterMap.of(pattern);
        if (movementFits(tactic.movement(), bias.micro())) {
            return true;
        }
        if (tactic.choice().isEmpty()) {
            return false;
        }
        var choice = profile.choices().get(tactic.choice().get());
        return choice != null && CastPreference.fits(bias.preference(), choice);
    }

    private static boolean movementFits(Movement movement, MicroBehaviour micro) {
        return switch (micro) {
            case ENTER -> movement == Movement.APPROACH;
            case BACKSTEP -> movement == Movement.RETREAT;
            case SIDESTEP, ORBIT -> movement == Movement.FLANK;
            case STILL -> movement == Movement.HOLD;
            default -> false;
        };
    }

    private static List<CandidateIntent> diverse(List<CandidateIntent> ranked, int limit) {
        var chosen = new ArrayList<CandidateIntent>();
        var seen = new LinkedHashMap<Movement, CandidateIntent>();
        for (var candidate : ranked) {
            seen.putIfAbsent(candidate.movement(), candidate);
        }
        for (var candidate : seen.values()) {
            if (chosen.size() == limit) {
                return chosen;
            }
            chosen.add(candidate);
        }
        for (var candidate : ranked) {
            if (chosen.size() == limit) {
                break;
            }
            if (!chosen.contains(candidate)) {
                chosen.add(candidate);
            }
        }
        return chosen;
    }
}
