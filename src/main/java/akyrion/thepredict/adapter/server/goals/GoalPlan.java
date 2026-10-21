package akyrion.thepredict.adapter.server.goals;

import java.util.List;

/**
 * Goal set generated from one profile (M05.1): MythicMobs {@code AIGoalSelectors} lines in human-readable
 * form plus the slots of ThePredict's Paper goals. Immutable; built without touching the server.
 *
 * @param mythicLines lines with explicit priorities; the first one is {@link #CLEAR}
 * @param paperGoals Paper goals and their priorities, none repeating a line priority
 * @param revision catalog revision the set was generated for, {@code >= 0}
 */
public record GoalPlan(List<String> mythicLines, List<PaperGoalSlot> paperGoals, long revision) {
    /** First line of every generated set. */
    public static final String CLEAR = "0 clear";

    /**
     * Validates the plan and copies its lists.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if the first line is not {@link #CLEAR} or the revision is negative
     */
    public GoalPlan {
        mythicLines = List.copyOf(mythicLines);
        paperGoals = List.copyOf(paperGoals);
        if (mythicLines.isEmpty() || !mythicLines.getFirst().equals(CLEAR)) {
            throw new IllegalArgumentException("a goal plan must start with '" + CLEAR + "'");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be >= 0, was " + revision);
        }
    }

    /**
     * Counts the goals the plan installs, without {@code clear}.
     *
     * @return MythicMobs lines minus one plus Paper goals
     */
    public int goalCount() {
        return mythicLines.size() - 1 + paperGoals.size();
    }
}
