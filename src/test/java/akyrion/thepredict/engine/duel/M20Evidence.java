package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickLayerMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * M20 E1 and E2 outside {@code check}. FULL, REACTIONS and the reactive cadence are overrides of the duel profiles.
 * The pack YAML and the product defaults stay as they are. No network and no claim of superiority.
 */
public final class M20Evidence {
    private static final int SEEDS = 200;
    private static final List<DuelSimulator.Arm> ARMS = List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B,
            DuelSimulator.Arm.C, DuelSimulator.Arm.D);

    private M20Evidence() {
    }

    /**
     * Writes {@code duel-tick.md}.
     *
     * @param arguments the report file
     * @throws IOException when the report cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var report = Path.of(arguments[0]).toAbsolutePath();
        var out = new StringBuilder("# M20 duel evidence\n\n");
        out.append("Overrides via DecisionTiming.withLayer. Defaults stay REACTIONS and REACTIVE. ");
        out.append("The duel pack stays casts and standard. No superiority claim.\n\n");
        var started = System.nanoTime();
        out.append(determinism());
        out.append(matrix());
        out.append(String.format(Locale.ROOT, "Measured in %.1f s on this machine.%n",
                (System.nanoTime() - started) / 1e9));
        Files.createDirectories(report.getParent());
        Files.writeString(report, out.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + report);
    }

    private static String determinism() {
        var profiles = layered(TickLayerMode.FULL, DecisionCadence.STANDARD);
        var arms = List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.C);
        var first = digests(profiles, arms);
        var second = digests(profiles, arms);
        var mismatches = 0;
        var compared = 0;
        for (var arm : arms) {
            for (var seed = 1; seed <= SEEDS; seed++) {
                compared++;
                var left = first.get(arm).get(seed);
                var right = second.get(arm).get(seed);
                if (!Arrays.equals(left.digest().getBytes(StandardCharsets.UTF_8),
                        right.digest().getBytes(StandardCharsets.UTF_8)) || left.winner() != right.winner()
                        || left.ticks() != right.ticks() || !left.casts().equals(right.casts())) {
                    mismatches++;
                }
            }
        }
        return String.format(Locale.ROOT, "## E1. FULL byte for byte, arms A and C, seeds 1–%d%n%nTwo passes. "
                + "Compared %d digests. Mismatches %d. %s. golden/m20 was not rewritten.%n%n", SEEDS, compared,
                mismatches, mismatches == 0 ? "cumple" : "no cumple");
    }

    private static Map<DuelSimulator.Arm, Map<Integer, Shot>> digests(Map<String, TacticalProfile> profiles,
                                                                     List<DuelSimulator.Arm> arms) {
        var simulator = new DuelSimulator(new DuelSimulator.Mode.None(), profiles, (DuelCastAudit) null);
        var out = new EnumMap<DuelSimulator.Arm, Map<Integer, Shot>>(DuelSimulator.Arm.class);
        for (var arm : arms) {
            out.put(arm, new LinkedHashMap<>());
        }
        for (var seed = 1; seed <= SEEDS; seed++) {
            for (var arm : arms) {
                var result = simulator.round(arm, seed, seed % 2 == 0, DuelSimulator.Options.PLAIN);
                out.get(arm).put(seed, new Shot(result.eventDigest(), result.winner(), result.ticks(),
                        result.redCasts().toString() + result.blueCasts()));
            }
            if (seed % 50 == 0) {
                System.out.println("E1 seed " + seed);
            }
        }
        return out;
    }

    private static String matrix() {
        var out = new StringBuilder("## E2. REACTIONS and FULL against CASTS\n\n");
        out.append("ORACLE(0.85), ").append(SEEDS).append(" paired seeds, arms A–D. ");
        out.append("JEV coverage is JEV ticks / (JEV + ALTERNATIVE + LOCAL) on arm A. ");
        out.append("Gate 80%. Wilson lower bound of arm A must not fall below CASTS. ");
        out.append("Objective Wilson lower > 0.5 is not a gate. The simulator does not sample MSPT; ");
        out.append("the step bound is E3. The duel world does not run MicroGoal, so FULL can match REACTIONS.\n\n");
        Play casts = null;
        for (var layer : List.of(
                new Layer("CASTS baseline", TickLayerMode.CASTS, DecisionCadence.STANDARD),
                new Layer("REACTIONS", TickLayerMode.REACTIONS, DecisionCadence.REACTIVE),
                new Layer("FULL", TickLayerMode.FULL, DecisionCadence.REACTIVE))) {
            var play = play(layered(layer.mode(), layer.cadence()));
            out.append(DuelMatrix.markdown(layer.name(), play.matrix()));
            out.append(gate(layer.name(), play, casts));
            if (casts == null) {
                casts = play;
            }
        }
        return out.toString();
    }

    private static Play play(Map<String, TacticalProfile> profiles) {
        var audit = new DuelCastAudit();
        var mode = new DuelSimulator.Mode.Oracle(0.85, 20260929L);
        var simulator = new DuelSimulator(mode, profiles, audit);
        var results = new EnumMap<DuelSimulator.Arm, List<DuelResult>>(DuelSimulator.Arm.class);
        for (var index = 0; index < SEEDS; index++) {
            var order = index % 2 == 0 ? ARMS : ARMS.reversed();
            for (var arm : order) {
                results.computeIfAbsent(arm, key -> new ArrayList<>()).add(simulator.round(arm, index,
                        (index / 2) % 2 == 1, DuelSimulator.Options.PLAIN));
            }
            if ((index + 1) % 50 == 0) {
                System.out.println("E2 rounds " + (index + 1));
            }
        }
        var matrix = new EnumMap<DuelSimulator.Arm, DuelMatrix.Summary>(DuelSimulator.Arm.class);
        results.forEach((arm, list) -> matrix.put(arm, DuelMatrix.summary(arm, list)));
        return new Play(matrix, audit, results.get(DuelSimulator.Arm.A));
    }

    private static String gate(String name, Play play, Play baseline) {
        var arm = play.matrix().get(DuelSimulator.Arm.A);
        var jev = arm.levelTicks().getOrDefault(ControlLevel.JEV, 0L);
        var alternative = arm.levelTicks().getOrDefault(ControlLevel.ALTERNATIVE, 0L);
        var local = arm.levelTicks().getOrDefault(ControlLevel.LOCAL, 0L);
        var controlled = jev + alternative + local;
        var coverage = controlled == 0 ? 0 : jev / (double) controlled;
        var duplicates = play.matrix().values().stream().mapToLong(DuelMatrix.Summary::duplicateCasts).sum();
        var wilson = baseline == null || arm.wilson()[0] + 1e-9 >= baseline.matrix().get(DuelSimulator.Arm.A).wilson()[0];
        var ttk = medianWins(play.armA());
        var baseTtk = baseline == null ? ttk : medianWins(baseline.armA());
        var ttkMet = baseline == null || !(ttk > baseTtk);
        var coverageMet = name.startsWith("CASTS") || coverage + 1e-9 >= 0.80;
        var met = duplicates == 0 && play.audit().preparationStarts == 0 && wilson && ttkMet && coverageMet;
        return String.format(Locale.ROOT, "Gate %s: %s. Duplicates %d (%s). Preparation starts %d (%s). "
                + "Late ready ticks %d. Arm A Wilson [%.3f, %.3f] vs CASTS (%s). Median TTK on red wins %.1f vs CASTS "
                + "%.1f (%s). JEV coverage %.1f%% of controlled ticks (%s). Objective Wilson lower > 0.5: %s.%n%n",
                name, met ? "cumple" : "no cumple", duplicates, duplicates == 0 ? "cumple" : "no cumple",
                play.audit().preparationStarts, play.audit().preparationStarts == 0 ? "cumple" : "no cumple",
                play.audit().lateTicks, arm.wilson()[0], arm.wilson()[1], wilson ? "cumple" : "no cumple", ttk, baseTtk,
                ttkMet ? "cumple" : "no cumple", coverage * 100.0,
                coverageMet ? "cumple" : "no cumple — risk: JEV covers under 80% of controlled ticks",
                arm.wilson()[0] > 0.5 ? "seen" : "not seen, not a gate");
    }

    private static double medianWins(List<DuelResult> results) {
        var wins = new ArrayList<Integer>();
        for (var result : results) {
            if (result.winner() == DuelResult.Winner.RED) {
                wins.add(result.ticks());
            }
        }
        if (wins.isEmpty()) {
            return Double.NaN;
        }
        wins.sort(Integer::compareTo);
        var mid = wins.size() / 2;
        if (wins.size() % 2 == 1) {
            return wins.get(mid);
        }
        return (wins.get(mid - 1) + wins.get(mid)) / 2.0;
    }

    private static Map<String, TacticalProfile> layered(TickLayerMode mode, DecisionCadence cadence) {
        var out = new LinkedHashMap<String, TacticalProfile>();
        DuelSimulator.packs().forEach((id, profile) -> out.put(id, copy(profile, mode, cadence)));
        return Map.copyOf(out);
    }

    private static TacticalProfile copy(TacticalProfile profile, TickLayerMode mode, DecisionCadence cadence) {
        var timing = profile.timing();
        var next = timing.withLayer(mode, timing.reactions(), timing.reactionsAhead(), timing.reactionDelayTicks(),
                cadence, timing.reactiveIntervalTicks(), timing.intentHorizonTicks());
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), profile.contexts(), profile.movement(), profile.health(), profile.threat(),
                profile.baseAi(), next, profile.participants());
    }

    private record Shot(String digest, DuelResult.Winner winner, int ticks, String casts) {
    }

    private record Layer(String name, TickLayerMode mode, DecisionCadence cadence) {
    }

    private record Play(Map<DuelSimulator.Arm, DuelMatrix.Summary> matrix, DuelCastAudit audit, List<DuelResult> armA) {
    }
}
