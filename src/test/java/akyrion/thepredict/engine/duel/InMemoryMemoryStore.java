package akyrion.thepredict.engine.duel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Campaign store with no SQLite (M27.11). Bytes are copied on the way in and out.
 */
public final class InMemoryMemoryStore {
    private final Map<UUID, byte[]> rows = new LinkedHashMap<>();

    /**
     * Stores one record.
     *
     * @param subject the player
     * @param record the blob
     */
    public void save(UUID subject, byte[] record) {
        rows.put(subject, record.clone());
    }

    /**
     * Loads one record.
     *
     * @param subject the player
     * @return a copy, or empty
     */
    public Optional<byte[]> load(UUID subject) {
        var row = rows.get(subject);
        return row == null ? Optional.empty() : Optional.of(row.clone());
    }

    /**
     * How many subjects are stored.
     *
     * @return the count
     */
    public int size() {
        return rows.size();
    }
}
