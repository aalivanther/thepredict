package akyrion.thepredict;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuelWiringTest {
    @Test
    void theSamplesOfARecordingRunEveryFiveTicksOfTheEngineTaskUntilClosed() throws Exception {
        var hook = new DuelWiring.SampleHook();
        var samples = new AtomicInteger();
        for (var tick = 0; tick < 20; tick++) {
            hook.run();
        }
        assertEquals(0, samples.get(), "nothing runs without a recording");
        var handle = hook.every(5, samples::incrementAndGet);
        for (var tick = 0; tick < 20; tick++) {
            hook.run();
        }
        assertEquals(4, samples.get());
        handle.close();
        for (var tick = 0; tick < 20; tick++) {
            hook.run();
        }
        assertEquals(4, samples.get());
    }

    @Test
    void closingAnOldRecordingNeverStopsTheNewOne() throws Exception {
        var hook = new DuelWiring.SampleHook();
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var old = hook.every(1, first::incrementAndGet);
        old.close();
        hook.every(1, second::incrementAndGet);
        old.close();
        hook.run();
        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }
}
