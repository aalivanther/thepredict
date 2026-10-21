package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySample;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.PartyBand;

import akyrion.thepredict.domain.CounterMemory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One outcome of one profile in one encounter (M27.3, B1).
 *
 * @param subject the player
 * @param profileHash FNV-1a of the profile name
 * @param outcome the outcome
 * @param band the party band
 * @param section solo or group
 * @param difficulty the sample, empty below fair or on a non-anchor profile
 * @param endEpochMillis when the encounter ended
 * @param anchorChanged whether the group anchor moved
 * @param counters counters of this profile, at most two survive the fold
 */
public record FightDigest(UUID subject, int profileHash, FightOutcome outcome, PartyBand band,
                          DifficultySection section, Optional<DifficultySample> difficulty, long endEpochMillis,
                          boolean anchorChanged, List<CounterMemory> counters) {
    /**
     * A digest with no counters (rule W5).
     *
     * @param subject the player
     * @param profileHash FNV-1a of the profile name
     * @param outcome the outcome
     * @param band the party band
     * @param section solo or group
     * @param difficulty the sample
     * @param endEpochMillis when the encounter ended
     * @param anchorChanged whether the group anchor moved
     */
    public FightDigest(UUID subject, int profileHash, FightOutcome outcome, PartyBand band, DifficultySection section,
                       Optional<DifficultySample> difficulty, long endEpochMillis, boolean anchorChanged) {
        this(subject, profileHash, outcome, band, section, difficulty, endEpochMillis, anchorChanged, List.of());
    }

    /**
     * Copies the counters.
     */
    public FightDigest {
        difficulty = difficulty == null ? Optional.empty() : difficulty;
        counters = List.copyOf(counters == null ? List.of() : counters);
    }
}
