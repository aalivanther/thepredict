package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ReactionBands;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.UntypedCastMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;

/**
 * Local harness of the A/B protocol of M13.5 (plan §12): the same scripted fight, with the same profile, skills and
 * outcomes, runs once without Jev (local planner and base AI) and once with a scripted Jev whose answers are an
 * acceptable tactic with a seeded probability. It measures the pipeline, not the model: time with an acceptable
 * movement, casts, skipped and deferred casts, tactic switches, requests, tokens and their cost, and, since M16.8, the
 * effect and hit rate of the outcome windows with a scripted damage model that is the same for both arms: a cast in
 * melee without a raised shield deals {@value #DAMAGE_PER_HIT} of the target's health, and each tick in melee costs the
 * mob {@value #DAMAGE_TAKEN_PER_MELEE_TICK}. The arm without the
 * Predictor (the MythicMobs boss alone) cannot be simulated here; it is measured on the server (script of M13).
 */
final class TacticalAB {
    /** Length of one run: 30 s at 20 ticks per second. */
    static final int TICKS = 600;
    /** Ticks between a send and its scripted answer, 300 ms. */
    static final int LATENCY_TICKS = 6;
    /** Ticks a target stays invulnerable after a hit. */
    static final int INVULNERABLE_TICKS = 10;
    /** Scripted damage of a cast that lands, as a fraction of the target's health (M16.8). */
    static final double DAMAGE_PER_HIT = 0.05;
    /** Scripted damage the mob takes each tick its target is in melee, as a fraction of its health. */
    static final double DAMAGE_TAKEN_PER_MELEE_TICK = 0.002;
    /** Public price of Jev 1.13 per million input tokens, in USD (plan §6). */
    static final double USD_PER_MILLION_INPUT_TOKENS = 0.042;
    /** Context of the local arm for a critical mob, as the {@code low_health_phase} of the example profile. */
    static final ContextId LOW_HEALTH_PHASE = new ContextId("low_health_phase");
    /**
     * Timing of the {@code config.yml} of 0.0.1, which the golden references of M17 keep: heartbeat and interval 20
     * ticks, intents of 40, 0.45 and 0.15 (the minimum confidence of the re-bench of M16.10), the default question
     * detail, {@code intent_only}, tick layer {@code casts} and cadence {@code standard}. The delivered timing is
     * {@link #DELIVERED} (PZ-33).
     */
    static final DecisionTiming DEFAULTS = new DecisionTiming(20, 20, 40, 0.45, 0.15, 1);
    /**
     * Timing of the delivered {@code config.yml} as {@code PluginSettings.timing} builds it: the same heartbeat,
     * interval, intents, confidence and margin as {@link #DEFAULTS}, detail {@code full}, {@code any_ready}, tick layer
     * {@code reactions} and cadence {@code reactive} with a reactive interval of 6 and an intent horizon of 10 (PZ-33).
     */
    static final DecisionTiming DELIVERED = new DecisionTiming(20, 20, 40, 0.45, 0.15, 1, QuestionDetail.FULL, false,
            CastMode.ANY_READY, 0, UntypedCastMode.TACTIC_ONLY).withLayer(TickLayerMode.REACTIONS,
            ReactionScope.DEFAULT, ReactionBands.DEFAULT_AHEAD, 0, DecisionCadence.REACTIVE, 6, 10);

    private TacticalAB() {
    }

    /** Scenarios of M13.5; each phase has the tactics a person would accept. */
    enum Scenario {
        MELEE_DUEL, SHOOTER_BACKING_AWAY, SHIELD, LOW_HEALTH, PILLAR
    }

    /** Arms of the local comparison. */
    enum Arm {
        WITHOUT_JEV, WITH_JEV
    }

    /** State of the target and the mob in one tick of a scenario. */
    record Phase(DistanceBand distance, boolean shield, HealthBand mobHealth, boolean reachable,
                 Set<TacticId> acceptable) {
        Set<Movement> movements(TacticalProfile profile) {
            var movements = new HashSet<Movement>();
            for (var tactic : acceptable) {
                movements.add(profile.tactic(tactic).orElseThrow().movement());
            }
            return Set.copyOf(movements);
        }
    }

    /** What one arm did in one run. */
    record Result(Scenario scenario, Arm arm, long seed, double usefulShare, long casts, long skippedShield,
                  long deferredInvulnerable, int tacticSwitches, int requests, long tokens, double costUsd,
                  long duplicateCasts, int scriptHash, double effect, double hitRate) {
    }

    /**
     * The script of a scenario: one phase per tick, with phase lengths jittered by the seed.
     *
     * @return {@link #TICKS} phases
     */
    static List<Phase> script(Scenario scenario, long seed) {
        var random = new Random(seed * 31 + scenario.ordinal());
        var phases = new ArrayList<Phase>(TICKS);
        var alternate = false;
        while (phases.size() < TICKS) {
            var length = 60 + random.nextInt(41) - 20;
            var phase = phase(scenario, alternate, phases.size());
            for (var tick = 0; tick < length && phases.size() < TICKS; tick++) {
                phases.add(phase);
            }
            alternate = !alternate;
        }
        return List.copyOf(phases);
    }

    private static Phase phase(Scenario scenario, boolean alternate, int tick) {
        return switch (scenario) {
            case MELEE_DUEL -> new Phase(alternate ? DistanceBand.RANGED : DistanceBand.MELEE, false,
                    HealthBand.HEALTHY, true, Set.of(PRESS));
            case SHOOTER_BACKING_AWAY -> new Phase(alternate ? DistanceBand.BEYOND : DistanceBand.RANGED, false,
                    HealthBand.HEALTHY, true, Set.of(PRESS));
            case SHIELD -> alternate ? new Phase(DistanceBand.MELEE, true, HealthBand.HEALTHY, true, Set.of(KEEP, GUARD))
                    : new Phase(DistanceBand.MELEE, false, HealthBand.HEALTHY, true, Set.of(PRESS));
            case LOW_HEALTH -> tick < TICKS / 3 ? new Phase(DistanceBand.MELEE, false, HealthBand.HEALTHY, true,
                    Set.of(PRESS)) : new Phase(DistanceBand.MELEE, false, HealthBand.CRITICAL, true,
                    Set.of(FALL_BACK, KEEP));
            case PILLAR -> new Phase(DistanceBand.RANGED, false, HealthBand.HEALTHY, false, Set.of(KEEP, GUARD));
        };
    }

    /**
     * Runs one arm of one scenario.
     *
     * @param correctRatio probability that the scripted Jev answers an acceptable tactic; ignored without Jev
     */
    static Result run(Scenario scenario, Arm arm, long seed, double correctRatio) {
        return run(scenario, arm, seed, correctRatio, DEFAULTS);
    }

    /** Runs one arm of one scenario with another decision timing of the profile. */
    static Result run(Scenario scenario, Arm arm, long seed, double correctRatio, DecisionTiming timing) {
        return run(scenario, arm, seed, correctRatio, timing, Hooks.NONE);
    }

    /**
     * Hooks of the golden references of M17.0: how the decision client double prepares each request, and a look at
     * the fixture after each tick of the script.
     *
     * @param preparation preparation of the client double
     * @param afterTick receives the tick of the script and the fixture after it ran
     */
    record Hooks(Function<DecisionRequest, Preparation> preparation, BiConsumer<Integer, ExecutionFixture> afterTick) {
        /** The preparation of the engine tests and no look. */
        static final Hooks NONE = new Hooks(PreparedFixtures::ready, (tick, fixture) -> {
        });
    }

    /** Runs one arm of one scenario with another decision timing and the hooks of the golden references. */
    static Result run(Scenario scenario, Arm arm, long seed, double correctRatio, DecisionTiming timing, Hooks hooks) {
        var profile = withLowHealthContext(timing);
        var script = script(scenario, seed);
        var fixture = arm == Arm.WITH_JEV ? new ExecutionFixture() : ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.client.preparation = hooks.preparation();
        if (arm == Arm.WITH_JEV) {
            fixture.ready();
        }
        fixture.open(1, profile);
        fixture.target(1, 1);
        var run = new Run(fixture, profile, new Random(seed ^ 0x5DEECE66DL), correctRatio);
        for (var tick = 0; tick < TICKS; tick++) {
            run.tick(tick, script.get(tick));
            hooks.afterTick().accept(tick, fixture);
        }
        var duplicates = run.castsPerIntent.values().stream().filter(count -> count > 1).count()
                + early(fixture.actions.castTicks(1));
        var levels = fixture.outcomes.metrics().byLevel();
        var effect = levels.stream().mapToDouble(level -> level.effect() * level.windows()).sum();
        var casts = levels.stream().mapToLong(OutcomeMetrics.LevelOutcomes::casts).sum();
        var hits = levels.stream().mapToLong(OutcomeMetrics.LevelOutcomes::hits).sum();
        return new Result(scenario, arm, seed, run.useful / (double) TICKS, fixture.count(ExecutionOutcome.CAST),
                fixture.count(ExecutionOutcome.SKIPPED_SHIELD), fixture.count(ExecutionOutcome.DEFERRED_INVULNERABLE),
                fixture.actions.signals(1).size(), fixture.client.requests.size(), run.tokens,
                run.tokens * USD_PER_MILLION_INPUT_TOKENS / 1_000_000, duplicates, script.hashCode(), effect,
                casts == 0 ? 0.0 : (double) hits / casts);
    }

    /**
     * Casts that came before the native {@code Cooldown} of the doubles had passed since the previous cast. With
     * {@code any_ready} the only skill of the profile also fires as a ready candidate under an intent whose tactic names
     * no choice, at its own cooldown; those casts are not duplicates of the intent's choice, so the count per intent
     * reads only intents that name a choice, and this count catches a cast that really came too early (PZ-33).
     */
    private static long early(List<Long> castTicks) {
        var early = 0L;
        for (var index = 1; index < castTicks.size(); index++) {
            if (castTicks.get(index) - castTicks.get(index - 1) < ExecutionFixture.Actions.DEFAULT_COOLDOWN_TICKS) {
                early++;
            }
        }
        return early;
    }

    /** State of one run: casts per intent, the answers due, the useful ticks and the tokens. */
    private static final class Run {
        private final ExecutionFixture fixture;
        private final TacticalProfile profile;
        private final Random random;
        private final double correctRatio;
        private final Map<Integer, Integer> sentAt = new HashMap<>();
        private final Map<Integer, Phase> answerPhase = new HashMap<>();
        private final Map<String, Integer> castsPerIntent = new HashMap<>();
        private int lastCastTick = -INVULNERABLE_TICKS - 1;
        private int useful;
        private long tokens;

        private Run(ExecutionFixture fixture, TacticalProfile profile, Random random, double correctRatio) {
            this.fixture = fixture;
            this.profile = profile;
            this.random = random;
            this.correctRatio = correctRatio;
        }

        private void tick(int tick, Phase phase) {
            apply(fixture, profile, phase, tick - lastCastTick <= INVULNERABLE_TICKS);
            var castsBefore = fixture.count(ExecutionOutcome.CAST);
            fixture.run(1);
            var mob = fixture.session(1).key().entityId();
            if (phase.distance() == DistanceBand.MELEE) {
                fixture.outcomes.taken(mob, DAMAGE_TAKEN_PER_MELEE_TICK, fixture.clock.tick);
            }
            if (fixture.count(ExecutionOutcome.CAST) > castsBefore) {
                if (phase.distance() == DistanceBand.MELEE && !phase.shield()) {
                    fixture.outcomes.dealt(mob, EngineFixtures.player(1), DAMAGE_PER_HIT, fixture.clock.tick);
                }
                lastCastTick = tick;
                fixture.session(1).intent().filter(intent -> intent.choice().isPresent()).ifPresent(intent ->
                        castsPerIntent.merge(intent.tactic().value() + "@" + intent.startTick() + "@" + intent.level()
                                + "@" + fixture.session(1).choiceEpoch(), 1, Integer::sum));
            }
            answerDue(tick, phase);
            var view = fixture.view(1);
            if (view.level() != ControlLevel.BASE && phase.movements(profile).contains(view.movement())) {
                useful++;
            }
        }

        /** Answers each request {@link #LATENCY_TICKS} after it was sent, for the phase it was sent in. */
        private void answerDue(int tick, Phase phase) {
            for (var index = 0; index < fixture.client.requests.size(); index++) {
                sentAt.putIfAbsent(index, tick);
                answerPhase.putIfAbsent(index, phase);
                if (!fixture.client.futures.get(index).isDone() && tick - sentAt.get(index) >= LATENCY_TICKS) {
                    var request = fixture.client.requests.get(index);
                    tokens += PreparedFixtures.legacyTokens(request);
                    var asked = answerPhase.get(index);
                    fixture.answer(index, question -> answer(question, asked, random, correctRatio));
                }
            }
        }
    }

    private static void apply(ExecutionFixture fixture, TacticalProfile profile, Phase phase, boolean invulnerable) {
        fixture.reader.distances.put(id(1), phase.distance());
        fixture.reader.health.put(id(1), phase.mobHealth());
        var critical = phase.mobHealth() == HealthBand.CRITICAL;
        fixture.reader.contexts.put(id(1), critical ? Set.of(LOW_HEALTH_PHASE) : Set.of());
        if (critical) {
            fixture.reader.eligible.put(id(1), profile.contexts().getFirst().allowedTactics());
        } else {
            fixture.reader.eligible.remove(id(1));
        }
        if (phase.reachable()) {
            fixture.reader.unreachable.remove(id(1));
        } else {
            fixture.reader.unreachable.add(id(1));
        }
        fixture.mobs.progress.put(id(1), new ArrayDeque<>(List.of(phase.reachable()
                ? new MovementProgress(true, true, true, OptionalDouble.empty())
                : new MovementProgress(true, true, false, OptionalDouble.of(6.0)))));
        var outcome = phase.shield() ? ExecutionOutcome.SKIPPED_SHIELD
                : invulnerable ? ExecutionOutcome.DEFERRED_INVULNERABLE
                : phase.distance() == DistanceBand.MELEE ? ExecutionOutcome.CAST : ExecutionOutcome.NOT_ELIGIBLE;
        fixture.actions.scripted.put(id(1), new ArrayDeque<>(List.of(outcome)));
    }

    private static Answer answer(QuestionSpec question, Phase asked, Random random, double correctRatio) {
        if (question.situation() == Situation.IF_OWN_HEALTH_CRITICAL) {
            return ExecutionFixture.prefer(FALL_BACK, 0.7, 0.8);
        }
        if (question.situation() == Situation.IF_TARGET_IN_MELEE) {
            return ExecutionFixture.prefer(asked.shield() ? KEEP : PRESS, 0.7, 0.8);
        }
        var acceptable = asked.acceptable().stream().sorted((a, b) -> a.value().compareTo(b.value())).toList();
        var others = new ArrayList<>(List.of(PRESS, KEEP, FALL_BACK, GUARD));
        others.removeAll(acceptable);
        var correct = random.nextDouble() < correctRatio || others.isEmpty();
        var chosen = correct ? acceptable.get(random.nextInt(acceptable.size()))
                : others.get(random.nextInt(others.size()));
        return ExecutionFixture.prefer(chosen, 0.7, 0.8);
    }

    /**
     * The profile of both arms: the four tactics of the execution fixture and a low-health context that allows only
     * {@code fall_back} and {@code keep_range}, like the {@code low_health_phase} of the example, so the local planner
     * of the arm without Jev has the same classic rule the MythicMobs boss of M13.5 has. While the context is active the
     * reader reports only its tactics as eligible, as {@code Eligibility} does in the capture.
     */
    private static TacticalProfile withLowHealthContext(DecisionTiming timing) {
        var profile = ExecutionFixture.profile(timing, 0);
        var context = new ContextRule(LOW_HEALTH_PHASE, List.of("healthpercent{p=<30%}"), 90, Set.of(FALL_BACK, KEEP),
                Optional.empty());
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), List.of(context), profile.movement(), profile.health(), profile.threat(),
                profile.baseAi(), profile.timing());
    }

    /** Mean, standard deviation, minimum and maximum of a series. */
    record Spread(double mean, double deviation, double minimum, double maximum) {
        static Spread of(List<Double> values) {
            var mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            var variance = values.stream().mapToDouble(value -> (value - mean) * (value - mean)).sum()
                    / Math.max(1, values.size() - 1);
            return new Spread(mean, Math.sqrt(variance), values.stream().mapToDouble(Double::doubleValue).min()
                    .orElse(0), values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        }
    }

    /**
     * Runs {@code repetitions} seeds of both arms, alternating which arm goes first, as M13.5 asks.
     *
     * @return the results of each arm
     */
    static Map<Arm, List<Result>> repeat(Scenario scenario, int repetitions, double correctRatio) {
        return repeat(scenario, repetitions, correctRatio, DEFAULTS);
    }

    /**
     * Runs {@code repetitions} seeds of both arms with another decision timing, alternating which arm goes first.
     *
     * @return the results of each arm
     */
    static Map<Arm, List<Result>> repeat(Scenario scenario, int repetitions, double correctRatio,
                                         DecisionTiming timing) {
        var results = new HashMap<Arm, List<Result>>();
        for (var seed = 1; seed <= repetitions; seed++) {
            var order = seed % 2 == 0 ? List.of(Arm.WITH_JEV, Arm.WITHOUT_JEV) : List.of(Arm.WITHOUT_JEV, Arm.WITH_JEV);
            for (var arm : order) {
                results.computeIfAbsent(arm, key -> new ArrayList<>()).add(run(scenario, arm, seed, correctRatio,
                        timing));
            }
        }
        return Map.copyOf(results);
    }
}
