package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the plans a capture may offer (M25). Candidate 0 is the local plan. Fewer than two possible plans
 * means no group question.
 */
public final class GroupPlanPolicy {
    private GroupPlanPolicy() {
    }

    /**
     * Plans still possible for this capture.
     *
     * @param members controlled members
     * @param leader whether a leader exists
     * @param ranged whether someone can shoot
     * @param support whether someone can help an ally
     * @param unseen whether the target is unseen
     * @return the possible plans, local plan first
     */
    public static List<GroupPlan> possible(int members, boolean leader, boolean ranged, boolean support,
                                           boolean unseen) {
        var found = new ArrayList<GroupPlan>();
        if (members < 2) {
            return found;
        }
        found.add(GroupPlan.PRESS_MAIN_TARGET);
        found.add(GroupPlan.SURROUND_TARGET);
        if (members >= 3) {
            found.add(GroupPlan.SPLIT_TARGETS);
        }
        if (leader) {
            found.add(GroupPlan.SCREEN_THE_LEADER);
        }
        if (ranged) {
            found.add(GroupPlan.HARASS_FROM_RANGE);
        }
        if (support || leader) {
            found.add(GroupPlan.FALL_BACK_AND_REGROUP);
        }
        if (unseen) {
            found.add(GroupPlan.SEARCH_LAST_KNOWN);
        }
        return found;
    }

    /**
     * Utility of one plan. The incumbent is multiplied by 1.15 and truncated, in integers: {@code 1.15} has no exact
     * binary form, and {@code (int) (100 * 1.15)} would give 114.
     *
     * @param plan the plan
     * @param incumbent whether it is the plan in force
     * @param members controlled members
     * @return the integer utility
     */
    public static int utility(GroupPlan plan, boolean incumbent, int members) {
        var base = switch (plan) {
            case PRESS_MAIN_TARGET -> 100;
            case SURROUND_TARGET -> 90;
            case SPLIT_TARGETS -> 80 + members;
            case SCREEN_THE_LEADER -> 85;
            case HARASS_FROM_RANGE -> 70;
            case FALL_BACK_AND_REGROUP -> 60;
            case SEARCH_LAST_KNOWN -> 50;
        };
        if (!incumbent) {
            return base;
        }
        return base * 115 / 100;
    }

    /**
     * Top candidates after diversity. One plan per family, then the local plan is forced into slot 0.
     *
     * @param possible plans that can run
     * @param incumbent plan in force, or null
     * @param members controlled members
     * @param limit {@code group.maxPlanCandidates}
     * @return the candidates, or empty when fewer than two plans are possible
     */
    public static List<GroupPlan> candidates(List<GroupPlan> possible, GroupPlan incumbent, int members, int limit) {
        if (possible.size() < 2) {
            return List.of();
        }
        var ranked = new ArrayList<>(possible);
        ranked.sort((left, right) -> Integer.compare(utility(right, right == incumbent, members),
                utility(left, left == incumbent, members)));
        var picked = new ArrayList<GroupPlan>();
        var families = 0;
        for (var plan : ranked) {
            var bit = 1 << plan.family().ordinal();
            if ((families & bit) != 0) {
                continue;
            }
            families |= bit;
            picked.add(plan);
            if (picked.size() == limit) {
                break;
            }
        }
        var local = possible.getFirst();
        picked.remove(local);
        picked.addFirst(local);
        if (picked.size() > limit) {
            return List.copyOf(picked.subList(0, limit));
        }
        return List.copyOf(picked);
    }

    /**
     * Whether a capture may replace the plan in force. Without a plan in force a capture always adopts, and the caller
     * says so before asking (C4, DAL-11): a missing plan is never passed as {@code null}.
     *
     * @param asks whether the request carries {@code g0}
     * @param current the plan in force
     * @param ageTicks ticks the definitive plan has lasted
     * @param horizonTicks plan horizon
     * @param possible whether the current plan is still possible
     * @param leaderLost whether the leader was lost
     * @return whether the capture may adopt
     * @throws NullPointerException if {@code current} is null
     */
    public static boolean mayAdopt(boolean asks, GroupPlan current, int ageTicks, int horizonTicks, boolean possible,
                                   boolean leaderLost) {
        java.util.Objects.requireNonNull(current, "current");
        if (leaderLost || !possible || ageTicks >= horizonTicks) {
            return true;
        }
        return asks;
    }
}
