package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionPolicyTest {
    @Test
    void oldDaysGoFirstThenTheOldestKeptDayButNeverToday() {
        var days = List.of(new RetentionPolicy.DaySize("2026-09-01", 10),
                new RetentionPolicy.DaySize("2026-09-25", 50),
                new RetentionPolicy.DaySize("2026-10-06", 80));
        var aged = RetentionPolicy.plan(days, "2026-10-06", 14, 1_000);
        assertEquals(List.of("2026-09-01"), aged.delete());
        assertFalse(aged.pausedSize());
        var sized = RetentionPolicy.plan(days, "2026-10-06", 400, 100);
        assertTrue(sized.delete().contains("2026-09-25"));
        assertTrue(!sized.delete().contains("2026-10-06"));
        var paused = RetentionPolicy.plan(List.of(new RetentionPolicy.DaySize("2026-10-06", 500)), "2026-10-06", 14, 100);
        assertTrue(paused.delete().isEmpty());
        assertTrue(paused.pausedSize());
    }
}
