package akyrion.thepredict.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Profile-level spawn step remembered after a fight ends (M22.12). The dying session is gone before the next spawn,
 * so the step lives here until a later fight replaces it. Server thread only.
 */
public final class SpawnLevelBook {
    private final Map<String, Integer> deltas = new HashMap<>();

    /**
     * An empty book.
     */
    public SpawnLevelBook() {
    }

    /**
     * Remembers the step for one profile. Zero clears it.
     *
     * @param profileId profile id
     * @param delta sign of the step
     */
    public void note(String profileId, int delta) {
        Objects.requireNonNull(profileId, "profileId");
        if (delta == 0) {
            deltas.remove(profileId);
            return;
        }
        deltas.put(profileId, delta);
    }

    /**
     * The step stored for a profile.
     *
     * @param profileId profile id
     * @return the step, or {@code 0}
     */
    public int delta(String profileId) {
        return deltas.getOrDefault(Objects.requireNonNull(profileId, "profileId"), 0);
    }
}
