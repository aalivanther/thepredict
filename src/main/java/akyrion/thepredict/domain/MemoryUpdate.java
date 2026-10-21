package akyrion.thepredict.domain;

/**
 * What one close wrote into a player's memory (M27.2).
 *
 * @param styleBreaks breaks applied this encounter
 * @param anchorChanged whether the group anchor moved between open and close
 * @param bytesBefore encoded size before the update
 * @param bytesAfter encoded size after the update
 * @param profilesEvicted profiles dropped to stay within the cap
 * @param saturatedFields fields clipped to the float16 range
 * @param digests fight digests applied
 */
public record MemoryUpdate(int styleBreaks, boolean anchorChanged, int bytesBefore, int bytesAfter,
                           int profilesEvicted, int saturatedFields, int digests) {
}
