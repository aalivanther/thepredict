package akyrion.thepredict.engine;

import akyrion.thepredict.domain.PressureLadder;

import java.nio.charset.StandardCharsets;

/**
 * SHA-256 of a candidate body (M22.5). The gate keys verdicts by this fingerprint.
 */
public final class CandidateFingerprint {
    private CandidateFingerprint() {
    }

    /**
     * Fingerprint of a body.
     *
     * @param body the candidate text
     * @return lower-case hex
     */
    public static String of(String body) {
        return PressureLadder.sha256(body.getBytes(StandardCharsets.UTF_8));
    }
}
