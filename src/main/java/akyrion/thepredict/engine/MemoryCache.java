package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemoryDecoded;
import akyrion.thepredict.domain.MemoryEntryState;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.domain.StyleMemory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The server-thread cache of player records (M27.4). Eviction prefers a disconnected player with no encounter among
 * the 16 oldest entries. Digests live outside this map. The loads still waiting for storage are also kept apart, by
 * the time they started, so the timeout of every tick reads only them and never walks or reorders the whole cache
 * (EFO-14); there are never more of them than cached entries.
 */
public final class MemoryCache {
    private static final long RETRY_NANOS = 60_000_000_000L;
    private final int cap;
    private final MemoryCounters counters;
    private final LinkedHashMap<UUID, Slot> slots;
    private final LinkedHashMap<UUID, Long> pending = new LinkedHashMap<>();

    /**
     * Creates the cache.
     *
     * @param subjects the cap, at least 1
     * @param counters where an active eviction is counted
     */
    public MemoryCache(int subjects, MemoryCounters counters) {
        this.cap = subjects;
        this.counters = counters;
        this.slots = new LinkedHashMap<>(16, 0.75f, true);
    }

    /**
     * Starts a load when the player is absent or the last failure is at least a minute old.
     *
     * @param subject the player
     * @param nanos monotonic time
     * @return {@code true} when a query should be sent
     */
    public boolean beginLoad(UUID subject, long nanos) {
        var slot = slots.get(subject);
        if (slot != null && !(slot.state == MemoryEntryState.FAILED && nanos - slot.changed >= RETRY_NANOS)) {
            return false;
        }
        var through = slot == null ? 0L : slot.appliedThrough;
        slots.put(subject, new Slot(MemoryEntryState.PENDING, Optional.empty(), false, nanos, true, false, through));
        pending.put(subject, nanos);
        evict();
        return true;
    }

    /**
     * Stores a loaded record. A newer record is read-only and is not kept as bytes to rewrite.
     *
     * @param subject the player
     * @param record the blob; ignored when {@code present} is false
     * @param present whether a row was stored
     * @param newer whether the blob is a newer version
     * @param nanos monotonic time
     */
    public void loaded(UUID subject, byte[] record, boolean present, boolean newer, long nanos) {
        var previous = slots.get(subject);
        loaded(subject, record, present, newer, nanos, previous == null ? 0L : previous.appliedThrough);
    }

    /**
     * Stores a loaded record and how far the writer has applied batches.
     *
     * @param subject the player
     * @param record the blob; ignored when {@code present} is false
     * @param present whether a row was stored
     * @param newer whether the blob is a newer version
     * @param nanos monotonic time
     * @param appliedThrough highest batch already folded into {@code record}
     */
    public void loaded(UUID subject, byte[] record, boolean present, boolean newer, long nanos,
                       long appliedThrough) {
        var state = newer ? MemoryEntryState.READ_ONLY
                : !present ? MemoryEntryState.ABSENT : MemoryEntryState.LOADED;
        var previous = slots.get(subject);
        var online = previous == null || previous.online;
        var encounter = previous != null && previous.encounter;
        var through = Math.max(appliedThrough, previous == null ? 0L : previous.appliedThrough);
        var kept = newer || !present ? Optional.<byte[]>empty() : Optional.of(record.clone());
        slots.put(subject, new Slot(state, kept, newer, nanos, online, encounter, through));
        pending.remove(subject);
        if (newer) {
            counters.add(MemoryCounters.Kind.RECORD_NEWER, 1);
        }
        evict();
    }

    /**
     * Marks a pending load as failed once the timeout has elapsed.
     *
     * @param subject the player
     * @param nanos monotonic time
     * @param timeoutSeconds the configured timeout
     */
    public void timeout(UUID subject, long nanos, int timeoutSeconds) {
        var slot = slots.get(subject);
        if (slot != null && slot.state == MemoryEntryState.PENDING
                && nanos - slot.changed >= timeoutSeconds * 1_000_000_000L) {
            slots.put(subject, new Slot(MemoryEntryState.FAILED, Optional.empty(), false, nanos, slot.online,
                    slot.encounter, slot.appliedThrough));
            pending.remove(subject);
        }
    }

    /**
     * Marks as failed every pending load whose timeout has elapsed, reading only the pending loads. With nothing
     * pending it touches nothing and allocates nothing.
     *
     * @param nanos monotonic time
     * @param timeoutSeconds the configured timeout
     */
    public void timeoutPending(long nanos, int timeoutSeconds) {
        if (pending.isEmpty()) {
            return;
        }
        var limit = timeoutSeconds * 1_000_000_000L;
        var entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (nanos - entry.getValue() < limit) {
                continue;
            }
            entries.remove();
            var slot = slots.get(entry.getKey());
            if (slot != null && slot.state == MemoryEntryState.PENDING) {
                slots.put(entry.getKey(), new Slot(MemoryEntryState.FAILED, Optional.empty(), false, nanos,
                        slot.online, slot.encounter, slot.appliedThrough));
            }
        }
    }

    /**
     * How many loads still wait for storage.
     *
     * @return the count, never above the cached entries
     */
    public int pendingLoads() {
        return pending.size();
    }

    /**
     * Notes presence. Access refreshes the LRU position.
     *
     * @param subject the player
     * @param online whether the player is connected
     * @param encounter whether the player is in an encounter
     */
    public void mark(UUID subject, boolean online, boolean encounter) {
        var slot = slots.get(subject);
        if (slot == null) {
            return;
        }
        slots.put(subject, new Slot(slot.state, slot.record, slot.newer, slot.changed, online, encounter,
                slot.appliedThrough));
    }

    /**
     * Removes one player, for an administrative reset.
     *
     * @param subject the player
     */
    public void remove(UUID subject) {
        slots.remove(subject);
        pending.remove(subject);
    }

    /**
     * The cached slot.
     *
     * @param subject the player
     * @return the slot
     */
    public Optional<Slot> slot(UUID subject) {
        return Optional.ofNullable(slots.get(subject));
    }

    /**
     * How many entries are cached.
     *
     * @return the size
     */
    public int size() {
        return slots.size();
    }

    /**
     * Subjects currently cached, oldest first.
     *
     * @return the ids
     */
    public java.util.List<UUID> subjects() {
        return java.util.List.copyOf(slots.keySet());
    }

    /**
     * Text for an administrator. A missing or failed entry is not the same as an empty history.
     *
     * @param subject the player
     * @return {@code memoria no cargada} or {@code sin historia} or the loaded summary
     */
    public String inspect(UUID subject) {
        var slot = slots.get(subject);
        if (slot == null || slot.state == MemoryEntryState.PENDING || slot.state == MemoryEntryState.FAILED) {
            return MemoryInspect.notLoaded();
        }
        if (slot.state == MemoryEntryState.ABSENT || slot.record.isEmpty()) {
            return MemoryInspect.emptyHistory();
        }
        return "cargada";
    }

    private void evict() {
        while (slots.size() > cap) {
            var oldest = new ArrayList<UUID>();
            for (var id : slots.keySet()) {
                if (oldest.size() == 16) {
                    break;
                }
                oldest.add(id);
            }
            UUID chosen = null;
            for (var id : oldest) {
                var slot = slots.get(id);
                if (!slot.online && !slot.encounter) {
                    chosen = id;
                    break;
                }
            }
            if (chosen == null) {
                chosen = oldest.getFirst();
                counters.add(MemoryCounters.Kind.CACHE_EVICTED_ACTIVE, 1);
            }
            slots.remove(chosen);
            pending.remove(chosen);
        }
    }

    /**
     * One cache entry.
     *
     * @param state the state
     * @param record the blob
     * @param newer whether the row is newer than this build
     * @param changed monotonic time of the last state change
     * @param online whether the player is connected
     * @param encounter whether the player is in an encounter
     * @param appliedThrough highest batch already inside {@code record}
     */
    public record Slot(MemoryEntryState state, Optional<byte[]> record, boolean newer, long changed, boolean online,
                       boolean encounter, long appliedThrough) {
    }

    /**
     * Loaded record, plus later batches, plus digests that are not in a batch yet. Read-only rows stay as stored.
     *
     * @param subject the player
     * @param batches batches still in flight
     * @param pending digests not yet flushed
     * @param captured style captured with those digests
     * @param settings the limits
     * @return the record an admission should read
     */
    public PlayerMemory view(UUID subject, List<AggregateBatch> batches, List<FightDigest> pending,
                             Optional<StyleMemory> captured, MemorySettings settings) {
        return fold(subject, batches, pending, captured, settings);
    }

    /**
     * {@link #view(UUID, List, List, Optional, MemorySettings)} without a captured style (rule C4, EFO-24).
     *
     * @param subject the player
     * @param batches batches still in flight
     * @param pending digests not yet flushed
     * @param settings the limits
     * @return the record an admission should read
     */
    public PlayerMemory view(UUID subject, List<AggregateBatch> batches, List<FightDigest> pending,
                             MemorySettings settings) {
        return fold(subject, batches, pending, Optional.empty(), settings);
    }

    /**
     * {@link #view(UUID, List, List, Optional, MemorySettings)} with the style captured with those digests (rule C4,
     * EFO-24).
     *
     * @param subject the player
     * @param batches batches still in flight
     * @param pending digests not yet flushed
     * @param captured style captured with those digests
     * @param settings the limits
     * @return the record an admission should read
     */
    public PlayerMemory view(UUID subject, List<AggregateBatch> batches, List<FightDigest> pending,
                             StyleMemory captured, MemorySettings settings) {
        return fold(subject, batches, pending, Optional.of(Objects.requireNonNull(captured, "captured")), settings);
    }

    private PlayerMemory fold(UUID subject, List<AggregateBatch> batches, List<FightDigest> pending,
                              Optional<StyleMemory> captured, MemorySettings settings) {
        var slot = slots.get(subject);
        var base = decoded(slot);
        if (slot != null && slot.state == MemoryEntryState.READ_ONLY) {
            return base;
        }
        var through = slot == null ? 0L : slot.appliedThrough;
        var scratch = new MemoryCounters();
        base = foldBatches(subject, batches, through, base, scratch);
        if (!pending.isEmpty() || captured.isPresent()) {
            var day = pending.isEmpty() ? 0L : Math.max(0L, pending.getFirst().endEpochMillis() / 86_400_000L);
            base = PlayerMemoryUpdater.apply(base, pending, captured, day, settings, scratch);
        }
        return base;
    }

    private static PlayerMemory foldBatches(UUID subject, List<AggregateBatch> batches, long through,
                                            PlayerMemory base, MemoryCounters counters) {
        for (var batch : batches) {
            if (batch.batchId() <= through) {
                continue;
            }
            for (var delta : batch.memory()) {
                if (!delta.subject().equals(subject) || delta.fold().isEmpty()) {
                    continue;
                }
                var fold = delta.fold().get();
                base = PlayerMemoryUpdater.apply(base, fold.fights(), fold.style(), fold.epochDay(), fold.settings(),
                        counters);
            }
        }
        return base;
    }

    private static PlayerMemory decoded(Slot slot) {
        if (slot == null || slot.record.isEmpty()
                || (slot.state != MemoryEntryState.LOADED && slot.state != MemoryEntryState.READ_ONLY)) {
            return PlayerMemory.empty();
        }
        var decoded = PlayerMemoryCodec.decode(slot.record.get());
        if (decoded instanceof MemoryDecoded.Ready ready) {
            return ready.memory();
        }
        return PlayerMemory.empty();
    }
}
