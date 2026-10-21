package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryPolicyFingerprintTest {
    @Test
    void cacheSizeIsExcludedAndADecisionKeyIsIncluded() {
        var left = MemoryPolicy.fingerprint(MemorySettings.DEFAULT);
        var cache = new MemorySettings(MemorySettings.DEFAULT.mode(), 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60,
                1024, 16, 10, 60, 8, 1024, 64, 7, 1000, false, "sqlite");
        assertEquals(left, MemoryPolicy.fingerprint(cache));
        var decay = new MemorySettings(MemorySettings.DEFAULT.mode(), 0.5, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60,
                512, 16, 10, 60, 8, 1024, 64, 180, 1_000_000, false, "sqlite");
        assertNotEquals(left, MemoryPolicy.fingerprint(decay));
        assertEquals(64, left.length());
        var facts = new MemorySettings(MemorySettings.DEFAULT.mode(), 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 1, 60,
                512, 16, 10, 60, 8, 1024, 64, 180, 1_000_000, LearningSettings.DEFAULT.allowUnverified(), "sqlite");
        assertNotEquals(left, MemoryPolicy.fingerprint(facts));
        var canonical = MemoryPolicy.canonical(MemorySettings.DEFAULT);
        assertTrue(canonical.contains("\"maxFactsPerRequest\":2"));
        assertTrue(canonical.contains("\"p1\":" + PlayerMemoryMath.p1()));
        assertTrue(!canonical.contains("cacheSubjects"));
        assertTrue(!canonical.contains("retentionDays"));
        assertEquals(PlayerMemoryMath.HABIT_THETA - 0.1, PlayerMemoryMath.p1(), 0.0);
    }
}
