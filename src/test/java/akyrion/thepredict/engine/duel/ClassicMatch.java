package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.StableRandom;

import java.util.UUID;

/**
 * A fair round between two classics without the engine, for the searches of §7.3 that play millions of rounds. It is
 * the arm D of {@link FairRound} step for step: the same spawn points, phases, decisions, movement, commit order and
 * landing; {@code ClassicMatchTest} checks that both give the same winner and length. Pure: the outcome depends only on
 * the setup, the seed and the side swap.
 */
final class ClassicMatch {
    private static final UUID RED = new UUID(0x0D0E3L, 0);
    private static final UUID BLUE = new UUID(0x0D0E3L, 1);

    private ClassicMatch() {
    }

    /**
     * The end of one round.
     *
     * @param winner who won
     * @param ticks how long it lasted
     */
    record Outcome(DuelResult.Winner winner, int ticks) {
        /** The score of red: 1, a draw 0.5, a loss 0. */
        double redScore() {
            return winner == DuelResult.Winner.RED ? 1.0 : winner == DuelResult.Winner.BLUE ? 0.0 : 0.5;
        }
    }

    /**
     * Plays one round.
     *
     * @param setup scenario, physics, rules and the classic of each side
     * @param seed the seed of the round
     * @param swapped whether red starts on the second spawn point
     * @return the outcome
     */
    static Outcome play(DuelSimulator.Setup setup, long seed, boolean swapped) {
        var scenario = setup.scenario();
        var world = world(setup, seed, swapped);
        var phase = Scenario.timerPhase(seed);
        var redDuelist = new ClassicDuelist(world, world.red, setup.red(), phase);
        var blueDuelist = new ClassicDuelist(world, world.blue, setup.blue(), phase);
        var start = 1L + Scenario.enginePhase(seed);
        for (var index = 0; index < scenario.maxTicks(); index++) {
            var tick = start + index;
            redDuelist.decide(index, tick, (body, skill) -> {
            });
            blueDuelist.decide(index, tick, (body, skill) -> {
            });
            remember(world.red);
            remember(world.blue);
            Motion.advance(world);
            world.commit(tick, (StableRandom.draw(seed, index, FairRound.SALT_ORDER) & 1L) == 0L);
            world.step(tick);
            if (world.over()) {
                return new Outcome(world.winner().map(side -> side == DuelWorld.Side.RED ? DuelResult.Winner.RED
                        : DuelResult.Winner.BLUE).orElse(DuelResult.Winner.DRAW), index + 1);
            }
        }
        return new Outcome(DuelResult.Winner.DRAW, scenario.maxTicks());
    }

    /** The world of a round: the spawn points, the goal parities and the headings of the fair round. */
    private static DuelWorld world(DuelSimulator.Setup setup, long seed, boolean swapped) {
        var scenario = setup.scenario();
        var points = scenario.spawnPoints(seed);
        var at = swapped ? 2 : 0;
        var red = new DuelWorld.Body(RED, DuelWorld.Side.RED, points[at], points[at + 1], scenario.maxHealth());
        var blue = new DuelWorld.Body(BLUE, DuelWorld.Side.BLUE, points[2 - at], points[3 - at], scenario.maxHealth());
        var world = new DuelWorld(red, blue, hit -> {
        }, setup.physics(), setup.rules(), scenario.arena(), scenario.kit().vanillaDamage());
        red.goalPhase = Scenario.goalPhase(seed, DuelWorld.Side.RED);
        blue.goalPhase = Scenario.goalPhase(seed, DuelWorld.Side.BLUE);
        Motion.face(world, red);
        Motion.face(world, blue);
        return world;
    }

    private static void remember(DuelWorld.Body body) {
        body.previousX = body.x;
        body.previousZ = body.z;
    }
}
