package akyrion.thepredict.engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters of player memory (M27.12). Each one is monotonic.
 */
public final class MemoryCounters {
    /** One named counter. */
    public enum Kind {
        /** A prefetch hint was dropped. */
        HINT_DROPPED,
        /** A digest was dropped. */
        DIGEST_DROPPED,
        /** A memory batch failed. */
        BATCH_FAILED,
        /** An active or online entry was evicted. */
        CACHE_EVICTED_ACTIVE,
        /** A record newer than this build was left unread. */
        RECORD_NEWER,
        /** A record could not be decoded. */
        RECORD_CORRUPT,
        /** A profile was evicted to stay within 1024 bytes. */
        PROFILE_EVICTED,
        /** Two profile names share a hash. */
        PROFILE_HASH_COLLISION,
        /** A style break fired. */
        STYLE_BREAK,
        /** A field saturated at the float16 cap. */
        FIELD_SATURATED,
        /** A request named more than one profile, so no fact was sent. */
        FACT_AMBIGUOUS,
        /** The fight started before the record was loaded. */
        NOT_LOADED_AT_ADMISSION
    }

    private final AtomicLong[] counts = new AtomicLong[Kind.values().length];

    /** Starts every counter at zero. */
    public MemoryCounters() {
        for (var index = 0; index < counts.length; index++) {
            counts[index] = new AtomicLong();
        }
    }

    /**
     * Adds to one counter.
     *
     * @param kind the counter
     * @param amount the increment, zero or more
     */
    public void add(Kind kind, long amount) {
        counts[kind.ordinal()].addAndGet(amount);
    }

    /**
     * The current value.
     *
     * @param kind the counter
     * @return the total
     */
    public long get(Kind kind) {
        return counts[kind.ordinal()].get();
    }
}
