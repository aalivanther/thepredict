package akyrion.thepredict.distill;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tape task and its main refuse a paid run. Neither opens a connection. */
class JevChampionTapeTest {
    @Test
    void liveJevIsRefusedByTheTaskAndTheMain() throws Exception {
        var script = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(script.contains("evalTape refuses -PliveJev=true"));
        assertTrue(script.contains("distillTrain dagger is off, kev or rollout"));
        assertTrue(script.contains("Plugin JAR must not package a Jev source"));
        System.setProperty("thepredict.liveJev", "true");
        try {
            assertThrows(IllegalStateException.class, () -> EvalTapeMain.main(new String[0]));
        } finally {
            System.clearProperty("thepredict.liveJev");
        }
        System.setProperty("thepredict.dagger", "jev");
        try {
            assertThrows(IllegalArgumentException.class, () -> DistillTrainMain.main(new String[0]));
        } finally {
            System.clearProperty("thepredict.dagger");
        }
        assertEquals("FRESH", TapeFreshness.status("finger", "finger", "jev-1.13.0", "jev-1.13.0"));
        assertEquals("TAPE_STALE", TapeFreshness.status("finger", "other", "jev-1.13.0", "jev-1.13.0"));
        assertEquals("TAPE_STALE", TapeFreshness.status("finger", "finger", "jev-1.13.0", "jev-1.14.0"));
    }
}
