package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CounterMemory;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.NarrativeMemory;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryMath;
import akyrion.thepredict.domain.ProfileMemory;
import akyrion.thepredict.domain.StyleFold;
import akyrion.thepredict.domain.StyleMemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies fight digests to a player's record (M27.2). Exact counts do not decay. A loss never raises a stored rung.
 * Style, counters, histograms and moments go through {@link PlayerMemoryMath}. The counters of one profile are merged
 * by pattern, stored and fight evidence together, and the two with the best hit rate over at least three effective
 * tries are kept (M27.1).
 */
public final class PlayerMemoryUpdater {
    private static final int PATTERNS = 25;
    private static final float MIN_TRIES = 3f;
    private static final int KEPT_COUNTERS = 2;
    private static final Comparator<CounterMemory> BEST_RATE = Comparator
            .comparingDouble((CounterMemory counter) -> counter.landed() / (double) counter.tries()).reversed()
            .thenComparing(Comparator.comparingDouble(CounterMemory::tries).reversed())
            .thenComparingInt(CounterMemory::pattern);

    private PlayerMemoryUpdater() {
    }

    /**
     * Applies the digests of one encounter with the delivered settings.
     *
     * @param memory the record before the encounter
     * @param digests one per profile
     * @param epochDay the civil day
     * @return the updated record
     */
    public static PlayerMemory apply(PlayerMemory memory, java.util.List<FightDigest> digests, long epochDay) {
        return fold(memory, digests, null, epochDay, MemorySettings.DEFAULT, new MemoryCounters());
    }

    /**
     * Applies digests without a style summary.
     *
     * @param memory the record before the fold
     * @param digests digests in any order; they are applied by end time
     * @param epochDay the civil day
     * @param settings the limits
     * @param counters where saturation and breaks are counted
     * @return the updated record
     */
    public static PlayerMemory apply(PlayerMemory memory, List<FightDigest> digests, long epochDay,
                                     MemorySettings settings, MemoryCounters counters) {
        return fold(memory, digests, null, epochDay, settings, counters);
    }

    /**
     * Applies digests and one style summary.
     *
     * @param memory the record before the fold
     * @param digests digests in any order; they are applied by end time
     * @param style the fight style
     * @param epochDay the civil day
     * @param settings the limits
     * @param counters where saturation and breaks are counted
     * @return the updated record
     */
    public static PlayerMemory apply(PlayerMemory memory, List<FightDigest> digests, StyleMemory style,
                                     long epochDay, MemorySettings settings, MemoryCounters counters) {
        return fold(memory, digests, Objects.requireNonNull(style, "style"), epochDay, settings, counters);
    }

    /**
     * Applies digests and one style summary that may be absent. Kept for callers that still pass an {@code Optional}
     * (rule W5); the two overloads above take none (rule C4).
     *
     * @param memory the record before the fold
     * @param digests digests in any order; they are applied by end time
     * @param style the fight style, empty when there is none
     * @param epochDay the civil day
     * @param settings the limits
     * @param counters where saturation and breaks are counted
     * @return the updated record
     */
    public static PlayerMemory apply(PlayerMemory memory, List<FightDigest> digests, Optional<StyleMemory> style,
                                    long epochDay, MemorySettings settings, MemoryCounters counters) {
        return fold(memory, digests, style.orElse(null), epochDay, settings, counters);
    }

    private static PlayerMemory fold(PlayerMemory memory, List<FightDigest> digests, StyleMemory style,
                                     long epochDay, MemorySettings settings, MemoryCounters counters) {
        if (digests.isEmpty() && style == null) {
            return memory;
        }
        var ordered = new ArrayList<>(digests);
        ordered.sort(Comparator.comparingLong(FightDigest::endEpochMillis).thenComparingInt(FightDigest::profileHash));
        var profiles = new ArrayList<>(memory.profiles());
        for (var digest : ordered) {
            bumpInto(profiles, digest, epochDay, settings);
        }
        var killer = narrative(ordered, FightOutcome.LOSS, memory.lastKiller(), epochDay);
        var victim = narrative(ordered, FightOutcome.WIN, memory.lastVictim(), epochDay);
        var encounters = encounters(ordered);
        var gamma = settings.fightDecay() * PlayerMemoryMath.tau(epochDay - memory.lastEpochDay(),
                settings.halfLifeDays());
        var folded = StyleFold.apply(memory.style(), Optional.ofNullable(style), gamma, settings.crossFightH(),
                settings.styleBreakFactor());
        counters.add(MemoryCounters.Kind.FIELD_SATURATED, folded.saturated());
        counters.add(MemoryCounters.Kind.STYLE_BREAK, folded.breaks());
        var first = memory.fights() == 0 && encounters > 0 ? epochDay : memory.firstEpochDay();
        var day = encounters == 0 ? memory.lastEpochDay() : PlayerMemoryMath.forwardDay(memory.lastEpochDay(), epochDay);
        return new PlayerMemory(first, day, memory.fights() + encounters, killer, victim, folded.style(), profiles);
    }

    /** The profile of the last digest with this outcome, in end order, or the stored one without such a digest. */
    private static Optional<NarrativeMemory> narrative(List<FightDigest> ordered, FightOutcome outcome,
                                                       Optional<NarrativeMemory> stored, long epochDay) {
        var last = stored;
        for (var digest : ordered) {
            if (digest.outcome() == outcome) {
                last = Optional.of(new NarrativeMemory(digest.profileHash(), epochDay));
            }
        }
        return last;
    }

    /** Encounters among the digests: one per distinct end time, the digests already in end order. */
    private static int encounters(List<FightDigest> ordered) {
        var seen = Long.MIN_VALUE;
        var count = 0;
        for (var digest : ordered) {
            if (digest.endEpochMillis() != seen) {
                seen = digest.endEpochMillis();
                count++;
            }
        }
        return count;
    }

    private static void bumpInto(List<ProfileMemory> profiles, FightDigest digest, long epochDay,
                                 MemorySettings settings) {
        var index = index(profiles, digest.profileHash());
        var row = index < 0 ? blank(digest.profileHash()) : profiles.get(index);
        var next = bump(row, digest, epochDay, settings);
        if (index < 0) {
            profiles.add(next);
        } else {
            profiles.set(index, next);
        }
    }

    private static int index(List<ProfileMemory> profiles, int hash) {
        for (var index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).hash() == hash) {
                return index;
            }
        }
        return -1;
    }

    private static ProfileMemory blank(int hash) {
        return new ProfileMemory(hash, 0, 0, 0, 0, FightOutcome.OTHER, 0, 0f, 0f, 0f,
                new int[] {ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE,
                        ProfileMemory.NONE, ProfileMemory.NONE}, List.of());
    }

    private static ProfileMemory bump(ProfileMemory row, FightDigest digest, long epochDay, MemorySettings settings) {
        var wins = row.wins() + (digest.outcome() == FightOutcome.WIN ? 1 : 0);
        var losses = row.losses() + (digest.outcome() == FightOutcome.LOSS ? 1 : 0);
        var disengaged = row.disengaged() + (digest.outcome() == FightOutcome.DISENGAGED ? 1 : 0);
        var gamma = settings.fightDecay() * PlayerMemoryMath.tau(epochDay - row.lastEpochDay(), settings.halfLifeDays());
        var rungs = row.rungs().clone();
        var weight = (float) (row.ttkWeight() * gamma);
        var mean = row.ttkMean();
        var moment = row.ttkM2();
        if (digest.difficulty().isPresent()) {
            var sample = digest.difficulty().get();
            var slot = digest.band().ordinal() * 2 + digest.section().ordinal();
            rungs[slot] = PlayerMemoryMath.stepRung(rungs[slot], sample.startRungEstimate(),
                    digest.outcome() == FightOutcome.LOSS);
            var updated = PlayerMemoryMath.welford(weight, mean, moment, 1.0, sample.lnTtk(), 0.0);
            weight = (float) updated[0];
            mean = (float) updated[1];
            moment = (float) updated[2];
        }
        return new ProfileMemory(row.hash(), row.fights() + 1, wins, losses, disengaged, digest.outcome(),
                PlayerMemoryMath.forwardDay(row.lastEpochDay(), epochDay), weight, mean, moment, rungs,
                counters(row.counters(), digest.counters(), gamma));
    }

    /**
     * Merges the stored counters, faded by {@code gamma}, with those of the fight, one entry per pattern, and keeps the
     * two best hit rates among the patterns with at least three effective tries. Ties go to more tries, then to the
     * lower pattern ordinal.
     */
    private static List<CounterMemory> counters(List<CounterMemory> stored, List<CounterMemory> fight, double gamma) {
        var landed = new double[PATTERNS];
        var tries = new double[PATTERNS];
        for (var counter : stored) {
            landed[counter.pattern()] += counter.landed() * gamma;
            tries[counter.pattern()] += counter.tries() * gamma;
        }
        for (var counter : fight) {
            landed[counter.pattern()] += counter.landed();
            tries[counter.pattern()] += counter.tries();
        }
        var kept = new ArrayList<CounterMemory>();
        for (var pattern = 0; pattern < PATTERNS; pattern++) {
            var effective = (float) tries[pattern];
            if (effective >= MIN_TRIES) {
                kept.add(new CounterMemory(pattern, (float) landed[pattern], effective));
            }
        }
        kept.sort(BEST_RATE);
        return List.copyOf(kept.size() > KEPT_COUNTERS ? kept.subList(0, KEPT_COUNTERS) : kept);
    }
}
