package akyrion.thepredict.engine;

import java.util.UUID;

/**
 * Drains {@link EnemyEvents} while the tick budget lasts (M26.3). A zero budget leaves the ring for the next tick
 * and counts a deferral. {@code off} is {@link PatternControl#NONE} and never reaches this class.
 */
public final class PatternStep implements PatternControl {
    private final EnemyEvents events = new EnemyEvents();
    private int deferred;
    private int drained;

    /**
     * A step with an empty ring.
     */
    public PatternStep() {
    }

    /**
     * Queues one event.
     *
     * @param participant the enemy
     * @param action token ordinal
     * @param tick the server tick
     */
    public void offer(UUID participant, int action, int tick) {
        events.offer(participant, action, tick);
    }

    @Override
    public void step(long tick, long budgetNanos) {
        if (budgetNanos <= 0L) {
            deferred++;
            return;
        }
        while (events.size() > 0) {
            events.poll();
            drained++;
        }
    }

    /**
     * Events still waiting.
     *
     * @return the count
     */
    public int pending() {
        return events.size();
    }

    /**
     * Ticks that deferred the remainder.
     *
     * @return the count
     */
    public int deferred() {
        return deferred;
    }

    /**
     * Events drained.
     *
     * @return the count
     */
    public int drained() {
        return drained;
    }
}
