package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract a future M28 loader must keep (M22.5). Without {@code PASS} nothing activates, and each other state stays
 * what it is: {@code PENDING} before the first load, {@code FAIL} for a failed row and {@code NONE} for a missing one,
 * so a caller can tell an unverified artifact from a refused one (EFO-28).
 */
class FakeM28LoaderContractTest {
    @Test
    void onlyAPassActivatesAndEveryOtherStateKeepsItsName() {
        var gates = new GateVerdicts();
        assertEquals(GateVerdicts.Status.PENDING, gates.status("sha"), "nothing was loaded yet");
        gates.armNext("sha");
        assertEquals("", gates.takeArmed());
        gates.load(List.of(new StorageReply.GateVerdictRow("sha", "G1", "FAIL", "run", 1L)));
        assertEquals(GateVerdicts.Status.FAIL, gates.status("sha"));
        assertEquals(GateVerdicts.Status.NONE, gates.status("absent"), "a missing row is unverified, not failed");
        gates.armNext("sha");
        gates.armNext("absent");
        assertEquals("", gates.takeArmed(), "neither a failure nor a missing row activates");
        gates.load(List.of(new StorageReply.GateVerdictRow("sha", "G1", "PASS", "run", 2L)));
        assertEquals(GateVerdicts.Status.PASS, gates.status("sha"));
        gates.armNext("sha");
        assertEquals("sha", gates.takeArmed());
        gates.load(List.of(new StorageReply.GateVerdictRow("sim", "G1", "PASS_SIM_ONLY", "run", 3L)));
        assertEquals(GateVerdicts.Status.PASS_SIM_ONLY, gates.status("sim"));
    }
}
