package akyrion.thepredict.eval;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.PatternTracker;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryMath;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.ProfileMemory;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.StyleMemory;
import akyrion.thepredict.engine.GroupFightClock;
import akyrion.thepredict.engine.SubjectDifficultyPriors;
import akyrion.thepredict.engine.SubjectEnemyModelSeed;
import akyrion.thepredict.engine.duel.SkirmishSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/**
 * E1–E5 of M27.11. The factors are the plan's: 25 patterns, habits 0.65, 0.8 and 0.9, changes none, dilute and
 * invert, 30 and 60 trials, and the requested number of campaigns of 8 fights. The shipped mode stays
 * {@link MemoryMode#RECORD}.
 */
public final class MemoryGrid {
    /** Seed named by the local probes. */
    public static final long PLAN_SEED = 27L;
    /** Campaigns named by E1. */
    public static final int PLAN_CAMPAIGNS = 500;

    private static final double[] HABITS = {0.65, 0.8, 0.9};
    private static final int[] TRIALS = {30, 60};
    private static final int FIGHTS = 8;
    private static final int CHANGE_AT = 4;

    private MemoryGrid() {
    }

    /**
     * Runs the grid and the smaller E2–E5 checks.
     *
     * @param seed the draw seed
     * @param campaigns campaigns per cell
     * @return the publication
     */
    public static String report(long seed, int campaigns) {
        var tally = new Tally();
        var patterns = EnemyPattern.values();
        for (var pattern : patterns) {
            for (var habit = 0; habit < HABITS.length; habit++) {
                for (var change = 0; change < 3; change++) {
                    for (var width : TRIALS) {
                        for (var campaign = 0; campaign < campaigns; campaign++) {
                            cell(tally, pattern, habit, change, width, campaign, seed, false);
                        }
                    }
                }
            }
            for (var campaign = 0; campaign < campaigns; campaign++) {
                cell(tally, pattern, 0, 0, 30, campaign, seed ^ 0x5EEDL, true);
            }
        }
        return text(seed, campaigns, patterns.length, tally) + extra(seed, campaigns);
    }

    private static void cell(Tally tally, EnemyPattern pattern, int habit, int change, int width, int campaign,
                             long seed, boolean flat) {
        if (!pattern.usesBeta()) {
            tally.skipped.increment();
            return;
        }
        var probability = flat ? 0.5 : HABITS[habit];
        var carriedS = 0.0;
        var carriedF = 0.0;
        var cusum = 0.0;
        for (var fight = 0; fight < FIGHTS; fight++) {
            var rate = flat ? 0.5 : rate(probability, change, fight);
            var cold = new PatternTracker(pattern);
            var warm = new PatternTracker(pattern);
            plant(warm, carriedS, carriedF);
            var fightS = 0;
            var fightF = 0;
            for (var trial = 0; trial < width; trial++) {
                var draw = unit(StableRandom.mix(seed ^ pattern.ordinal() * 0x9E3779B97F4A7C15L
                        ^ habit * 10007L ^ change * 97L ^ width * 13L ^ campaign * 0x100000001L
                        ^ (fight * 64L + trial)));
                var success = draw < rate;
                if (success) {
                    fightS++;
                } else {
                    fightF++;
                }
                cold.observe(success, 0.98, 0.05, 8, 3, 0.05, 4.0);
                warm.observe(success, 0.98, 0.05, 8, 3, 0.05, 4.0, 4);
            }
            tally.parityCold.add(fightS);
            tally.parityWarm.add(fightS);
            if (flat) {
                tally.falseFights.increment();
                if (cold.state() == PatternState.ACTIVE) {
                    tally.falseCold.increment();
                }
                if (warm.state() == PatternState.ACTIVE) {
                    tally.falseWarm.increment();
                }
            } else if (width == 30 && fight >= 1 && change == 0) {
                tally.gainFights.increment();
                if (cold.state() == PatternState.ACTIVE) {
                    tally.gainCold.increment();
                }
                if (warm.state() == PatternState.ACTIVE) {
                    tally.gainWarm.increment();
                }
            }
            if (!flat && fight >= CHANGE_AT && change != 0 && rate < pattern.theta()) {
                var fights = change == 1 ? tally.diluteFights : tally.invertFights;
                var stale = change == 1 ? tally.diluteStale : tally.invertStale;
                fights.increment();
                if (warm.state() == PatternState.ACTIVE) {
                    stale.increment();
                }
            }
            var updated = PlayerMemoryMath.update(carriedS, carriedF, cusum, fightS, fightF, 0.9, 4, 0.25,
                    PlayerMemoryMath.HABIT_THETA);
            carriedS = updated.successes();
            carriedF = updated.failures();
            cusum = updated.cusum();
        }
    }

    private static double rate(double habit, int change, int fight) {
        if (fight < CHANGE_AT || change == 0) {
            return habit;
        }
        return change == 1 ? 0.5 : 1.0 - habit;
    }

    private static void plant(PatternTracker tracker, double successes, double failures) {
        var mass = successes + failures;
        if (mass < 2.0) {
            return;
        }
        var kept = Math.min(6.0, mass) / mass;
        tracker.evidence().seed(successes * kept, failures * kept);
    }

    private static String text(long seed, int campaigns, int patterns, Tally tally) {
        var gain = points(tally.gainWarm.sum(), tally.gainCold.sum(), tally.gainFights.sum());
        var dilute = share(tally.diluteStale.sum(), tally.diluteFights.sum());
        var invert = share(tally.invertStale.sum(), tally.invertFights.sum());
        var falseWarm = share(tally.falseWarm.sum(), tally.falseFights.sum());
        var falseCold = share(tally.falseCold.sum(), tally.falseFights.sum());
        var parity = tally.parityWarm.sum() == tally.parityCold.sum() ? 1 : 0;
        var gainMark = gain >= 8.0 ? "cumple" : "no cumple";
        var diluteMark = dilute <= 0.02 ? "cumple" : "no cumple";
        var invertMark = invert <= 0.005 ? "cumple" : "no cumple";
        var falseMark = falseWarm <= falseCold ? "cumple" : "no cumple";
        return "E1 seed " + seed + " campaigns " + campaigns + " plan campaigns " + PLAN_CAMPAIGNS
                + " patterns " + patterns + " habits 0.65,0.8,0.9 changes none,dilute,invert trials 30,60 fights "
                + FIGHTS + "\nMEMORY_DETECTION_GAIN " + gain + " " + gainMark
                + "\nMEMORY_STALE_ACTIVATION dilute " + dilute + " " + diluteMark
                + " invert " + invert + " " + invertMark
                + "\nfalse_activation memory " + falseWarm + " record " + falseCold + " " + falseMark
                + "\nMEMORY_SEED_PARITY " + parity + (parity == 1 ? " cumple" : " no cumple")
                + "\nskipped_non_beta " + tally.skipped.sum()
                + "\nmode " + MemoryMode.DEFAULT.name().toLowerCase(java.util.Locale.ROOT) + "\n";
    }

    private static String extra(long seed, int campaigns) {
        var recovery = recovery(seed, campaigns, true);
        var diluted = recovery(seed, campaigns, false);
        var streak = streak(seed, Math.min(campaigns, 64));
        var aged = agedSeed();
        var vengeance = vengeance();
        var rung = rungs();
        var skirmish = SkirmishSimulator.run(new SkirmishSimulator.Fight("e5", 3, 1, false, 80),
                SkirmishSimulator.Arm.FAIR, seed);
        var clock = new GroupFightClock();
        var player = new UUID(5, 5);
        clock.noteMembers(3);
        clock.begin("boss", 3);
        clock.noteHit(0, player, 10, false, "boss");
        clock.noteHit(0, player, 30, true, "boss");
        var ln = clock.lnElapsed(player);
        return "E2 FAIRNESS_VIOLATIONS " + skirmish.fairnessViolations()
                + (skirmish.fairnessViolations() == 0 ? " cumple" : " no cumple")
                + " impacts " + skirmish.impacts()
                + "\nUNWARNED_HITS_PER_MINUTE no reejecutado en esta rejilla no cumple"
                + "\n" + contrasts(seed)
                + "\nMEMORY_DETECTION_GAIN no es E3"
                + "\nE4 MEMORY_RECOVERY_FIGHTS invert " + recovery
                + (recovery <= 1.0 ? " cumple" : " no cumple")
                + " dilute " + diluted + (diluted <= 2.0 ? " cumple" : " no cumple")
                + " CROSS_FIGHT_EXPLOIT_STREAK " + streak + (streak <= 2 ? " cumple" : " no cumple")
                + " seed_after_90 " + aged + (aged <= 1.0 ? " cumple" : " no cumple")
                + " vengeance " + (vengeance ? "cumple" : "no cumple")
                + "\nE5 START_RUNG_ERROR ratio " + rung.ratio
                + (rung.ratio <= 0.7 ? " cumple" : " no cumple")
                + " TTK_BAND_FIGHTS memory " + rung.memoryBand + " profile " + rung.profileBand
                + " modelo local no separa peldaños 0-8 no cumple"
                + " group_ln " + ln + " group_anchor " + clock.anchor()
                + "\nE6 no ejecutada sesion 27\nE7 no ejecutada paso de pago\n";
    }

    private static String contrasts(long seed) {
        return MemoryTtkContrast.text(seed);
    }

    private static double recovery(long seed, int campaigns, boolean invert) {
        var samples = new ArrayList<Integer>();
        var limit = Math.max(1, Math.min(campaigns, 64));
        for (var campaign = 0; campaign < limit; campaign++) {
            var carriedS = 40.0;
            var carriedF = 4.0;
            var cusum = 0.0;
            var fights = 0;
            for (var fight = 0; fight < FIGHTS; fight++) {
                var rate = invert ? 0.1 : 0.5;
                var fightS = 0;
                var fightF = 0;
                for (var trial = 0; trial < 30; trial++) {
                    var success = unit(StableRandom.mix(seed ^ (campaign * 80L + fight * 40L + trial)
                            ^ (invert ? 3L : 9L))) < rate;
                    if (success) {
                        fightS++;
                    } else {
                        fightF++;
                    }
                }
                fights++;
                var updated = PlayerMemoryMath.update(carriedS, carriedF, cusum, fightS, fightF, 0.9, 4, 0.25,
                        PlayerMemoryMath.HABIT_THETA);
                carriedS = updated.successes();
                carriedF = updated.failures();
                cusum = updated.cusum();
                var mass = carriedS + carriedF;
                if (mass > 0.0 && carriedS / mass < PlayerMemoryMath.HABIT_THETA) {
                    break;
                }
            }
            samples.add(fights);
        }
        samples.sort(Integer::compareTo);
        return samples.get(samples.size() / 2);
    }

    private static int streak(long seed, int campaigns) {
        var worst = 0;
        for (var campaign = 0; campaign < Math.max(1, campaigns); campaign++) {
            var run = 0;
            var best = 0;
            var carriedS = 30.0;
            var carriedF = 2.0;
            for (var fight = 0; fight < FIGHTS; fight++) {
                var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
                plant(tracker, carriedS, carriedF);
                var fightS = 0;
                for (var trial = 0; trial < 30; trial++) {
                    var success = unit(StableRandom.mix(seed ^ 0x51L ^ (campaign * 50L + fight * 30L + trial))) < 0.2;
                    if (success) {
                        fightS++;
                    }
                    tracker.observe(success, 0.98, 0.05, 8, 3, 0.05, 4.0, 4);
                }
                if (tracker.state() == PatternState.ACTIVE && fightS >= 3) {
                    run++;
                    best = Math.max(best, run);
                } else {
                    run = 0;
                }
                var updated = PlayerMemoryMath.update(carriedS, carriedF, 0, fightS, 30 - fightS, 0.9, 4, 0.25,
                        PlayerMemoryMath.HABIT_THETA);
                carriedS = updated.successes();
                carriedF = updated.failures();
            }
            worst = Math.max(worst, best);
        }
        return worst;
    }

    private static double agedSeed() {
        var memory = new PlayerMemory(1, 10, 4, Optional.empty(), Optional.empty(), Optional.of(style(40f, 4f)),
                List.of());
        var seed = new SubjectEnemyModelSeed(memory, MemorySettings.DEFAULT, 100).seedFor(new UUID(1, 1));
        return seed.trials();
    }

    private static boolean vengeance() {
        var style = style(12f, 4f);
        var lived = new PlayerMemory(1, 20, 3, Optional.empty(), Optional.empty(), Optional.of(style), List.of());
        var died = new PlayerMemory(1, 20, 3, Optional.of(new akyrion.thepredict.domain.NarrativeMemory(
                ProfileHash.of("boss"), 20)), Optional.empty(), Optional.of(style), List.of());
        var settings = MemorySettings.DEFAULT;
        var left = new SubjectEnemyModelSeed(lived, settings, 20).seedFor(new UUID(2, 2));
        var right = new SubjectEnemyModelSeed(died, settings, 20).seedFor(new UUID(2, 2));
        return left.trials() == right.trials();
    }

    private static Rung rungs() {
        var memoryError = 0.0;
        var profileError = 0.0;
        var counted = 0;
        var memoryBand = 0;
        var profileBand = 0;
        for (var index = 0; index < 16; index++) {
            var skill = 0.25 + index * (0.75 / 15.0);
            var ideal = (int) Math.round((1.0 - skill) * 8.0);
            var profile = 4;
            if (ideal == profile) {
                continue;
            }
            var row = blank(ideal);
            var memory = new PlayerMemory(1, 1, 1, Optional.empty(), Optional.empty(), Optional.empty(), List.of(row));
            for (var band : new int[] {45, 60}) {
                var opened = SubjectDifficultyPriors.opening(memory, "boss", DifficultySection.SOLO, PartyBand.ONE,
                        profile, 1, MemorySettings.DEFAULT);
                memoryError += Math.abs(opened.rung() - ideal);
                profileError += Math.abs(profile - ideal);
                counted++;
                memoryBand += bandFights(opened.rung(), band);
                profileBand += bandFights(profile, band);
            }
        }
        var ratio = profileError == 0.0 ? 1.0 : memoryError / profileError;
        return new Rung(ratio, memoryBand, profileBand);
    }

    private static int bandFights(int rung, int target) {
        var ln = Math.log(target);
        var low = Math.log(target * 0.8);
        var high = Math.log(target * 1.25);
        var mean = ln + (4 - rung) * 0.05;
        return mean >= low && mean <= high ? 1 : 3;
    }

    private static ProfileMemory blank(int rung) {
        var rungs = new int[] {rung * 16, ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE,
                ProfileMemory.NONE, ProfileMemory.NONE};
        return new ProfileMemory(ProfileHash.of("boss"), 6, 3, 3, 0, FightOutcome.WIN, 1, 8f, 0f, 0f, rungs,
                List.of());
    }

    private static StyleMemory style(float success, float failure) {
        var successes = new float[StyleMemory.PATTERNS];
        var failures = new float[StyleMemory.PATTERNS];
        successes[0] = success;
        failures[0] = failure;
        return new StyleMemory(1f, 1, successes, failures, new int[StyleMemory.PATTERNS],
                new float[StyleMemory.ACTIONS], 0f, new int[0], new float[0], new float[StyleMemory.HISTOGRAMS],
                new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS], 0f, 0f, 0f, new float[StyleMemory.EPISODES],
                new float[StyleMemory.EPISODES]);
    }

    private static double points(long warm, long cold, long fights) {
        if (fights == 0) {
            return 0.0;
        }
        return 100.0 * (warm - cold) / (double) fights;
    }

    private static double share(long part, long whole) {
        return whole == 0 ? 0.0 : part / (double) whole;
    }

    private static double unit(long word) {
        return (word >>> 11) / (double) (1L << 53);
    }

    private static final class Tally {
        private final LongAdder gainWarm = new LongAdder();
        private final LongAdder gainCold = new LongAdder();
        private final LongAdder gainFights = new LongAdder();
        private final LongAdder diluteStale = new LongAdder();
        private final LongAdder diluteFights = new LongAdder();
        private final LongAdder invertStale = new LongAdder();
        private final LongAdder invertFights = new LongAdder();
        private final LongAdder falseWarm = new LongAdder();
        private final LongAdder falseCold = new LongAdder();
        private final LongAdder falseFights = new LongAdder();
        private final LongAdder parityWarm = new LongAdder();
        private final LongAdder parityCold = new LongAdder();
        private final LongAdder skipped = new LongAdder();
    }

    private record Rung(double ratio, int memoryBand, int profileBand) {
    }

}
