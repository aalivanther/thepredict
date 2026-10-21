package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.engine.ServerClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimingLogTest {
    @Test
    void linesAreRateLimitedByTheMonotonicClock() {
        var nanos = new AtomicLong(1_000);
        var lines = new ArrayList<String>();
        var log = new TimingLog(ServerClock.of(() -> 0, nanos::get), lines::add);
        log.record(50_000, 900);
        log.record(50_000, 900);
        nanos.addAndGet(TimingLog.INTERVAL_NANOS - 1);
        log.record(1_000, 10);
        nanos.addAndGet(1);
        log.record(4_000, 20);
        assertEquals(List.of("SystemOne timings: parse 50 us, request 900 chars",
                "SystemOne timings: parse 4 us, request 20 chars"), lines);
    }
}
