package akyrion.thepredict.engine.duel;

import akyrion.thepredict.adapter.server.duel.DuelTestKit;
import akyrion.thepredict.adapter.server.duel.DuelistReading;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.SwitchableDecisionTap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The local duel simulator of M18.7: the real engine of ThePredict ({@link DuelEngine}) against or beside the classic
 * duelist of MythicMobs ({@link ClassicDuelist}) in a {@link DuelWorld}, deterministic for a seed and free. Each round
 * spawns red and blue at {@code (0.5, -9.5)} and {@code (0.5, 10.5)} with ±1 block from the seed (swapped when asked),
 * ends with the first death or at {@value #MAX_TICKS} ticks as a draw, as {@code DuelTimeout} does. A ThePredict duelist
 * moves as its published intent says while ThePredict controls it and plays the classic duelist at level 4. The same
 * seeds give the same rounds in every arm. With {@link #recordInto} the real duel recorder of M18.4 writes the same
 * files as on a server.
 */
final class DuelSimulator {
    static final int MAX_TICKS = 1800;
    private akyrion.thepredict.adapter.storage.RecordTap recordsTap;

    /**
     * Receives round and encounter-close events.
     *
     * @param tap the records tap
     */
    void records(akyrion.thepredict.adapter.storage.RecordTap tap) {
        recordsTap = tap;
    }
    static final String RED_FACTION = "duel_red";
    static final String BLUE_FACTION = "duel_blue";
    private static Map<String, TacticalProfile> packProfiles;

    /** The arms of M18.3. */
    enum Arm {
        A(true, false), B(false, true), C(true, true), D(false, false);

        final boolean redTp;
        final boolean blueTp;

        Arm(boolean redTp, boolean blueTp) {
            this.redTp = redTp;
            this.blueTp = blueTp;
        }
    }

    /** The modes of Jev of M18.7 and of the bench. */
    sealed interface Mode permits Mode.None, Mode.Oracle, Mode.Judge {
        /** No key: ThePredict plays at {@code LOCAL} and {@code BASE}. */
        record None() implements Mode {
        }

        /** A scripted Jev that answers the best option with probability {@code correctRatio}. */
        record Oracle(double correctRatio, long seed) implements Mode {
        }

        /**
         * The judge of the bench (§7.5): the lookahead oracle answers its best option with probability {@code pBest}
         * ({@link OracleJev#UNIFORM} for one over the options) after the latency of {@code latency}.
         *
         * @param pBest probability of the best option
         * @param latency the round trip
         * @param seed the seed of the round
         */
        record Judge(double pBest, Latency latency, long seed) implements Mode {
        }

        /** The round trip of the judge. */
        enum Latency {
            /** The measured lognormal with its tail and lost answers. */
            MEASURED,
            /** No wait at all: the ceiling. */
            NONE;

            /** The latency model of a seed. */
            LatencyModel model(long seed) {
                return this == MEASURED ? LatencyModel.measured(seed) : LatencyModel.fixed(0);
            }
        }
    }

    /**
     * How a simulator resolves its rounds (§7.2 to §7.4): the physics and the tick rules of the world, the scenario,
     * and the classic configuration of each side, which a ThePredict duelist of that side plays at level 4.
     * {@link #LEGACY} is the simulator of M18.7.
     *
     * @param physics the world
     * @param rules the tick
     * @param scenario kit, health, arena and spawn
     * @param red the classic of red
     * @param blue the classic of blue
     */
    record Setup(DuelPhysics physics, TickRules rules, Scenario scenario, ClassicConfig red, ClassicConfig blue) {
        static final Setup LEGACY = new Setup(DuelPhysics.LEGACY, TickRules.LEGACY, Scenario.LEGACY,
                ClassicConfig.pack(Kit.K1), ClassicConfig.pack(Kit.K1));

        /** Whether this is the simulator of M18.7, whose bytes the golden files keep. */
        boolean legacy() {
            return physics.legacy() && rules.legacy() && scenario.legacy();
        }

        /** The same setup with other classics. */
        Setup classics(ClassicConfig nextRed, ClassicConfig nextBlue) {
            return new Setup(physics, rules, scenario, nextRed, nextBlue);
        }
    }

    /**
     * How a round deviates from the plain duel.
     *
     * @param forceBaseRed red is forced to level 4 from the first tick
     * @param blindRedAtTick tick of the round after which red stops perceiving its rival, or −1
     * @param zoneMode zone mode stored on both duelists; {@link ZoneMode#OFF} is the plain duel
     */
    record Options(boolean forceBaseRed, long blindRedAtTick, ZoneMode zoneMode) {
        Options(boolean forceBaseRed, long blindRedAtTick) {
            this(forceBaseRed, blindRedAtTick, ZoneMode.OFF);
        }

        static final Options PLAIN = new Options(false, -1);
    }

    private final DuelEngine engine;
    private final Map<String, TacticalProfile> profiles;
    private final DuelCastAudit audit;
    private final Setup setup;
    private final SwitchableDecisionTap forward = new SwitchableDecisionTap();
    private final Counting counting = new Counting();
    private Optional<DuelTestKit> recording = Optional.empty();
    private long rounds;
    private boolean engineCast;
    private java.util.Optional<java.util.function.ObjIntConsumer<DuelWorld>> observer = java.util.Optional.empty();

    DuelSimulator(Mode mode) {
        this(mode, Map.of(), (DuelCastAudit) null);
    }

    /**
     * A simulator whose duelists use {@code profiles} instead of the pack, and whose casts feed {@code audit} when it
     * is not null. The pack itself is never modified. M19.9 uses it for cast mode and horizon.
     */
    DuelSimulator(Mode mode, Map<String, TacticalProfile> profiles, DuelCastAudit audit) {
        this.profiles = Map.copyOf(profiles);
        this.audit = audit;
        this.setup = Setup.LEGACY;
        engine = new DuelEngine(mode, counting);
    }

    /**
     * A simulator of the bench: its rounds follow {@code setup}, and its ThePredict duelists use {@code profiles} by
     * MythicMobs id ({@code DuelRed} and {@code DuelBlue}). One simulator per round keeps every round independent of
     * the others: the engine carries state from one round to the next.
     *
     * @param mode the mode of Jev
     * @param profiles the profiles of the ThePredict duelists
     * @param setup how the rounds resolve
     */
    DuelSimulator(Mode mode, Map<String, TacticalProfile> profiles, Setup setup) {
        this.profiles = Map.copyOf(profiles);
        this.audit = null;
        this.setup = setup;
        engine = new DuelEngine(mode, counting, setup.rules(), setup.scenario().kit());
    }

    /**
     * A simulator whose engine hears {@code engineTap} instead of the counting tap, to measure what the tap costs
     * while nothing records; the duplicated casts of its results are not meaningful.
     */
    DuelSimulator(Mode mode, DecisionTap engineTap) {
        profiles = Map.of();
        audit = null;
        setup = Setup.LEGACY;
        engine = new DuelEngine(mode, engineTap);
    }

    /** The engine of this simulator. */
    DuelEngine engine() {
        return engine;
    }

    /** The counting tap of this simulator. */
    Counting counting() {
        return counting;
    }

    /** The next round number, which makes the body ids of a round unique in this simulator. */
    long nextRound() {
        return ++rounds;
    }

    /**
     * Lets an observer read the world at the end of every tick of the fair rounds of this simulator; it must not
     * change the world.
     *
     * @param observer receives the tick of the round and the world
     */
    void watch(java.util.function.ObjIntConsumer<DuelWorld> observer) {
        this.observer = java.util.Optional.of(observer);
    }

    /**
     * Forwards what the engine tells (decisions, casts, closed outcome windows) to {@code tap} from now on, as a duel
     * recording would hear it; the engine itself does not change.
     *
     * @param tap the listener
     */
    void listen(DecisionTap tap) {
        forward.attach(tap, () -> {
        });
    }

    /** The observer of the fair rounds, if any. */
    java.util.Optional<java.util.function.ObjIntConsumer<DuelWorld>> observer() {
        return observer;
    }

    TacticalProfile pack(String id) {
        var overridden = profiles.get(id);
        return overridden != null ? overridden : packs().get(id);
    }

    /** From now on the real duel recorder of M18.4 hears this simulator; returns its test kit. */
    DuelTestKit recordInto(Path root) {
        var kit = new DuelTestKit(root, engine.board, forward);
        packs().values().forEach(kit.scene.catalog::add);
        recording = Optional.of(kit);
        return kit;
    }

    static synchronized Map<String, TacticalProfile> packs() {
        if (packProfiles == null) {
            packProfiles = DuelTestKit.packProfiles();
        }
        return packProfiles;
    }

    DuelResult round(Arm arm, long seed, boolean swapped, Options options) {
        if (!setup.legacy()) {
            return new FairRound(this, setup, arm, seed, swapped, options).play();
        }
        var round = new Round(arm, seed, swapped, options);
        round.play();
        return round.result();
    }

    /** One round. */
    private final class Round {
        private final Arm arm;
        private final long seed;
        private final boolean swapped;
        private final Options options;
        private final DuelWorld world;
        private final ClassicDuelist classicRed;
        private final ClassicDuelist classicBlue;
        private final long start;
        private final Map<String, Integer> redCasts = new TreeMap<>();
        private final Map<String, Integer> blueCasts = new TreeMap<>();
        private final Map<String, Integer> redHits = new TreeMap<>();
        private final Map<String, Integer> blueHits = new TreeMap<>();
        private final Map<ControlLevel, Integer> levels = new EnumMap<>(ControlLevel.class);
        private final List<String> redSequence = new ArrayList<>();
        private final StringBuilder events = new StringBuilder();
        private double redDealt;
        private double blueDealt;
        private int retreats;
        private final Map<UUID, Movement> movements = new HashMap<>();
        private DuelResult.Winner winner = DuelResult.Winner.DRAW;
        private int ticks = MAX_TICKS;
        private long baseDelay = -1;

        private Round(Arm arm, long seed, boolean swapped, Options options) {
            this.arm = arm;
            this.seed = seed;
            this.swapped = swapped;
            this.options = options;
            var random = new Random(seed);
            rounds++;
            var redZ = (swapped ? 10.5 : -9.5) + jitter(random);
            var red = new DuelWorld.Body(new UUID(0x0D0E1L, rounds * 2), DuelWorld.Side.RED, 0.5 + jitter(random), redZ);
            var blueZ = (swapped ? -9.5 : 10.5) + jitter(random);
            var blue = new DuelWorld.Body(new UUID(0x0D0E1L, rounds * 2 + 1), DuelWorld.Side.BLUE,
                    0.5 + jitter(random), blueZ);
            world = new DuelWorld(red, blue, this::hit);
            start = engine.clock.tick;
            classicRed = new ClassicDuelist(world, red, random, start);
            classicBlue = new ClassicDuelist(world, blue, random, start);
            engine.round(world, this::cast);
            if (arm.redTp) {
                engine.open(red, pack("DuelRed"), options.forceBaseRed(), options.blindRedAtTick() < 0 ? -1
                        : start + options.blindRedAtTick());
                applyZone(red.id, options.zoneMode());
                recording.ifPresent(kit -> kit.scene.bound.put(red.id, pack("DuelRed")));
            }
            if (arm.blueTp) {
                engine.open(blue, pack("DuelBlue"), false, -1);
                applyZone(blue.id, options.zoneMode());
                recording.ifPresent(kit -> kit.scene.bound.put(blue.id, pack("DuelBlue")));
            }
            counting.reset();
        }

        private void applyZone(UUID id, ZoneMode mode) {
            engine.sessions.find(id).ifPresent(session -> session.zoneReading(mode, PushDanger.UNKNOWN,
                    Feasibility.UNKNOWN));
        }

        private void play() {
            if (audit != null) {
                audit.beginRound();
            }
            for (var index = 0; index < MAX_TICKS; index++) {
                var tick = start + index;
                engine.clock.tick = tick;
                engine.clock.nanos = tick * DuelEngine.TICK_NANOS;
                if (audit != null) {
                    audit.before(world, arm, tick);
                }
                var preparations = engine.preparationCasts();
                engineCast = audit != null;
                engine.tick();
                engineCast = false;
                if (audit != null) {
                    audit.preparationStarts += engine.preparationCasts() - preparations;
                    audit.afterControl(world.red, arm.redTp && DuelCastAudit.controls(engine.board.current(world.red.id),
                            engine.clock.nanos));
                    audit.afterControl(world.blue, arm.blueTp && DuelCastAudit.controls(engine.board.current(world.blue.id),
                            engine.clock.nanos));
                    audit.finishTick(tick);
                }
                if (index % 2 == 0) {
                    act(world.red, arm.redTp, classicRed, tick, index);
                    act(world.blue, arm.blueTp, classicBlue, tick, index);
                } else {
                    act(world.blue, arm.blueTp, classicBlue, tick, index);
                    act(world.red, arm.redTp, classicRed, tick, index);
                }
                world.step(tick);
                record(tick);
                if (world.over()) {
                    var won = world.winner();
                    winner = won.map(side -> side == DuelWorld.Side.RED ? DuelResult.Winner.RED
                            : DuelResult.Winner.BLUE).orElse(DuelResult.Winner.DRAW);
                    ticks = index + 1;
                    for (var body : List.of(world.red, world.blue)) {
                        if (body.health <= 0) {
                            recording.ifPresent(kit -> kit.recorder.gone(body.id, true));
                        }
                    }
                    break;
                }
            }
            end();
        }

        private void act(DuelWorld.Body body, boolean tp, ClassicDuelist classic, long tick, int index) {
            var view = engine.board.current(body.id);
            if (tp && view.controls(engine.clock.nanos)) {
                if (view.movement() == Movement.RETREAT && movements.get(body.id) != Movement.RETREAT) {
                    retreats++;
                }
                movements.put(body.id, view.movement());
                world.move(body, view.movement(), engine.profile(body.id).movement());
                levels.merge(view.level(), 1, Integer::sum);
                return;
            }
            movements.put(body.id, Movement.NONE);
            if (tp) {
                levels.merge(ControlLevel.BASE, 1, Integer::sum);
                if (body.side == DuelWorld.Side.RED && baseDelay < 0 && options.blindRedAtTick() >= 0
                        && index >= options.blindRedAtTick()) {
                    baseDelay = index - options.blindRedAtTick();
                }
            }
            classic.tick(tick, this::cast);
        }

        private void cast(DuelWorld.Body caster, DuelSkill skill) {
            if (engineCast) {
                audit.onCast(caster, skill, engine.clock.tick);
            }
            (caster.side == DuelWorld.Side.RED ? redCasts : blueCasts).merge(skill.id, 1, Integer::sum);
            note(engine.clock.tick, caster.side, skill.id, "CAST");
            if (caster.side == DuelWorld.Side.RED) {
                redSequence.add((engine.clock.tick - start) + ":" + skill.id);
            }
            recording.ifPresent(kit -> kit.recorder.skillEvent(caster.id, skill.id));
        }

        private void hit(DuelWorld.Hit hit) {
            var fraction = hit.damage() / DuelWorld.MAX_HEALTH;
            if (hit.attacker().side == DuelWorld.Side.RED) {
                redDealt += fraction;
            } else {
                blueDealt += fraction;
            }
            var hits = hit.attacker().side == DuelWorld.Side.RED ? redHits : blueHits;
            var skillId = hit.skill().map(skill -> skill.id).orElse("vanilla");
            hits.merge(skillId, 1, Integer::sum);
            note(hit.tick(), hit.attacker().side, skillId, "HIT");
            recording.ifPresent(kit -> kit.recorder.damaged(hit.attacker().id, hit.victim().id,
                    "ENTITY_ATTACK", hit.damage(), fraction, false));
        }

        private void record(long tick) {
            recording.ifPresent(kit -> {
                kit.clock.tick = tick;
                kit.clock.nanos = engine.clock.nanos;
                for (var body : List.of(world.red, world.blue)) {
                    if (body.health > 0) {
                        kit.scene.put(new DuelistReading(body.id, body.side == DuelWorld.Side.RED ? RED_FACTION
                                : BLUE_FACTION, body.healthRatio(), body.absorption, body.x, 65, body.z, 0f,
                                DuelWorld.WIDTH, 1.95, Optional.of(world.rival(body).id), false, false, ""));
                    }
                }
                kit.repeater.tick(tick);
            });
        }

        private void end() {
            if (recordsTap != null) {
                recordsTap.round(seed, ticks);
                recordsTap.encounterClosed(new EncounterId(1), new akyrion.thepredict.engine.EncounterClosed(
                        new EncounterId(1), new UUID(1, 2), start, start + ticks,
                        akyrion.thepredict.engine.SessionMetrics.EncounterEnd.COMBAT_ENDED, List.of(), Map.of(),
                        java.util.Set.of(), 0, 0));
            }
            for (var body : List.of(world.red, world.blue)) {
                if (body.side == DuelWorld.Side.RED ? arm.redTp : arm.blueTp) {
                    engine.close(body);
                }
                recording.ifPresent(kit -> {
                    kit.scene.alive.remove(body.id);
                    kit.recorder.gone(body.id, false);
                    kit.scene.bound.remove(body.id);
                });
            }
        }

        private DuelResult result() {
            var jevRequests = engine.jev.map(oracle -> oracle.requests).orElse(0);
            var tokens = engine.jev.map(oracle -> oracle.tokens).orElse(0L);
            return new DuelResult(arm, seed, swapped, winner, ticks, redDealt, blueDealt, redCasts, blueCasts, redHits,
                    blueHits, retreats, levels, counting.requestsSince(jevRequests), counting.tokensSince(tokens),
                    counting.tactics(), engine.actionCasts - counting.casts(), List.copyOf(redSequence), baseDelay,
                    digest(events.toString()), world.red.lostToInvulnerability, world.blue.lostToInvulnerability,
                    Conduct.EMPTY, Conduct.EMPTY);
        }

        private void note(long tick, DuelWorld.Side side, String skill, String result) {
            if (!events.isEmpty()) {
                events.append('\n');
            }
            events.append(tick - start).append('\t').append(side.name()).append('\t').append(skill).append('\t')
                    .append(result);
        }
    }

    /** SHA-256 of the event lines of a round. */
    static String digest(String events) {
        try {
            var hash = MessageDigest.getInstance("SHA-256").digest(events.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static double jitter(Random random) {
        return random.nextDouble() * 2 - 1;
    }

    /** Counts what the engine tells and forwards it to the recorder, when one listens. */
    final class Counting implements DecisionTap {
        private long casts;
        private long castsAtStart;
        private int requestsAtStart;
        private long tokensAtStart;
        private final Map<String, double[]> tactics = new TreeMap<>();
        private final Map<UUID, Map<String, Integer>> reactions = new HashMap<>();

        void reset() {
            castsAtStart = engine.actionCasts;
            casts = 0;
            requestsAtStart = engine.jev.map(oracle -> oracle.requests).orElse(0);
            tokensAtStart = engine.jev.map(oracle -> oracle.tokens).orElse(0L);
            tactics.clear();
            reactions.clear();
        }

        /** The reactions a mob armed this round, by name. */
        Map<String, Integer> reactions(UUID mob) {
            return new TreeMap<>(reactions.getOrDefault(mob, Map.of()));
        }

        long casts() {
            return casts + castsAtStart;
        }

        int requestsSince(int now) {
            return now - requestsAtStart;
        }

        long tokensSince(long now) {
            return now - tokensAtStart;
        }

        Map<String, double[]> tactics() {
            var copy = new TreeMap<String, double[]>();
            tactics.forEach((tactic, cell) -> copy.put(tactic, cell.clone()));
            return copy;
        }

        @Override
        public boolean recording() {
            return true;
        }

        @Override
        public akyrion.thepredict.engine.TapDetail detail() {
            return akyrion.thepredict.engine.TapDetail.FULL;
        }

        @Override
        public void questionsPlanned(EncounterId encounter, long sequence,
                                     List<QuestionSpec> planned,
                                     Preparation preparation,
                                     DecisionDispatcher.Outcome dispatch,
                                     CombatFacts facts) {
            forward.questionsPlanned(encounter, sequence, planned, preparation, dispatch, facts);
        }

        @Override
        public void requestBody(EncounterId encounter, long sequence, String body) {
            forward.requestBody(encounter, sequence, body);
        }

        @Override
        public void resultReceived(EncounterId encounter, long sequence,
                                   DecisionResult result, long roundTripNanos) {
            forward.resultReceived(encounter, sequence, result, roundTripNanos);
        }

        @Override
        public void answerDiscarded(EncounterId encounter, long sequence,
                                    DiscardReason reason) {
            forward.answerDiscarded(encounter, sequence, reason);
        }

        @Override
        public void answerDiscarded(EncounterId encounter, long sequence,
                                    QuestionKey question, MobKey mob,
                                    DiscardReason reason) {
            forward.answerDiscarded(encounter, sequence, question, mob, reason);
        }

        @Override
        public void intentEvent(MobKey mob, IntentEvent event) {
            forward.intentEvent(mob, event);
        }

        @Override
        public void castAttempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome) {
            if (outcome == ExecutionOutcome.CAST) {
                casts++;
            }
            forward.castAttempted(mob, tactic, choice, outcome);
        }

        @Override
        public void reactionFired(MobKey mob, akyrion.thepredict.domain.Reaction reaction) {
            reactions.computeIfAbsent(mob.entityId(), key -> new TreeMap<>()).merge(reaction.name(), 1,
                    Integer::sum);
            forward.reactionFired(mob, reaction);
        }

        @Override
        public void threatChanged(MobKey mob, UUID participant, double amount, String cause) {
            forward.threatChanged(mob, participant, amount, cause);
        }

        @Override
        public void outcomeClosed(MobKey mob, OutcomeWindow.Summary summary) {
            var cell = tactics.computeIfAbsent(summary.tactic().value(), key -> new double[2]);
            cell[0]++;
            cell[1] += summary.effect();
            forward.outcomeClosed(mob, summary);
        }
    }
}
