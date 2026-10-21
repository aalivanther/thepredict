package akyrion.thepredict.eval;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternTracker;
import akyrion.thepredict.domain.PlayerMemoryMath;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.engine.duel.CampaignScript;
import akyrion.thepredict.engine.duel.CampaignScripts;
import akyrion.thepredict.engine.duel.SkirmishSimulator;

/**
 * E3 of M27.11. Δ log TTK of the bank, seed against record, on fights numbered 2 and later of a scripted campaign.
 * The four rivals are entities, so the memory does not act against them and the plan asks {@code NON_REGRESSION} with
 * the two arms identical: Δ = 0 on every pair (SIM-05). The transcript flag is not an arm. The GSPRT runs until it
 * decides; with identical arms its variance is the Bernoulli floor of {@link EvalStats#pairLlr}. The gain against the
 * scripted synthetics is not measured here: the memory acts only through the engine, which this skirmish does not run.
 */
public final class MemoryTtkContrast {
    static final int FIGHTS = 8;
    static final int FIRST = 2;
    private static final double QUIET = 0.05;
    private static final int HORIZON = 200;
    private static final String[] NAMES = {"none", "classic", "classic-tuned", "local-best"};

    private MemoryTtkContrast() {
    }

    /**
     * One line per rival. The sample grows by {@link EvalStats#LOOK_EVERY} until the GSPRT stops or the cap is hit.
     *
     * @param seed campaign seed
     * @return the publication
     */
    public static String text(long seed) {
        var lines = new String[NAMES.length + 1];
        for (var index = 0; index < NAMES.length; index++) {
            lines[index] = line(seed, NAMES[index]);
        }
        lines[NAMES.length] = "E3 GAIN contra sinteticos no medida: la memoria solo actua por el motor y esta"
                + " escaramuza no lo ejecuta";
        return String.join("\n", lines);
    }

    private static String line(long seed, String name) {
        var sample = sample(seed, name);
        var n = sample.n;
        var mean = mean(sample.deltas, n);
        var gain = mean < 0.0;
        var s0 = gain ? 0.50 : 0.45;
        var s1 = gain ? 0.55 : 0.50;
        var decision = decide(sample.halves, n, gain);
        var llr = EvalStats.pairLlr(sample.halves, n, s0, s1);
        var same = identical(sample);
        var mark = same && "ACCEPT_H1".equals(decision) ? "cumple" : "no cumple";
        var band = gain ? "GAIN 0.50-0.55" : "NON_REGRESSION 0.45-0.50";
        return name + " EXPLOIT_GAIN_TTK fights>=2 arms seed,record n=" + n
                + " meanDelta=" + mean + " min=" + bound(sample.deltas, n, false)
                + " max=" + bound(sample.deltas, n, true)
                + " variance=" + variance(sample.halves, n)
                + " llr=" + (Double.isNaN(llr) ? "none" : Double.toString(llr))
                + " GSPRT " + band + " " + decision + " identical=" + (same ? "yes" : "no") + " " + mark
                + " pairTicks=" + ticks(sample);
    }

    /** Whether the two arms gave the same death tick on every pair: the entity contrast of the plan, Δ = 0. */
    private static boolean identical(Sample sample) {
        for (var index = 0; index < sample.n; index++) {
            if (sample.seeded[index] != sample.recorded[index]) {
                return false;
            }
        }
        return sample.n > 0;
    }

    private static String decide(double[] halves, int n, boolean gain) {
        if (gain) {
            return OutcomeOracle.gain(java.util.Arrays.copyOf(halves, n));
        }
        return EvalStats.gsprtPairs(halves, n, 0.45, 0.50, EvalStats.MAX_PAIRS, EvalStats.SPRT_A);
    }

    private static Sample sample(long seed, String name) {
        var cap = EvalStats.MAX_PAIRS;
        var halves = new double[cap];
        var deltas = new double[cap];
        var seeded = new int[cap];
        var recorded = new int[cap];
        var filled = 0;
        var campaign = 0;
        var decision = "CONTINUE";
        while (filled < cap && "CONTINUE".equals(decision)) {
            var goal = Math.min(cap, filled + EvalStats.LOOK_EVERY);
            var step = fill(seed, name, campaign, filled, goal, halves, deltas, seeded, recorded);
            campaign = step[0];
            filled = step[1];
            decision = decide(halves, filled, mean(deltas, filled) < 0.0);
        }
        return new Sample(halves, deltas, seeded, recorded, filled);
    }

    private static int[] fill(long seed, String name, int campaign, int filled, int goal, double[] halves,
                              double[] deltas, int[] seeded, int[] recorded) {
        while (filled < goal) {
            filled = campaign(seed, name, campaign, filled, goal, halves, deltas, seeded, recorded);
            campaign++;
        }
        return new int[] {campaign, filled};
    }

    private static int campaign(long seed, String name, int campaign, int filled, int goal, double[] halves,
                                double[] deltas, int[] seeded, int[] recorded) {
        var script = CampaignScripts.stable(0.5, 0.9).switchAt(4);
        var carriedS = 0.0;
        var carriedF = 0.0;
        for (var fight = 1; fight <= FIGHTS && filled < goal; fight++) {
            var rate = rate(script, fight);
            var counts = trials(seed, campaign, fight, rate);
            if (fight >= FIRST) {
                filled = pair(seed, name, campaign, fight, rate, carriedS, carriedF, filled, halves, deltas, seeded,
                        recorded);
            }
            var updated = PlayerMemoryMath.update(carriedS, carriedF, 0, counts[0], counts[1], 0.9, 4, 0.25,
                    PlayerMemoryMath.HABIT_THETA);
            carriedS = updated.successes();
            carriedF = updated.failures();
        }
        return filled;
    }

    private static int pair(long seed, String name, int campaign, int fight, double rate, double carriedS,
                            double carriedF, int filled, double[] halves, double[] deltas, int[] seeded,
                            int[] recorded) {
        var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
        plant(tracker, carriedS, carriedF);
        var fightSeed = StableRandom.seed(seed ^ name.hashCode(), campaign + 1L, fight);
        var arena = new SkirmishSimulator.Fight("e3-" + name, 3, 1, false, HORIZON);
        var seededTick = SkirmishSimulator.bank(arena, name, fightSeed, tracker, rate);
        var recordedTick = SkirmishSimulator.bank(arena, name, fightSeed);
        var delta = StrictMath.log(seededTick.deathTick() > 0 ? seededTick.deathTick() : HORIZON)
                - StrictMath.log(recordedTick.deathTick() > 0 ? recordedTick.deathTick() : HORIZON);
        deltas[filled] = delta;
        halves[filled] = delta < 0.0 ? 1.0 : delta > 0.0 ? 0.0 : 0.5;
        seeded[filled] = seededTick.deathTick();
        recorded[filled] = recordedTick.deathTick();
        return filled + 1;
    }

    private static double rate(CampaignScript script, int fight) {
        var style = script.styleAt(fight - 1, 0, 0L);
        return "switched".equals(style) ? QUIET : script.pattern().probability();
    }

    private static int[] trials(long seed, int campaign, int fight, double rate) {
        var successes = 0;
        var failures = 0;
        var base = StableRandom.seed(seed, campaign + 1L, fight);
        for (var trial = 0; trial < 30; trial++) {
            var word = StableRandom.draw(base, trial, 11);
            if ((word >>> 11) / (double) (1L << 53) < rate) {
                successes++;
            } else {
                failures++;
            }
        }
        return new int[] {successes, failures};
    }

    private static void plant(PatternTracker tracker, double successes, double failures) {
        var mass = successes + failures;
        if (mass < 2.0) {
            return;
        }
        var kept = Math.min(6.0, mass) / mass;
        tracker.evidence().seed(successes * kept, failures * kept);
    }

    private static double mean(double[] values, int n) {
        var total = 0.0;
        for (var index = 0; index < n; index++) {
            total += values[index];
        }
        return total / n;
    }

    private static double variance(double[] values, int n) {
        var center = mean(values, n);
        var sum = 0.0;
        for (var index = 0; index < n; index++) {
            var delta = values[index] - center;
            sum += delta * delta;
        }
        return sum / (n - 1.0);
    }

    private static double bound(double[] values, int n, boolean high) {
        var edge = values[0];
        for (var index = 0; index < n; index++) {
            edge = high ? Math.max(edge, values[index]) : Math.min(edge, values[index]);
        }
        return edge;
    }

    private static String ticks(Sample sample) {
        var text = new StringBuilder();
        for (var index = 0; index < sample.n; index++) {
            if (index > 0) {
                text.append(',');
            }
            text.append(sample.seeded[index]).append(':').append(sample.recorded[index]);
        }
        return text.toString();
    }

    private record Sample(double[] halves, double[] deltas, int[] seeded, int[] recorded, int n) {
    }
}
