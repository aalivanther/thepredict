package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.vocabulary.PredictorMoveCondition;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickLayerMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a profile and its base AI into the ordered goal set of M05.1, as text, without touching the server.
 * Stateless, deterministic and free of clocks; safe on any thread.
 *
 * <p>Priorities: {@code 0 clear}, {@code 1 float}, {@code 2} arena, {@code 3–6} tactical MythicMobs goals
 * gated by {@code predictormove}, {@code 7} Paper {@code flank}, {@code 8} Paper {@code hold}, the base AI
 * shifted by {@link #BASE_AI_OFFSET}, then {@code lookAtTarget} if the base AI lacks it and the Paper
 * {@code owner} marker. In {@link TickLayerMode#FULL} the Paper {@code micro} goal takes priority 3 and the goals
 * from 3 to 8 move up by one. Only movements used by a tactic get goals. Numbers are written with at most two
 * decimals, a dot and no exponent; the only other text comes from constants and the base AI lines.
 *
 * <p>The two flee lines carry {@code targetconditions=[ - isTarget true ]}: {@code FleeConditionalGoal} builds an
 * {@code AsyncAvoidEntityGoal} over every {@code LivingEntity} and, without target conditions, flees from the nearest
 * one, allies included (javap, MythicMobs 5.13.1). {@code isTarget} ({@code IsTargetCondition}, name {@code target})
 * compares each candidate with {@code Mob.getTarget()}, so the mob only flees from the target of its AI.
 */
public final class GoalLineGenerator {
    /** Added to every base AI priority so the base AI always runs after the tactical goals. */
    public static final int BASE_AI_OFFSET = 10;

    /** Priority of the Paper {@code flank} goal. */
    public static final int FLANK_PRIORITY = 7;

    /** Priority of the Paper {@code hold} goal. */
    public static final int HOLD_PRIORITY = 8;

    /** Priority of the Paper {@code micro} goal in {@link TickLayerMode#FULL}. */
    public static final int MICRO_PRIORITY = 3;

    private static final String LOOK_AT_TARGET = "lookAtTarget";

    /** Target conditions of a flee line: only the AI target is avoided ({@code IsTargetCondition}). */
    static final String FLEE_FROM_TARGET = ";targetconditions=[ - isTarget true ]";

    /** Creates the generator; it holds no state. */
    public GoalLineGenerator() {
    }

    /**
     * Generates the goal set of a profile.
     *
     * @param profile tactical profile; its tactics decide which movement goals exist
     * @param baseAi base AI chosen by {@link BaseAiResolver}
     * @param followRangeBlocks the mob's follow range, used as the maximum range of {@code APPROACH}; finite and positive
     * @param catalogRevision catalog revision stored in the plan, {@code >= 0}
     * @return the plan
     * @throws IllegalArgumentException if the follow range is not finite and positive or the revision is negative
     */
    public GoalPlan generate(TacticalProfile profile, BaseAiSource baseAi, double followRangeBlocks,
                             long catalogRevision) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(baseAi, "baseAi");
        if (!(followRangeBlocks > 0) || Double.isInfinite(followRangeBlocks)) {
            throw new IllegalArgumentException("followRangeBlocks must be finite and positive, was " + followRangeBlocks);
        }
        var used = usedMovements(profile);
        var lines = new ArrayList<String>();
        lines.add(GoalPlan.CLEAR);
        lines.add("1 float");
        addTacticalLines(lines, profile.movement(), used, followRangeBlocks);
        var paper = new ArrayList<PaperGoalSlot>(3);
        if (used.contains(Movement.FLANK)) {
            paper.add(new PaperGoalSlot(PaperGoalKind.FLANK, FLANK_PRIORITY));
        }
        if (!used.isEmpty()) {
            paper.add(new PaperGoalSlot(PaperGoalKind.HOLD, HOLD_PRIORITY));
        }
        var last = addBaseAi(lines, baseAi.lines());
        paper.add(new PaperGoalSlot(PaperGoalKind.OWNER, last + 1));
        return new GoalPlan(lines, paper, catalogRevision);
    }

    /**
     * Same lines as {@link #generate(TacticalProfile, BaseAiSource, double, long)} unless the layer is
     * {@link TickLayerMode#FULL}, which adds the Paper goal {@code thepredict:micro} at priority 3 and shifts
     * priorities 3–8 up by one. The base AI stays at {@link #BASE_AI_OFFSET}. The micro goal is never a MythicMobs
     * line: {@code addPathfinderGoals} skips a goal name it does not know without creating it (javap,
     * {@code VolatileAIHandlerImpl}), and the installer would then count the set as incomplete.
     *
     * @param profile tactical profile; its tactics decide which movement goals exist
     * @param baseAi base AI chosen by {@link BaseAiResolver}
     * @param followRangeBlocks the mob's follow range, used as the maximum range of {@code APPROACH}
     * @param catalogRevision catalog revision stored in the plan, {@code >= 0}
     * @param mode tick layer of the profile
     * @return the plan
     */
    public GoalPlan generate(TacticalProfile profile, BaseAiSource baseAi, double followRangeBlocks,
                             long catalogRevision, TickLayerMode mode) {
        var plan = generate(profile, baseAi, followRangeBlocks, catalogRevision);
        if (mode != TickLayerMode.FULL) {
            return plan;
        }
        var lines = new ArrayList<String>(plan.mythicLines().size());
        for (var line : plan.mythicLines()) {
            var space = line.indexOf(' ');
            var priority = Integer.parseInt(line.substring(0, space));
            lines.add(shifted(priority) + line.substring(space));
        }
        var paper = new ArrayList<PaperGoalSlot>(plan.paperGoals().size() + 1);
        paper.add(new PaperGoalSlot(PaperGoalKind.MICRO, MICRO_PRIORITY));
        for (var slot : plan.paperGoals()) {
            paper.add(new PaperGoalSlot(slot.kind(), shifted(slot.priority())));
        }
        return new GoalPlan(lines, paper, catalogRevision);
    }

    /** Priorities 3–8 move up by one in {@link TickLayerMode#FULL}, leaving {@link #MICRO_PRIORITY} free. */
    private static int shifted(int priority) {
        return priority >= MICRO_PRIORITY && priority <= HOLD_PRIORITY ? priority + 1 : priority;
    }

    /**
     * Formats a number for a goal line: at most two decimals rounded half up, a dot as separator, no
     * exponent and no trailing zeros, whatever the default locale.
     *
     * @param value finite number
     * @return text such as {@code 24}, {@code 1.15} or {@code 0.5}
     */
    public static String number(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Movements asked by at least one tactic, without {@code NONE}. */
    private static Set<Movement> usedMovements(TacticalProfile profile) {
        var used = EnumSet.noneOf(Movement.class);
        for (var tactic : profile.tactics().values()) {
            used.add(tactic.movement());
        }
        used.remove(Movement.NONE);
        return used;
    }

    /** Adds the arena line and the MythicMobs goals of the used movements, priorities 2 to 6. */
    private static void addTacticalLines(List<String> lines, MovementSettings movement, Set<Movement> used,
                                         double followRangeBlocks) {
        var approach = number(movement.approachSpeed());
        movement.arenaRadiusBlocks().ifPresent(radius -> lines.add(
                "2 goToSpawnLocation{maxrange=" + number(radius) + ";speed=" + approach + "}"));
        if (used.contains(Movement.RETREAT)) {
            lines.add("3 fleeConditional{distance=" + number(movement.retreatDistanceBlocks()) + ";speed="
                    + number(movement.retreatSpeed()) + ";mc=" + gate(Movement.RETREAT) + FLEE_FROM_TARGET + "}");
        }
        if (used.contains(Movement.KEEP_DISTANCE)) {
            lines.add("4 fleeConditional{distance=" + number(movement.bandInnerBlocks()) + ";speed=" + approach
                    + ";mc=" + gate(Movement.KEEP_DISTANCE) + FLEE_FROM_TARGET + "}");
            lines.add("5 moveWithinConditional{range=" + number(movement.bandOuterBlocks()) + ";speed=" + approach
                    + ";mc=" + gate(Movement.KEEP_DISTANCE) + "}");
        }
        if (used.contains(Movement.APPROACH)) {
            lines.add("6 moveTowardsTargetConditional{speed=" + approach + ";minrange="
                    + number(movement.meleeReachBlocks()) + ";maxrange=" + number(followRangeBlocks)
                    + ";mc=" + gate(Movement.APPROACH) + "}");
        }
    }

    /** Condition list that lets a goal run only while the intent asks for the movement. */
    private static String gate(Movement movement) {
        return "[ - " + PredictorMoveCondition.NAME + "{m=" + movement.name() + "} true ]";
    }

    /**
     * Adds the base AI shifted by {@link #BASE_AI_OFFSET} and {@code lookAtTarget} when it is missing.
     *
     * @return the highest priority written
     */
    private static int addBaseAi(List<String> lines, List<String> baseAi) {
        var last = BASE_AI_OFFSET - 1;
        var looks = false;
        for (var line : BaseAiLines.parse(baseAi)) {
            var priority = line.priority() + BASE_AI_OFFSET;
            lines.add(priority + " " + line.goal());
            last = Math.max(last, priority);
            looks |= BaseAiLines.hasGoal(line.goal(), LOOK_AT_TARGET);
        }
        if (!looks) {
            last++;
            lines.add(last + " " + LOOK_AT_TARGET);
        }
        return last;
    }
}
