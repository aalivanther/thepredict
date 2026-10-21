package akyrion.thepredict.domain;

/**
 * Bytes of one record and how many profiles were dropped to stay inside the cap (M27.1).
 *
 * @param bytes the record, at most {@link PlayerMemoryCodec#MAX_BYTES} when profiles could be dropped
 * @param profilesEvicted profiles removed, never a style field
 */
public record EncodeResult(byte[] bytes, int profilesEvicted) {
    /**
     * Copies the bytes.
     */
    public EncodeResult {
        bytes = bytes.clone();
    }
}
