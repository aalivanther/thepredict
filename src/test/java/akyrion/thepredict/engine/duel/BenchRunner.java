package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.ArrayList;
import java.util.Map;

/**
 * Plays the rounds of the effectiveness bench (§7.4, §7.6 of the simulator audit). Every round gets its own simulator,
 * engine and world, so a round depends only on its cell and its seed: arm A and arm B of a seed meet the same spawn
 * points, phases and draws, and no state passes from one round to the next. A ThePredict duelist plays the pack
 * classic of its kit at level 4, the base AI of its file, never the rival's classic.
 */
final class BenchRunner {
    /** First seed of the measured cells, far from the search seeds (1 to 10 000). */
    static final long EVALUATION_SEED = 1_000_001L;
    /** First seed of the mirrors of the validity gates. */
    static final long GATE_SEED = 2_000_001L;
    /**
     * First seed of the development runs (fase de efectividad, 2026-10-16): a change of the engine is tuned on these
     * cells, never on the evaluation seeds that give the verdict, nor on the search or gate seeds.
     */
    static final long DEVELOPMENT_SEED = 3_000_001L;
    /** Last seed the searches of the rivals may use. */
    static final long LAST_SEARCH_SEED = 10_000L;
    private static final int SALT_JUDGE = 113;

    private final BenchPool pool;
    private final DuelPhysics physics;
    private final TickRules rules;
    private final Map<BenchProfiles.Timing, Map<Kit, Map<String, TacticalProfile>>> profiles;

    BenchRunner(BenchPool pool, DuelPhysics physics, TickRules rules,
                Map<BenchProfiles.Timing, Map<Kit, Map<String, TacticalProfile>>> profiles) {
        this.pool = pool;
        this.physics = physics;
        this.rules = rules;
        this.profiles = profiles;
    }

    BenchPool pool() {
        return pool;
    }

    DuelPhysics physics() {
        return physics;
    }

    TickRules rules() {
        return rules;
    }

    /** Every ThePredict profile of the run. */
    Map<BenchProfiles.Timing, Map<Kit, Map<String, TacticalProfile>>> profiles() {
        return profiles;
    }

    /** The ThePredict profiles of a kit and a timing. */
    Map<String, TacticalProfile> profiles(BenchProfiles.Timing timing, Kit kit) {
        return profiles.get(timing).get(kit);
    }

    /**
     * Plays one cell on {@code seeds} seeds from {@code firstSeed}, both arms of each, and the rival's mirror.
     *
     * @param spec the cell
     * @param firstSeed the first seed
     * @param seeds how many seeds
     * @return the cell
     */
    BenchCell.Result cell(BenchCell.Spec spec, long firstSeed, int seeds) {
        var duels = pool.map(seeds * 2, job -> duel(spec, firstSeed + job / 2, job % 2 == 0));
        var a = new ArrayList<BenchCell.Duel>(seeds);
        var b = new ArrayList<BenchCell.Duel>(seeds);
        for (var index = 0; index < duels.size(); index++) {
            (index % 2 == 0 ? a : b).add(duels.get(index));
        }
        var mirror = new DuelSimulator.Setup(physics, rules, spec.scenario(), spec.config(), spec.config());
        var ticks = pool.map(seeds, index -> ClassicMatch.play(mirror, firstSeed + index, false));
        var mirrorTicks = new int[seeds];
        for (var index = 0; index < seeds; index++) {
            var length = ticks.get(index).ticks();
            mirrorTicks[index] = length < spec.scenario().maxTicks() ? length : 0;
        }
        return new BenchCell.Result(spec, a, b, mirrorTicks);
    }

    /**
     * One round of ThePredict against the rival of a cell.
     *
     * @param spec the cell
     * @param seed the seed
     * @param red whether ThePredict is red (arm A) or blue (arm B)
     * @return the round seen from ThePredict
     */
    BenchCell.Duel duel(BenchCell.Spec spec, long seed, boolean red) {
        var kit = spec.scenario().kit();
        var base = ClassicConfig.pack(kit);
        var setup = new DuelSimulator.Setup(physics, rules, spec.scenario(), red ? base : spec.config(),
                red ? spec.config() : base);
        DuelSimulator.Mode mode = spec.judge().<DuelSimulator.Mode>map(judge -> new DuelSimulator.Mode.Judge(
                judge.pBest(), judge.latency(), StableRandom.seed(judge.seed(), seed, SALT_JUDGE)))
                .orElse(new DuelSimulator.Mode.None());
        var result = new DuelSimulator(mode, profiles(spec.timing(), kit), setup).round(red ? DuelSimulator.Arm.A
                : DuelSimulator.Arm.B, seed, false, DuelSimulator.Options.PLAIN);
        return duel(result, seed, red);
    }

    static BenchCell.Duel duel(DuelResult result, long seed, boolean red) {
        var mine = red ? DuelResult.Winner.RED : DuelResult.Winner.BLUE;
        var outcome = result.winner() == mine ? BenchCell.Outcome.WIN : result.winner() == DuelResult.Winner.DRAW
                ? BenchCell.Outcome.DRAW : BenchCell.Outcome.LOSS;
        return new BenchCell.Duel(seed, red, outcome, result.ticks(), red ? result.redDealt() : result.blueDealt(),
                red ? result.blueDealt() : result.redDealt(), red ? result.redLostHits() : result.blueLostHits(),
                red ? result.blueLostHits() : result.redLostHits(),
                result.levelTicks().getOrDefault(ControlLevel.BASE, 0), result.retreats(), result.requests(),
                red ? result.redCasts() : result.blueCasts(), red ? result.blueCasts() : result.redCasts(),
                red ? result.redHits() : result.blueHits(), red ? result.blueHits() : result.redHits(),
                red ? result.redConduct() : result.blueConduct());
    }

    /**
     * The mean score of ThePredict against a classic on seeds, both arms of each: the objective of the adversarial
     * search.
     *
     * @param spec the cell
     * @param firstSeed the first seed
     * @param seeds how many seeds
     * @return the mean score
     */
    double score(BenchCell.Spec spec, long firstSeed, int seeds) {
        var total = 0.0;
        for (var index = 0; index < seeds; index++) {
            total += duel(spec, firstSeed + index, true).score() + duel(spec, firstSeed + index, false).score();
        }
        return total / (2.0 * seeds);
    }
}
