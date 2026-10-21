package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.ImpactTiming;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MicroSide;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.Steering;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code thepredict:micro} (M20.5). Declares only {@code MOVE}. Active while the view of its generation controls
 * and the micro is not native. A path is asked only with budget, on the ground and not while staggered. Each published
 * micro has its own geometry. An orbit renews every profile refresh after it was placed; its first renewal waits a
 * phase from {@link StableRandom#SALT_PHASE} so the mobs of a server do not repath together. Enter and hover renew
 * when the target moves more than one block. Sidestep and backstep are asked once. The first path failure flips the
 * side of the next paths ({@link MicroRoute#side}); the second leaves native movement for a while. Stopping cuts a
 * path only when its final point is the one this goal asked for.
 *
 * <p>Paper calls {@link #tick()} every other tick with a parity fixed per mob (V4 of M20), so steering never depends
 * on the parity of the server tick: a steer happens on the first {@code tick()} at least two ticks after the last one,
 * and on the tick an orbit comes due.
 */
public final class MicroGoal implements Goal<Mob> {
    /** Ticks a pending path may stay invisible before it counts as a failure. */
    public static final int PENDING_TICKS = 4;
    /** Reach of a player, the hover distance the tick layer already publishes. */
    static final double TARGET_REACH_BLOCKS = 3.0;
    private static final double RETARGET_BLOCKS = 1.0;

    private final MicroBody body;
    private final UUID entityId;
    private final GoalKey<Mob> key;
    private final IntentBoard board;
    private final ServerClock clock;
    private final WaypointBudget budget;
    private final int generation;
    private final MovementSettings installedMovement;
    private final long installedSeed;
    private final DestinationGuard destinations;
    private final MicroRoute route = new MicroRoute();
    private double askedX;
    private double askedZ;
    private boolean hasAsked;
    private boolean placedOnce;
    private MicroBehaviour placed = MicroBehaviour.NATIVE;
    private double lastTargetX;
    private double lastTargetZ;
    private long lastSteerTick = Long.MIN_VALUE;
    private long orbitDueTick = Long.MIN_VALUE;
    private boolean orbitPhased;

    /**
     * Creates the goal for one session. The movement band is the profile default until the tick layer notes one,
     * and the waypoint refresh is the one published on the board.
     *
     * @param mob owner
     * @param key {@link GoalKeys#micro()}
     * @param board intent board
     * @param clock clock of the deadline
     * @param budget paths left this tick
     * @param generation generation the goal was installed for
     */
    public MicroGoal(Mob mob, GoalKey<Mob> key, IntentBoard board, ServerClock clock, WaypointBudget budget,
                     int generation) {
        this(mob, key, board, clock, budget, generation, MovementSettings.DEFAULTS);
    }

    /**
     * Creates the goal with the movement band of the installed profile.
     *
     * @param mob owner
     * @param key {@link GoalKeys#micro()}
     * @param board intent board
     * @param clock clock of the deadline
     * @param budget paths left this tick
     * @param generation generation the goal was installed for
     * @param movement band, speeds and arena of the profile
     */
    public MicroGoal(Mob mob, GoalKey<Mob> key, IntentBoard board, ServerClock clock, WaypointBudget budget,
                     int generation, MovementSettings movement) {
        this(mob, key, board, clock, budget, generation, movement, DestinationGuard.KEEP);
    }

    /**
     * Creates the goal with the movement band and the Q4 destination guard.
     *
     * @param mob owner
     * @param key {@link GoalKeys#micro()}
     * @param board intent board
     * @param clock clock of the deadline
     * @param budget paths left this tick
     * @param generation generation the goal was installed for
     * @param movement band, speeds and arena of the profile
     * @param destinations Q4; {@link DestinationGuard#KEEP} accepts the geometry
     */
    public MicroGoal(Mob mob, GoalKey<Mob> key, IntentBoard board, ServerClock clock, WaypointBudget budget,
                     int generation, MovementSettings movement, DestinationGuard destinations) {
        this(new MobBody(Objects.requireNonNull(mob, "mob")), mob.getUniqueId(),
                Objects.requireNonNull(key, "key"), board, clock, budget, generation, movement, 0L, destinations);
    }

    /**
     * Goal over a double, for tests. A null key is tolerated because the tests do not ask {@link #getKey()}.
     *
     * @param body mob double
     * @param entityId mob id
     * @param key goal key, or null in a test that does not read it
     * @param board intent board
     * @param clock clock of the deadline
     * @param budget paths left this tick
     * @param generation generation the goal was installed for
     * @param movement band used until the board notes one
     * @param seed session seed used until the board notes one
     */
    MicroGoal(MicroBody body, UUID entityId, GoalKey<Mob> key, IntentBoard board, ServerClock clock,
              WaypointBudget budget, int generation, MovementSettings movement, long seed) {
        this(body, entityId, key, board, clock, budget, generation, movement, seed, DestinationGuard.KEEP);
    }

    /**
     * Goal over a double, with the destination guard of Q4.
     *
     * @param body mob double
     * @param entityId mob id
     * @param key goal key, or null in a test that does not read it
     * @param board intent board
     * @param clock clock of the deadline
     * @param budget paths left this tick
     * @param generation generation the goal was installed for
     * @param movement band used until the board notes one
     * @param seed session seed used until the board notes one
     * @param destinations Q4; {@link DestinationGuard#KEEP} accepts the geometry
     */
    MicroGoal(MicroBody body, UUID entityId, GoalKey<Mob> key, IntentBoard board, ServerClock clock,
              WaypointBudget budget, int generation, MovementSettings movement, long seed,
              DestinationGuard destinations) {
        this.body = Objects.requireNonNull(body, "body");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.key = key;
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.generation = generation;
        this.installedMovement = Objects.requireNonNull(movement, "movement");
        this.installedSeed = seed;
        this.destinations = Objects.requireNonNull(destinations, "destinations");
    }

    /**
     * Pure activation rule.
     *
     * @param view current view
     * @param generation generation of the goal
     * @param nowNanos monotonic time
     * @return whether this goal may move
     */
    public static boolean shouldMove(IntentView view, int generation, long nowNanos) {
        return view.generation() == generation && view.controls(nowNanos) && view.micro() != MicroBehaviour.NATIVE;
    }

    @Override
    public boolean shouldActivate() {
        return shouldMove(board.current(entityId), generation, clock.nanoTime());
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void start() {
        steer(clock.currentTick());
    }

    @Override
    public void tick() {
        var now = clock.currentTick();
        if (route.nativeNow(now)) {
            holdNative();
            return;
        }
        var committed = sample(now) && steer(now);
        if (!committed && route.tracking()) {
            apply(route.note(true, visible(), now));
        }
    }

    @Override
    public void stop() {
        if (hasAsked && body.hasEnd() && MicroRoute.owns(askedX, askedZ, body.endX(), body.endZ())) {
            body.stopPathfinding();
        }
        hasAsked = false;
    }

    @Override
    public GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE);
    }

    /**
     * Last destination asked for.
     *
     * @return the last destination, empty before the first path
     */
    public Optional<Location> asked() {
        if (!hasAsked) {
            return Optional.empty();
        }
        return Optional.of(body.at(askedX, body.y(), askedZ));
    }

    private boolean sample(long tick) {
        if (lastSteerTick == Long.MIN_VALUE || tick - lastSteerTick >= 2) {
            return true;
        }
        return board.current(entityId).micro() == MicroBehaviour.ORBIT && tick >= orbitDueTick;
    }

    /**
     * Asks a path when the budget, the body and the published micro allow it. The side is the published one until the
     * first path failure flips it ({@link MicroRoute#side}).
     *
     * @return whether a path was asked or the goal parked itself, so the caller does not note a pending path again
     */
    private boolean steer(long tick) {
        budget.beginTick(tick);
        if (!budget.allow() || !body.onGround() || staggered() || !body.hasTarget()) {
            return false;
        }
        lastSteerTick = tick;
        var view = board.current(entityId);
        var behaviour = view.micro();
        if (!due(behaviour, tick)) {
            return false;
        }
        var movement = movement();
        var side = route.side(view.microSide());
        var point = destination(behaviour, view, movement, side);
        if (behaviour == MicroBehaviour.ENTER) {
            commit(tick, behaviour, point, movement);
            return true;
        }
        var columnX = (int) Math.floor(body.x());
        var columnZ = (int) Math.floor(body.z());
        var decided = destinations.ask(entityId, behaviour, point, columnX, columnZ);
        if (decided.isEmpty() && (behaviour == MicroBehaviour.SIDESTEP || behaviour == MicroBehaviour.ORBIT)) {
            var flipped = side == MicroSide.LEFT ? MicroSide.RIGHT : MicroSide.LEFT;
            var other = destination(behaviour, view, movement, flipped);
            decided = destinations.ask(entityId, behaviour, other, columnX, columnZ);
        }
        if (decided.isEmpty()) {
            route.park(tick);
            holdNative();
            return true;
        }
        commit(tick, behaviour, decided.get(), movement);
        return true;
    }

    private void commit(long tick, MicroBehaviour behaviour, Steering point, MovementSettings movement) {
        var started = budget.nanoTime();
        var accepted = ask(behaviour, point, movement);
        budget.spent(started, budget.nanoTime());
        askedX = point.x();
        askedZ = point.z();
        hasAsked = true;
        placedOnce = true;
        placed = behaviour;
        lastTargetX = body.targetX();
        lastTargetZ = body.targetZ();
        if (behaviour == MicroBehaviour.ORBIT) {
            orbitDueTick = tick + orbitRenewal();
        }
        apply(route.note(accepted, visible(), tick));
    }

    /** Ticks until the orbit renews: the published refresh, plus the phase of the seed before the first renewal. */
    private long orbitRenewal() {
        var refresh = board.waypointRefreshTicks();
        if (orbitPhased) {
            return refresh;
        }
        orbitPhased = true;
        return refresh + StableRandom.below(StableRandom.draw(seed(), generation, StableRandom.SALT_PHASE), refresh);
    }

    private boolean ask(MicroBehaviour behaviour, Steering point, MovementSettings movement) {
        if (behaviour == MicroBehaviour.ENTER) {
            return body.moveToTarget(Steering.enterSpeed(movement.approachSpeed()));
        }
        return body.moveToPoint(point.x(), body.y(), point.z(), 1.0);
    }

    private Steering destination(MicroBehaviour behaviour, IntentView view, MovementSettings movement,
                                 MicroSide side) {
        var arena = movement.arenaRadiusBlocks().isPresent() ? movement.arenaRadiusBlocks().getAsDouble() : 0.0;
        return switch (behaviour) {
            case ORBIT -> Steering.orbit(body.x(), body.z(), body.targetX(), body.targetZ(), side,
                    movement.bandInnerBlocks(), movement.bandOuterBlocks(), body.x(), body.z(), arena);
            case SIDESTEP -> Steering.sidestep(body.x(), body.z(), body.targetX(), body.targetZ(), side);
            case BACKSTEP -> Steering.backstep(body.x(), body.z(), body.targetX(), body.targetZ());
            case HOVER -> Steering.hover(body.x(), body.z(), body.targetX(), body.targetZ(), TARGET_REACH_BLOCKS);
            case STILL -> new Steering(body.x(), body.z());
            case REPOSITION -> new Steering(body.targetX(), body.targetZ());
            default -> new Steering(body.targetX(), body.targetZ());
        };
    }

    private boolean due(MicroBehaviour behaviour, long tick) {
        if (!placedOnce || behaviour != placed) {
            return true;
        }
        return switch (behaviour) {
            case SIDESTEP, BACKSTEP -> false;
            case ORBIT -> tick >= orbitDueTick;
            case ENTER, HOVER -> targetMoved();
            default -> false;
        };
    }

    private boolean targetMoved() {
        var dx = body.targetX() - lastTargetX;
        var dz = body.targetZ() - lastTargetZ;
        return dx * dx + dz * dz > RETARGET_BLOCKS * RETARGET_BLOCKS;
    }

    private MovementSettings movement() {
        var noted = board.steerMovement(entityId);
        return noted.isPresent() ? noted.get() : installedMovement;
    }

    private long seed() {
        var noted = board.steerSeed(entityId);
        return noted.isPresent() ? noted.getAsLong() : installedSeed;
    }

    private void apply(MicroRoute.Mark mark) {
        if (mark == MicroRoute.Mark.PENDING) {
            board.noteMicroPending();
        } else if (mark == MicroRoute.Mark.FLIP || mark == MicroRoute.Mark.NATIVE) {
            board.noteMicroMissing();
            if (mark == MicroRoute.Mark.NATIVE) {
                holdNative();
            }
        }
    }

    private void holdNative() {
        var view = board.current(entityId);
        if (view.generation() == generation) {
            board.publish(new akyrion.thepredict.domain.MobKey(entityId, generation),
                    view.withMicro(MicroBehaviour.NATIVE, view.microSide()));
        }
        board.holdMicroNative(entityId, route.nativeUntil());
    }

    private boolean visible() {
        return body.hasPath();
    }

    private boolean staggered() {
        return ImpactTiming.ticksUntilHittable(body.noDamageTicks(), body.maximumNoDamageTicks()) > 0;
    }

    private static final class MobBody implements MicroBody {
        private final Mob mob;

        private MobBody(Mob mob) {
            this.mob = mob;
        }

        @Override
        public double x() {
            return mob.getX();
        }

        @Override
        public double y() {
            return mob.getY();
        }

        @Override
        public double z() {
            return mob.getZ();
        }

        @Override
        public boolean onGround() {
            return mob.isOnGround();
        }

        @Override
        public int noDamageTicks() {
            return mob.getNoDamageTicks();
        }

        @Override
        public int maximumNoDamageTicks() {
            return mob.getMaximumNoDamageTicks();
        }

        @Override
        public boolean hasTarget() {
            return mob.getTarget() instanceof LivingEntity;
        }

        @Override
        public double targetX() {
            return live().getX();
        }

        @Override
        public double targetY() {
            return live().getY();
        }

        @Override
        public double targetZ() {
            return live().getZ();
        }

        @Override
        public boolean moveToPoint(double x, double y, double z, double speed) {
            return mob.getPathfinder().moveTo(at(x, y, z), speed);
        }

        @Override
        public boolean moveToTarget(double speed) {
            Entity target = mob.getTarget();
            return target != null && mob.getPathfinder().moveTo(target, speed);
        }

        @Override
        public boolean hasPath() {
            return mob.getPathfinder().hasPath();
        }

        @Override
        public boolean hasEnd() {
            var path = mob.getPathfinder().getCurrentPath();
            return path != null && path.getFinalPoint() != null;
        }

        @Override
        public double endX() {
            return mob.getPathfinder().getCurrentPath().getFinalPoint().getX();
        }

        @Override
        public double endZ() {
            return mob.getPathfinder().getCurrentPath().getFinalPoint().getZ();
        }

        @Override
        public void stopPathfinding() {
            mob.getPathfinder().stopPathfinding();
        }

        @Override
        public Location at(double x, double y, double z) {
            return new Location(mob.getWorld(), x, y, z);
        }

        private LivingEntity live() {
            return (LivingEntity) mob.getTarget();
        }
    }
}
