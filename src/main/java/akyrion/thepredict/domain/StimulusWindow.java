package akyrion.thepredict.domain;

/**
 * Four overlapping occasion windows of one participant (M26.2). Opening a fifth drops the oldest.
 */
public final class StimulusWindow {
    /** Windows kept. */
    public static final int SLOTS = 4;

    private final StimulusKind[] kinds = new StimulusKind[SLOTS];
    private final int[] opened = new int[SLOTS];
    private final int[] closes = new int[SLOTS];
    private int size;

    /**
     * No windows open.
     */
    public StimulusWindow() {
    }

    /**
     * Opens a window and drops the oldest when the ring is full.
     *
     * @param kind the occasion
     * @param tick the open tick
     * @param durationTicks how long it stays open
     */
    public void open(StimulusKind kind, int tick, int durationTicks) {
        if (size == SLOTS) {
            shift();
        }
        kinds[size] = kind;
        opened[size] = tick;
        closes[size] = tick + durationTicks;
        size++;
    }

    /**
     * Whether a kind is open at a tick.
     *
     * @param kind the occasion
     * @param tick the tick
     * @return {@code true} when one window of that kind covers the tick
     */
    public boolean open(StimulusKind kind, int tick) {
        for (var index = 0; index < size; index++) {
            if (kinds[index] == kind && tick >= opened[index] && tick <= closes[index]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops windows whose close tick is already past.
     *
     * @param tick the server tick
     */
    public void closeExpired(int tick) {
        var kept = 0;
        for (var index = 0; index < size; index++) {
            if (tick <= closes[index]) {
                kinds[kept] = kinds[index];
                opened[kept] = opened[index];
                closes[kept] = closes[index];
                kept++;
            }
        }
        for (var index = kept; index < size; index++) {
            kinds[index] = null;
        }
        size = kept;
    }

    /**
     * Ticks from the open of a tell window to the response, or {@code -1} when no tell window covers that tick.
     *
     * @param responseTick the tick of the answer
     * @return the delay, or {@code -1}
     */
    public int reactionTicks(int responseTick) {
        for (var index = 0; index < size; index++) {
            if (kinds[index] == StimulusKind.TELL_OPEN && responseTick >= opened[index]
                    && responseTick <= closes[index]) {
                return responseTick - opened[index];
            }
        }
        return -1;
    }

    private void shift() {
        for (var index = 1; index < SLOTS; index++) {
            kinds[index - 1] = kinds[index];
            opened[index - 1] = opened[index];
            closes[index - 1] = closes[index];
        }
        size = SLOTS - 1;
    }
}
