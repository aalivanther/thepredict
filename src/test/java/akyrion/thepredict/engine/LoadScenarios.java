package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.IntConsumer;

import static akyrion.thepredict.engine.LoadFixture.WORLD_A;
import static akyrion.thepredict.engine.LoadFixture.WORLD_B;

/**
 * Scenarios of the M13 load suite, shared by the tests and by the local measurements: the steps of M13.3, scattered
 * mobs for the fragmentation and the cost of {@code assign}, and encounters with a simulated capture cost for the
 * work budget. Every scenario is deterministic: positions come from the mob index or from a seeded generator.
 */
final class LoadScenarios {
    /** Ten minutes of combat at 20 ticks per second. */
    static final int TEN_MINUTES_TICKS = 12_000;
    /** One minute at 20 ticks per second. */
    static final int MINUTE_TICKS = 1_200;
    /** Farthest a scattered mob stands from the player it was placed around: the follow range of the example. */
    static final double TARGET_RADIUS_BLOCKS = 32;

    private LoadScenarios() {
    }

    /**
     * A step of M13.3: bound mobs split between two worlds, two players per world, every mob with a target from the
     * start. Jev answers each {@code NOW} question with the first option and each contingency with the second.
     *
     * @param mobCount mobs of the step
     * @return the fixture, ready and with every session opened
     */
    static LoadFixture combat(int mobCount) {
        var fixture = arena(new LoadFixture(), mobCount);
        fixture.ready();
        return fixture;
    }

    /**
     * The same step without a key: the service stays {@code UNCONFIGURED}, so every session plays in {@code LOCAL} and
     * the real-time readiness of M10 runs on each reactive cycle while a skill cools down.
     *
     * @param mobCount mobs of the step
     * @return the fixture with every session opened
     */
    static LoadFixture combatWithoutKey(int mobCount) {
        return arena(new LoadFixture(CoordinatorFixture.settings(2000), ServiceState.UNCONFIGURED), mobCount);
    }

    private static LoadFixture arena(LoadFixture fixture, int mobCount) {
        fixture.player(1, WORLD_A, 0, 0);
        fixture.player(2, WORLD_A, 6, 0);
        fixture.player(3, WORLD_B, 0, 0);
        fixture.player(4, WORLD_B, 6, 0);
        fixture.jev.policy = question -> ExecutionFixture.prefer(question.situation() == Situation.NOW
                || question.options().size() < 2 ? question.options().getFirst() : question.options().get(1), 0.7, 0.8);
        for (var index = 1; index <= mobCount; index++) {
            var world = worldOf(index);
            var position = position(index, false);
            fixture.spawn(index, world, position[0], position[1], firstPlayer(world) + (index / 2) % 2);
        }
        return fixture;
    }

    /**
     * Script of a step: each mob calls {@code predictordecide} once a second, as the {@code ~onTimer:20} mechanic of
     * the verification pack does; every 2 s each mob alternates between melee reach and its ranged position; every 10 s
     * a fifth of the mobs switch to the other player of their world.
     */
    static IntConsumer combatScript(LoadFixture fixture, int mobCount) {
        return tick -> {
            for (var index = 1; index <= mobCount; index++) {
                if ((tick + index) % 20 == 0) {
                    fixture.signals.offer(LoadFixture.mob(index));
                }
                if ((tick + index) % 40 == 0) {
                    var position = position(index, (tick / 40 + index) % 2 == 0);
                    fixture.move(index, position[0], position[1]);
                }
                if (tick % 200 == 0 && tick > 0 && (index + tick / 200) % 5 == 0) {
                    var world = worldOf(index);
                    var current = fixture.arena.targets.get(LoadFixture.mob(index));
                    var first = firstPlayer(world);
                    fixture.target(index, current.equals(LoadFixture.player(first)) ? first + 1 : first);
                }
            }
        };
    }

    /**
     * One mob per encounter, {@code encounters} in all, split between two worlds: the mobs stand 100 blocks apart, out
     * of each other's follow range, and target the one player of their world.
     *
     * @return the fixture, ready and with every session opened
     */
    static LoadFixture separateEncounters(int encounters, EngineSettings settings) {
        return separateEncounters(encounters, 2, settings);
    }

    /** Like {@link #separateEncounters(int, EngineSettings)} over two or three worlds, one player in each. */
    static LoadFixture separateEncounters(int encounters, int worlds, EngineSettings settings) {
        var fixture = new LoadFixture(settings);
        var all = List.of(WORLD_A, WORLD_B, LoadFixture.WORLD_C).subList(0, worlds);
        for (var world = 0; world < worlds; world++) {
            fixture.player(world + 1, all.get(world), 0, 0);
        }
        for (var index = 1; index <= encounters; index++) {
            var world = (index - 1) % worlds;
            fixture.spawn(index, all.get(world), 100.0 * index, 0, world + 1);
        }
        fixture.ready();
        return fixture;
    }

    /**
     * Players scattered over a square of {@code sideBlocks} in one world, and mobs scattered around them: each mob
     * stands at most {@link #TARGET_RADIUS_BLOCKS} from a random player and targets its nearest player, as a mob only
     * targets a player it perceives.
     *
     * @return the fixture, ready and with every session opened
     */
    static LoadFixture scattered(int mobCount, int playerCount, double sideBlocks, long seed) {
        var fixture = new LoadFixture();
        var random = new Random(seed);
        for (var player = 1; player <= playerCount; player++) {
            fixture.player(player, WORLD_A, random.nextDouble() * sideBlocks, random.nextDouble() * sideBlocks);
        }
        for (var index = 1; index <= mobCount; index++) {
            var around = fixture.arena.players.get(LoadFixture.player(1 + random.nextInt(playerCount)));
            var angle = random.nextDouble() * 2 * Math.PI;
            var radius = TARGET_RADIUS_BLOCKS * Math.sqrt(random.nextDouble());
            var x = around.x() + radius * Math.cos(angle);
            var z = around.z() + radius * Math.sin(angle);
            fixture.spawn(index, WORLD_A, x, z, nearest(fixture, x, z, playerCount));
        }
        fixture.ready();
        return fixture;
    }

    /**
     * The fewest encounters the rules of M06.3 allow: for each target, the groups of mobs linked by the follow range,
     * each split into encounters of at most {@link EncounterRegistry#MAX_MEMBERS} members.
     */
    static int minimumEncounters(LoadFixture fixture) {
        var byTarget = new HashMap<UUID, List<UUID>>();
        fixture.arena.targets.forEach((mob, player) -> byTarget.computeIfAbsent(player, key -> new ArrayList<>())
                .add(mob));
        var minimum = 0;
        for (var group : byTarget.values()) {
            for (var size : components(fixture, group)) {
                minimum += (size + EncounterRegistry.MAX_MEMBERS - 1) / EncounterRegistry.MAX_MEMBERS;
            }
        }
        return minimum;
    }

    private static List<Integer> components(LoadFixture fixture, List<UUID> mobs) {
        var parent = new HashMap<UUID, UUID>();
        mobs.forEach(mob -> parent.put(mob, mob));
        for (var first = 0; first < mobs.size(); first++) {
            for (var second = first + 1; second < mobs.size(); second++) {
                var a = fixture.arena.mobs.get(mobs.get(first));
                var b = fixture.arena.mobs.get(mobs.get(second));
                if (a.world().equals(b.world()) && a.distance(b) <= LoadFixture.FOLLOW_RANGE_BLOCKS) {
                    parent.put(root(parent, mobs.get(first)), root(parent, mobs.get(second)));
                }
            }
        }
        var sizes = new HashMap<UUID, Integer>();
        mobs.forEach(mob -> sizes.merge(root(parent, mob), 1, Integer::sum));
        return List.copyOf(sizes.values());
    }

    private static UUID root(Map<UUID, UUID> parent, UUID node) {
        var current = node;
        while (!parent.get(current).equals(current)) {
            current = parent.get(current);
        }
        return current;
    }

    private static int nearest(LoadFixture fixture, double x, double z, int playerCount) {
        var best = 1;
        var bestDistance = Double.MAX_VALUE;
        for (var player = 1; player <= playerCount; player++) {
            var body = fixture.arena.players.get(LoadFixture.player(player));
            var distance = Math.hypot(body.x() - x, body.z() - z);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static UUID worldOf(int index) {
        return index % 2 == 1 ? WORLD_A : WORLD_B;
    }

    private static int firstPlayer(UUID world) {
        return world.equals(WORLD_A) ? 1 : 3;
    }

    private static double[] position(int index, boolean inMelee) {
        var radius = inMelee ? 2.0 : 3.0 + index % 10;
        var angle = index * 0.7;
        return new double[] {radius * Math.cos(angle), radius * Math.sin(angle)};
    }
}
