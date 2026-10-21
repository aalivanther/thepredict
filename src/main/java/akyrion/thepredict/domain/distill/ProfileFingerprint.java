package akyrion.thepredict.domain.distill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Fingerprint of the tactics, choices and purposes a distilled artifact was built for (M29.2). A mismatch is
 * {@link OodCause#OOD_PROFILE_CHANGED}.
 */
public final class ProfileFingerprint {
    private ProfileFingerprint() {
    }

    /**
     * SHA-256 of the canonical lines. Lines are sorted. Blank lines are dropped.
     *
     * @param lines tactic, choice and purpose lines
     * @return the hex fingerprint
     */
    public static String of(List<String> lines) {
        var ordered = new ArrayList<String>();
        for (var line : lines) {
            if (line != null && !line.isBlank()) {
                ordered.add(line);
            }
        }
        ordered.sort(String::compareTo);
        var text = String.join("\n", ordered);
        return HexFormat.of().formatHex(digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256", exception);
        }
    }
}
