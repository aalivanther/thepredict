package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CandidateIntent;
import akyrion.thepredict.domain.CandidatePolicy;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MicroPolicy;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ReactionPolicy;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.TickSignals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Measured cost of the tick layer on this machine (M20 E3). No server. The step bound is the configured cap plus the
 * visits that run after the clock is read: one cast, or eight visits that did not cast.
 */
public final class TickPassMeasure {
    /** Cap used by {@code decision.tickLayer.maxMicros}. */
    public static final int MAX_MICROS = 600;
    private static final int SAMPLES = 40;
    private static final int BATCH = 50;

    private TickPassMeasure() {
    }

    /** Markdown table of 20, 100 and 200 sessions, without and with micro. */
    public static String table() {
        var out = new StringBuilder();
        out.append("Sessions mix 50% HOT, 30% WARM, 20% COLD. Times are one pass, in µs, on this machine. ");
        out.append("No server sample. Step bound: maxMicros ").append(MAX_MICROS);
        out.append(" plus at most 8 visits without a cast plus 1 cast, which is when the clock is read.\n\n");
        out.append("| Sessions | Layer | mean | p50 | p95 | p99 | max | bound |\n");
        out.append("|---|---|---|---|---|---|---|---|\n");
        passes(200, TickLayerMode.FULL);
        passes(200, TickLayerMode.REACTIONS);
        for (var sessions : new int[] {20, 100, 200}) {
            row(out, sessions, TickLayerMode.REACTIONS, "sin micro (REACTIONS)");
            row(out, sessions, TickLayerMode.FULL, "con micro (FULL)");
        }
        out.append('\n');
        out.append(components());
        return out.toString();
    }

    private static void row(StringBuilder out, int sessions, TickLayerMode mode, String label) {
        var samples = passes(sessions, mode);
        var max = samples[samples.length - 1];
        out.append(String.format(Locale.ROOT, "| %d | %s | %.3f | %.3f | %.3f | %.3f | %.3f | %s |%n", sessions, label,
                mean(samples), percentile(samples, 0.50), percentile(samples, 0.95), percentile(samples, 0.99), max,
                max <= MAX_MICROS ? "cumple" : "no cumple"));
    }

    private static String components() {
        var out = new StringBuilder("Component rows, µs per call unless noted. ReactionPolicy p50 gate 100 ns. ");
        out.append("MicroPolicy p50 gate 50 ns. castIfReady gates 1.5 µs mean and 5 µs p99.\n\n");
        out.append("| Piece | mean | p50 | p95 | p99 | max | gate |\n|---|---|---|---|---|---|---|\n");
        line(out, "ReactionPolicy", nanos(TickPassMeasure::reaction), 0.100, "p50 <= 100 ns");
        line(out, "MicroPolicy", nanos(TickPassMeasure::micro), 0.050, "p50 <= 50 ns");
        line(out, "reorder CandidatePolicy.select", nanos(TickPassMeasure::reorder), Double.NaN, "measured");
        line(out, "tickSignals lower bound (doubles, no server)", nanos(TickPassMeasure::signalBound), 1.5,
                "p99 <= 1.5 µs");
        line(out, "castIfReady", nanos(TickPassMeasure::readyCast), 5.0, "p99 <= 5 µs");
        return out.toString();
    }

    private static void line(StringBuilder out, String name, double[] micros, double gate, String label) {
        var p50 = percentile(micros, 0.50);
        var p99 = percentile(micros, 0.99);
        var met = Double.isNaN(gate) || (name.startsWith("Reaction") || name.startsWith("Micro") ? p50 <= gate
                : p99 <= gate);
        out.append(String.format(Locale.ROOT, "| %s | %.4f | %.4f | %.4f | %.4f | %.4f | %s %s |%n", name, mean(micros),
                p50, percentile(micros, 0.95), p99, micros[micros.length - 1], met ? "cumple" : "no cumple", label));
    }

    private static double[] passes(int count, TickLayerMode mode) {
        var sessions = population(count, mode);
        var board = board(sessions);
        var samples = new double[SAMPLES];
        for (var index = 0; index < 5; index++) {
            visit(sessions, board);
        }
        for (var index = 0; index < SAMPLES; index++) {
            var started = System.nanoTime();
            visit(sessions, board);
            samples[index] = (System.nanoTime() - started) / 1000.0;
        }
        Arrays.sort(samples);
        return samples;
    }

    private static void visit(List<MobSession> sessions, IntentBoard board) {
        TickLayer.visit(sessions, sessions.size(), 0, 1, 1_000, board, reader(), steps(), new DecisionMetrics(() -> 0L),
                found -> {
                }, MAX_MICROS, 4, 0, System.nanoTime(), System::nanoTime, found -> {
                });
    }

    private static double[] nanos(Runnable work) {
        var samples = new double[SAMPLES];
        for (var index = 0; index < 5; index++) {
            work.run();
        }
        for (var index = 0; index < SAMPLES; index++) {
            var started = System.nanoTime();
            work.run();
            samples[index] = (System.nanoTime() - started) / 1000.0 / BATCH;
        }
        Arrays.sort(samples);
        return samples;
    }

    private static void reaction() {
        var signals = TickSignals.pack(TickSignals.PRESENT, 12, 1, 1, 0, 80, 40);
        var settings = movement();
        var cooldown = new long[akyrion.thepredict.domain.Reaction.values().length];
        var since = new long[cooldown.length];
        var chosen = 0;
        for (var index = 0; index < BATCH; index++) {
            chosen += ReactionPolicy.select(signals, index, 0, ReactionScope.ALL, false, true, Movement.APPROACH, 0.3,
                    settings, true, 0, cooldown, since);
        }
        if (chosen == Integer.MIN_VALUE) {
            throw new IllegalStateException("sink");
        }
    }

    private static void micro() {
        var settings = movement();
        var signals = TickSignals.PRESENT;
        var chosen = 0;
        for (var index = 0; index < BATCH; index++) {
            chosen += MicroPolicy.choose(Movement.APPROACH, signals, -1, false, true, 3.0, settings).ordinal();
        }
        if (chosen < 0) {
            throw new IllegalStateException("sink");
        }
    }

    private static void reorder() {
        var scored = List.of(
                new CandidateIntent(new TacticId("hold_ground"), Movement.HOLD, Optional.empty(), 10, 0),
                new CandidateIntent(new TacticId("press_attack"), Movement.APPROACH, Optional.empty(), 40, 0),
                new CandidateIntent(new TacticId("fall_back"), Movement.RETREAT, Optional.empty(), 20, 0));
        var order = List.of(scored.get(0).tactic(), scored.get(1).tactic(), scored.get(2).tactic());
        var sink = 0;
        for (var index = 0; index < BATCH; index++) {
            sink += CandidatePolicy.select(index, 0, scored, 3, false, order).candidates().size();
        }
        if (sink < BATCH) {
            throw new IllegalStateException("sink");
        }
    }

    private static void signalBound() {
        var sink = 0.0;
        for (var index = 0; index < BATCH; index++) {
            var gap = index * 0.25;
            var ratio = (18.0 + 2.0) / 20.0;
            var quarters = gap > 0.0 ? gap * 4.0 : 0.0;
            sink += ratio + quarters;
        }
        if (sink < 0) {
            throw new IllegalStateException("sink");
        }
    }

    private static CastHarness ready;
    private static void readyCast() {
        if (ready == null) {
            ready = castHarness();
        }
        for (var index = 0; index < BATCH; index++) {
            ready.control.castIfReady(ready.session, 1, 1_000);
        }
    }

    private static CastHarness castHarness() {
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var control = new IntentControl(sessions.board, new IdleActions(), metrics, new ExecutionLog(line -> {
        }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics),
                DecisionTap.NONE);
        var session = sessions.open(1);
        session.adoptIntent(EngineFixtures.intent(session.key()));
        session.scheduleReady(1);
        return new CastHarness(control, session);
    }

    private static List<MobSession> population(int count, TickLayerMode mode) {
        var hot = count / 2;
        var warm = count * 3 / 10;
        var sessions = new ArrayList<MobSession>(count);
        for (var index = 0; index < count; index++) {
            var session = session(mode);
            if (index < hot) {
                session.recordTarget(UUID.randomUUID(), 3);
                session.scheduleReady(1);
            } else if (index < hot + warm) {
                var target = UUID.randomUUID();
                session.recordTarget(target, 12);
                session.noteProbe(new MobProbe(session.key(), new UUID(0, 1), HealthBand.HEALTHY, Optional.of(target),
                        Optional.of(DistanceBand.BEYOND)), 0);
                session.scheduleReady(8);
            }
            sessions.add(session);
        }
        return sessions;
    }

    private static IntentBoard board(List<MobSession> sessions) {
        var board = new IntentBoard();
        for (var session : sessions) {
            if (session.lastTargetId().isEmpty()) {
                continue;
            }
            var intent = new TacticalIntent(session.key(), new TacticId("hold_ground"), Movement.HOLD, Optional.empty(),
                    0.9, List.of(), Map.of(), ControlLevel.JEV, 0, Long.MAX_VALUE, false);
            session.adoptIntent(intent);
            board.publish(session.key(), intent.toView());
        }
        return board;
    }

    private static MobSession session(TickLayerMode mode) {
        var hold = new TacticId("hold_ground");
        var timing = new DecisionTiming(40, 20, 100, 0.5, 0.2, 1).withLayer(mode, ReactionScope.ALL, java.util.Set.of(),
                0, DecisionCadence.STANDARD, 6, 40);
        var profile = new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(hold, new akyrion.thepredict.domain.Tactic(hold, Movement.HOLD, Optional.empty(),
                        Optional.empty(), 50)),
                Map.of(), List.of(), movement(), new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10),
                List.of(), timing);
        return new MobSession(new MobKey(UUID.randomUUID(), 1), profile);
    }

    private static MovementSettings movement() {
        return new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty());
    }

    private static double mean(double[] values) {
        var sum = 0.0;
        for (var value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double percentile(double[] sorted, double fraction) {
        var index = (int) Math.ceil(fraction * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        }
        return sorted[Math.min(index, sorted.length - 1)];
    }

    private static CombatReader reader() {
        return new CombatReader() {
            @Override
            public Optional<akyrion.thepredict.domain.CombatFacts> capture(
                    akyrion.thepredict.domain.EncounterId encounter, List<MobKey> members,
                    akyrion.thepredict.domain.CaptureContext context) {
                return Optional.empty();
            }

            @Override
            public Optional<MobProbe> probe(MobKey mob) {
                return Optional.empty();
            }

            @Override
            public boolean inCombat(UUID entityId) {
                return false;
            }

            @Override
            public boolean withinFollowRange(MobKey mob, MobKey member) {
                return false;
            }

            @Override
            public boolean isActive(UUID entityId) {
                return true;
            }

            @Override
            public Optional<akyrion.thepredict.domain.ThreatTableState> threatTable(MobKey mob) {
                return Optional.empty();
            }
        };
    }

    private static ExecutionSteps steps() {
        return new ExecutionSteps() {
            @Override
            public void accept(InboxEntry.CompletedRequest completed, long tick, long nowNanos) {
            }

            @Override
            public void react(MobSession found, MobProbe probe, long tick, long nowNanos) {
            }

            @Override
            public void evaluated(Encounter encounter, long tick, long nowNanos) {
            }

            @Override
            public void castReady(MobSession found, long tick, long nowNanos) {
            }
        };
    }

    private record CastHarness(IntentControl control, MobSession session) {
    }

    private static final class IdleActions implements CombatActions {
        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return false;
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }
    }
}
