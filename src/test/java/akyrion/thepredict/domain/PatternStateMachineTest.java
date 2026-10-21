package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternStateMachineTest {
    @Test
    void threeTailsEnterAndTheMeanLeaves() {
        var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
        var entered = 0;
        for (var trial = 1; trial <= 12; trial++) {
            var cause = tracker.observe(true, 1.0, 0.05, 8, 3, 0.05, 4.0);
            if (trial == 7) {
                assertEquals(PatternState.INACTIVE, tracker.state());
            }
            if (cause.isPresent() && cause.get() == PatternCause.ENTER) {
                entered = trial;
            }
        }
        assertEquals(9, entered);
        assertEquals(PatternState.ACTIVE, tracker.state());
        var left = false;
        for (var trial = 0; trial < 40 && !left; trial++) {
            var cause = tracker.observe(false, 1.0, 0.05, 8, 3, 0.05, 4.0);
            left = cause.isPresent() && cause.get() == PatternCause.EXIT_MEAN;
        }
        assertTrue(left);
        assertEquals(PatternState.INACTIVE, tracker.state());
    }

    @Test
    void aCusumHalvesEvidenceAndKeepsThePatternActive() {
        var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
        for (var trial = 0; trial < 12; trial++) {
            tracker.observe(true, 1.0, 0.05, 8, 3, 0.05, 4.0);
        }
        assertEquals(PatternState.ACTIVE, tracker.state());
        var before = tracker.evidence().successes();
        var halved = false;
        for (var trial = 0; trial < 40 && !halved; trial++) {
            var cause = tracker.observe(false, 1.0, 0.05, 8, 3, 0.05, 4.0);
            halved = cause.isPresent() && cause.get() == PatternCause.CHANGEPOINT_HALVED;
        }
        assertTrue(halved, "state " + tracker.state());
        assertEquals(PatternState.ACTIVE, tracker.state());
        assertTrue(tracker.evidence().successes() < before);
        tracker.rest();
        assertEquals(PatternState.RESTING, tracker.state());
        assertEquals(PatternCause.REST_END, tracker.endRest());
        assertEquals(PatternCause.ENCOUNTER_END, tracker.endEncounter());
        assertEquals(PatternState.INACTIVE, tracker.state());
    }

    @Test
    void forgettingKeepsFalsePositivesRareAndDetectsAStrongPattern() {
        var falseAt60 = rate(0.5, 60, 2000, 0.98);
        var falseAt400 = rate(0.5, 400, 2000, 0.98);
        var detected = rate(0.8, 80, 2000, 0.98);
        assertTrue(falseAt60 <= 0.005, "fp60 " + falseAt60);
        assertTrue(falseAt400 <= 0.005, "fp400 " + falseAt400);
        assertTrue(detected >= 0.9, "detect " + detected);
    }

    @Test
    void theInControlCusumMedianLastsAtLeastTwoHundredFiftySteps() {
        var samples = 41;
        var lengths = new int[samples];
        for (var sample = 0; sample < samples; sample++) {
            var cusum = new Cusum();
            cusum.arm(0.8);
            var steps = 0;
            while (steps < 4000 && !cusum.observe(bit(sample, steps) < 0.8, 0.7, 4.0)) {
                steps++;
            }
            lengths[sample] = steps;
        }
        java.util.Arrays.sort(lengths);
        assertTrue(lengths[samples / 2] >= 250, "arl " + lengths[samples / 2]);
    }

    private static double rate(double probability, int trials, int seeds, double lambda) {
        var hits = 0;
        for (var seed = 0; seed < seeds; seed++) {
            var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
            for (var trial = 0; trial < trials; trial++) {
                var success = bit(seed, trial) < probability;
                tracker.observe(success, lambda, 0.05, 8, 3, 0.05, 4.0);
            }
            if (tracker.state() == PatternState.ACTIVE || tracker.state() == PatternState.RESTING) {
                hits++;
            }
        }
        return hits / (double) seeds;
    }

    private static double bit(int seed, int trial) {
        var word = StableRandom.mix(StableRandom.seed(26L, seed, trial));
        return (word >>> 11) / (double) (1L << 53);
    }
}
