package akyrion.thepredict.domain;

/**
 * Result of reading a record (M27.1). {@link Corrupt} is returned instead of throwing.
 */
public sealed interface MemoryDecoded permits MemoryDecoded.Ready, MemoryDecoded.Newer, MemoryDecoded.Corrupt {
    /**
     * A record this code can use.
     *
     * @param memory the memory
     */
    record Ready(PlayerMemory memory) implements MemoryDecoded {
    }

    /**
     * A version or vocabulary newer than this code. The row stays read-only.
     *
     * @param version the version byte
     */
    record Newer(int version) implements MemoryDecoded {
    }

    /**
     * The bytes are truncated or impossible.
     *
     * @param cause a short cause
     */
    record Corrupt(String cause) implements MemoryDecoded {
    }
}
