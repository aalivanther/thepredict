package akyrion.thepredict.engine;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/**
 * The outcome windows of one session (M16.8): the open window, if any, and a ring of the last {@value #RECENT} closed
 * summaries for {@code inspect} (rule A5). Package-private; owned by its {@link MobSession}; server thread only.
 */
final class SessionOutcomes {
    /** Closed windows kept per session. */
    static final int RECENT = 8;

    private Optional<OutcomeWindow> open = Optional.empty();
    private final ArrayDeque<OutcomeWindow.Summary> recent = new ArrayDeque<>(RECENT);

    Optional<OutcomeWindow> open() {
        return open;
    }

    void open(OutcomeWindow window) {
        open = Optional.of(window);
    }

    /** Closes the open window, keeps its summary in the ring and returns it. */
    Optional<OutcomeWindow.Summary> close(long tick) {
        var summary = open.map(window -> window.close(tick));
        open = Optional.empty();
        summary.ifPresent(closed -> {
            if (recent.size() == RECENT) {
                recent.removeFirst();
            }
            recent.addLast(closed);
        });
        return summary;
    }

    /** Drops the open window of a previous incarnation without recording it. */
    void discardOpen() {
        open = Optional.empty();
    }

    /** The closed summaries, oldest first. */
    List<OutcomeWindow.Summary> recent() {
        return List.copyOf(recent);
    }
}
