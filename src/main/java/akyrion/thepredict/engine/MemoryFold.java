package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.StyleMemory;

import java.util.List;
import java.util.Optional;

/**
 * Digests the writer applies inside the batch transaction (M27.3). The blob on the delta is ignored while this is
 * present: the stored row is decoded, the digests are folded, and the result is encoded again.
 *
 * @param fights digests of one subject, applied in {@code endEpochMillis} order
 * @param style style of the encounter, empty when patterns are off
 * @param epochDay civil day of the fold
 * @param settings limits used by the math
 */
public record MemoryFold(List<FightDigest> fights, Optional<StyleMemory> style, long epochDay,
                         MemorySettings settings) {
    /**
     * Copies the digests.
     */
    public MemoryFold {
        fights = List.copyOf(fights);
        if (epochDay < 0) {
            throw new IllegalArgumentException("epochDay");
        }
    }
}
