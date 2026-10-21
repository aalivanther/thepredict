package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.eval.EvalStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The validity gates of the effectiveness bench (§7.1 of the simulator audit). They run before any cell is read; if
 * one fails, the report gives no verdict. V1 and V2 are mirrors that must sit at one half, V3 asks that the side does
 * not matter, V4 that two lethal hits of one tick are a draw, V5 that both sides share the stats and that every speed
 * modifier of ThePredict is within the classic's reach, V6 that a ThePredict duelist forced to level 4 plays exactly as
 * the classic of its side.
 */
final class BenchGates {
    /** Largest distance of a mirror from one half. */
    static final double MIRROR_TOLERANCE = 0.05;
    /** Smallest p-value of the side test. */
    static final double SIDE_LEVEL = 0.05;

    private BenchGates() {
    }

    /**
     * One gate.
     *
     * @param id {@code V1} to {@code V6}
     * @param what what it checks
     * @param timing the ThePredict configuration it concerns, empty for all
     * @param threshold how it passes
     * @param measured what came out
     * @param passed whether it passed
     */
    record Gate(String id, String what, Optional<BenchProfiles.Timing> timing, String threshold, String measured,
                boolean passed) {
    }

    /** V1: the classic against itself, on the gate seeds, each seed from both spawn points. */
    static Gate classicMirror(BenchRunner runner, Scenario scenario, ClassicConfig config, int seeds) {
        var setup = new DuelSimulator.Setup(runner.physics(), runner.rules(), scenario, config, config);
        var outcomes = runner.pool().map(seeds * 2, job -> ClassicMatch.play(setup, BenchRunner.GATE_SEED + job / 2,
                job % 2 == 1).redScore());
        return mirror("V1", "espejo clásico (" + config.id() + ", " + scenario.id() + ")", Optional.empty(), outcomes);
    }

    /** V2: ThePredict against itself (arm C), each seed from both spawn points. */
    static Gate thePredictMirror(BenchRunner runner, Scenario scenario, BenchProfiles.Timing timing, int seeds) {
        var base = ClassicConfig.pack(scenario.kit());
        var setup = new DuelSimulator.Setup(runner.physics(), runner.rules(), scenario, base, base);
        var profiles = runner.profiles(timing, scenario.kit());
        var outcomes = runner.pool().map(seeds * 2, job -> new DuelSimulator(new DuelSimulator.Mode.None(), profiles,
                setup).round(DuelSimulator.Arm.C, BenchRunner.GATE_SEED + job / 2, job % 2 == 1,
                DuelSimulator.Options.PLAIN).redScore());
        return mirror("V2", "espejo ThePredict (" + timing.label + ", " + scenario.id() + ")", Optional.of(timing),
                outcomes);
    }

    private static Gate mirror(String id, String what, Optional<BenchProfiles.Timing> timing, List<Double> outcomes) {
        var total = 0.0;
        for (var outcome : outcomes) {
            total += outcome;
        }
        var score = total / outcomes.size();
        var interval = EvalStats.wilson(total, outcomes.size());
        var passed = Math.abs(score - 0.5) <= MIRROR_TOLERANCE && interval[0] <= 0.5 && 0.5 <= interval[1];
        return new Gate(id, what, timing, "|s − 0,5| ≤ 0,05 y el Wilson 95 % contiene 0,5", String.format(Locale.ROOT,
                "s = %.3f, Wilson [%.3f; %.3f], %d rondas", score, interval[0], interval[1], outcomes.size()), passed);
    }

    /**
     * V3: the side of ThePredict does not change its result: McNemar of arm A against arm B, paired by seed, on all
     * cells together and on each cell with Holm.
     */
    static Gate sides(List<BenchCell.Result> cells, BenchProfiles.Timing timing) {
        var redOnly = 0;
        var blueOnly = 0;
        var raw = new double[cells.size()];
        for (var at = 0; at < cells.size(); at++) {
            var cell = cells.get(at);
            raw[at] = cell.sides();
            for (var index = 0; index < cell.a().size(); index++) {
                var red = cell.a().get(index).won();
                var blue = cell.b().get(index).won();
                redOnly += red && !blue ? 1 : 0;
                blueOnly += blue && !red ? 1 : 0;
            }
        }
        var pooled = EvalStats.mcnemar(redOnly, blueOnly);
        var worst = 1.0;
        for (var adjusted : EvalStats.holm(raw)) {
            worst = Math.min(worst, adjusted);
        }
        return new Gate("V3", "lado (" + timing.label + ")", Optional.of(timing),
                "McNemar A frente a B: p ≥ 0,05 en el total y por celda con Holm",
                String.format(Locale.ROOT, "total p = %.3f (%d frente a %d pares discordantes); peor celda p Holm = "
                        + "%.3f", pooled, redOnly, blueOnly, worst), pooled >= SIDE_LEVEL && worst >= SIDE_LEVEL);
    }

    /** V4: two lethal hits that land in the same tick kill both, and the round is a draw. */
    static Gate doubleKill(DuelPhysics physics, TickRules rules) {
        var red = new DuelWorld.Body(new UUID(0x0D0E4L, 0), DuelWorld.Side.RED, 0, -1, 60);
        var blue = new DuelWorld.Body(new UUID(0x0D0E4L, 1), DuelWorld.Side.BLUE, 0, 1, 60);
        red.health = DuelSkill.STRIKE.damage;
        blue.health = DuelSkill.STRIKE.damage;
        var world = new DuelWorld(red, blue, hit -> {
        }, physics, rules, Arena.OPEN18, 0);
        world.cast(red, DuelSkill.STRIKE, 0);
        world.cast(blue, DuelSkill.STRIKE, 0);
        world.commit(0, true);
        for (var tick = 0L; tick <= DuelSkill.STRIKE.delayTicks; tick++) {
            world.step(tick);
        }
        var draw = world.over() && world.winner().isEmpty();
        return new Gate("V4", "dos golpes letales en el mismo tick", Optional.empty(), "empate, exacto", draw ? "empate"
                : "gana " + world.winner().map(Enum::name).orElse("nadie"), draw);
    }

    /**
     * V5: both sides carry the kit's skills and health, every speed modifier of ThePredict's profile is one the
     * classic can use, and {@code legal-equal} uses exactly those.
     */
    static Gate sameStats(List<Scenario> scenarios, MovementSettings movement, List<ClassicConfig> equals) {
        var problems = new ArrayList<String>();
        var speeds = List.of(ClassicSpace.CHASES[0], ClassicSpace.CHASES[1], ClassicSpace.CHASES[2],
                ClassicSpace.FLEE_SPEED, ClassicSpace.BAND_SPEED);
        for (var modifier : List.of(movement.approachSpeed(), movement.retreatSpeed())) {
            if (speeds.stream().noneMatch(speed -> Math.abs(speed - modifier) < 1e-9)) {
                problems.add("ThePredict usa " + modifier + ", fuera del espacio legal");
            }
        }
        for (var equal : equals) {
            var goals = equal.goals();
            if (Math.abs(goals.meleeSpeed() - movement.approachSpeed()) > 1e-9 || goals.band()
                    && Math.abs(goals.bandSpeed() - movement.approachSpeed()) > 1e-9 || goals.fleeBelowHealth() > 0
                    && Math.abs(goals.fleeSpeed() - movement.retreatSpeed()) > 1e-9) {
                problems.add("legal-equal de " + equal.kit().id() + " no copia las velocidades");
            }
        }
        for (var scenario : scenarios) {
            if (!(scenario.maxHealth() > 0)) {
                problems.add(scenario.id());
            }
        }
        return new Gate("V5", "mismas estadísticas y velocidades a los dos lados", Optional.empty(),
                "comprobación estática",
                problems.isEmpty() ? "kit y vida compartidos; acercarse " + movement.approachSpeed() + ", retirarse "
                        + movement.retreatSpeed() + ", banda " + movement.approachSpeed() + " dentro del espacio legal"
                        : String.join("; ", problems), problems.isEmpty());
    }

    /** V6: ThePredict forced to level 4 plays the classic of its side, round for round. */
    static Gate forcedBase(BenchRunner runner, Scenario scenario, BenchProfiles.Timing timing, ClassicConfig rival,
                           int seeds) {
        var base = ClassicConfig.pack(scenario.kit());
        var setup = new DuelSimulator.Setup(runner.physics(), runner.rules(), scenario, base, rival);
        var profiles = runner.profiles(timing, scenario.kit());
        var mismatches = runner.pool().map(seeds, seed -> {
            var forced = new DuelSimulator(new DuelSimulator.Mode.None(), profiles, setup).round(DuelSimulator.Arm.A,
                    BenchRunner.GATE_SEED + seed, false, new DuelSimulator.Options(true, -1));
            var classic = ClassicMatch.play(setup, BenchRunner.GATE_SEED + seed, false);
            return forced.winner() == classic.winner() && forced.ticks() == classic.ticks() ? 0 : 1;
        });
        var wrong = mismatches.stream().mapToInt(Integer::intValue).sum();
        return new Gate("V6", "BASE forzado ≡ clásico (" + timing.label + ")", Optional.of(timing),
                "mismo ganador y duración en cada "
                + "semilla", wrong + " de " + seeds + " semillas distintas", wrong == 0);
    }

    /** The first gate that failed, if any. */
    static Optional<Gate> failed(List<Gate> gates) {
        return gates.stream().filter(gate -> !gate.passed()).findFirst();
    }
}
