package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.eval.BradleyTerry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The searches of the legal space (§7.3 of the simulator audit, SIM-02). {@link #legalBest} never lets ThePredict play:
 * successive halving against the pack classic and {@code legal-equal}, then against a league of the best, then a
 * round robin read by Bradley–Terry. {@link #adversarial} is cross-entropy over the same space with ThePredict's mean
 * score as the objective, lower being better for the classic; its first population holds {@code legal-equal} and the
 * legal best, and its finalists are measured again on fresh seeds. Every search seed lies within 1 to 10 000, apart
 * from the evaluation seeds of the bench. Deterministic for its sizes and seeds.
 */
final class ClassicSearch {
    /** First seeds of the search stages. */
    static final long LEGAL_SEED = 1;
    static final long ADVERSARIAL_SEED = 5_001;
    static final long FINAL_SEED = 9_001;
    private static final int SALT_SAMPLE = 223;

    private ClassicSearch() {
    }

    /**
     * How large a search is.
     *
     * @param stageSeeds seeds of the four stages of the legal best
     * @param keep the fraction kept after each of the first two stages
     * @param league size of the league of the third stage
     * @param finalists how many reach the round robin
     * @param population size of a population of the cross-entropy
     * @param trainingSeeds seeds that score one member
     * @param elite members that update the distribution
     * @param generations generations
     * @param survivors finalists measured again
     * @param finalSeeds seeds of that measure
     * @param sample configurations drawn for the first stage of the legal best, 0 for the whole space
     */
    record Sizes(int[] stageSeeds, double keep, int league, int finalists, int population, int trainingSeeds, int elite,
                 int generations, int survivors, int finalSeeds, int sample) {
        /** The sizes of §7.3, the whole space. */
        static final Sizes FULL = new Sizes(new int[] {10, 40, 160, 400}, 1.0 / 8, 8, 10, 256, 20, 32, 5, 5, 200, 0);
    }

    /**
     * A scored configuration of the space.
     *
     * @param index its number
     * @param score its score: against the league, or ThePredict's against it
     */
    record Scored(int index, double score) {
    }

    /**
     * One stage or generation of a search, for the report.
     *
     * @param stage name
     * @param candidates configurations played
     * @param rounds rounds played
     * @param best the best ones, best first
     */
    record Step(String stage, int candidates, long rounds, List<Scored> best) {
    }

    /**
     * The winner of a search.
     *
     * @param winner the configuration
     * @param index its number in the space
     * @param score its last score
     * @param steps how the search went
     */
    record Found(ClassicConfig winner, int index, double score, List<Step> steps) {
    }

    /**
     * The best classic of the legal space against a league of classics.
     *
     * @param space the space of a kit
     * @param scenario the scenario of the search
     * @param runner the runner, for its pool, physics and rules
     * @param equal the classic {@code legal-equal} of the kit
     * @param sizes how large
     * @return the legal best
     */
    static Found legalBest(ClassicSpace space, Scenario scenario, BenchRunner runner, ClassicConfig equal,
                           Sizes sizes) {
        var steps = new ArrayList<Step>();
        var pack = ClassicConfig.pack(space.kit());
        var references = List.of(pack, equal);
        var alive = start(space, sizes.sample());
        var seed = LEGAL_SEED;
        for (var stage = 0; stage < 2; stage++) {
            var seeds = sizes.stageSeeds()[stage];
            var scored = league(space, scenario, runner, alive, references, seed, seeds);
            steps.add(new Step("etapa " + (stage + 1), alive.size(), (long) alive.size() * seeds * 2
                    * references.size(), top(scored, 10)));
            alive = indexes(scored, Math.max(sizes.league(), (int) Math.ceil(alive.size() * sizes.keep())));
            seed += seeds;
        }
        var leaders = new ArrayList<ClassicConfig>();
        for (var index = 0; index < sizes.league() - 1 && index < alive.size(); index++) {
            leaders.add(space.config(alive.get(index)));
        }
        leaders.add(pack);
        var third = sizes.stageSeeds()[2];
        var scored = league(space, scenario, runner, alive, leaders, seed, third);
        steps.add(new Step("etapa 3", alive.size(), (long) alive.size() * third * 2 * leaders.size(), top(scored, 10)));
        seed += third;
        var finalists = indexes(scored, sizes.finalists());
        return roundRobin(space, scenario, runner, finalists, seed, sizes.stageSeeds()[3], steps);
    }

    /** The whole space, or a sample of distinct configurations drawn from {@link StableRandom}. */
    private static List<Integer> start(ClassicSpace space, int sample) {
        var alive = new ArrayList<Integer>();
        if (sample <= 0 || sample >= space.size()) {
            for (var index = 0; index < space.size(); index++) {
                alive.add(index);
            }
            return alive;
        }
        var taken = new java.util.HashSet<Integer>();
        for (var draw = 0L; alive.size() < sample; draw++) {
            var index = StableRandom.below(StableRandom.draw(SALT_SAMPLE, draw, SALT_SAMPLE + 1), space.size());
            if (taken.add(index)) {
                alive.add(index);
            }
        }
        return alive;
    }

    private static List<Scored> league(ClassicSpace space, Scenario scenario, BenchRunner runner,
                                       List<Integer> candidates, List<ClassicConfig> opponents, long seed, int seeds) {
        var scores = runner.pool().map(candidates.size(), job -> {
            var index = candidates.get(job);
            var config = space.config(index);
            var total = 0.0;
            var games = 0;
            for (var opponent : opponents) {
                if (opponent.code().equals(config.code())) {
                    continue;
                }
                total += versus(scenario, runner, config, opponent, seed, seeds);
                games++;
            }
            return new Scored(index, games == 0 ? 0.5 : total / games);
        });
        return sorted(scores, false);
    }

    /**
     * The mean score of one classic against another over seeds and both sides.
     *
     * @return the score of {@code first}
     */
    static double versus(Scenario scenario, BenchRunner runner, ClassicConfig first, ClassicConfig second, long seed,
                         int seeds) {
        var setup = new DuelSimulator.Setup(runner.physics(), runner.rules(), scenario, first, second);
        var swapped = setup.classics(second, first);
        var total = 0.0;
        for (var at = 0; at < seeds; at++) {
            total += ClassicMatch.play(setup, seed + at, false).redScore();
            total += 1 - ClassicMatch.play(swapped, seed + at, false).redScore();
        }
        return total / (2.0 * seeds);
    }

    private static Found roundRobin(ClassicSpace space, Scenario scenario, BenchRunner runner, List<Integer> finalists,
                                    long seed, int seeds, List<Step> steps) {
        var count = finalists.size();
        var pairs = new ArrayList<int[]>();
        for (var first = 0; first < count; first++) {
            for (var second = first + 1; second < count; second++) {
                pairs.add(new int[] {first, second});
            }
        }
        var results = runner.pool().map(pairs.size(), job -> versus(scenario, runner,
                space.config(finalists.get(pairs.get(job)[0])), space.config(finalists.get(pairs.get(job)[1])), seed,
                seeds));
        var wins = new int[count][count];
        for (var job = 0; job < pairs.size(); job++) {
            var points = (int) Math.round(results.get(job) * 4 * seeds);
            wins[pairs.get(job)[0]][pairs.get(job)[1]] = points;
            wins[pairs.get(job)[1]][pairs.get(job)[0]] = 4 * seeds - points;
        }
        var strength = BradleyTerry.strengths(wins, 200);
        var ranked = new ArrayList<Scored>();
        for (var at = 0; at < count; at++) {
            ranked.add(new Scored(finalists.get(at), strength[at]));
        }
        ranked = new ArrayList<>(sorted(ranked, false));
        steps.add(new Step("etapa 4 (todos contra todos, Bradley-Terry)", count, (long) pairs.size() * seeds * 2,
                ranked));
        var best = ranked.getFirst();
        return new Found(space.config(best.index()).named("legal-best"), best.index(), best.score(), steps);
    }

    /**
     * The classic of the legal space that lowers ThePredict's score the most.
     *
     * @param space the space of a kit
     * @param spec the cell the search plays: scenario, ThePredict configuration and mode
     * @param runner the runner
     * @param starts configurations put into the first population
     * @param sizes how large
     * @return the adversarial classic
     */
    static Found adversarial(ClassicSpace space, BenchCell.Spec spec, BenchRunner runner, List<ClassicConfig> starts,
                             Sizes sizes) {
        var radix = space.radix();
        var probability = uniform(radix);
        var seen = new java.util.HashMap<Integer, Double>();
        var steps = new ArrayList<Step>();
        for (var generation = 0; generation < sizes.generations(); generation++) {
            var population = population(space, probability, generation, sizes.population(),
                    generation == 0 ? starts : List.of());
            var fresh = population.stream().filter(index -> !seen.containsKey(index)).distinct().toList();
            var scores = runner.pool().map(fresh.size(), job -> runner.score(rival(space, spec, fresh.get(job)),
                    ADVERSARIAL_SEED, sizes.trainingSeeds()));
            for (var job = 0; job < fresh.size(); job++) {
                seen.put(fresh.get(job), scores.get(job));
            }
            var ranked = sorted(population.stream().distinct().map(index -> new Scored(index, seen.get(index)))
                    .toList(), true);
            steps.add(new Step("generación " + (generation + 1), population.size(), (long) fresh.size()
                    * sizes.trainingSeeds() * 2, top(ranked, 10)));
            probability = update(probability, ranked.subList(0, Math.min(sizes.elite(), ranked.size())), space);
        }
        return finals(space, spec, runner, seen, sizes, steps);
    }

    private static BenchCell.Spec rival(ClassicSpace space, BenchCell.Spec spec, int index) {
        return new BenchCell.Spec("cfg-" + index, space.config(index), spec.scenario(), spec.timing(), spec.mode(),
                spec.judge());
    }

    private static Found finals(ClassicSpace space, BenchCell.Spec spec, BenchRunner runner,
                                java.util.Map<Integer, Double> seen, Sizes sizes, List<Step> steps) {
        var all = new ArrayList<Scored>();
        seen.forEach((index, score) -> all.add(new Scored(index, score)));
        var finalists = sorted(all, true).subList(0, Math.min(sizes.survivors(), all.size()));
        var scores = runner.pool().map(finalists.size(), job -> runner.score(rival(space, spec,
                finalists.get(job).index()), FINAL_SEED, sizes.finalSeeds()));
        var measured = new ArrayList<Scored>();
        for (var job = 0; job < finalists.size(); job++) {
            measured.add(new Scored(finalists.get(job).index(), scores.get(job)));
        }
        var ranked = sorted(measured, true);
        steps.add(new Step("finalistas en semillas nuevas", finalists.size(), (long) finalists.size()
                * sizes.finalSeeds() * 2, ranked));
        var best = ranked.getFirst();
        return new Found(space.config(best.index()).named("adversarial"), best.index(), best.score(), steps);
    }

    private static double[][] uniform(int[] radix) {
        var probability = new double[radix.length][];
        for (var dimension = 0; dimension < radix.length; dimension++) {
            probability[dimension] = new double[radix[dimension]];
            java.util.Arrays.fill(probability[dimension], 1.0 / radix[dimension]);
        }
        return probability;
    }

    private static List<Integer> population(ClassicSpace space, double[][] probability, int generation, int size,
                                            List<ClassicConfig> starts) {
        var members = new ArrayList<Integer>(size);
        for (var start : starts) {
            space.indexOf(start).ifPresent(members::add);
        }
        for (var member = members.size(); member < size; member++) {
            var digits = new int[probability.length];
            for (var dimension = 0; dimension < probability.length; dimension++) {
                var draw = Scenario.unit(StableRandom.draw(StableRandom.seed(SALT_SAMPLE, generation, member),
                        dimension, SALT_SAMPLE));
                digits[dimension] = pick(probability[dimension], draw);
            }
            members.add(space.index(digits));
        }
        return members;
    }

    private static int pick(double[] weights, double draw) {
        var cumulative = 0.0;
        for (var value = 0; value < weights.length; value++) {
            cumulative += weights[value];
            if (draw < cumulative) {
                return value;
            }
        }
        return weights.length - 1;
    }

    private static double[][] update(double[][] probability, List<Scored> elite, ClassicSpace space) {
        var next = new double[probability.length][];
        for (var dimension = 0; dimension < probability.length; dimension++) {
            var counts = new double[probability[dimension].length];
            for (var member : elite) {
                counts[space.digits(member.index())[dimension]]++;
            }
            next[dimension] = new double[counts.length];
            var total = 0.0;
            for (var value = 0; value < counts.length; value++) {
                var mixed = 0.3 * probability[dimension][value] + 0.7 * counts[value] / elite.size();
                next[dimension][value] = Math.max(0.01, mixed);
                total += next[dimension][value];
            }
            for (var value = 0; value < counts.length; value++) {
                next[dimension][value] /= total;
            }
        }
        return next;
    }

    private static List<Scored> sorted(List<Scored> scored, boolean lowestFirst) {
        var copy = new ArrayList<>(scored);
        Comparator<Scored> order = Comparator.comparingDouble(Scored::score);
        if (!lowestFirst) {
            order = order.reversed();
        }
        copy.sort(order.thenComparingInt(Scored::index));
        return List.copyOf(copy);
    }

    private static List<Integer> indexes(List<Scored> scored, int count) {
        var out = new ArrayList<Integer>();
        for (var at = 0; at < Math.min(count, scored.size()); at++) {
            out.add(scored.get(at).index());
        }
        return out;
    }

    private static List<Scored> top(List<Scored> scored, int count) {
        return List.copyOf(scored.subList(0, Math.min(count, scored.size())));
    }

    /** One line of a step for the report. */
    static String describe(Step step) {
        var out = new StringBuilder(String.format(Locale.ROOT, "%s: %d configuraciones, %d rondas; mejores:",
                step.stage(), step.candidates(), step.rounds()));
        for (var scored : step.best().subList(0, Math.min(3, step.best().size()))) {
            out.append(String.format(Locale.ROOT, " cfg-%d %.3f", scored.index(), scored.score()));
        }
        return out.toString();
    }
}
