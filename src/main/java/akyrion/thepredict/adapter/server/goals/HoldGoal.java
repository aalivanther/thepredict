package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code thepredict:hold} Paper goal (I10, M05.3).
 *
 * <p>Declares only {@link GoalType#MOVE}, so {@code lookAtTarget} keeps the {@code LOOK} flag; that is why
 * {@code doNothing}, which declares {@code MOVE_LOOK}, is not used. Active while the view of its session
 * generation controls the mob; higher-priority tactical goals interrupt it when their condition holds, and
 * while it runs no base goal can take {@code MOVE}. It stops the current navigation on start and does nothing
 * else. Paper selects and ticks it every 2 ticks ({@code PaperCustomGoal}). Server thread only.
 */
public final class HoldGoal implements Goal<Mob> {
    private final Mob mob;
    private final UUID entityId;
    private final GoalKey<Mob> key;
    private final IntentBoard board;
    private final ServerClock clock;
    private final int generation;

    /**
     * Creates the goal for one mob session.
     *
     * @param mob owner of the goal
     * @param key {@link GoalKeys#hold()}
     * @param board intent board
     * @param clock clock whose {@code nanoTime} checks the intent deadline
     * @param generation generation of the session the goal belongs to
     */
    public HoldGoal(Mob mob, GoalKey<Mob> key, IntentBoard board, ServerClock clock, int generation) {
        this.mob = Objects.requireNonNull(mob, "mob");
        this.entityId = mob.getUniqueId();
        this.key = Objects.requireNonNull(key, "key");
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.generation = generation;
    }

    /**
     * Pure activation rule, tested without entities.
     *
     * @param view current view of the mob
     * @param generation generation the goal was installed for
     * @param nowNanos current monotonic time
     * @return {@code true} if the view belongs to that generation and controls
     */
    public static boolean shouldHold(IntentView view, int generation, long nowNanos) {
        return view.generation() == generation && view.controls(nowNanos);
    }

    /**
     * Activates while ThePredict controls the mob.
     *
     * @return whether the goal may start
     */
    @Override
    public boolean shouldActivate() {
        return shouldHold(board.current(entityId), generation, clock.nanoTime());
    }

    /**
     * Stays active under the same rule as activation.
     *
     * @return whether the goal keeps running
     */
    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    /** Stops the navigation the mob was following. */
    @Override
    public void start() {
        mob.getPathfinder().stopPathfinding();
    }

    /**
     * Returns the key {@code <plugin>:hold}.
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
}
