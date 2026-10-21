package akyrion.thepredict.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Active sessions split by the parity of their UUID hash, so a tick visits only the sessions whose
 * {@code (uuid.hashCode() + tick) % 2 == 0} (M06.5) without scanning the others. Adding and removing are O(1);
 * a rotating cursor per parity continues where the previous visit stopped when the limit cut it. Server
 * thread only.
 */
final class ProbeRing {
    private final List<List<MobSession>> buckets = List.of(new ArrayList<>(), new ArrayList<>());
    private final Map<UUID, Integer> positions = new HashMap<>();
    private final int[] cursors = new int[2];

    /** Adds an active session once. */
    void add(MobSession session) {
        var entityId = session.key().entityId();
        if (positions.containsKey(entityId)) {
            return;
        }
        var bucket = buckets.get(parity(entityId.hashCode()));
        bucket.add(session);
        positions.put(entityId, bucket.size() - 1);
    }

    /** Removes a session by moving the last one of its bucket into its slot. */
    void remove(UUID entityId) {
        var position = positions.remove(entityId);
        if (position == null) {
            return;
        }
        var bucket = buckets.get(parity(entityId.hashCode()));
        var last = bucket.removeLast();
        if (position < bucket.size()) {
            bucket.set(position, last);
            positions.put(last.key().entityId(), position);
        }
    }

    /**
     * Visits at most {@code maximum} sessions whose probe falls on this tick. Each gets its next probe two ticks later
     * before the visitor sees it, so the caller needs no capturing lambda (AE-14, C12).
     *
     * @return number of visited sessions
     */
    int visit(long tick, int maximum, Consumer<MobSession> visitor) {
        var index = parity(tick);
        var bucket = buckets.get(index);
        var count = Math.min(maximum, bucket.size());
        if (count <= 0) {
            return 0;
        }
        var start = cursors[index] % bucket.size();
        for (var offset = 0; offset < count; offset++) {
            var session = bucket.get((start + offset) % bucket.size());
            session.nextProbeTick(tick + 2);
            visitor.accept(session);
        }
        cursors[index] = (start + count) % bucket.size();
        return count;
    }

    /** Counts the sessions in the ring. */
    int size() {
        return positions.size();
    }

    /**
     * Returns the parity class of a hash or tick: {@code (hash + tick) % 2 == 0} holds exactly when both have
     * the same class, also for negative hashes.
     */
    static int parity(long value) {
        return (int) Math.floorMod(value, 2L);
    }
}
