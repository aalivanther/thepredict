package akyrion.thepredict.domain;

import java.util.List;
import java.util.Optional;

/**
 * One player's memory, version 1 (M27.1). {@code lastEpochDay} only moves forward.
 *
 * @param firstEpochDay first day with a fight
 * @param lastEpochDay latest day
 * @param fights encounters, exact
 * @param lastKiller profile of the last killing blow, for inspect
 * @param lastVictim profile of the last win, for inspect
 * @param style forgotten style, absent when the player has no style yet
 * @param profiles per-profile history, most recent first is not required
 */
public record PlayerMemory(long firstEpochDay, long lastEpochDay, long fights, Optional<NarrativeMemory> lastKiller,
                           Optional<NarrativeMemory> lastVictim, Optional<StyleMemory> style,
                           List<ProfileMemory> profiles) {
    /**
     * Copies the profiles.
     *
     * @throws IllegalArgumentException if a day or the fight count is negative
     */
    public PlayerMemory {
        if (firstEpochDay < 0 || lastEpochDay < 0 || fights < 0) {
            throw new IllegalArgumentException("player memory");
        }
        profiles = List.copyOf(profiles);
    }

    /**
     * A player with no fights.
     *
     * @return the empty record
     */
    public static PlayerMemory empty() {
        return new PlayerMemory(0, 0, 0, Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }
}
