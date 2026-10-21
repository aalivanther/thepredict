package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** A packaged source cannot name Jev. The student arm is not a Jev call. */
class TapeIsolationTest {
    @Test
    void aJevSourceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PackagedSource.refuseJev("jev-1.13.0"));
        assertThrows(IllegalArgumentException.class, () -> PackagedSource.refuseJev("JEV_ARGMAX"));
        PackagedSource.refuseJev("outcome");
        PackagedSource.refuseJev("kev-0.8b");
    }

    @Test
    void anyCaseOfJevAndAnyNonTeacherIsRefused() {
        for (var source : new String[] {"Jev-1.13.0", "kev-from-jev", "KEV-Jev", "", " ", "random", "jevish"}) {
            assertThrows(IllegalArgumentException.class, () -> PackagedSource.refuseJev(source), source);
        }
        PackagedSource.refuseJev("OUTCOME");
        PackagedSource.refuseJev("rollout");
        PackagedSource.refuseJev("Kev-0.8b");
    }

    @Test
    void aJevSourceHiddenBehindANestedFieldIsStillRefused() throws Exception {
        var root = Files.createTempDirectory("packaged");
        var sample = root.resolve("learn").resolve("packaged").resolve("sample");
        Files.createDirectories(sample);
        Files.writeString(sample.resolve("verdict.json"), "{\"notes\":{\"source\":\"outcome\"},\"source\":\"Jev-1.13.0\"}");
        assertThrows(IllegalArgumentException.class, () -> PackagedSource.load(root));
        Files.writeString(sample.resolve("verdict.json"), "{\"source\":\"outcome\"");
        assertThrows(java.io.IOException.class, () -> PackagedSource.load(root));
    }
}
