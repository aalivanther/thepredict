package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemoryFact;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.ProfileHash;

import java.util.Optional;

/**
 * Picks one history phrase for a profile at admission (M27.6). It never reads last killer or last victim.
 */
public final class MemoryFactSelector {
    private MemoryFactSelector() {
    }

    /**
     * The first matching phrase, or empty when the last fight is too old or the profile has no history.
     *
     * @param memory the loaded record
     * @param profileName profile the asking mob uses
     * @param todayEpochDay the civil day of the admission
     * @param maxAgeDays oldest fact
     * @return the fact
     */
    public static Optional<MemoryFact> select(PlayerMemory memory, String profileName, long todayEpochDay,
                                              int maxAgeDays) {
        var hash = ProfileHash.of(profileName);
        for (var profile : memory.profiles()) {
            if (profile.hash() != hash) {
                continue;
            }
            if (todayEpochDay - profile.lastEpochDay() > maxAgeDays) {
                return Optional.empty();
            }
            if (profile.lastOutcome() == FightOutcome.DISENGAGED) {
                return Optional.empty();
            }
            if (profile.losses() >= 3 && profile.lastOutcome() == FightOutcome.LOSS) {
                return Optional.of(MemoryFact.LOST_MANY);
            }
            if (profile.lastOutcome() == FightOutcome.LOSS) {
                return Optional.of(MemoryFact.LOST_LAST);
            }
            if (profile.wins() >= 3) {
                return Optional.of(MemoryFact.BEAT_MANY);
            }
            if (profile.wins() >= 1) {
                return Optional.of(MemoryFact.BEAT_BEFORE);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }
}
