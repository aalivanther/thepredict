package akyrion.thepredict.domain;

import java.util.Optional;

/**
 * An administrator's imposition on one world (M22.13). A present preset beats an explicit target time.
 *
 * @param preset empty leaves the mob's preset
 * @param entropy empty leaves the mob; present forces entropy on or off
 */
public record WorldDifficulty(Optional<DifficultyPreset> preset, Optional<Boolean> entropy) {
    /** No imposition. */
    public static final WorldDifficulty NONE = new WorldDifficulty(Optional.empty(), Optional.empty());
}
