package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.StableRandom;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One round of the bench (§7.2 of the simulator audit, SIM-03): every tick the engine decides for the ThePredict
 * duelists and every other duelist decides with its classic rules, all on the same picture of the world; then both
 * bodies move together, the casts of the tick commit in an order drawn from the seed
 * ({@code StableRandom.draw(seed, tick, 31)}), and the effects due land together, so two lethal hits are a draw. The
 * spawn points, the global phase of the timers and the phase of the engine clock come from the scenario and the seed
 * only. A ThePredict duelist that does not control its body plays the classic of its side. Hits and deaths reach the
 * engine as the listeners of a server report them.
 */
final class FairRound {
    /** Draw of the commit order of one tick. */
    static final int SALT_ORDER = 31;

    private final DuelSimulator simulator;
    private final DuelEngine engine;
    private final DuelSimulator.Arm arm;
    private final long seed;
    private final boolean swapped;
    private final DuelWorld world;
    private final ConductTally redConduct;
    private final ConductTally blueConduct;
    private final ClassicDuelist redDuelist;
    private final ClassicDuelist blueDuelist;
    private final int horizon;
    private final long start;
    private final Map<String, Integer> redCasts = new TreeMap<>();
    private final Map<String, Integer> blueCasts = new TreeMap<>();
    private final Map<String, Integer> redHits = new TreeMap<>();
    private final Map<String, Integer> blueHits = new TreeMap<>();
    private final Map<ControlLevel, Integer> levels = new EnumMap<>(ControlLevel.class);
    private final Map<UUID, Movement> movements = new java.util.HashMap<>();
    private final List<String> redSequence = new ArrayList<>();
    private final StringBuilder events = new StringBuilder();
    private double redDealt;
    private double blueDealt;
    private int retreats;
    private int index;
    private DuelResult.Winner winner = DuelResult.Winner.DRAW;
    private int ticks;

    FairRound(DuelSimulator simulator, DuelSimulator.Setup setup, DuelSimulator.Arm arm, long seed, boolean swapped,
              DuelSimulator.Options options) {
        this.simulator = simulator;
        this.engine = simulator.engine();
        this.arm = arm;
        this.seed = seed;
        this.swapped = swapped;
        var scenario = setup.scenario();
        world = world(setup, simulator.nextRound());
        var strike = scenario.kit().skill(Kit.Role.STRIKE);
        redConduct = new ConductTally(world.red, strike);
        blueConduct = new ConductTally(world.blue, strike);
        horizon = scenario.maxTicks();
        start = engine.clock.tick + 1 + Scenario.enginePhase(seed);
        var phase = Scenario.timerPhase(seed);
        redDuelist = new ClassicDuelist(world, world.red, setup.red(), phase);
        blueDuelist = new ClassicDuelist(world, world.blue, setup.blue(), phase);
        open(options);
        simulator.counting().reset();
    }

    /** The world of the round: the spawn points of the seed, the goal parities and the headings. */
    private DuelWorld world(DuelSimulator.Setup setup, long number) {
        var scenario = setup.scenario();
        var points = scenario.spawnPoints(seed);
        var at = swapped ? 2 : 0;
        var red = new DuelWorld.Body(new UUID(0x0D0E2L, number * 2), DuelWorld.Side.RED, points[at], points[at + 1],
                scenario.maxHealth());
        var blue = new DuelWorld.Body(new UUID(0x0D0E2L, number * 2 + 1), DuelWorld.Side.BLUE, points[2 - at],
                points[3 - at], scenario.maxHealth());
        var built = new DuelWorld(red, blue, this::hit, setup.physics(), setup.rules(), scenario.arena(),
                scenario.kit().vanillaDamage());
        red.goalPhase = Scenario.goalPhase(seed, DuelWorld.Side.RED);
        blue.goalPhase = Scenario.goalPhase(seed, DuelWorld.Side.BLUE);
        Motion.face(built, red);
        Motion.face(built, blue);
        return built;
    }

    /** Opens the sessions of the ThePredict duelists and lets the judge watch the world. */
    private void open(DuelSimulator.Options options) {
        engine.round(world, this::cast);
        if (arm.redTp) {
            engine.open(world.red, simulator.pack("DuelRed"), options.forceBaseRed(), options.blindRedAtTick() < 0
                    ? -1 : start + options.blindRedAtTick());
        }
        if (arm.blueTp) {
            engine.open(world.blue, simulator.pack("DuelBlue"), false, -1);
        }
        engine.jev.ifPresent(oracle -> oracle.watch(new View()));
    }

    /** Plays the round to its end. */
    DuelResult play() {
        ticks = horizon;
        for (index = 0; index < horizon; index++) {
            if (step(start + index)) {
                finish();
                break;
            }
        }
        var redDid = conduct(redConduct, world.red);
        var blueDid = conduct(blueConduct, world.blue);
        for (var body : List.of(world.red, world.blue)) {
            if (body.side == DuelWorld.Side.RED ? arm.redTp : arm.blueTp) {
                engine.close(body);
            }
        }
        return result(redDid, blueDid);
    }

    /** One tick: decisions on one picture, movement together, the drawn commit, the landing; whether it ended. */
    private boolean step(long tick) {
        engine.clock.tick = tick;
        engine.clock.nanos = tick * DuelEngine.TICK_NANOS;
        engine.tick();
        act(world.red, arm.redTp, redDuelist, tick);
        act(world.blue, arm.blueTp, blueDuelist, tick);
        remember(world.red);
        remember(world.blue);
        Motion.advance(world);
        redConduct.tick(world);
        blueConduct.tick(world);
        world.commit(tick, (StableRandom.draw(seed, index, SALT_ORDER) & 1L) == 0L);
        world.step(tick);
        var at = index;
        simulator.observer().ifPresent(observer -> observer.accept(world, at));
        return world.over();
    }

    private void act(DuelWorld.Body body, boolean tp, ClassicDuelist duelist, long tick) {
        var view = engine.board.current(body.id);
        if (tp && view.controls(engine.clock.nanos)) {
            var rival = world.rival(body).id;
            var forecast = engine.patterns().flatMap(director -> director.modelOf(rival))
                    .map(akyrion.thepredict.engine.EnemyModel::forecast).orElse(-1);
            (body == world.red ? redConduct : blueConduct).controlled(view, forecast);
            if (view.movement() == Movement.RETREAT && movements.get(body.id) != Movement.RETREAT) {
                retreats++;
            }
            movements.put(body.id, view.movement());
            levels.merge(view.level(), 1, Integer::sum);
            Motion.select(world, body, Motion.intent(world, view.movement(), engine.profile(body.id).movement()),
                    index);
            return;
        }
        movements.put(body.id, Movement.NONE);
        if (tp) {
            levels.merge(ControlLevel.BASE, 1, Integer::sum);
        }
        duelist.decide(index, tick, this::cast);
    }

    private static void remember(DuelWorld.Body body) {
        body.previousX = body.x;
        body.previousZ = body.z;
    }

    private void finish() {
        winner = world.winner().map(side -> side == DuelWorld.Side.RED ? DuelResult.Winner.RED
                : DuelResult.Winner.BLUE).orElse(DuelResult.Winner.DRAW);
        ticks = index + 1;
        for (var body : List.of(world.red, world.blue)) {
            if (body.health <= 0) {
                engine.died(body);
            }
        }
    }

    private void cast(DuelWorld.Body caster, DuelSkill skill) {
        (caster.side == DuelWorld.Side.RED ? redConduct : blueConduct).cast(skill, engine.clock.tick);
        (caster.side == DuelWorld.Side.RED ? redCasts : blueCasts).merge(skill.id, 1, Integer::sum);
        note(caster.side, skill.id, "CAST");
        if (caster.side == DuelWorld.Side.RED) {
            redSequence.add(index + ":" + skill.id);
        }
    }

    private void hit(DuelWorld.Hit hit) {
        var fraction = hit.damage() / hit.victim().maxHealth;
        if (hit.attacker().side == DuelWorld.Side.RED) {
            redDealt += fraction;
        } else {
            blueDealt += fraction;
        }
        var skillId = hit.skill().map(skill -> skill.id).orElse("vanilla");
        (hit.attacker().side == DuelWorld.Side.RED ? redHits : blueHits).merge(skillId, 1, Integer::sum);
        note(hit.attacker().side, skillId, "HIT");
        redConduct.hit(hit, world);
        blueConduct.hit(hit, world);
        engine.hit(hit);
    }

    private void note(DuelWorld.Side side, String skill, String result) {
        if (!events.isEmpty()) {
            events.append('\n');
        }
        events.append(index).append('\t').append(side.name()).append('\t').append(skill).append('\t').append(result);
    }

    private DuelResult result(Conduct redDid, Conduct blueDid) {
        var counting = simulator.counting();
        var requests = engine.jev.map(oracle -> oracle.requests).orElse(0);
        var tokens = engine.jev.map(oracle -> oracle.tokens).orElse(0L);
        return new DuelResult(arm, seed, swapped, winner, ticks, redDealt, blueDealt, redCasts, blueCasts, redHits,
                blueHits, retreats, levels, counting.requestsSince(requests), counting.tokensSince(tokens),
                counting.tactics(), engine.actionCasts - counting.casts(), List.copyOf(redSequence), -1,
                DuelSimulator.digest(events.toString()), world.red.lostToInvulnerability,
                world.blue.lostToInvulnerability, redDid, blueDid);
    }

    private Conduct conduct(ConductTally tally, DuelWorld.Body body) {
        var rival = world.rival(body).id;
        var accuracy = engine.patterns().flatMap(director -> director.modelOf(rival))
                .map(akyrion.thepredict.engine.EnemyModel::accuracy).orElse(Double.NaN);
        return tally.conduct(simulator.counting().reactions(body.id), accuracy);
    }

    /** What the judge sees when a request is sent. */
    private final class View implements OracleJev.Lookout {
        @Override
        public DuelWorld world() {
            return world.copy();
        }

        @Override
        public ClassicDuelist duelist(DuelWorld.Side side) {
            return side == DuelWorld.Side.RED ? redDuelist : blueDuelist;
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public long tick() {
            return start + index;
        }
    }
}
