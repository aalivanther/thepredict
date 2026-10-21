package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultyKey;
import akyrion.thepredict.domain.DifficultyPolicy;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightSummary;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryMath;
import akyrion.thepredict.domain.PressureCause;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.ProfileMemory;

import java.util.Optional;

/**
 * Opening rung pulled toward a player's memory of one profile (M27.5). No subject, an unloaded record, a missing rung
 * or a mix without weight (no stored weight and {@code memory.rungPriorFights: 0}) keeps the profile prior. A death
 * never raises the rung: the caller applies that rule.
 */
public final class SubjectDifficultyPriors implements akyrion.thepredict.domain.DifficultyPriors {
    private final PlayerMemory memory;
    private final MemorySettings settings;
    private final long today;
    private final int profileRung;

    /**
     * A prior over one loaded record.
     *
     * @param memory the record
     * @param settings the limits
     * @param todayEpochDay the civil day
     * @param profileRung the profile prior
     */
    public SubjectDifficultyPriors(PlayerMemory memory, MemorySettings settings, long todayEpochDay, int profileRung) {
        this.memory = memory;
        this.settings = settings;
        this.today = todayEpochDay;
        this.profileRung = profileRung;
    }

    @Override
    public int startRung(DifficultyKey key) {
        return opening(memory, key.profile(), key.section(), PartyBand.ONE, profileRung, today, settings).rung();
    }

    /**
     * Nothing to learn here: a closed fight reaches the record through its digest (M27.2), not through the opening
     * prior.
     *
     * @param key the fight key
     * @param summary the close
     */
    @Override
    public void observe(DifficultyKey key, FightSummary summary) {
    }

    /**
     * The contracted rung.
     *
     * @param memory the loaded record
     * @param profileName the anchor profile
     * @param section solo or group
     * @param party the party band
     * @param profileRung the profile's prior rung
     * @param todayEpochDay the civil day
     * @param settings the limits
     * @return the rung and whether memory moved it
     */
    public static Prior opening(PlayerMemory memory, String profileName, DifficultySection section, PartyBand party,
                                int profileRung, long todayEpochDay, MemorySettings settings) {
        var found = profile(memory, ProfileHash.of(profileName));
        if (found.isEmpty()) {
            return new Prior(profileRung, false);
        }
        var row = found.get();
        var index = party.ordinal() * DifficultySection.values().length + section.ordinal();
        var stored = row.rungs()[index];
        if (stored == ProfileMemory.NONE) {
            return new Prior(profileRung, false);
        }
        var tau = PlayerMemoryMath.tau(todayEpochDay - row.lastEpochDay(), settings.halfLifeDays());
        var weight = row.ttkWeight() * tau;
        var prior = settings.rungPriorFights();
        if (!(weight + prior > 0.0)) {
            return new Prior(profileRung, false);
        }
        var mixed = (weight * (stored / 16.0) + prior * profileRung) / (weight + prior);
        var low = Math.max(DifficultyPolicy.MIN_RUNG, profileRung - settings.maxRungOffset());
        var high = Math.min(DifficultyPolicy.MAX_RUNG, profileRung + settings.maxRungOffset());
        var rung = (int) Math.round(Math.min(high, Math.max(low, mixed)));
        return new Prior(rung, true);
    }

    private static Optional<ProfileMemory> profile(PlayerMemory memory, int hash) {
        for (var profile : memory.profiles()) {
            if (profile.hash() == hash) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    /**
     * An opening rung.
     *
     * @param rung the rung
     * @param fromSubject whether memory moved it
     */
    public record Prior(int rung, boolean fromSubject) {
        /**
         * The cause to append when memory moved the rung.
         *
         * @return the subject cause, or empty
         */
        public Optional<PressureCause> cause() {
            return fromSubject ? Optional.of(PressureCause.START_PRIOR_SUBJECT) : Optional.empty();
        }
    }
}
