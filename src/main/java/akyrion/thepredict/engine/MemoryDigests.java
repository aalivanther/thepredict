package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Pending fight digests, capped per subject and across subjects (M27.3). An exempt player is not offered a digest.
 */
public final class MemoryDigests {
    private final MemorySettings settings;
    private final MemoryCounters counters;
    private final LinkedHashMap<UUID, List<FightDigest>> pending;

    /**
     * Creates the buffer.
     *
     * @param settings the caps and the mode
     * @param counters where drops are counted
     */
    public MemoryDigests(MemorySettings settings, MemoryCounters counters) {
        this.settings = settings;
        this.counters = counters;
        this.pending = new LinkedHashMap<>(settings.maxPendingSubjects());
    }

    /**
     * Stores one digest. {@link MemoryMode#OFF} stores nothing.
     *
     * @param digest the digest
     * @return {@code false} when it was dropped
     */
    public boolean add(FightDigest digest) {
        if (settings.mode() == MemoryMode.OFF) {
            return false;
        }
        var list = pending.get(digest.subject());
        if (list == null) {
            if (pending.size() >= settings.maxPendingSubjects()) {
                counters.add(MemoryCounters.Kind.DIGEST_DROPPED, 1);
                return false;
            }
            list = new ArrayList<>();
            pending.put(digest.subject(), list);
        }
        if (list.size() >= settings.maxPendingDigestsPerSubject()) {
            counters.add(MemoryCounters.Kind.DIGEST_DROPPED, 1);
            return false;
        }
        list.add(digest);
        return true;
    }

    /**
     * Drops every pending digest of one player. An in-flight batch is the caller's concern.
     *
     * @param subject the player
     */
    public void clear(UUID subject) {
        pending.remove(subject);
    }

    /**
     * Takes the digests of at most {@code subjects} players, in insertion order.
     *
     * @param subjects the cap
     * @return the digests
     */
    public List<FightDigest> take(int subjects) {
        var taken = new ArrayList<FightDigest>();
        var iterator = pending.entrySet().iterator();
        var count = 0;
        while (iterator.hasNext() && count < subjects) {
            taken.addAll(iterator.next().getValue());
            iterator.remove();
            count++;
        }
        return List.copyOf(taken);
    }

    /**
     * Players with at least one pending digest.
     *
     * @return the count
     */
    public int subjects() {
        return pending.size();
    }

    /**
     * Digests still waiting for a batch. The buffer stays.
     *
     * @param subject the player
     * @return a copy, empty when none are waiting
     */
    public List<FightDigest> pending(UUID subject) {
        var list = pending.get(subject);
        return list == null ? List.of() : List.copyOf(list);
    }
}
