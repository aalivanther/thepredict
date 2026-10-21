package akyrion.thepredict.learn;

import akyrion.thepredict.domain.learn.CreditMath;
import akyrion.thepredict.domain.learn.RewardSpec;
import akyrion.thepredict.engine.duel.SkirmishSimulator;

/**
 * Alignment of {@code r1} with winning, against the raw effect index, on 200 seeds (M28.0). The constants of
 * {@link RewardSpec} are not changed when {@code r1} does not win.
 */
public final class RewardAlignmentBaseline {
    /** Seeds {@code 1} through this value. */
    public static final int SEEDS = 200;

    private RewardAlignmentBaseline() {
    }

    /**
     * Death tick of one 5-against-1 fight. Two calls with the same seed return the same tick.
     *
     * @param seed fight seed
     * @return the death tick, or -1 when the player lived
     */
    public static int deathTick(long seed) {
        return fight(seed).deathTick();
    }

    /**
     * Spearman of raw impacts and of {@code r1} with the group win, plus the paired bootstrap difference.
     *
     * @return one line that names the decision
     */
    public static String report() {
        var effect = new double[SEEDS];
        var reward = new double[SEEDS];
        var win = new double[SEEDS];
        for (var index = 0; index < SEEDS; index++) {
            var measured = fight(index + 1L);
            var impacts = measured.impacts();
            effect[index] = impacts;
            var died = measured.deathTick() > 0;
            var took = died ? measured.deathTick() : SkirmishSimulator.TICKS;
            var speed = CreditMath.speed(400, took);
            var credit = CreditMath.mix(CreditMath.individual(false, died, speed),
                    CreditMath.group(false, died, speed), true);
            reward[index] = CreditMath.reward(CreditMath.effectTerm(impacts), credit, 0.0);
            win[index] = died ? 1.0 : 0.0;
        }
        var effectRank = spearman(effect, win);
        var rewardRank = spearman(reward, win);
        var paired = bootstrap(effect, reward, win);
        var decision = Double.isFinite(paired[0]) && paired[0] > 0.0 ? "GAIN" : "NO CUMPLE";
        return "alignment n=" + SEEDS + " seed=1.." + SEEDS + " eta=" + RewardSpec.ETA + " beta=" + RewardSpec.BETA
                + " wins=" + sum(win) + " meanEffect=" + mean(effect) + " meanR1=" + mean(reward)
                + " effectSpearman=" + effectRank + " r1Spearman=" + rewardRank
                + " paired=" + paired[1] + " low=" + paired[0] + " high=" + paired[2]
                + " decision=" + decision;
    }

    private static double sum(double[] values) {
        var total = 0.0;
        for (var value : values) {
            total += value;
        }
        return total;
    }

    private static double mean(double[] values) {
        return sum(values) / values.length;
    }

    private static SkirmishSimulator.Measured fight(long seed) {
        var fight = new SkirmishSimulator.Fight("m28-seed", 5, 1, false, SkirmishSimulator.TICKS);
        return SkirmishSimulator.run(fight, SkirmishSimulator.Arm.OFF, seed);
    }

    private static double[] bootstrap(double[] effect, double[] reward, double[] win) {
        var draws = new double[1000];
        var state = 28L;
        for (var draw = 0; draw < draws.length; draw++) {
            var sampleEffect = new double[effect.length];
            var sampleReward = new double[reward.length];
            var sampleWin = new double[win.length];
            for (var index = 0; index < effect.length; index++) {
                state = state * 6364136223846793005L + 1;
                var pick = (int) Long.remainderUnsigned(state, effect.length);
                sampleEffect[index] = effect[pick];
                sampleReward[index] = reward[pick];
                sampleWin[index] = win[pick];
            }
            draws[draw] = spearman(sampleReward, sampleWin) - spearman(sampleEffect, sampleWin);
        }
        java.util.Arrays.sort(draws);
        return new double[] {draws[25], spearman(reward, win) - spearman(effect, win), draws[974]};
    }

    private static double spearman(double[] left, double[] right) {
        return pearson(ranks(left), ranks(right));
    }

    private static double pearson(double[] left, double[] right) {
        var count = left.length;
        var meanLeft = 0.0;
        var meanRight = 0.0;
        for (var index = 0; index < count; index++) {
            meanLeft += left[index];
            meanRight += right[index];
        }
        meanLeft /= count;
        meanRight /= count;
        var product = 0.0;
        var leftSquare = 0.0;
        var rightSquare = 0.0;
        for (var index = 0; index < count; index++) {
            var a = left[index] - meanLeft;
            var b = right[index] - meanRight;
            product += a * b;
            leftSquare += a * a;
            rightSquare += b * b;
        }
        if (leftSquare == 0.0 || rightSquare == 0.0) {
            return Double.NaN;
        }
        return product / Math.sqrt(leftSquare * rightSquare);
    }

    private static double[] ranks(double[] values) {
        var count = values.length;
        var order = new Integer[count];
        for (var index = 0; index < count; index++) {
            order[index] = index;
        }
        java.util.Arrays.sort(order, (left, right) -> Double.compare(values[left], values[right]));
        var ranked = new double[count];
        var start = 0;
        while (start < count) {
            var end = start;
            while (end + 1 < count && values[order[end + 1]] == values[order[start]]) {
                end++;
            }
            var rank = (start + end) / 2.0 + 1.0;
            for (var index = start; index <= end; index++) {
                ranked[order[index]] = rank;
            }
            start = end + 1;
        }
        return ranked;
    }
}
