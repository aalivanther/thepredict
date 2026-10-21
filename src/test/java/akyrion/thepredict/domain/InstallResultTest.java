package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstallResultTest {
    @Test
    void installedCountMustNotBeNegative() {
        assertEquals(3, new InstallResult.Installed(3).goalCount());
        assertThrows(IllegalArgumentException.class, () -> new InstallResult.Installed(-1));
    }

    @Test
    void deferredAndUnsupportedNeedAReason() {
        assertThrows(IllegalArgumentException.class, () -> new InstallResult.Deferred(""));
        assertThrows(NullPointerException.class, () -> new InstallResult.Unsupported(null));
        assertEquals("brain", new InstallResult.Unsupported("brain").reason());
    }
}
