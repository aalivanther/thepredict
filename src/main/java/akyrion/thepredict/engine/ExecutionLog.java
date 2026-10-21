package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Rate-limited log of the tactical execution (rule C8). Package-private; server thread only.
 *
 * <p>At most {@link #LINES_PER_WINDOW} lines every {@link #WINDOW_TICKS} ticks; the first line of the next window
 * reports how many were suppressed. Lines per decision, discard and cast are written only with
 * {@code decision.logDecisions}; they name mobs by entity UUID and tactics by ID, never a player, a key or a body.
 */
final class ExecutionLog {
    static final long WINDOW_TICKS = 200;
    static final int LINES_PER_WINDOW = 20;

    private final Consumer<String> sink;
    private final boolean decisions;
    private long windowStart = Long.MIN_VALUE / 2;
    private int lines;
    private long suppressed;

    ExecutionLog(Consumer<String> sink, boolean decisions) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.decisions = decisions;
    }

    /** Writes a line about one decision when {@code decision.logDecisions} is on; the text is built only then. */
    void decision(long tick, Supplier<String> line) {
        if (decisions) {
            write(tick, line.get());
        }
    }

    /** Writes a line that is always worth an operator's attention, within the same limit. */
    void always(long tick, String line) {
        write(tick, line);
    }

    private void write(long tick, String line) {
        if (tick - windowStart >= WINDOW_TICKS) {
            windowStart = tick;
            lines = 0;
            if (suppressed > 0) {
                sink.accept("Tactical execution: " + suppressed + " log line(s) suppressed");
                suppressed = 0;
            }
        }
        if (lines >= LINES_PER_WINDOW) {
            suppressed++;
            return;
        }
        lines++;
        sink.accept(line);
    }
}
