package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.StableRandom;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One duel setting of the effectiveness bench (§7.4 of the simulator audit): the kit both sides carry, their health,
 * the arena and how a seed places them. {@link #LEGACY} is the round of M18.7, whose spawn points stay fixed with one
 * block of jitter from {@code java.util.Random} so the golden files keep their bytes. Every other scenario draws from
 * {@link StableRandom} only (rule C16, SIM-20): the spawn angle in {@code [0, 2π)}, the distance in
 * {@code [min, max]} cut to the arena and symmetric around the centre (SIM-18), the global phase of the MythicMobs
 * timers and the phase of the engine clock, each within {@code [0, 20)}.
 *
 * @param id name in the report
 * @param kit equipment of both sides
 * @param maxHealth health of both sides
 * @param arena the floor
 * @param spawn how far apart the duelists start
 * @param maxTicks last tick of a round, a draw when reached: the 90 s of {@code DuelTimeout} for 60 health, and
 *     as much more as the health is larger, so a fight of a boss does not end in a draw only for its length
 */
record Scenario(String id, Kit kit, double maxHealth, Arena arena, SpawnPlan spawn, int maxTicks) {
    /** Draws of a scenario: angle, distance, timer phase and engine phase. */
    static final int SALT_ANGLE = 101;
    static final int SALT_DISTANCE = 102;
    static final int SALT_TIMER_PHASE = 103;
    static final int SALT_ENGINE_PHASE = 104;
    static final int SALT_GOAL_PHASE = 105;
    /** Phases cover the least common multiple of the timers of the legal space. */
    static final int PHASES = 20;
    private static final int RETRIES = 32;

    /**
     * Distances between the two spawn points.
     *
     * @param minDistance shortest
     * @param maxDistance longest, cut to the arena
     */
    record SpawnPlan(double minDistance, double maxDistance) {
        /** The fixed points of M18.7. */
        static final SpawnPlan LEGACY = new SpawnPlan(0, 0);
        /** The plan of §7.4. */
        static final SpawnPlan BENCH = new SpawnPlan(8, 24);
    }

    /** The round of M18.7: the duel pack, 60 health, the open arena and the fixed spawn points. */
    static final Scenario LEGACY = new Scenario("legacy", Kit.K1, DuelWorld.MAX_HEALTH, Arena.OPEN18,
            SpawnPlan.LEGACY, DuelSimulator.MAX_TICKS);

    /**
     * Validates the scenario.
     *
     * @throws IllegalArgumentException when the health or the length is not positive
     */
    Scenario {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(spawn, "spawn");
        if (!(maxHealth > 0) || maxTicks < 1) {
            throw new IllegalArgumentException("scenario out of range: " + id);
        }
    }

    /**
     * A bench scenario with the spawn plan of §7.4 and the timeout of the duel pack.
     *
     * @param kit equipment of both sides
     * @param arena the floor
     * @param health health of both sides
     * @return the scenario, named after its parts
     */
    static Scenario bench(Kit kit, Arena arena, double health) {
        return new Scenario(String.format(Locale.ROOT, "%s-%s-%.0f", kit.id(), arena.id(), health), kit, health,
                arena, SpawnPlan.BENCH, (int) Math.round(DuelSimulator.MAX_TICKS * health / DuelWorld.MAX_HEALTH));
    }

    /** The seven scenarios with skills and the vanilla one of the gate set of §7.4, in report order. */
    static List<Scenario> gateSet() {
        return List.of(bench(Kit.K1, Arena.OPEN18, 60), bench(Kit.K1, Arena.SMALL8, 60),
                bench(Kit.K1, Arena.PILLARS18, 60), bench(Kit.K1, Arena.OPEN18, 200), bench(Kit.K2, Arena.OPEN18, 60),
                bench(Kit.K3, Arena.OPEN18, 60), bench(Kit.K4, Arena.OPEN18, 60), bench(Kit.K0, Arena.OPEN18, 60));
    }

    /** Whether this is the round of M18.7. */
    boolean legacy() {
        return spawn.equals(SpawnPlan.LEGACY);
    }

    /**
     * The two spawn points of a seed, symmetric around the centre and free of pillars.
     *
     * @param seed the seed of the round
     * @return red x, red z, blue x and blue z, before any swap of sides
     */
    double[] spawnPoints(long seed) {
        var longest = Math.min(spawn.maxDistance(), 2 * (arena.radius() - 1));
        var shortest = Math.min(spawn.minDistance(), longest);
        for (var attempt = 0; attempt < RETRIES; attempt++) {
            var angle = 2 * Math.PI * unit(StableRandom.draw(seed, attempt, SALT_ANGLE));
            var half = (shortest + (longest - shortest) * unit(StableRandom.draw(seed, attempt, SALT_DISTANCE))) / 2;
            var x = Math.cos(angle) * half;
            var z = Math.sin(angle) * half;
            if (arena.free(x, z) && arena.free(-x, -z)) {
                return new double[] {x, z, -x, -z};
            }
        }
        throw new IllegalStateException("no free spawn points in " + id + " for seed " + seed);
    }

    /**
     * The global phase of the MythicMobs timers of a seed, shared by both sides (SIM-10).
     *
     * @param seed the seed of the round
     * @return a phase within {@code [0, 20)}
     */
    static int timerPhase(long seed) {
        return StableRandom.below(StableRandom.draw(seed, 0, SALT_TIMER_PHASE), PHASES);
    }

    /**
     * The phase of the engine clock of a seed: the tick of the engine at the first tick of the round.
     *
     * @param seed the seed of the round
     * @return a phase within {@code [0, 20)}
     */
    static int enginePhase(long seed) {
        return StableRandom.below(StableRandom.draw(seed, 0, SALT_ENGINE_PHASE), PHASES);
    }

    /**
     * The parity of the goal selection of one body of a seed, as {@code tickCount + id} of its entity.
     *
     * @param seed the seed of the round
     * @param side the side of the body
     * @return 0 or 1
     */
    static int goalPhase(long seed, DuelWorld.Side side) {
        return (int) (StableRandom.draw(seed, side.ordinal(), SALT_GOAL_PHASE) & 1L);
    }

    /**
     * A uniform number in {@code [0, 1)} from a word of {@link StableRandom}.
     *
     * @param word the word
     * @return 53 bits of it as a fraction
     */
    static double unit(long word) {
        return (word >>> 11) * 0x1.0p-53;
    }
}
