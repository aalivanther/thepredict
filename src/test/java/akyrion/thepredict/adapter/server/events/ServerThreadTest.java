package akyrion.thepredict.adapter.server.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerThreadTest {
    private final List<Runnable> nextTick = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> ran = new ArrayList<>();
    private boolean primary = true;
    private final ServerThread thread = new ServerThread(() -> primary, nextTick::add, warnings::add);

    @Test
    void aSynchronousEventOnTheServerThreadRunsAtOnce() {
        thread.run(false, () -> ran.add("now"));
        assertEquals(List.of("now"), ran);
        assertTrue(nextTick.isEmpty());
    }

    @Test
    void anAsynchronousEventWaitsForTheServerThread() {
        thread.run(true, () -> ran.add("later"));
        assertTrue(ran.isEmpty());
        nextTick.getFirst().run();
        assertEquals(List.of("later"), ran);
    }

    @Test
    void aSynchronousEventSeenOffTheServerThreadAlsoWaits() {
        primary = false;
        thread.run(false, () -> ran.add("later"));
        assertTrue(ran.isEmpty());
        assertEquals(1, nextTick.size());
    }

    @Test
    void aTaskThatCannotBeScheduledIsReportedWithTheScriptWarning() {
        var failing = new ServerThread(() -> true, task -> {
            throw new IllegalStateException("plugin disabled");
        }, warnings::add);
        failing.runNextTick(() -> ran.add("never"));
        assertEquals(List.of(ServerThread.SCHEDULE_FAILED), warnings);
        assertEquals("A MythicMobs lifecycle update could not return to the server thread", ServerThread.SCHEDULE_FAILED);
        assertTrue(ran.isEmpty());
    }
}
