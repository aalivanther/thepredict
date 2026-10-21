package akyrion.thepredict.eval;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.PatternTracker;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryMath;
import akyrion.thepredict.domain.ProfileMemory;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.engine.SubjectDifficultyPriors;
import akyrion.thepredict.engine.duel.CampaignScripts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

/**
 * The offline memory campaign (M27.11). {@code ./gradlew evalMemory} runs the known local case. The full
 * 25 by 3 by 3 by 2 by 500 grid stays outside the unit suite. Paid arms are not called from here.
 */
public final class MemoryEval {
    private MemoryEval() {
    }

    /**
     * One published local case. The mode stays {@link MemoryMode#RECORD}. A gain under 8 is still published.
     *
     * @param fights fights in the stable habit
     * @param seed the draw seed
     * @return the measured case
     */
    public static LocalCase knownCase(int fights, long seed) {
        var script = CampaignScripts.stable(0.5, 0.9);
        var label = script.label();
        var pattern = EnemyPattern.SPAMS_CLICKS;
        var memoryActive = 0;
        var coldActive = 0;
        var carriedS = 0.0;
        var carriedF = 0.0;
        var total = fights * 30;
        for (var fight = 0; fight < fights; fight++) {
            var cold = new PatternTracker(pattern);
            var warm = new PatternTracker(pattern);
            seed(warm, carriedS, carriedF);
            var fightS = 0;
            var fightF = 0;
            for (var trial = 0; trial < 30; trial++) {
                var success = unit(StableRandom.mix(seed ^ (fight * 31L + trial))) < script.pattern().probability();
                if (success) {
                    fightS++;
                } else {
                    fightF++;
                }
                cold.observe(success, 0.98, 0.05, 8, 3, 0.05, 4.0);
                warm.observe(success, 0.98, 0.05, 8, 3, 0.05, 4.0, 4);
                if (cold.state() == PatternState.ACTIVE) {
                    coldActive++;
                }
                if (warm.state() == PatternState.ACTIVE) {
                    memoryActive++;
                }
            }
            var updated = PlayerMemoryMath.update(carriedS, carriedF, 0, fightS, fightF, 0.9, 4, 0.25, 0.6);
            carriedS = updated.successes();
            carriedF = updated.failures();
        }
        var gain = 100.0 * (memoryActive - coldActive) / total;
        var staleDilute = staleRate(seed ^ 9L, true);
        var staleInvert = staleRate(seed ^ 11L, false);
        var recovery = recoveryMedian(seed ^ 13L);
        var rung = startRungRatio();
        return new LocalCase(label, MemorySettings.DEFAULT.mode(), gain, staleDilute, staleInvert, recovery, rung,
                fights, 30);
    }

    /**
     * Writes the known case. The first argument is the directory, or {@code report} plus the directory.
     *
     * @param arguments the directory
     * @throws Exception when the report cannot be written
     */
    public static void main(String[] arguments) throws Exception {
        var report = arguments.length == 0 || arguments[0].equals("report")
                ? Path.of(arguments.length < 2 ? "build/reports/m27" : arguments[1])
                : Path.of(arguments[0]);
        Files.createDirectories(report);
        var grid = MemoryGrid.report(MemoryGrid.PLAN_SEED, MemoryGrid.PLAN_CAMPAIGNS);
        var known = knownCase(64, 27L);
        var text = grid + known.text();
        Files.writeString(report.resolve("memory.txt"), text);
        System.out.println(text);
    }

    private static void seed(PatternTracker tracker, double successes, double failures) {
        var mass = successes + failures;
        if (mass < 2.0) {
            return;
        }
        var kept = Math.min(6.0, mass) / mass;
        tracker.evidence().seed(successes * kept, failures * kept);
    }

    private static double staleRate(long seed, boolean dilute) {
        var active = 0;
        var fights = 32;
        var carriedS = 40.0;
        var carriedF = 4.0;
        for (var fight = 0; fight < fights; fight++) {
            var warm = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
            seed(warm, carriedS, carriedF);
            var probability = dilute ? 0.35 : 0.1;
            var fightS = 0;
            var fightF = 0;
            for (var trial = 0; trial < 30; trial++) {
                var success = unit(StableRandom.mix(seed ^ (fight * 17L + trial))) < probability;
                if (success) {
                    fightS++;
                } else {
                    fightF++;
                }
                warm.observe(success, 0.98, 0.05, 8, 3, 0.05, 4.0, 4);
            }
            if (warm.state() == PatternState.ACTIVE) {
                active++;
            }
            var updated = PlayerMemoryMath.update(carriedS, carriedF, 0, fightS, fightF, 0.9, 4, 0.25, 0.6);
            carriedS = updated.successes();
            carriedF = updated.failures();
        }
        return active / (double) fights;
    }

    private static double recoveryMedian(long seed) {
        var samples = new ArrayList<Integer>();
        for (var campaign = 0; campaign < 21; campaign++) {
            var carriedS = 40.0;
            var carriedF = 4.0;
            var fights = 0;
            for (var fight = 0; fight < 8; fight++) {
                var warm = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
                seed(warm, carriedS, carriedF);
                var fightS = 0;
                var fightF = 0;
                for (var trial = 0; trial < 30; trial++) {
                    var success = unit(StableRandom.mix(seed ^ (campaign * 100L + fight * 30L + trial))) < 0.1;
                    if (success) {
                        fightS++;
                    } else {
                        fightF++;
                    }
                    warm.observe(success, 0.98, 0.05, 8, 3, 0.05, 4.0, 4);
                }
                fights++;
                var updated = PlayerMemoryMath.update(carriedS, carriedF, 0, fightS, fightF, 0.9, 4, 0.25, 0.6);
                carriedS = updated.successes();
                carriedF = updated.failures();
                if (warm.state() != PatternState.ACTIVE && warm.evidence().mean() < EnemyPattern.SPAMS_CLICKS.theta()) {
                    break;
                }
            }
            samples.add(fights);
        }
        samples.sort(Integer::compareTo);
        return samples.get(samples.size() / 2);
    }

    private static double startRungRatio() {
        var ideal = 2;
        var profile = 6;
        var rungs = new int[] {ideal * 16, ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE,
                ProfileMemory.NONE, ProfileMemory.NONE};
        var row = new ProfileMemory(akyrion.thepredict.domain.ProfileHash.of("boss"), 8, 4, 4, 0,
                akyrion.thepredict.domain.FightOutcome.WIN, 1, 20f, 0f, 0f, rungs, java.util.List.of());
        var memory = new PlayerMemory(1, 1, 8, java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.List.of(row));
        var opened = SubjectDifficultyPriors.opening(memory, "boss", DifficultySection.SOLO, PartyBand.ONE, profile, 1,
                MemorySettings.DEFAULT);
        var with = Math.abs(opened.rung() - ideal);
        var without = Math.abs(profile - ideal);
        return without == 0 ? 1.0 : with / (double) without;
    }

    private static double unit(long word) {
        return (word >>> 11) / (double) (1L << 53);
    }

    /**
     * Numbers from the known case. {@code gainPoints} is percentage points of active trials.
     *
     * @param label the script label
     * @param mode the shipped mode
     * @param gainPoints detection gain
     * @param staleDilute share of diluted fights still active
     * @param staleInvert share of inverted fights still active
     * @param recoveryMedian fights until the mean falls
     * @param startRungErrorRatio error with memory over error without it
     * @param fights fights measured
     * @param trials trials per fight
     */
    public record LocalCase(String label, MemoryMode mode, double gainPoints, double staleDilute, double staleInvert,
                            double recoveryMedian, double startRungErrorRatio, int fights, int trials) {
        /**
         * The report body.
         *
         * @return the text
         */
        public String text() {
            return "label " + label + "\nmode " + mode.name().toLowerCase(Locale.ROOT)
                    + "\ngain " + gainPoints + "\nstale_dilute " + staleDilute + "\nstale_invert " + staleInvert
                    + "\nrecovery " + recoveryMedian + "\nstart_rung_error_ratio " + startRungErrorRatio
                    + "\nfights " + fights + "\ntrials " + trials
                    + "\npaid arms not run\n";
        }
    }
}
