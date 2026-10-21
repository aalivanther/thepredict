package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rule C8 for the tactical execution: a bounded number of lines per window and decisions only when asked. */
class ExecutionLogTest {
    private final List<String> lines = new ArrayList<>();

    @Test
    void atMostTwentyLinesAreWrittenPerWindowAndTheNextWindowReportsTheRest() {
        var log = new ExecutionLog(lines::add, true);
        for (var index = 0; index < 25; index++) {
            log.always(0, "line " + index);
        }
        assertEquals(ExecutionLog.LINES_PER_WINDOW, lines.size());
        log.decision(ExecutionLog.WINDOW_TICKS, () -> "next");
        assertEquals(List.of("Tactical execution: 5 log line(s) suppressed", "next"), lines.subList(20, 22));
    }

    @Test
    void decisionLinesAreNeitherBuiltNorWrittenWithoutDecisionLogging() {
        var log = new ExecutionLog(lines::add, false);
        log.decision(0, () -> {
            throw new AssertionError("the line must not be built");
        });
        log.always(0, "model changed");
        assertEquals(List.of("model changed"), lines);
    }
}
