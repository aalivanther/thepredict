package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.EvalStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One cell of the effectiveness bench (§7.6 of the simulator audit): a rival in a scenario against one ThePredict
 * configuration and one mode of Jev, played on paired seeds, each seed once with ThePredict as red (arm A) and once as
 * blue (arm B). A win counts 1, a draw half and a loss 0; the lower bound is Wilson's for the quantile the caller gives
 * (one side, Bonferroni over the cells of its family).
 */
final class BenchCell {
    private BenchCell() {
    }

    /**
     * What a cell measures.
     *
     * @param rival name of the rival
     * @param config the rival's classic
     * @param scenario kit, health, arena and spawn
     * @param timing the ThePredict configuration
     * @param mode name of the mode of Jev
     * @param judge the judge of the mode, empty without Jev
     */
    record Spec(String rival, ClassicConfig config, Scenario scenario, BenchProfiles.Timing timing, String mode,
                java.util.Optional<DuelSimulator.Mode.Judge> judge) {
        Spec {
            Objects.requireNonNull(rival, "rival");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(judge, "judge");
        }

        /** One line naming the cell. */
        String label() {
            return scenario.id() + " · " + rival + " · " + timing.name().toLowerCase(java.util.Locale.ROOT) + " · "
                    + mode;
        }
    }

    /** How a round ended for ThePredict. */
    enum Outcome {
        WIN(1.0), DRAW(0.5), LOSS(0.0);

        final double score;

        Outcome(double score) {
            this.score = score;
        }
    }

    /**
     * One round seen from ThePredict.
     *
     * @param seed the seed
     * @param red whether ThePredict was red
     * @param outcome how it ended
     * @param ticks the length of the round
     * @param dealt share of the rival's health ThePredict's hits dealt, before absorption
     * @param taken share of its health the rival's hits dealt to ThePredict, before absorption
     * @param lostHits damaging hits of ThePredict the rival's window took whole
     * @param rivalLostHits damaging hits of the rival ThePredict's window took whole
     * @param baseTicks ticks ThePredict spent at level 4
     * @param retreats retreats ThePredict started
     * @param requests requests sent to Jev
     * @param casts casts of ThePredict by skill
     * @param rivalCasts casts of the rival by skill
     * @param hits hits of ThePredict that dealt damage, by skill
     * @param rivalHits hits of the rival that dealt damage, by skill
     * @param conduct what ThePredict did
     */
    record Duel(long seed, boolean red, Outcome outcome, int ticks, double dealt, double taken, long lostHits,
                long rivalLostHits, int baseTicks, int retreats, int requests, Map<String, Integer> casts,
                Map<String, Integer> rivalCasts, Map<String, Integer> hits, Map<String, Integer> rivalHits,
                Conduct conduct) {
        double score() {
            return outcome.score;
        }

        boolean won() {
            return outcome == Outcome.WIN;
        }
    }

    /**
     * The rounds of a cell and the lengths of the mirror of its rival.
     *
     * @param spec the cell
     * @param a the rounds with ThePredict as red, by seed
     * @param b the rounds with ThePredict as blue, by seed
     * @param mirrorTicks length of the round of the rival against itself on each seed, 0 when it ran out of time
     */
    record Result(Spec spec, List<Duel> a, List<Duel> b, int[] mirrorTicks) {
        Result {
            a = List.copyOf(a);
            b = List.copyOf(b);
            mirrorTicks = mirrorTicks.clone();
            if (a.size() != b.size() || a.size() != mirrorTicks.length) {
                throw new IllegalArgumentException("the arms of a cell must pair by seed");
            }
        }

        int rounds() {
            return a.size() + b.size();
        }

        int wins() {
            return count(Outcome.WIN);
        }

        int draws() {
            return count(Outcome.DRAW);
        }

        int losses() {
            return count(Outcome.LOSS);
        }

        private int count(Outcome outcome) {
            var found = 0;
            for (var list : List.of(a, b)) {
                for (var duel : list) {
                    found += duel.outcome() == outcome ? 1 : 0;
                }
            }
            return found;
        }

        double score() {
            return (wins() + 0.5 * draws()) / rounds();
        }

        /** The lower bound of the score for a normal quantile. */
        double lower(double z) {
            return EvalStats.wilson(wins() + 0.5 * draws(), rounds(), z)[0];
        }

        /** The upper bound of the score for a normal quantile. */
        double upper(double z) {
            return EvalStats.wilson(wins() + 0.5 * draws(), rounds(), z)[1];
        }

        /**
         * McNemar of the sides: seeds won as red and not as blue against the reverse.
         *
         * @return the two-sided exact p-value
         */
        double sides() {
            var redOnly = 0;
            var blueOnly = 0;
            for (var index = 0; index < a.size(); index++) {
                var red = a.get(index).won();
                var blue = b.get(index).won();
                redOnly += red && !blue ? 1 : 0;
                blueOnly += blue && !red ? 1 : 0;
            }
            return EvalStats.mcnemar(redOnly, blueOnly);
        }

        /** Lengths of ThePredict's wins, sorted. */
        double[] winTicks() {
            var ticks = new ArrayList<Double>();
            for (var list : List.of(a, b)) {
                for (var duel : list) {
                    if (duel.won()) {
                        ticks.add((double) duel.ticks());
                    }
                }
            }
            var out = ticks.stream().mapToDouble(Double::doubleValue).toArray();
            Arrays.sort(out);
            return out;
        }

        /** {@code log(win ticks) − log(mirror ticks)} on each won round whose mirror ended with a death. */
        double[] logRatios() {
            var ratios = new ArrayList<Double>();
            for (var list : List.of(a, b)) {
                for (var index = 0; index < list.size(); index++) {
                    var duel = list.get(index);
                    if (duel.won() && mirrorTicks[index] > 0) {
                        ratios.add(StrictMath.log(duel.ticks()) - StrictMath.log(mirrorTicks[index]));
                    }
                }
            }
            return ratios.stream().mapToDouble(Double::doubleValue).toArray();
        }

        double meanTaken() {
            return mean(Duel::taken);
        }

        double meanLostHits() {
            return mean(duel -> duel.lostHits());
        }

        double baseShare() {
            var base = 0.0;
            var ticks = 0.0;
            for (var list : List.of(a, b)) {
                for (var duel : list) {
                    base += duel.baseTicks();
                    ticks += duel.ticks();
                }
            }
            return ticks == 0 ? 0 : base / ticks;
        }

        double meanRetreats() {
            return mean(Duel::retreats);
        }

        double meanRequests() {
            return mean(Duel::requests);
        }

        private double mean(java.util.function.ToDoubleFunction<Duel> field) {
            var sum = 0.0;
            for (var list : List.of(a, b)) {
                for (var duel : list) {
                    sum += field.applyAsDouble(duel);
                }
            }
            return sum / rounds();
        }

        /** The conduct of ThePredict over the cell. */
        ConductSummary conduct() {
            return ConductSummary.of(this);
        }

        /** What each side dealt and cast per minute. */
        BenchDamage.Summary damage() {
            return BenchDamage.of(this);
        }
    }

    /**
     * The median and the quartiles of a sorted series.
     *
     * @param sorted the series, sorted
     * @return the first quartile, the median and the third quartile, or NaN for an empty series
     */
    static double[] quartiles(double[] sorted) {
        if (sorted.length == 0) {
            return new double[] {Double.NaN, Double.NaN, Double.NaN};
        }
        return new double[] {at(sorted, 0.25), at(sorted, 0.5), at(sorted, 0.75)};
    }

    private static double at(double[] sorted, double fraction) {
        var position = fraction * (sorted.length - 1);
        var low = (int) Math.floor(position);
        var high = Math.min(sorted.length - 1, low + 1);
        return sorted[low] + (sorted[high] - sorted[low]) * (position - low);
    }
}
