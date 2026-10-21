package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure rule that coordinates the intents of one encounter (M10.7): at most {@link #maximumRetreats(int)}
 * simultaneous {@code RETREAT} intents. Stateless, reads no clock, safe on any thread.
 *
 * <p>The retreats with the highest confidence are kept, ties broken by encounter order, so the result never depends
 * on the order in which answers arrive. Every other retreat takes its next declared, eligible alternative whose
 * movement is not {@code RETREAT}; without one it keeps its previous unexpired, eligible intent if that one does not
 * retreat; otherwise it is left to the local planner, which the caller runs without retreating tactics.
 */
public final class CoordinationPolicy {
    private static final boolean[] KEEP_NONE = new boolean[0];

    private CoordinationPolicy() {
    }

    /**
     * One proposed intent of the encounter with what its replacement needs.
     *
     * @param proposed intent adopted or renewed from the answer
     * @param previous intent the session had before the answer, if any
     * @param eligibleNow tactics eligible now for that mob
     * @param profile tactical profile of that mob
     */
    public record Candidate(TacticalIntent proposed, Optional<TacticalIntent> previous, Set<TacticId> eligibleNow,
                            TacticalProfile profile) {
        /**
         * Validates the candidate and copies the eligible set.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Candidate {
            Objects.requireNonNull(proposed, "proposed");
            Objects.requireNonNull(previous, "previous");
            eligibleNow = Set.copyOf(Objects.requireNonNull(eligibleNow, "eligibleNow"));
            Objects.requireNonNull(profile, "profile");
        }
    }

    /**
     * Returns the cap of simultaneous retreats of an encounter.
     *
     * @param members members of the encounter
     * @return {@code max(1, members / 3)} with integer division
     */
    public static int maximumRetreats(int members) {
        return Math.max(1, members / 3);
    }

    /**
     * Limits the simultaneous retreats of an encounter.
     *
     * @param candidates proposed intents in encounter order
     * @param members members of the encounter
     * @param retreatingOthers members outside {@code candidates} that already run an unexpired {@code RETREAT} intent
     * @param tick current server tick, the start of a replacement alternative
     * @param nowNanos current monotonic time, to tell whether a previous intent is still valid
     * @return one entry per candidate in the same order: the intent to apply, or empty to plan locally without retreat
     */
    public static List<Optional<TacticalIntent>> limitRetreats(List<Candidate> candidates, int members,
                                                               int retreatingOthers, long tick, long nowNanos) {
        return limitRetreats(candidates, members, retreatingOthers, tick, nowNanos, KEEP_NONE);
    }

    /**
     * Limits simultaneous retreats, keeping the indexes the group plan assigned.
     *
     * @param candidates proposed intents in encounter order
     * @param members members of the encounter
     * @param retreatingOthers members outside {@code candidates} that already run an unexpired {@code RETREAT} intent
     * @param tick current server tick, the start of a replacement alternative
     * @param nowNanos current monotonic time, to tell whether a previous intent is still valid
     * @param keep one flag per candidate index whose retreat the plan assigned and must not be demoted; a shorter array
     *        keeps nothing past its end, and an empty one keeps nothing
     * @return one entry per candidate in the same order
     * @throws NullPointerException if {@code candidates} or {@code keep} is {@code null}
     */
    public static List<Optional<TacticalIntent>> limitRetreats(List<Candidate> candidates, int members,
                                                               int retreatingOthers, long tick, long nowNanos,
                                                               boolean[] keep) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(keep, "keep");
        var allowed = Math.max(0, maximumRetreats(members) - Math.max(0, retreatingOthers));
        var retreating = new ArrayList<Integer>();
        for (var index = 0; index < candidates.size(); index++) {
            if (kept(keep, index)) {
                continue;
            }
            if (candidates.get(index).proposed().movement() == Movement.RETREAT) {
                retreating.add(index);
            }
        }
        retreating.sort(Comparator.comparingDouble((Integer index) -> -candidates.get(index).proposed().confidence())
                .thenComparingInt(index -> index));
        var demoted = retreating.subList(Math.min(allowed, retreating.size()), retreating.size());
        var result = new ArrayList<Optional<TacticalIntent>>(candidates.size());
        for (var index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            if (kept(keep, index)) {
                result.add(Optional.of(candidate.proposed()));
            } else {
                result.add(demoted.contains(index) ? replacement(candidate, tick, nowNanos)
                        : Optional.of(candidate.proposed()));
            }
        }
        return List.copyOf(result);
    }

    private static boolean kept(boolean[] keep, int index) {
        return index < keep.length && keep[index];
    }

    private static Optional<TacticalIntent> replacement(Candidate candidate, long tick, long nowNanos) {
        var proposed = candidate.proposed();
        var alternatives = proposed.alternatives();
        for (var index = 0; index < alternatives.size(); index++) {
            var tactic = candidate.profile().tactic(alternatives.get(index))
                    .filter(found -> candidate.eligibleNow().contains(found.id()) && found.movement() != Movement.RETREAT);
            if (tactic.isPresent()) {
                return Optional.of(alternative(proposed, tactic.orElseThrow(), alternatives.subList(index + 1,
                        alternatives.size()), tick));
            }
        }
        return candidate.previous().filter(previous -> !previous.expired(nowNanos)
                && previous.movement() != Movement.RETREAT && candidate.eligibleNow().contains(previous.tactic()));
    }

    private static TacticalIntent alternative(TacticalIntent proposed, Tactic tactic, List<TacticId> remaining,
                                              long tick) {
        var contingencies = new EnumMap<Situation, TacticId>(Situation.class);
        proposed.contingencies().forEach((situation, planned) -> {
            if (!planned.equals(tactic.id())) {
                contingencies.put(situation, planned);
            }
        });
        return new TacticalIntent(proposed.mob(), tactic.id(), tactic.movement(), tactic.choice(), proposed.confidence(),
                remaining, contingencies, ControlLevel.ALTERNATIVE, tick, proposed.expiresAtNanos(), false);
    }
}
