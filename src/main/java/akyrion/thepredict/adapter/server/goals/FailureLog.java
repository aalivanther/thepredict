package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.engine.ServerClock;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Rate-limited log of goal installation failures (rule C8): at most one line every {@link #INTERVAL_TICKS}
 * server ticks, with the count of the failures it skipped.
 * The line names the entity and a fixed cause (an exception type or a missing goal count), never an exception
 * message. It is mutable state outside {@code engine} (rule A3), limited to this log limit and justified by C8.
 * Package-private; server thread only.
 */
final class FailureLog {
    /** Minimum server ticks between two logged failures. */
    static final long INTERVAL_TICKS = 200L;

    private final ServerClock clock;
    private final Consumer<String> warnings;
    private boolean logged;
    private long lastLogTick;
    private int suppressed;

    FailureLog(ServerClock clock, Consumer<String> warnings) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * Records one failure.
     *
     * @param entityId mob whose goals could not change
     * @param cause fixed text: an exception type or {@code N MythicMobs goals missing}
     */
    void record(UUID entityId, String cause) {
        var now = clock.currentTick();
        if (logged && now - lastLogTick < INTERVAL_TICKS) {
            suppressed++;
            return;
        }
        warnings.accept("Goal change failed on mob " + entityId + " (" + cause
                + "); it keeps the MythicMobs AI" + (suppressed == 0 ? ""
                : "; " + suppressed + " similar failures were not logged"));
        logged = true;
        lastLogTick = now;
        suppressed = 0;
    }
}
