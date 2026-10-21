package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Engine runs of the golden references of M17.0 (rules P2, A8): the load steps of M13.3 with 1, 10 and 30 mobs and
 * the local A/B of M13.5 (five scenarios, ten seeds, both arms), each with a decision client double that prepares every
 * request with the real encoder of the System One client, without any network. Each run writes the SHA-256 of every
 * body sent, in order; the complete bodies of the first {@value #FULL_BODIES} requests of each scenario; a log of the
 * published {@link IntentView} of each mob, one line only when it changes; and the counters of
 * {@link DecisionMetrics}, {@link ThreatMetrics}, {@link SessionMetrics} and {@link OutcomeMetrics} at the end. The
 * text is deterministic: the fixtures move a scripted clock and the answers come from seeded generators.
 */
public final class GoldenEngineRuns {
    /** Complete bodies kept per scenario; the rest are kept as their SHA-256 only. */
    public static final int FULL_BODIES = 20;
    /** Mob counts of the load steps. */
    public static final List<Integer> LOAD_STEPS = List.of(1, 10, 30);
    /** Seeds of each A/B scenario. */
    public static final int AB_SEEDS = 10;
    /** Probability that the scripted Jev of the A/B answers an acceptable tactic, as its fairness test uses. */
    public static final double AB_CORRECT_RATIO = 0.85;

    private GoldenEngineRuns() {
    }

    /**
     * The golden text of every engine run, by file name.
     *
     * @param preparation preparation of the decision client double: the encoder of the System One client
     * @return file name to text, in a fixed order
     */
    public static Map<String, String> all(Function<DecisionRequest, Preparation> preparation) {
        var files = new LinkedHashMap<String, String>();
        for (var mobs : LOAD_STEPS) {
            files.put("load-" + mobs + ".txt", load(mobs, preparation));
        }
        for (var scenario : TacticalAB.Scenario.values()) {
            files.put("ab-" + scenario.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".txt",
                    ab(scenario, preparation));
        }
        return files;
    }

    /**
     * Ten minutes of a load step of M13.3 with its script.
     *
     * @param mobCount mobs of the step
     * @param preparation preparation of the decision client double
     * @return the golden text
     */
    public static String load(int mobCount, Function<DecisionRequest, Preparation> preparation) {
        var text = new StringBuilder("# M17.0 golden: load step of M13.3, ").append(mobCount)
                .append(" mobs, ").append(LoadScenarios.TEN_MINUTES_TICKS).append(" ticks\n");
        try (var fixture = LoadScenarios.combat(mobCount)) {
            fixture.jev.preparation = preparation;
            var script = LoadScenarios.combatScript(fixture, mobCount);
            var views = new ViewLog();
            for (var tick = 0; tick < LoadScenarios.TEN_MINUTES_TICKS; tick++) {
                fixture.run(1, script);
                for (var index = 1; index <= mobCount; index++) {
                    views.observe(fixture.clock.tick, index, fixture.sessions.board.current(LoadFixture.mob(index)));
                }
            }
            bodies(text, fixture.jev.bodies, Math.min(FULL_BODIES, fixture.jev.bodies.size()));
            text.append("views\n").append(views.text());
            counters(text, fixture.metrics, fixture.threat.metrics(), fixture.sessions.metrics,
                    fixture.outcomes.metrics());
            text.append("casts ").append(fixture.actions.casts).append(" signals ").append(fixture.actions.signals)
                    .append(" threatChanges ").append(fixture.actions.threatChanges).append('\n');
        }
        return text.toString();
    }

    /**
     * Real bodies of a duel of factions (M17.6, M17.9): four mobs of ThePredict, two red and two blue, each targeting
     * a mob of the other faction for one minute, so every request carries {@code entity_N} participants with their
     * species. Not a reference: {@code EntityGeneralizationParityTest} checks that these bodies carry no UUID, name or
     * faction.
     *
     * @param preparation preparation of the decision client double: the encoder of the System One client
     * @return the bodies sent, in order
     */
    public static List<String> factionDuelBodies(Function<DecisionRequest, Preparation> preparation) {
        var mobs = 4;
        try (var fixture = new LoadFixture()) {
            fixture.jev.preparation = preparation;
            for (var index = 1; index <= mobs; index++) {
                fixture.arena.factions.put(LoadFixture.mob(index), index % 2 == 1 ? "red" : "blue");
                fixture.arena.mobs.put(LoadFixture.mob(index), new LoadFixture.Body(LoadFixture.WORLD_A, index, 0));
            }
            for (var index = 1; index <= mobs; index++) {
                fixture.arena.targets.put(LoadFixture.mob(index), LoadFixture.mob(index % 2 == 1 ? index + 1
                        : index - 1));
                fixture.sessions.lifecycle.discovered(LoadFixture.mob(index), fixture.profile);
            }
            fixture.ready();
            fixture.run(LoadScenarios.MINUTE_TICKS, tick -> {
                for (var index = 1; index <= mobs; index++) {
                    if ((tick + index) % 20 == 0) {
                        fixture.signals.offer(LoadFixture.mob(index));
                    }
                }
            });
            return List.copyOf(fixture.jev.bodies);
        }
    }

    /**
     * The ten seeds of an A/B scenario of M13.5, both arms in the alternating order of its fairness test.
     *
     * @param scenario scenario of M13.5
     * @param preparation preparation of the decision client double
     * @return the golden text
     */
    public static String ab(TacticalAB.Scenario scenario, Function<DecisionRequest, Preparation> preparation) {
        var text = new StringBuilder("# M17.0 golden: local A/B of M13.5, ").append(scenario).append(", ")
                .append(AB_SEEDS).append(" seeds, correct ratio ").append(AB_CORRECT_RATIO).append('\n');
        var fullBodiesLeft = new int[] {FULL_BODIES};
        for (var seed = 1; seed <= AB_SEEDS; seed++) {
            var order = seed % 2 == 0 ? List.of(TacticalAB.Arm.WITH_JEV, TacticalAB.Arm.WITHOUT_JEV)
                    : List.of(TacticalAB.Arm.WITHOUT_JEV, TacticalAB.Arm.WITH_JEV);
            for (var arm : order) {
                var views = new ViewLog();
                var last = new ExecutionFixture[1];
                var result = TacticalAB.run(scenario, arm, seed, AB_CORRECT_RATIO, TacticalAB.DEFAULTS,
                        new TacticalAB.Hooks(preparation, (tick, fixture) -> {
                            views.observe(fixture.clock.tick, 1, fixture.view(1));
                            last[0] = fixture;
                        }));
                var fixture = last[0];
                text.append("run seed=").append(seed).append(" arm=").append(arm).append('\n');
                text.append(String.format(Locale.ROOT, "result useful=%.6f casts=%d skippedShield=%d deferred=%d "
                        + "switches=%d requests=%d tokens=%d duplicates=%d effect=%.6f hitRate=%.6f%n",
                        result.usefulShare(), result.casts(), result.skippedShield(), result.deferredInvulnerable(),
                        result.tacticSwitches(), result.requests(), result.tokens(), result.duplicateCasts(),
                        result.effect(), result.hitRate()));
                var bodies = fixture.client.sent.stream().map(PreparedRequest::body).toList();
                var full = Math.min(fullBodiesLeft[0], bodies.size());
                bodies(text, bodies, full);
                fullBodiesLeft[0] -= full;
                text.append("views\n").append(views.text());
                counters(text, fixture.metrics, fixture.threat.metrics(), fixture.sessions.metrics,
                        fixture.outcomes.metrics());
            }
        }
        return text.toString();
    }

    private static void bodies(StringBuilder text, List<String> bodies, int fullLimit) {
        text.append("requests ").append(bodies.size()).append(" (index and SHA-256 of each body sent, in order)\n");
        for (var index = 0; index < bodies.size(); index++) {
            text.append(index).append(' ').append(sha256(bodies.get(index))).append('\n');
        }
        for (var index = 0; index < fullLimit; index++) {
            text.append("body ").append(index).append('\n').append(bodies.get(index)).append('\n');
        }
    }

    /** The counters of the engine at the end of a run, one per line, in the order of their enums. */
    private static void counters(StringBuilder text, DecisionMetrics decisions, ThreatMetrics threat,
                                 SessionMetrics sessions, OutcomeMetrics outcomes) {
        text.append("counters\n");
        for (var counter : DecisionMetrics.Counter.values()) {
            text.append("decision.").append(counter).append(' ').append(decisions.total(counter)).append('\n');
        }
        for (var span : DecisionMetrics.Span.values()) {
            text.append("decision.samples.").append(span).append(' ').append(decisions.samples(span)).append('\n');
        }
        List<Enum<?>> causes = new ArrayList<>();
        causes.addAll(List.of(FailureKind.values()));
        for (var outcome : DecisionDispatcher.Outcome.values()) {
            if (outcome != DecisionDispatcher.Outcome.CACHE_HIT) {
                causes.add(outcome);
            }
        }
        causes.addAll(List.of(FallbackReason.values()));
        causes.addAll(List.of(ExecutionOutcome.values()));
        for (var reason : DiscardReason.values()) {
            if (reason != DiscardReason.SITUATION_CHANGED && reason != DiscardReason.GROUP_EPOCH_CHANGED) {
                causes.add(reason);
            }
        }
        for (var cause : causes) {
            text.append("decision.").append(cause.getDeclaringClass().getSimpleName()).append('.').append(cause)
                    .append(' ').append(decisions.count(cause)).append('\n');
        }
        text.append("threat assists=").append(threat.assists()).append(" blockedHits=").append(threat.blockedHits())
                .append(" rangedHarass=").append(threat.rangedHarass()).append(" decays=").append(threat.decays())
                .append(" limited=").append(threat.limited()).append(" failures=").append(threat.failures())
                .append(" added=").append(decimal(threat.pointsAdded())).append(" removed=")
                .append(decimal(threat.pointsRemoved())).append('\n');
        text.append("sessions opened=").append(sessions.openedSessions());
        for (var end : SessionMetrics.SessionEnd.values()) {
            text.append(' ').append(end).append('=').append(sessions.endedSessions(end));
        }
        text.append(" encounters=").append(sessions.openedEncounters());
        for (var end : SessionMetrics.EncounterEnd.values()) {
            text.append(' ').append(end).append('=').append(sessions.closedEncounters(end));
        }
        text.append(" averageTicks=").append(sessions.averageEncounterTicks()).append(" failedReleases=")
                .append(sessions.failedReleases()).append('\n');
        for (var level : ControlLevel.values()) {
            for (var movement : Movement.values()) {
                var windows = outcomes.windows(level, movement);
                if (windows > 0) {
                    text.append("outcomes ").append(level).append(' ').append(movement).append(' ').append(windows)
                            .append('\n');
                }
            }
        }
        for (var level : outcomes.byLevel()) {
            text.append("outcomes ").append(level.level()).append(" windows=").append(level.windows())
                    .append(" dealt=").append(decimal(level.dealt())).append(" taken=").append(decimal(level.taken()))
                    .append(" reach=").append(decimal(level.reachRate())).append('\n');
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    /**
     * SHA-256 of a body in UTF-8, in lower-case hexadecimal.
     *
     * @param body request body
     * @return 64 hexadecimal digits
     */
    public static String sha256(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Log of the published views. Every change of generation, level, movement, tactic, confidence, expiry, micro or
     * zone goes into one SHA-256; the text keeps one line only when the generation, level, movement or tactic changes.
     * Posture, fury, the tell answer, poise and the hit tick are not part of this trace (M23.8): counting them would
     * rewrite the M17 references.
     */
    private static final class ViewLog {
        private final Map<Integer, IntentView> last = new HashMap<>();
        private final StringBuilder text = new StringBuilder();
        private final MessageDigest digest;
        private long changes;

        ViewLog() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        void observe(long tick, int mob, IntentView view) {
            var previous = last.put(mob, view);
            if (previous != null && sameTrace(previous, view)) {
                return;
            }
            changes++;
            digest.update((tick + " " + mob + " " + view.generation() + " " + view.level() + " " + view.movement() + " "
                    + view.tactic().value() + " " + String.format(Locale.ROOT, "%.4f", view.confidence()) + " "
                    + view.expiresAtNanos() + "\n").getBytes(StandardCharsets.UTF_8));
            if (previous != null && previous.generation() == view.generation() && previous.level() == view.level()
                    && previous.movement() == view.movement() && previous.tactic().equals(view.tactic())) {
                return;
            }
            text.append(tick).append(" m").append(mob).append(" g").append(view.generation()).append(' ')
                    .append(view.level()).append(' ').append(view.movement()).append(' ')
                    .append(view.tactic().value()).append('\n');
        }

        String text() {
            return "changes " + changes + " sha256 " + HexFormat.of().formatHex(digest.digest())
                    + " (tick, mob, generation, level, movement and tactic when one of them changes)\n" + text;
        }

        private static boolean sameTrace(IntentView previous, IntentView view) {
            return previous.generation() == view.generation() && previous.level() == view.level()
                    && previous.movement() == view.movement() && previous.tactic().equals(view.tactic())
                    && Float.compare(previous.confidence(), view.confidence()) == 0
                    && previous.expiresAtNanos() == view.expiresAtNanos() && previous.micro() == view.micro()
                    && previous.microSide() == view.microSide() && previous.pressure() == view.pressure()
                    && previous.zoneFacts() == view.zoneFacts() && previous.pushDanger() == view.pushDanger();
        }
    }
}
