package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque kinematic words of the enemies an encounter is watching (M26.1, errata E26-8). The engine stores the
 * long and does not decode it. A missing id reads as word {@code 0} and tick {@code -1}. A participant no open
 * encounter keeps, or a player who quit, is forgotten and frees its slot (SP-05), so the 64 slots hold the enemies of
 * the fights in progress and not those of past fights.
 */
public final class KinematicBook {
    /** Slots. A full book replaces the oldest insertion. */
    public static final int CAPACITY = 64;

    private final UUID[] ids = new UUID[CAPACITY];
    private final long[] words = new long[CAPACITY];
    private final int[] ticks = new int[CAPACITY];
    private final int[] free = new int[CAPACITY];
    private int freeCount;
    private int used;
    private int oldest;

    /**
     * An empty book.
     */
    public KinematicBook() {
        for (var index = 0; index < CAPACITY; index++) {
            ticks[index] = -1;
        }
    }

    /**
     * The stored word.
     *
     * @param participant the enemy
     * @return the word, or {@code 0} when this book has no sample
     */
    public long word(UUID participant) {
        var slot = index(participant);
        return slot < 0 ? 0L : words[slot];
    }

    /**
     * The tick of the stored word.
     *
     * @param participant the enemy
     * @return the tick, or {@code -1} when this book has no sample
     */
    public int tick(UUID participant) {
        var slot = index(participant);
        return slot < 0 ? -1 : ticks[slot];
    }

    /**
     * Stores one word.
     *
     * @param participant the enemy
     * @param stored the opaque word
     * @param at the server tick
     */
    public void store(UUID participant, long stored, int at) {
        var slot = index(participant);
        if (slot < 0) {
            slot = freeCount > 0 ? free[--freeCount] : used < CAPACITY ? used++ : replaceOldest();
            ids[slot] = participant;
        }
        words[slot] = stored;
        ticks[slot] = at;
    }

    /**
     * Forgets one participant (SP-05): its slot is free for the next new enemy and its word reads as {@code 0}, its
     * tick as {@code -1}. An id the book does not hold changes nothing. Server thread only, as {@link #store}.
     *
     * @param participant the enemy that left every encounter or quit
     */
    public void forget(UUID participant) {
        var slot = index(Objects.requireNonNull(participant, "participant"));
        if (slot < 0) {
            return;
        }
        ids[slot] = null;
        words[slot] = 0L;
        ticks[slot] = -1;
        free[freeCount++] = slot;
    }

    /**
     * Enemies the book holds now.
     *
     * @return the count, at most {@link #CAPACITY}
     */
    public int size() {
        return used - freeCount;
    }

    /**
     * The slot of the oldest insertion of a full book, as the class promises (EFO-23): the slots are replaced in the
     * order they were filled, so a new enemy never keeps evicting the same one while the first 63 stay forever.
     */
    private int replaceOldest() {
        var slot = oldest;
        oldest = (oldest + 1) % CAPACITY;
        return slot;
    }

    private int index(UUID participant) {
        for (var slot = 0; slot < used; slot++) {
            if (participant.equals(ids[slot])) {
                return slot;
            }
        }
        return -1;
    }
}
