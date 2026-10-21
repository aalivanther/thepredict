package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.context.ShieldGeometry;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@code thepredict:flank} Paper goal (I10, M05.3): walks around a target that raises a shield until the
 * mob stands outside the shield's blocking angle. Declares only {@code MOVE}. Server thread only.
 *
 * <p>Active while the view of its generation controls with {@link Movement#FLANK} and the target raises an
 * item with {@code BLOCKS_ATTACKS}. It asks Paper's {@code Pathfinder} for a path to two points beside the
 * target, at {@code meleeReachBlocks} and at the blocking angle plus 20° from its facing, and follows the
 * reachable one with fewer points. One planning round (both sides) happens at most once every
 * {@link #REPLAN_INTERVAL_TICKS} ticks, after the first only if the target moved over 1 block or turned over
 * 30°. It stops without a path on either side or when the view drops {@code FLANK}; M10 judges progress.
 */
public final class FlankGoal implements Goal<Mob> {
    /** Minimum number of server ticks between two planning rounds. */
    public static final int REPLAN_INTERVAL_TICKS = 10;

    private final Mob mob;
    private final UUID entityId;
    private final GoalKey<Mob> key;
    private final IntentBoard board;
    private final ServerClock clock;
    private final int generation;
    private final double reachBlocks;
    private final double speed;
    private final Function<LivingEntity, OptionalDouble> shieldAngle;
    private final FlankSafety safety;
    private boolean planned;
    private int lastPlanTick;
    private Location lastTarget;
    private double blockingAngleDegrees = ShieldGeometry.DEFAULT_BLOCKING_ANGLE_DEGREES;
    private boolean following;

    /**
     * Creates the goal for one mob session.
     *
     * @param mob owner of the goal
     * @param key {@link GoalKeys#flank()}
     * @param board intent board
     * @param clock clock for the intent deadline and the server tick
     * @param generation generation of the session the goal belongs to
     * @param reachBlocks distance from the target to each flank point, in blocks
     * @param speed navigation speed multiplier
     * @param shieldAngle shield angle of a target, empty without one ({@link ShieldGeometry#raisedShieldAngleDegrees})
     */
    public FlankGoal(Mob mob, GoalKey<Mob> key, IntentBoard board, ServerClock clock, int generation,
                     double reachBlocks, double speed, Function<LivingEntity, OptionalDouble> shieldAngle) {
        this(mob, key, board, clock, generation, reachBlocks, speed, shieldAngle, FlankSafety.ALLOW);
    }

    /**
     * Creates the goal with the zone filter of its path points.
     *
     * @param mob owner of the goal
     * @param key {@link GoalKeys#flank()}
     * @param board intent board
     * @param clock clock for the intent deadline and the server tick
     * @param generation generation of the session the goal belongs to
     * @param reachBlocks distance from the target to each flank point, in blocks
     * @param speed navigation speed multiplier
     * @param shieldAngle shield angle of a target, empty without one
     * @param safety Q4 of the path; {@link FlankSafety#ALLOW} accepts every point
     */
    public FlankGoal(Mob mob, GoalKey<Mob> key, IntentBoard board, ServerClock clock, int generation,
                     double reachBlocks, double speed, Function<LivingEntity, OptionalDouble> shieldAngle,
                     FlankSafety safety) {
        this.mob = Objects.requireNonNull(mob, "mob");
        this.entityId = mob.getUniqueId();
        this.key = Objects.requireNonNull(key, "key");
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.generation = generation;
        if (!(reachBlocks > 0) || !(speed > 0) || Double.isInfinite(reachBlocks) || Double.isInfinite(speed)) {
            throw new IllegalArgumentException("reach and speed must be positive and finite");
        }
        this.reachBlocks = reachBlocks;
        this.speed = speed;
        this.shieldAngle = Objects.requireNonNull(shieldAngle, "shieldAngle");
        this.safety = Objects.requireNonNull(safety, "safety");
    }

    /**
     * Pure activation rule without the target check, tested without entities.
     *
     * @param view current view of the mob
     * @param generation generation the goal was installed for
     * @param nowNanos current monotonic time
     * @return {@code true} if the view of that generation controls with {@code FLANK}
     */
    public static boolean wantsFlank(IntentView view, int generation, long nowNanos) {
        return view.generation() == generation && view.movement() == Movement.FLANK && view.controls(nowNanos);
    }

    /**
     * Pure re-planning interval, tested without entities. Ticks are compared modulo 2<sup>32</sup>, like the
     * Bukkit tick counter.
     *
     * @param lastPlanTick server tick of the last planning round
     * @param nowTick current server tick
     * @return {@code true} once at least {@link #REPLAN_INTERVAL_TICKS} ticks have passed
     */
    public static boolean shouldReplan(int lastPlanTick, int nowTick) {
        return nowTick - lastPlanTick >= REPLAN_INTERVAL_TICKS;
    }

    /**
     * Activates when the view asks for {@code FLANK}, a planning round is allowed and the target raises a
     * shield that can be outflanked.
     *
     * @return whether the goal may start
     */
    @Override
    public boolean shouldActivate() {
        if (!wantsFlank(board.current(entityId), generation, clock.nanoTime()) || !mayPlan()) {
            return false;
        }
        var target = mob.getTarget();
        var angle = target == null ? OptionalDouble.empty() : shieldAngle.apply(target);
        if (angle.isEmpty() || angle.getAsDouble() >= ShieldGeometry.BEHIND_DEGREES) {
            return false;
        }
        blockingAngleDegrees = angle.getAsDouble();
        return true;
    }

    /**
     * Stays active while the last plan found a path and the view still asks for {@code FLANK}.
     *
     * @return whether the goal keeps running
     */
    @Override
    public boolean shouldStayActive() {
        return following && wantsFlank(board.current(entityId), generation, clock.nanoTime());
    }

    /** Plans the first path. */
    @Override
    public void start() {
        plan();
    }

    /** Plans again when allowed and the target moved or turned enough. */
    @Override
    public void tick() {
        var target = mob.getTarget();
        if (target != null && mayPlan()
                && (lastTarget == null || FlankPaths.targetMovedOrTurned(lastTarget, target.getLocation()))) {
            plan();
        }
    }

    /** Stops the navigation this goal started. */
    @Override
    public void stop() {
        following = false;
        mob.getPathfinder().stopPathfinding();
    }

    /**
     * Returns the key {@code <plugin>:flank}.
     *
     * @return goal key
     */
    @Override
    public GoalKey<Mob> getKey() {
        return key;
    }

    /**
     * Declares only {@code MOVE}.
     *
     * @return a new set with {@link GoalType#MOVE}
     */
    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE);
    }

    /** Whether a planning round is allowed now. */
    private boolean mayPlan() {
        return !planned || shouldReplan(lastPlanTick, (int) clock.currentTick());
    }

    /** Chooses the reachable flank point with the shorter path and follows it. */
    private void plan() {
        planned = true;
        lastPlanTick = (int) clock.currentTick();
        var target = mob.getTarget();
        if (target == null) {
            following = false;
            return;
        }
        var origin = target.getLocation();
        lastTarget = origin;
        blockingAngleDegrees = shieldAngle.apply(target).orElse(blockingAngleDegrees);
        var best = FlankPaths.shortestReachable(mob.getPathfinder(), origin,
                ShieldGeometry.flankTurnDegrees(blockingAngleDegrees), reachBlocks, safety, entityId);
        following = best.isPresent() && mob.getPathfinder().moveTo(best.get(), speed);
    }
}
