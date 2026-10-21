package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.MemoryAdmission;
import akyrion.thepredict.domain.MemoryAdmissionCause;
import akyrion.thepredict.domain.MemoryEntryState;
import akyrion.thepredict.domain.MemoryFact;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.domain.PressureCause;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides what one admission may use (M27.5, M27.6). A late load does not change an open fight: the caller keeps the
 * admission it already fixed.
 */
public final class MemoryAdmissionPolicy {
    private MemoryAdmissionPolicy() {
    }

    /**
     * The admission for one player and one profile name.
     *
     * @param mode the active mode
     * @param exempt whether the player is exempt
     * @param state the cache state
     * @param record the loaded blob; ignored when {@code present} is false
     * @param present whether a decoded row is available
     * @param unverified whether the requested mode did not pass its gate
     * @param profileName the asking profile, empty when several profiles share the request
     * @param ambiguous whether the request names more than one profile
     * @param profileRung the profile prior
     * @param todayEpochDay the civil day
     * @param settings the limits
     * @param subject the player, used only as the seed key
     * @param counters where a miss or an ambiguous fact is counted
     * @param section solo or group
     * @param band the party band
     * @return the admission
     */
    public static MemoryAdmission admit(MemoryMode mode, boolean exempt, MemoryEntryState state, byte[] record,
                                        boolean present, boolean unverified, String profileName, boolean ambiguous,
                                        int profileRung, long todayEpochDay, MemorySettings settings, UUID subject,
                                        MemoryCounters counters, DifficultySection section, PartyBand band) {
        if (exempt) {
            return bare(MemoryAdmissionCause.EXEMPT, profileRung, mode);
        }
        if (unverified || mode == MemoryMode.OFF || mode == MemoryMode.RECORD) {
            var cause = unverified ? MemoryAdmissionCause.UNVERIFIED : MemoryAdmissionCause.MODE_RECORD;
            return bare(cause, profileRung, mode == MemoryMode.OFF ? MemoryMode.OFF : MemoryMode.RECORD);
        }
        if (state != MemoryEntryState.LOADED && state != MemoryEntryState.READ_ONLY) {
            counters.add(MemoryCounters.Kind.NOT_LOADED_AT_ADMISSION, 1);
            return bare(MemoryAdmissionCause.NOT_LOADED, profileRung, mode);
        }
        var memory = decode(record, present);
        var prior = SubjectDifficultyPriors.opening(memory, profileName, section, band, profileRung, todayEpochDay,
                settings);
        var seed = new SubjectEnemyModelSeed(memory, settings, todayEpochDay).seedFor(subject);
        var seeded = 0;
        var mass = 0.0;
        for (var index = 0; index < seed.successes().length; index++) {
            var trials = seed.successes()[index] + seed.failures()[index];
            if (trials > 0.0) {
                seeded++;
                mass += trials;
            }
        }
        Optional<MemoryFact> fact = Optional.empty();
        if (mode == MemoryMode.FULL && !ambiguous && !profileName.isEmpty()) {
            fact = MemoryFactSelector.select(memory, profileName, todayEpochDay, settings.factMaxAgeDays());
        }
        if (ambiguous && mode == MemoryMode.FULL) {
            counters.add(MemoryCounters.Kind.FACT_AMBIGUOUS, 1);
        }
        var cause = seeded == 0 ? MemoryAdmissionCause.COLD : MemoryAdmissionCause.SEEDED;
        return new MemoryAdmission(cause, prior.rung(), prior.cause(), fact, seeded, mass, mode);
    }

    /**
     * Keeps at most {@code maxFacts} phrases, in alias order, and at most one per participant.
     *
     * @param facts facts already chosen, in alias order
     * @param maxFacts the cap
     * @return the kept facts
     */
    public static List<Optional<MemoryFact>> cap(List<Optional<MemoryFact>> facts, int maxFacts) {
        var kept = new ArrayList<Optional<MemoryFact>>();
        var used = 0;
        for (var fact : facts) {
            if (fact.isPresent() && used < maxFacts) {
                kept.add(fact);
                used++;
            } else {
                kept.add(Optional.empty());
            }
        }
        return List.copyOf(kept);
    }

    private static MemoryAdmission bare(MemoryAdmissionCause cause, int rung, MemoryMode mode) {
        return new MemoryAdmission(cause, rung, Optional.empty(), Optional.empty(), 0, 0.0, mode);
    }

    private static PlayerMemory decode(byte[] record, boolean present) {
        if (!present || record.length == 0) {
            return PlayerMemory.empty();
        }
        var decoded = PlayerMemoryCodec.decode(record);
        if (decoded instanceof akyrion.thepredict.domain.MemoryDecoded.Ready ready) {
            return ready.memory();
        }
        return PlayerMemory.empty();
    }
}
