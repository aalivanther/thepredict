package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The fingerprint is the SHA-256 of the question body and ignores a key (M22.5). */
class CandidateFingerprintTest {
    @Test
    void theSameBodyHasTheSameFingerprint() {
        var body = "{\"model\":\"jev\",\"options\":[\"a\"]}";
        assertEquals(CandidateFingerprint.of(body), CandidateFingerprint.of(body));
        assertNotEquals(CandidateFingerprint.of(body), CandidateFingerprint.of(body + " "));
        assertEquals(64, CandidateFingerprint.of(body).length());
    }
}
