package akyrion.thepredict.admin;

import akyrion.thepredict.engine.MemoryCounters;

import java.util.List;

/**
 * The three status lines of player memory (M27.12).
 */
public final class MemoryStatus {
    private MemoryStatus() {
    }

    /**
     * Three lines: drops, records, and admission.
     *
     * @param counters the counters
     * @return the lines, with no player name
     */
    public static List<String> lines(MemoryCounters counters) {
        return List.of(
                "hints " + counters.get(MemoryCounters.Kind.HINT_DROPPED) + " digests "
                        + counters.get(MemoryCounters.Kind.DIGEST_DROPPED) + " batches "
                        + counters.get(MemoryCounters.Kind.BATCH_FAILED),
                "newer " + counters.get(MemoryCounters.Kind.RECORD_NEWER) + " corrupt "
                        + counters.get(MemoryCounters.Kind.RECORD_CORRUPT) + " evicted "
                        + counters.get(MemoryCounters.Kind.PROFILE_EVICTED) + " active "
                        + counters.get(MemoryCounters.Kind.CACHE_EVICTED_ACTIVE),
                "collisions " + counters.get(MemoryCounters.Kind.PROFILE_HASH_COLLISION) + " breaks "
                        + counters.get(MemoryCounters.Kind.STYLE_BREAK) + " saturated "
                        + counters.get(MemoryCounters.Kind.FIELD_SATURATED) + " ambiguous "
                        + counters.get(MemoryCounters.Kind.FACT_AMBIGUOUS) + " notLoaded "
                        + counters.get(MemoryCounters.Kind.NOT_LOADED_AT_ADMISSION));
    }
}
