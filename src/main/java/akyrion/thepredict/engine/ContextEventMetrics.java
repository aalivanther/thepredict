package akyrion.thepredict.engine;

/**
 * Fixed counters of {@link ContextEvents} for {@code /thepredict status} and the server script (M07.5). Server
 * thread only; only {@link ContextEvents} changes them.
 */
public final class ContextEventMetrics {
    long recorded;
    long repeats;
    long marks;
    long pathfinds;

    ContextEventMetrics() {
    }

    /**
     * Counts the events recorded in some history.
     *
     * @return recorded events
     */
    public long recordedEvents() {
        return recorded;
    }

    /**
     * Counts the events dropped as repeats within {@link ContextEvents#REPEAT_WINDOW_TICKS} ticks.
     *
     * @return repeated events
     */
    public long repeatedEvents() {
        return repeats;
    }

    /**
     * Counts the pending marks handed to the agenda, before it condenses them.
     *
     * @return pending marks
     */
    public long pendingMarks() {
        return marks;
    }

    /**
     * Counts the path computations of member mobs, a diagnostic metric.
     *
     * @return member path computations
     */
    public long memberPathfinds() {
        return pathfinds;
    }
}
