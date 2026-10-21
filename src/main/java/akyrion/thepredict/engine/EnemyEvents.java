package akyrion.thepredict.engine;

import java.util.UUID;

/**
 * Ring of 64 enemy events for one encounter (M26.1). The oldest event is dropped and counted when the ring is full.
 */
public final class EnemyEvents {
    /** Capacity of the ring. */
    public static final int CAPACITY = 64;

    private final UUID[] who = new UUID[CAPACITY];
    private final int[] token = new int[CAPACITY];
    private final int[] at = new int[CAPACITY];
    private final boolean[] spear = new boolean[CAPACITY];
    private int head;
    private int size;
    private int dropped;
    private boolean lastSpear;

    /**
     * An empty ring.
     */
    public EnemyEvents() {
    }

    /**
     * Stores one event. A full ring drops the oldest.
     *
     * @param participant the enemy
     * @param action token ordinal
     * @param tick the server tick
     */
    public void offer(UUID participant, int action, int tick) {
        offer(participant, action, tick, false);
    }

    /**
     * Stores one event and whether the weapon was a spear. A full ring drops the oldest.
     *
     * @param participant the enemy
     * @param action token ordinal
     * @param tick the server tick
     * @param spearWeapon whether the weapon was a spear
     */
    public void offer(UUID participant, int action, int tick, boolean spearWeapon) {
        if (size == CAPACITY) {
            dropped++;
            head = (head + 1) % CAPACITY;
            size--;
        }
        var slot = (head + size) % CAPACITY;
        who[slot] = participant;
        token[slot] = action;
        at[slot] = tick;
        spear[slot] = spearWeapon;
        size++;
    }

    /**
     * Events still stored.
     *
     * @return the count
     */
    public int size() {
        return size;
    }

    /**
     * Events dropped because the ring was full.
     *
     * @return the count
     */
    public int dropped() {
        return dropped;
    }

    /**
     * Removes the oldest event.
     *
     * @return its token, or {@code -1} when empty
     */
    public int poll() {
        if (size == 0) {
            lastSpear = false;
            return -1;
        }
        var value = token[head];
        lastSpear = spear[head];
        who[head] = null;
        spear[head] = false;
        head = (head + 1) % CAPACITY;
        size--;
        return value;
    }

    /**
     * Whether the last polled token was a spear.
     *
     * @return {@code false} after an empty poll
     */
    public boolean lastSpear() {
        return lastSpear;
    }
}
