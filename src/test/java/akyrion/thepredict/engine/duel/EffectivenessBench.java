package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.TacticalProfile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The task {@code effectivenessBench}, outside {@code check} (§7 of the simulator audit): does ThePredict win at least
 * 75 % against the native AI and against good MythicMobs configurations, in the local simulator and without spending
 * on Jev? It loads the ThePredict profile of each kit with the real loader in two configurations (the pack as it is,
 * and the defaults of delivery), builds the rivals ({@code vanilla}, {@code pack}, {@code legal-equal}, and
 * {@code legal-best} and {@code adversarial} from the frozen files or from a new search), checks the validity gates,
 * plays the cells and the sensitivity to Jev, and writes {@code banco.md} and its tables. The criterion is fixed before
 * measuring and the report is written whether it passes or not. No network, no cost.
 */
public final class EffectivenessBench {
    private EffectivenessBench() {
    }

    /**
     * Writes the report and its tables into the given folder.
     *
     * @param arguments the output folder; system properties {@code thepredict.bench.seeds},
     *     {@code thepredict.bench.threads}, {@code thepredict.bench.search} ({@code auto}, {@code force} or
     *     {@code never}) and {@code thepredict.bench.frozen} change the run
     * @throws IOException when a file cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var output = Path.of(arguments[0]).toAbsolutePath();
        var settings = BenchSettings.fromProperties();
        var outcome = run(settings, output);
        BenchReport.write(outcome, output);
        System.out.println("Wrote " + output.resolve("banco.md") + " in " + Math.round(outcome.seconds()) + " s");
    }

    /**
     * One full run.
     *
     * @param settings sizes, seeds, threads and where the frozen rivals live
     * @param output the output folder, which also receives the generated profiles
     * @return everything the report needs
     */
    static BenchOutcome run(BenchSettings settings, Path output) {
        var started = System.nanoTime();
        try (var pool = new BenchPool(settings.threads())) {
            var runner = new BenchRunner(pool, DuelPhysics.VANILLA, TickRules.FAIR, profiles(settings, output));
            var rivals = new BenchRivals(settings, runner, output);
            progress(started, "rivales listos");
            var gates = gates(settings, runner, rivals);
            progress(started, "puertas V1, V2, V4, V5 y V6");
            var cells = new EnumMap<BenchProfiles.Timing, List<BenchCell.Result>>(BenchProfiles.Timing.class);
            for (var timing : settings.timings()) {
                cells.put(timing, cells(settings, runner, rivals, timing));
                gates.add(BenchGates.sides(cells.get(timing), timing));
                progress(started, "celdas de " + timing.label);
            }
            var jev = new ArrayList<BenchCell.Result>();
            for (var timing : settings.jevTimings()) {
                jev.addAll(jev(settings, runner, rivals, timing));
                progress(started, "sensibilidad a Jev de " + timing.label);
            }
            var sensitivities = sensitivities(settings, runner, rivals);
            var arms = BenchArms.measure(settings, runner);
            progress(started, "sensibilidades y matriz A-D");
            return new BenchOutcome(settings, gates, cells, jev, sensitivities, rivals.rivals(), rivals.searches(),
                    arms, (System.nanoTime() - started) / 1e9);
        }
    }

    private static void progress(long started, String what) {
        System.out.printf(java.util.Locale.ROOT, "[banco] %s (%.0f s)%n", what, (System.nanoTime() - started) / 1e9);
    }

    /**
     * The task {@code evalTuneClassic} of M22.3 (SIM-02): the best classic of the legal space of the duel pack against
     * a league of classics, from the frozen file when it still matches, otherwise from a new search.
     *
     * @param frozen the folder of the frozen rivals
     * @param output the folder for the generated profiles
     * @param threads threads
     * @return a report in Markdown
     */
    public static String tune(Path frozen, Path output, int threads) {
        var full = BenchSettings.full(frozen, threads);
        var settings = new BenchSettings(full.seeds(), full.jevSeeds(), full.gateSeeds(), full.armRounds(), threads,
                BenchSettings.Search.AUTO, false, frozen, full.sizes(), List.of(Kit.K1), List.of(), List.of(),
                List.of(), full.accuracies(), full.latencies(), full.firstSeed());
        try (var pool = new BenchPool(threads)) {
            var runner = new BenchRunner(pool, DuelPhysics.VANILLA, TickRules.FAIR, profiles(settings, output));
            var rivals = new BenchRivals(settings, runner, output);
            var best = rivals.rivals().stream().filter(rival -> rival.name().equals("legal-best")).findFirst()
                    .orElseThrow();
            var out = new StringBuilder("# Clásico afinado (M22.3, evalTuneClassic)\n\n");
            out.append("- Origen: ").append(best.origin()).append(". Fuerza de Bradley–Terry en la liga final: ")
                    .append(String.format(java.util.Locale.ROOT, "%.3f", best.score())).append(".\n");
            out.append("- Código: `").append(best.config().code()).append("`\n- Huella: `")
                    .append(best.config().sha256()).append("`\n- ").append(best.config().describe()).append("\n\n");
            for (var search : rivals.searches()) {
                for (var step : search.steps()) {
                    out.append("- ").append(ClassicSearch.describe(step)).append('\n');
                }
            }
            return out.append("\n```yaml\n").append(best.config().toMythicYaml()).append("```\n").toString();
        }
    }

    private static Map<BenchProfiles.Timing, Map<Kit, Map<String, TacticalProfile>>> profiles(BenchSettings settings,
                                                                                         Path output) {
        var profiles = new EnumMap<BenchProfiles.Timing, Map<Kit, Map<String, TacticalProfile>>>(
                BenchProfiles.Timing.class);
        for (var timing : BenchProfiles.Timing.values()) {
            var byKit = new LinkedHashMap<Kit, Map<String, TacticalProfile>>();
            for (var kit : settings.kits()) {
                byKit.put(kit, BenchProfiles.load(kit, timing, output.resolve("perfiles").resolve(kit.id() + "-"
                        + timing.name().toLowerCase(java.util.Locale.ROOT))));
            }
            profiles.put(timing, byKit);
        }
        return profiles;
    }

    private static List<BenchGates.Gate> gates(BenchSettings settings, BenchRunner runner, BenchRivals rivals) {
        var main = Scenario.bench(Kit.K1, Arena.OPEN18, 60);
        var gates = new ArrayList<BenchGates.Gate>();
        gates.add(BenchGates.classicMirror(runner, main, rivals.config(Kit.K1, "legal-equal"), settings.gateSeeds()));
        for (var timing : settings.timings()) {
            gates.add(BenchGates.thePredictMirror(runner, main, timing, settings.gateSeeds()));
        }
        gates.add(BenchGates.doubleKill(runner.physics(), runner.rules()));
        var equals = new ArrayList<ClassicConfig>();
        for (var kit : settings.kits()) {
            if (!kit.skills().isEmpty()) {
                equals.add(rivals.config(kit, "legal-equal"));
            }
        }
        gates.add(BenchGates.sameStats(settings.scenarios(), rivals.movement(), equals));
        for (var timing : settings.timings()) {
            gates.add(BenchGates.forcedBase(runner, main, timing, rivals.config(Kit.K1, "legal-equal"),
                    Math.max(2, settings.gateSeeds() / 10)));
        }
        return gates;
    }

    private static List<BenchCell.Result> cells(BenchSettings settings, BenchRunner runner, BenchRivals rivals,
                                                BenchProfiles.Timing timing) {
        var results = new ArrayList<BenchCell.Result>();
        for (var scenario : settings.scenarios()) {
            for (var rival : rivals.names(scenario.kit(), timing)) {
                var spec = new BenchCell.Spec(rival, rivals.config(scenario.kit(), rival, timing), scenario, timing,
                        "NONE", Optional.empty());
                results.add(runner.cell(spec, settings.firstSeed(), settings.seeds()));
            }
        }
        return results;
    }

    /** Cells outside the verdict that show what a figure depends on. */
    private static List<BenchCell.Result> sensitivities(BenchSettings settings, BenchRunner runner,
                                                        BenchRivals rivals) {
        var results = new ArrayList<BenchCell.Result>(zombieHit(settings, runner, rivals));
        results.addAll(healApproach(settings, runner, rivals));
        results.addAll(speedParity(settings, runner, rivals));
        return results;
    }

    /**
     * Every cell of the verdict with the variant of the profile that chases at the fastest legal speed (E-4), against
     * the rivals of the defaults of delivery: what the gap of 1.15 against 1.2 costs. A decision of the user, not a
     * default.
     */
    private static List<BenchCell.Result> speedParity(BenchSettings settings, BenchRunner runner,
                                                      BenchRivals rivals) {
        var results = new ArrayList<BenchCell.Result>();
        if (!settings.timings().contains(BenchProfiles.Timing.DEFAULTS)) {
            return results;
        }
        for (var scenario : settings.scenarios()) {
            for (var rival : rivals.names(scenario.kit(), BenchProfiles.Timing.DEFAULTS)) {
                var spec = new BenchCell.Spec(rival, rivals.config(scenario.kit(), rival,
                        BenchProfiles.Timing.DEFAULTS), scenario, BenchProfiles.Timing.SPEED_PARITY, "NONE",
                        Optional.empty());
                results.add(runner.cell(spec, settings.firstSeed(), settings.seeds()));
            }
        }
        return results;
    }

    /**
     * The kit without skills with the vanilla hit of ThePredict held to the zombie's own reach and cadence instead of
     * {@code Mob.attack}.
     */
    private static List<BenchCell.Result> zombieHit(BenchSettings settings, BenchRunner runner, BenchRivals rivals) {
        var results = new ArrayList<BenchCell.Result>();
        if (!settings.kits().contains(Kit.K0)) {
            return results;
        }
        var zombie = new BenchRunner(runner.pool(), runner.physics(), TickRules.FAIR_ZOMBIE_HIT, runner.profiles());
        for (var timing : settings.timings()) {
            var spec = new BenchCell.Spec("vanilla", rivals.config(Kit.K0, "vanilla"), Scenario.bench(Kit.K0,
                    Arena.OPEN18, 60), timing, "NONE, golpe propio del zombi", Optional.empty());
            results.add(zombie.cell(spec, settings.firstSeed(), settings.seeds()));
        }
        return results;
    }

    /**
     * The main scenario with the variant of the profile whose heal does not retreat, against the four rivals of the
     * duel pack (the adversarial one searched against the defaults of delivery): how much the retreat costs.
     */
    private static List<BenchCell.Result> healApproach(BenchSettings settings, BenchRunner runner,
                                                       BenchRivals rivals) {
        var results = new ArrayList<BenchCell.Result>();
        if (!settings.kits().contains(Kit.K1) || !settings.timings().contains(BenchProfiles.Timing.DEFAULTS)) {
            return results;
        }
        var scenario = Scenario.bench(Kit.K1, Arena.OPEN18, 60);
        for (var rival : rivals.names(Kit.K1, BenchProfiles.Timing.DEFAULTS)) {
            var spec = new BenchCell.Spec(rival, rivals.config(Kit.K1, rival, BenchProfiles.Timing.DEFAULTS), scenario,
                    BenchProfiles.Timing.HEAL_APPROACH, "NONE", Optional.empty());
            results.add(runner.cell(spec, settings.firstSeed(), settings.seeds()));
        }
        return results;
    }

    private static List<BenchCell.Result> jev(BenchSettings settings, BenchRunner runner, BenchRivals rivals,
                                              BenchProfiles.Timing timing) {
        var scenario = Scenario.bench(Kit.K1, Arena.OPEN18, 60);
        var results = new ArrayList<BenchCell.Result>();
        for (var rival : List.of("legal-best", "adversarial")) {
            for (var latency : settings.latencies()) {
                for (var p : settings.accuracies()) {
                    var judge = new DuelSimulator.Mode.Judge(p, latency, BenchSettings.JUDGE_SEED);
                    var spec = new BenchCell.Spec(rival, rivals.config(Kit.K1, rival, timing), scenario, timing,
                            BenchSettings.modeName(judge), Optional.of(judge));
                    results.add(runner.cell(spec, settings.firstSeed(), settings.jevSeeds()));
                }
            }
        }
        return results;
    }
}
