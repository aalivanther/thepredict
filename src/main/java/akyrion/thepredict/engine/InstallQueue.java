package akyrion.thepredict.engine;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Active sessions whose goal set still has to be installed, in arrival order (M05.4, M09.1 step 5). Owned by
 * {@link SessionRegistry}, which adds a session when it opens, resumes or advances while active and removes it when
 * it suspends or closes; the coordinator polls it within its limit per tick and adds a deferred one again at the end.
 * Every operation is O(1). Server thread only.
 */
final class InstallQueue {
    private final LinkedHashSet<UUID> order = new LinkedHashSet<>();

    /** Adds a session at the end unless it is already waiting. */
    void add(UUID entityId) {
        order.add(entityId);
    }

    /** Forgets a session. */
    void remove(UUID entityId) {
        order.remove(entityId);
    }

    /** Removes and returns the session that has waited longest. */
    Optional<UUID> poll() {
        if (order.isEmpty()) {
            return Optional.empty();
        }
        var iterator = order.iterator();
        var entityId = iterator.next();
        iterator.remove();
        return Optional.of(entityId);
    }

    /** Counts the waiting sessions. */
    int size() {
        return order.size();
    }
}
