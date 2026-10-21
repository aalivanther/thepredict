package akyrion.thepredict.engine;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Rings of enemy events keyed by participant (M26.1). A lookup is a bounded probe, so a stranger is rejected in
 * constant time. Each ring holds 64 events and drops the oldest. At most {@value #SLOTS} participants have a ring
 * (A5): a ring is forgotten when its participant leaves every open encounter, and the pattern step sweeps the rings of
 * actors that never became a participant before the table fills. Each ring also keeps the movement sample and the
 * shield state of its participant, so tokens offered from several encounters are offered once.
 */
public final class PatternInbox {
    /** Most participants with a ring. */
    static final int SLOTS = 128;

    private final UUID[] ids = new UUID[SLOTS];
    private final EnemyEvents[] rings = new EnemyEvents[SLOTS];
    private final boolean[] used = new boolean[SLOTS];
    private final int[] sampledTick = new int[SLOTS];
    private final int[] shield = new int[SLOTS];
    private final UUID[] doomed = new UUID[SLOTS];
    private int live;

    /**
     * An empty inbox.
     */
    public PatternInbox() {
    }

    /**
     * Whether this participant has a ring.
     *
     * @param participant the actor
     * @return {@code true} when the encounter has admitted them
     */
    public boolean participates(UUID participant) {
        return slot(participant) >= 0;
    }

    /**
     * Opens a ring. A second call keeps the ring that is already there. A full table admits nothing.
     *
     * @param participant the enemy
     */
    public void admit(UUID participant) {
        if (participates(participant)) {
            return;
        }
        var start = home(participant);
        for (var step = 0; step < SLOTS; step++) {
            var at = (start + step) % SLOTS;
            if (!used[at]) {
                used[at] = true;
                ids[at] = participant;
                rings[at] = new EnemyEvents();
                sampledTick[at] = -1;
                shield[at] = -1;
                live++;
                return;
            }
        }
    }

    /**
     * Forgets the ring of a participant and its events. The probe chain stays whole: the entries after it move back
     * to the slots their own probe reaches first.
     *
     * @param participant the enemy
     */
    public void forget(UUID participant) {
        var hole = slot(participant);
        if (hole < 0) {
            return;
        }
        clear(hole);
        live--;
        var next = (hole + 1) % SLOTS;
        for (var step = 1; step < SLOTS && used[next]; step++) {
            if (!reachable(hole, home(ids[next]), next)) {
                move(next, hole);
                hole = next;
            }
            next = (next + 1) % SLOTS;
        }
    }

    /**
     * Forgets every ring whose participant {@code keep} rejects.
     *
     * @param keep whether a participant keeps its ring
     * @return rings forgotten
     */
    int forgetUnless(Predicate<UUID> keep) {
        var count = 0;
        for (var at = 0; at < SLOTS; at++) {
            if (used[at] && !keep.test(ids[at])) {
                doomed[count++] = ids[at];
            }
        }
        for (var index = 0; index < count; index++) {
            forget(doomed[index]);
            doomed[index] = null;
        }
        return count;
    }

    /**
     * Participants with a ring.
     *
     * @return the count
     */
    int live() {
        return live;
    }

    /**
     * Appends one token when the participant has a ring.
     *
     * @param participant the enemy
     * @param action token ordinal
     * @param tick the server tick
     */
    public void offer(UUID participant, int action, int tick) {
        offer(participant, action, tick, false);
    }

    /**
     * Appends one token and whether the weapon was a spear.
     *
     * @param participant the enemy
     * @param action token ordinal
     * @param tick the server tick
     * @param spear whether the weapon was a spear
     */
    public void offer(UUID participant, int action, int tick, boolean spear) {
        var slot = slot(participant);
        if (slot >= 0) {
            rings[slot].offer(participant, action, tick, spear);
        }
    }

    /**
     * Events still stored for one participant.
     *
     * @param participant the enemy
     * @return the count, or zero when they have no ring
     */
    public int size(UUID participant) {
        var slot = slot(participant);
        return slot < 0 ? 0 : rings[slot].size();
    }

    /**
     * Events dropped because a ring was full.
     *
     * @param participant the enemy
     * @return the count
     */
    public int dropped(UUID participant) {
        var slot = slot(participant);
        return slot < 0 ? 0 : rings[slot].dropped();
    }

    /**
     * Removes the oldest token of one participant.
     *
     * @param participant the enemy
     * @return the token, or {@code -1} when the ring is empty or absent
     */
    public int poll(UUID participant) {
        var slot = slot(participant);
        return slot < 0 ? -1 : rings[slot].poll();
    }

    /**
     * Whether the last token polled for this participant was a spear.
     *
     * @param participant the enemy
     * @return {@code false} when they have no ring
     */
    public boolean lastSpear(UUID participant) {
        var slot = slot(participant);
        return slot >= 0 && rings[slot].lastSpear();
    }

    /**
     * Book tick of the last movement sample offered for this participant.
     *
     * @param participant the enemy
     * @return the tick, or {@code -1} when none was offered or there is no ring
     */
    int sampledTick(UUID participant) {
        var slot = slot(participant);
        return slot < 0 ? -1 : sampledTick[slot];
    }

    /**
     * Remembers the book tick of a movement sample just offered.
     *
     * @param participant the enemy
     * @param tick the book tick
     */
    void sampledTick(UUID participant, int tick) {
        var slot = slot(participant);
        if (slot >= 0) {
            sampledTick[slot] = tick;
        }
    }

    /**
     * Shield state last seen for this participant.
     *
     * @param participant the enemy
     * @return {@code 1} up, {@code 0} down, {@code -1} unknown or no ring
     */
    int shield(UUID participant) {
        var slot = slot(participant);
        return slot < 0 ? -1 : shield[slot];
    }

    /**
     * Remembers the shield state of this participant.
     *
     * @param participant the enemy
     * @param state {@code 1} up, {@code 0} down
     */
    void shield(UUID participant, int state) {
        var slot = slot(participant);
        if (slot >= 0) {
            shield[slot] = state;
        }
    }

    private int slot(UUID participant) {
        var start = home(participant);
        for (var step = 0; step < SLOTS; step++) {
            var at = (start + step) % SLOTS;
            if (!used[at]) {
                return -1;
            }
            if (participant.equals(ids[at])) {
                return at;
            }
        }
        return -1;
    }

    private static int home(UUID participant) {
        return Math.floorMod(participant.hashCode(), SLOTS);
    }

    /** Whether a probe from {@code home} reaches {@code at} without passing the hole, so the entry must stay. */
    private static boolean reachable(int hole, int home, int at) {
        return hole <= at ? hole < home && home <= at : hole < home || home <= at;
    }

    private void move(int from, int to) {
        used[to] = true;
        ids[to] = ids[from];
        rings[to] = rings[from];
        sampledTick[to] = sampledTick[from];
        shield[to] = shield[from];
        clear(from);
    }

    private void clear(int at) {
        used[at] = false;
        ids[at] = null;
        rings[at] = null;
        sampledTick[at] = -1;
        shield[at] = -1;
    }
}
