package akyrion.thepredict.domain;

/**
 * The only reader and writer of a player-memory record (C22, M27.1).
 */
public final class PlayerMemoryCodec {
    /** Largest record, in bytes. */
    public static final int MAX_BYTES = 1024;
    /** Action vocabulary written by this code. */
    public static final int ACTION_VOCAB = StyleMemory.ACTIONS;
    /** Pattern vocabulary written by this code. */
    public static final int PATTERN_VOCAB = StyleMemory.PATTERNS;

    private PlayerMemoryCodec() {
    }

    /**
     * Encodes a record, keeping at most {@code maxProfiles} of the most recent profiles. A tie keeps the smaller hash.
     * Bytes past {@link #MAX_BYTES} drop further profiles and never the style.
     *
     * @param memory the memory
     * @param maxProfiles cap, at least 1
     * @return the bytes and how many profiles were dropped
     */
    public static EncodeResult encode(PlayerMemory memory, int maxProfiles) {
        if (maxProfiles < 1) {
            throw new IllegalArgumentException("maxProfiles " + maxProfiles);
        }
        return MemoryWriter.write(memory, maxProfiles);
    }

    /**
     * Decodes a record. Never throws. A record longer than {@link #MAX_BYTES} or holding a float16 that is not finite
     * is corrupt (C22).
     *
     * @param bytes the column
     * @return ready, newer or corrupt
     */
    public static MemoryDecoded decode(byte[] bytes) {
        if (bytes.length == 0) {
            return new MemoryDecoded.Corrupt("empty");
        }
        if (bytes.length > MAX_BYTES) {
            return new MemoryDecoded.Corrupt("size");
        }
        return MemoryReader.read(bytes);
    }

    /**
     * Whether the bytes are a newer codec. Corrupt bytes are not newer.
     *
     * @param bytes the column
     * @return {@code true} when the version or a vocabulary is newer
     */
    public static boolean newer(byte[] bytes) {
        return decode(bytes) instanceof MemoryDecoded.Newer;
    }
}
