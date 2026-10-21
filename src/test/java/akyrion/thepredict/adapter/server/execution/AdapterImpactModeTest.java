package akyrion.thepredict.adapter.server.execution;

import akyrion.thepredict.domain.ImpactTiming;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterImpactModeTest {
    @Test
    void everyModeWaitsOnlyWhenHittableExceedsWindUp() throws Exception {
        for (var wind : new int[] {0, 5, 10, 15}) {
            for (var hittable = 0; hittable <= 12; hittable++) {
                assertEquals(hittable > wind, ImpactTiming.mustWait(hittable, wind));
            }
        }
        var checks = Files.readString(Path.of(
                "src/main/java/akyrion/thepredict/adapter/server/execution/HitChecks.java"));
        assertTrue(checks.contains("ImpactTiming.mustWait"));
    }
}
