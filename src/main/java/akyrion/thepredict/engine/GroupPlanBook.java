package akyrion.thepredict.engine;

import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupPlanPolicy;

import java.util.List;
import java.util.function.Predicate;

/**
 * Adoption of one group's plan (M25). A capture without {@code g0} does not replace a live plan. The answer of
 * the capture that opened the provisional does not wait for {@code minPlanTicks}. Adopting the same plan does
 * not count as a change. A dead plan is replaced from the last capture in the same call.
 */
final class GroupPlanBook {
    private static final int KEPT = 8;

    private final GroupPlan[] offered = new GroupPlan[KEPT];
    private int offeredCount;
    private GroupPlan plan;
    private long since = Long.MIN_VALUE;
    private boolean provisional;
    private long captureSequence = -1;
    private GroupPlan held;
    private boolean holding;
    private boolean answered;
    private boolean heldAnswered;

    GroupPlan plan() {
        return plan;
    }

    /**
     * Tick the current plan was adopted. {@link Long#MIN_VALUE} when none is in force.
     *
     * @return the tick
     */
    long started() {
        return since;
    }

    boolean provisional() {
        return provisional;
    }

    /**
     * Whether the plan in force came from the {@code g0} answer of Jev, directly or after waiting for
     * {@code minPlanTicks}, rather than from candidate 0 of a capture (EFO-25).
     *
     * @return {@code true} for a plan Jev chose
     */
    boolean answered() {
        return plan != null && answered;
    }

    int age(long tick) {
        if (plan == null || since == Long.MIN_VALUE) {
            return 0;
        }
        var delta = tick - since;
        if (delta <= 0) {
            return 0;
        }
        return delta > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) delta;
    }

    /**
     * Remembers the capture and adopts candidate 0 only when the rules allow it.
     *
     * @param tick current tick
     * @param sequence capture sequence
     * @param asks whether the request carries {@code g0}
     * @param candidates candidate 0 first
     * @param minTicks minimum life of a definitive plan
     * @param horizonTicks plan horizon
     * @param currentPossible whether the plan in force is still possible
     * @return whether the plan changed
     */
    boolean noteCapture(long tick, long sequence, boolean asks, List<GroupPlan> candidates, int minTicks,
                        int horizonTicks, boolean currentPossible) {
        remember(candidates);
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        var candidate = candidates.getFirst();
        var age = plan == null ? horizonTicks : age(tick);
        if (plan != null && !GroupPlanPolicy.mayAdopt(asks, plan, age, horizonTicks, currentPossible, false)) {
            return false;
        }
        if (plan != null && !provisional && age(tick) < minTicks && currentPossible && age(tick) < horizonTicks) {
            held = candidate;
            heldAnswered = false;
            holding = true;
            captureSequence = sequence;
            return false;
        }
        captureSequence = sequence;
        return adopt(candidate, tick, true, false);
    }

    /**
     * Applies the {@code g0} answer. The same capture replaces its provisional immediately.
     *
     * @param chosen the plan Jev named
     * @param sequence capture sequence
     * @param tick current tick
     * @param minTicks minimum life of a definitive plan
     * @return whether the plan changed
     */
    boolean acceptAnswer(GroupPlan chosen, long sequence, long tick, int minTicks) {
        if (chosen == null) {
            return false;
        }
        if (provisional && sequence == captureSequence) {
            return adopt(chosen, tick, false, true);
        }
        if (plan != null && !provisional && age(tick) < minTicks) {
            held = chosen;
            heldAnswered = true;
            holding = true;
            return false;
        }
        captureSequence = sequence;
        return adopt(chosen, tick, false, true);
    }

    /**
     * Adopts a held answer once {@code minPlanTicks} has elapsed.
     *
     * @param tick current tick
     * @param minTicks minimum life of a definitive plan
     * @return whether the plan changed
     */
    boolean release(long tick, int minTicks) {
        if (!holding || held == null || plan == null || age(tick) < minTicks) {
            return false;
        }
        var chosen = held;
        holding = false;
        held = null;
        return adopt(chosen, tick, false, heldAnswered);
    }

    /**
     * Replaces a dead plan with the best stored candidate that is still possible.
     *
     * @param tick current tick
     * @param live whether a stored candidate may run
     * @param members controlled members, for utility
     * @return whether the plan changed
     */
    boolean die(long tick, Predicate<GroupPlan> live, int members) {
        GroupPlan best = null;
        var bestScore = Integer.MIN_VALUE;
        for (var index = 0; index < offeredCount; index++) {
            var candidate = offered[index];
            if (candidate == null || (live != null && !live.test(candidate))) {
                continue;
            }
            var score = GroupPlanPolicy.utility(candidate, false, Math.max(2, members));
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        holding = false;
        held = null;
        if (best == null) {
            var changed = plan != null;
            plan = null;
            provisional = false;
            return changed;
        }
        return adopt(best, tick, true, false);
    }

    void clear() {
        plan = null;
        provisional = false;
        answered = false;
        holding = false;
        held = null;
        since = Long.MIN_VALUE;
    }

    private boolean adopt(GroupPlan next, long tick, boolean asProvisional, boolean fromAnswer) {
        var changed = plan != next;
        plan = next;
        since = tick;
        provisional = asProvisional;
        answered = fromAnswer;
        return changed;
    }

    private void remember(List<GroupPlan> candidates) {
        offeredCount = 0;
        if (candidates == null) {
            return;
        }
        for (var candidate : candidates) {
            if (candidate == null || offeredCount == KEPT) {
                continue;
            }
            offered[offeredCount++] = candidate;
        }
    }
}
