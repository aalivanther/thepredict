package akyrion.thepredict.eval;

import akyrion.thepredict.adapter.jev.LoopbackProbe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Latency steps and a loopback post at 0, 200 and 500 ms (M22.4). LIVE is refused. */
class EvalHarnessLoopbackTest {
    @Test
    void latencyStepsAndLiveIsARefusedKind() throws Exception {
        assertEquals(0, LoopbackProbe.ticks(0));
        assertEquals(4, LoopbackProbe.ticks(200));
        assertEquals(10, LoopbackProbe.ticks(500));
        assertEquals(1, LoopbackProbe.posts(0));
        assertEquals(1, LoopbackProbe.posts(200));
        assertEquals(1, LoopbackProbe.posts(500));
        assertEquals(2, EvalTools.dispatch(new String[] {"live"}));
    }
}
