package akyrion.thepredict.domain.distill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Unpinned profile keys proposed by autoconfiguration (M29.8). An author-pinned key is never overwritten.
 * The fingerprint is the SHA-256 of {@link #canonical()}: the fingerprint of the base profile and the keys sorted by
 * name, each text prefixed with its length (rule I22). The overlay id is a file name and stays out of it.
 *
 * @param id overlay id
 * @param keys key to value, unpinned only
 * @param baseFingerprint fingerprint of the author profile the overlay was built on
 */
public record ProfileOverlay(String id, Map<String, String> keys, String baseFingerprint) {
    /**
     * Copies the keys.
     */
    public ProfileOverlay {
        Objects.requireNonNull(baseFingerprint, "baseFingerprint");
        keys = Map.copyOf(keys);
    }

    /**
     * SHA-256 of the canonical form.
     *
     * @return the hex fingerprint
     */
    public String sha256() {
        return digest(canonical());
    }

    /**
     * Canonical form: a version line, the base fingerprint, then one line per key in name order. Every text carries
     * its length, so no key, value or fingerprint can be read as part of another, and the same overlay built for two
     * profiles never shares a fingerprint.
     *
     * @return the canonical text
     */
    public String canonical() {
        var text = new StringBuilder("overlay v1\nbase ").append(field(baseFingerprint)).append('\n');
        for (var entry : new TreeMap<>(keys).entrySet()) {
            text.append(field(entry.getKey())).append(' ').append(field(entry.getValue())).append('\n');
        }
        return text.toString();
    }

    private static String field(String text) {
        return text.length() + ":" + text;
    }

    /**
     * Applies the overlay onto unpinned keys. A pinned key keeps the author value. A stale base is refused.
     *
     * @param author author values
     * @param pinned keys the author pinned
     * @param liveBase fingerprint of the profile now on disk
     * @return the merged values
     */
    public Map<String, String> apply(Map<String, String> author, List<String> pinned, String liveBase) {
        if (!baseFingerprint.equals(liveBase)) {
            throw new IllegalArgumentException("OVERLAY_STALE");
        }
        var merged = new LinkedHashMap<>(author);
        for (var entry : keys.entrySet()) {
            if (!pinned.contains(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(merged);
    }

    private static String digest(String text) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256", exception);
        }
    }
}
