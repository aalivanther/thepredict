package akyrion.thepredict.adapter.server.context;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;

/**
 * Whether any living entity raises or blocks with an item that carries {@code BLOCKS_ATTACKS} (M17.4, DT-38): a human
 * blocks when {@code HumanEntity#isBlocking()} says so, as in 0.0.1; any other entity raises the item while it is its
 * active item ({@code hasActiveItem()}) and blocks once {@code getActiveItemUsedTime()} reaches
 * {@code blockDelayTicks}, which vanilla computes as {@code Math.round(blockDelaySeconds() × 20f)} (javap of
 * {@code BlocksAttacks.blockDelayTicks} on the patched server; {@code 0} blocks at once), the same comparison as
 * {@code LivingEntity.getItemBlockingWith}. {@code isHandRaised()} and
 * {@code getHandRaisedTime()} ({@code @ApiStatus.Obsolete} since 1.20.4) and {@code getShieldBlockingDelay()}
 * ({@code @Deprecated} since 1.21.9, a fixed 5) are not used. Shared by the capture, the check before a cast and the
 * listener of blocked hits. {@link #of} reads an entity on the server thread; {@link #state} is pure.
 */
public final class Blocking {
    /** Server ticks per second, for the block delay of the component. */
    public static final int TICKS_PER_SECOND = 20;

    private Blocking() {
    }

    /**
     * Raised and blocking state of an entity.
     *
     * @param raised the active item carries {@code BLOCKS_ATTACKS}
     * @param blocking the item already blocks
     */
    public record BlockState(boolean raised, boolean blocking) {
    }

    /**
     * Reads the state of an entity. Server thread only.
     *
     * @param entity a participant or target
     * @return its state
     */
    public static BlockState of(LivingEntity entity) {
        var active = entity.hasActiveItem();
        var blocks = active ? entity.getActiveItem().getData(DataComponentTypes.BLOCKS_ATTACKS) : null;
        if (entity instanceof HumanEntity human) {
            return new BlockState(blocks != null, human.isBlocking());
        }
        return state(blocks != null, blocks == null ? 0.0f : blocks.blockDelaySeconds(),
                active ? entity.getActiveItemUsedTime() : 0);
    }

    /**
     * Tells whether an entity blocks now, reading of a human only {@code isBlocking()}, as 0.0.1 did. Server thread
     * only.
     *
     * @param entity a target or a hit victim
     * @return {@code true} while it blocks
     */
    public static boolean blocking(LivingEntity entity) {
        return entity instanceof HumanEntity human ? human.isBlocking() : of(entity).blocking();
    }

    /**
     * The state of a non-human from its active item.
     *
     * @param raised the active item carries {@code BLOCKS_ATTACKS}
     * @param blockDelaySeconds {@code blockDelaySeconds()} of the component, in seconds
     * @param usedTicks {@code getActiveItemUsedTime()}
     * @return raised as given; blocking once raised for {@link #delayTicks} ticks
     */
    public static BlockState state(boolean raised, float blockDelaySeconds, int usedTicks) {
        return new BlockState(raised, raised && usedTicks >= delayTicks(blockDelaySeconds));
    }

    /**
     * The block delay of a component in ticks.
     *
     * @param blockDelaySeconds {@code blockDelaySeconds()}; a negative or undefined value counts as {@code 0}
     * @return {@code Math.round(blockDelaySeconds × 20f)}, as vanilla {@code BlocksAttacks.blockDelayTicks}
     */
    public static int delayTicks(float blockDelaySeconds) {
        return blockDelaySeconds > 0.0f ? Math.round(blockDelaySeconds * TICKS_PER_SECOND) : 0;
    }
}
