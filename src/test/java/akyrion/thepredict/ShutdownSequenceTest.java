package akyrion.thepredict;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CAR-18: a failing step of T7 is logged once and never skips the steps after it. */
class ShutdownSequenceTest {
    @Test
    void aStepThatThrowsDoesNotSkipTheNextOnes() {
        var warnings = new ArrayList<String>();
        var ran = new ArrayList<String>();
        var shutdown = new ShutdownSequence(warnings::add);
        shutdown.step("credit", () -> {
            throw new IllegalStateException("odd session");
        });
        shutdown.step("sessions", () -> ran.add("sessions"));
        shutdown.step("duel", () -> {
            throw new NoClassDefFoundError("gone");
        });
        shutdown.step("jev client", () -> ran.add("jev client"));
        assertEquals(List.of("sessions", "jev client"), ran);
        assertEquals(2, shutdown.failed());
        assertEquals(List.of("Shutdown step 'credit' failed (IllegalStateException); the next steps still run",
                "Shutdown step 'duel' failed (NoClassDefFoundError); the next steps still run"), warnings);
    }
}
