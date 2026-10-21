package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.context.ShieldGeometry;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import io.lumine.mythic.api.volatilecode.handlers.VolatileAIHandler;
import io.lumine.mythic.core.config.MythicLineConfigImpl;
import org.bukkit.entity.Mob;

import java.util.List;
import java.util.Objects;

/**
 * Writes goal sets into one mob with the native APIs: MythicMobs' {@link VolatileAIHandler#addPathfinderGoals},
 * the path MythicMobs uses for {@code AIGoalSelectors}, and Paper's {@link MobGoals} for ThePredict's four
 * goals. Keeps no per-mob state; the one shared piece is the waypoint budget of the micro goals, which
 * {@link WaypointBudget#beginTick} refills on every server tick (M20.5, A5). Server thread only (rule T1).
 *
 * <p>{@code addPathfinderGoals} splits each line at every space, so MythicMobs escapes spaces and dashes inside
 * argument blocks with {@code MythicLineConfigImpl.unparseBlock} when it reads {@code AIGoalSelectors}
 * ({@code MobType}, javap); {@link #selectorLines} does the same, as if the author had written the line.
 */
public final class GoalSetInstaller {
    /** Delivered {@code decision.tickLayer.maxWaypointPathsPerTick}. */
    public static final int DEFAULT_WAYPOINT_PATHS = 8;
    /** Delivered {@code decision.tickLayer.maxWaypointPathMicrosPerTick}. */
    public static final int DEFAULT_WAYPOINT_MICROS = 400;

    private final VolatileAIHandler aiHandler;
    private final MobGoals mobGoals;
    private final GoalKeys keys;
    private final IntentBoard board;
    private final ServerClock clock;
    private final GoalInspector inspector;
    private final WaypointBudget waypoints;
    private DestinationGuard destinations = DestinationGuard.KEEP;
    private FlankSafety flankSafety = FlankSafety.ALLOW;

    /**
     * Creates the installer with the delivered waypoint caps.
     *
     * @param aiHandler {@code MythicBukkit.inst().getVolatileCodeHandler().getAIHandler()}
     * @param mobGoals {@code Bukkit.getMobGoals()}
     * @param keys ThePredict goal keys
     * @param board intent board read by the Paper goals
     * @param clock clock read by the Paper goals
     */
    public GoalSetInstaller(VolatileAIHandler aiHandler, MobGoals mobGoals, GoalKeys keys, IntentBoard board,
                            ServerClock clock) {
        this(aiHandler, mobGoals, keys, board, clock, DEFAULT_WAYPOINT_PATHS, DEFAULT_WAYPOINT_MICROS);
    }

    /**
     * Creates the installer with the configured waypoint caps (rule D10: the root passes
     * {@code decision.tickLayer.maxWaypointPathsPerTick} and {@code maxWaypointPathMicrosPerTick}).
     *
     * @param aiHandler {@code MythicBukkit.inst().getVolatileCodeHandler().getAIHandler()}
     * @param mobGoals {@code Bukkit.getMobGoals()}
     * @param keys ThePredict goal keys
     * @param board intent board read by the Paper goals
     * @param clock clock read by the Paper goals and the waypoint budget
     * @param maxWaypointPaths paths every micro goal may ask for in one tick, together
     * @param maxWaypointMicros microseconds of paths per tick, together
     */
    public GoalSetInstaller(VolatileAIHandler aiHandler, MobGoals mobGoals, GoalKeys keys, IntentBoard board,
                            ServerClock clock, int maxWaypointPaths, int maxWaypointMicros) {
        this.aiHandler = Objects.requireNonNull(aiHandler, "aiHandler");
        this.mobGoals = Objects.requireNonNull(mobGoals, "mobGoals");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.inspector = new GoalInspector(mobGoals);
        this.waypoints = new WaypointBudget(maxWaypointPaths, maxWaypointMicros, clock::nanoTime);
    }

    /**
     * Escapes lines the way MythicMobs prepares {@code AIGoalSelectors}; pure.
     *
     * @param lines lines in human-readable form
     * @return lines ready for {@code addPathfinderGoals}
     */
    public static List<String> selectorLines(List<String> lines) {
        return lines.stream().map(MythicLineConfigImpl::unparseBlock).toList();
    }

    /**
     * Returns the inspector over the same {@link MobGoals}.
     *
     * @return goal inspector
     */
    public GoalInspector inspector() {
        return inspector;
    }

    /**
     * Q4 used by the next micro goal. {@link DestinationGuard#KEEP} is the default.
     *
     * @param destinations the guard
     */
    public void useDestination(DestinationGuard destinations) {
        this.destinations = Objects.requireNonNull(destinations, "destinations");
    }

    /**
     * Q4 used by the next flank goal. {@link FlankSafety#ALLOW} is the default.
     *
     * @param safety the filter
     */
    public void useFlank(FlankSafety safety) {
        this.flankSafety = Objects.requireNonNull(safety, "safety");
    }

    /**
     * Replaces the mob's goals with a generated set: removes ThePredict's Paper goals (DT-15 alternative),
     * installs the MythicMobs lines from {@code clear}, and adds the plan's Paper goals only if every line
     * produced a goal, so an incomplete set never gets the marker. MythicMobs never throws here: it skips a line
     * invalid for the species and stops at a line that fails, logging both ({@code addPathfinderGoals}, javap).
     *
     * @param mob entity to change
     * @param plan generated set
     * @param generation session generation the Paper goals check
     * @param movement movement settings of the profile, for the flank goal
     * @return MythicMobs goals missing after the lines; {@code 0} when the set is complete and the Paper goals were added
     */
    public int install(Mob mob, GoalPlan plan, int generation, MovementSettings movement) {
        removePaperGoals(mob);
        aiHandler.addPathfinderGoals(mob, selectorLines(plan.mythicLines()));
        var missing = plan.mythicLines().size() - 1 - mythicGoalCount(mob);
        if (missing > 0) {
            return missing;
        }
        for (var slot : plan.paperGoals()) {
            mobGoals.addGoal(mob, slot.priority(), paperGoal(mob, slot.kind(), plan, generation, movement));
        }
        return 0;
    }

    /**
     * Counts the goals of the goal selector that are not ThePredict's Paper goals. Every goal is listed:
     * {@code net.minecraft} {@code Goal} adds {@code UNKNOWN_BEHAVIOR} when its flag set is empty and
     * {@code PaperMobGoals.getAllGoals} walks every {@code GoalType} (javap). Only a goal-selector goal that
     * declares {@code TARGET} would be left out, and no generated or base goal does.
     *
     * @param mob inspected entity
     * @return goals of the goal selector, without target goals and ThePredict's keys
     */
    public int mythicGoalCount(Mob mob) {
        var count = 0;
        for (var goal : mobGoals.getAllGoals(mob)) {
            if (!goal.getTypes().contains(GoalType.TARGET) && !keys.owns(goal.getKey())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Leaves a refusal marker on a mob whose generated set could not be installed, after its goals were given
     * back to MythicMobs; see {@link OwnerMarkerGoal#refused}.
     *
     * @param mob entity to mark
     * @param revision catalog revision of the refused set
     * @param generation session generation of the refused set
     * @param reason fixed cause
     */
    public void markRefused(Mob mob, long revision, int generation, String reason) {
        mobGoals.addGoal(mob, OwnerMarkerGoal.REFUSAL_PRIORITY, OwnerMarkerGoal.refused(keys.owner(), revision, generation, reason));
    }

    /**
     * Removes ThePredict's four Paper goals, whatever their priority.
     *
     * @param mob entity to change
     */
    public void removePaperGoals(Mob mob) {
        mobGoals.removeGoal(mob, keys.flank());
        mobGoals.removeGoal(mob, keys.hold());
        mobGoals.removeGoal(mob, keys.owner());
        mobGoals.removeGoal(mob, keys.micro());
    }

    /**
     * Reads the {@code owner} key. {@code MobGoals#getGoal} returns the goal handed to {@code addGoal}
     * ({@code PaperCustomGoal.getHandle}, javap; DT-15 still open in game).
     *
     * @param mob inspected entity
     * @return missing, foreign, own or refusal marker
     */
    public InstallDecision.Marker marker(Mob mob) {
        Goal<Mob> goal = mobGoals.getGoal(mob, keys.owner());
        if (goal == null) {
            return new InstallDecision.Marker.Missing();
        }
        if (goal instanceof OwnerMarkerGoal owner && !owner.refusal().isEmpty()) {
            return new InstallDecision.Marker.Refused(owner.revision(), owner.generation(), owner.refusal());
        }
        if (goal instanceof OwnerMarkerGoal owner) {
            return new InstallDecision.Marker.Owned(owner.revision(), owner.generation(), owner.goalCount());
        }
        return new InstallDecision.Marker.Foreign();
    }

    /**
     * Tells whether a goal other than {@code hold} runs with the {@code MOVE} flag (M05.5). It compares keys
     * because the keys Paper gives MythicMobs goals are not confirmed yet (DT-06).
     *
     * @param mob inspected entity
     * @return {@code true} when a movement goal runs
     */
    public boolean movementGoalRunning(Mob mob) {
        for (var goal : mobGoals.getRunningGoals(mob, GoalType.MOVE)) {
            if (!goal.getKey().equals(keys.hold())) {
                return true;
            }
        }
        return false;
    }

    /** Builds one Paper goal of a generated set. */
    private Goal<Mob> paperGoal(Mob mob, PaperGoalKind kind, GoalPlan plan, int generation, MovementSettings movement) {
        return switch (kind) {
            case FLANK -> new FlankGoal(mob, keys.flank(), board, clock, generation, movement.meleeReachBlocks(),
                    movement.approachSpeed(), ShieldGeometry::raisedShieldAngleDegrees, flankSafety);
            case HOLD -> new HoldGoal(mob, keys.hold(), board, clock, generation);
            case OWNER -> new OwnerMarkerGoal(keys.owner(), plan.revision(), generation, plan.goalCount());
            case MICRO -> new MicroGoal(mob, keys.micro(), board, clock, waypoints, generation, movement,
                    destinations);
        };
    }
}
