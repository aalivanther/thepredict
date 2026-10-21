package akyrion.thepredict.domain;

import java.nio.charset.StandardCharsets;

/**
 * FNV-1a 32-bit of a profile name (M27.1). The same name is the one stored in outcome statistics. A collision at
 * load disables per-profile memory for both names.
 */
public final class ProfileHash {
    private static final int OFFSET = 0x811C9DC5;
    private static final int PRIME = 16777619;

    private ProfileHash() {
    }

    /**
     * Hashes the UTF-8 bytes of the name.
     *
     * @param name profile name
     * @return the 32-bit hash
     */
    public static int of(String name) {
        var hash = OFFSET;
        for (var bite : name.getBytes(StandardCharsets.UTF_8)) {
            hash ^= bite & 0xFF;
            hash *= PRIME;
        }
        return hash;
    }
}
