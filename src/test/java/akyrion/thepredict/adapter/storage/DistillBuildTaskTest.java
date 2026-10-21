package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.distill.DistillTrainMain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The storage task and distillTrain refuse the same Jev dagger. */
class DistillBuildTaskTest {
    @Test
    void jevIsRefusedByBothAndOutcomeModesAreAccepted() {
        assertThrows(IllegalArgumentException.class, () -> DistillBuildTask.requireDagger("jev"));
        assertDoesNotThrow(() -> DistillBuildTask.requireDagger("off"));
        assertDoesNotThrow(() -> DistillBuildTask.requireDagger("rollout"));
        assertDoesNotThrow(() -> DistillBuildTask.requireDagger("kev"));
        System.setProperty("thepredict.dagger", "jev");
        try {
            assertThrows(IllegalArgumentException.class, () -> DistillTrainMain.main(new String[0]));
        } finally {
            System.clearProperty("thepredict.dagger");
        }
    }
}
