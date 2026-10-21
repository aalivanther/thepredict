package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Outcome windows for the listener references of M17.0: opens one on a member as an adopted intent does, and writes
 * what the open window measured so far, so a reference shows every {@code dealt}, {@code taken} and death that reached
 * it through {@link OutcomeEvents}.
 */
public final class GoldenWindows {
    private GoldenWindows() {
    }

    /**
     * Opens a window of level {@code JEV} on a member whose last target is {@code targetId}.
     *
     * @param outcomes entry point of the windows
     * @param sessions sessions
     * @param mobId entity of the member
     * @param targetId entity the member targets
     * @param tick adoption tick
     */
    public static void open(OutcomeEvents outcomes, SessionRegistry sessions, UUID mobId, UUID targetId, long tick) {
        var session = sessions.find(mobId).orElseThrow();
        session.recordTarget(targetId, 2.0);
        outcomes.adopted(session, new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD,
                Optional.empty(), 0.8, List.of(), Map.of(), ControlLevel.JEV, tick, Long.MAX_VALUE, false), tick,
                false);
    }

    /**
     * What the open window of a member measured, or {@code closed}.
     *
     * @param sessions sessions
     * @param mobId entity of the member
     * @param tick tick to measure at
     * @return one line
     */
    public static String state(SessionRegistry sessions, UUID mobId, long tick) {
        return sessions.find(mobId).flatMap(session -> session.outcomes().open()).map(window -> window.close(tick))
                .map(summary -> String.format(Locale.ROOT, "window dealt=%.6f taken=%.6f hits=%d mobDied=%s "
                        + "targetDied=%s", summary.dealt(), summary.taken(), summary.hits(), summary.mobDied(),
                        summary.targetDied())).orElse("window closed");
    }
}
