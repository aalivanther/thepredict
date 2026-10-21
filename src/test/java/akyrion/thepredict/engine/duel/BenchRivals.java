package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.MovementSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rivals of the bench (§7.3 of the simulator audit) by kit: {@code vanilla} for the kit without skills; for the
 * others {@code pack} (the duel pack's rules on that kit), {@code legal-equal} (ThePredict's windows and speeds,
 * written by hand), {@code legal-best} (the best of the legal space against a league of classics, ThePredict never in
 * it) and {@code adversarial} (the classic of the legal space that lowers the score of one ThePredict configuration the
 * most). The searched ones come from the frozen files when their code and fingerprints still match, unless the run
 * forces a new search; a search writes its winners to {@code congelados/} and, when asked, to the frozen folder.
 */
final class BenchRivals {
    /**
     * One rival.
     *
     * @param kit its kit
     * @param name its name in the report
     * @param timing the ThePredict configuration it was searched against, empty when it does not depend on it
     * @param config the classic
     * @param origin where it comes from
     * @param score the score of its search, NaN when there was none
     * @param seeds the seeds of that score
     */
    record Rival(Kit kit, String name, Optional<BenchProfiles.Timing> timing, ClassicConfig config, String origin,
                 double score, String seeds) {
    }

    /**
     * The steps of one search.
     *
     * @param title what was searched
     * @param steps its stages or generations
     */
    record SearchLog(String title, List<ClassicSearch.Step> steps) {
    }

    private final List<Rival> rivals = new ArrayList<>();
    private final List<SearchLog> searches = new ArrayList<>();
    private final MovementSettings movement;

    BenchRivals(BenchSettings settings, BenchRunner runner, Path output) {
        movement = runner.profiles(BenchProfiles.Timing.DEFAULTS, settings.kits().getFirst()).get("DuelRed")
                .movement();
        var legal = settings.search() == BenchSettings.Search.FORCE ? Map.<String, BenchFrozen.Entry>of()
                : BenchFrozen.read(settings.frozen().resolve(BenchFrozen.LEGAL_BEST));
        var adversarial = settings.search() == BenchSettings.Search.FORCE ? Map.<String, BenchFrozen.Entry>of()
                : BenchFrozen.read(settings.frozen().resolve(BenchFrozen.ADVERSARIAL));
        var legalOut = new LinkedHashMap<String, BenchFrozen.Entry>();
        var adversarialOut = new LinkedHashMap<String, BenchFrozen.Entry>();
        for (var kit : settings.kits()) {
            if (kit.skills().isEmpty()) {
                rivals.add(new Rival(kit, "vanilla", Optional.empty(), ClassicConfig.vanilla(kit), "zombi vanilla",
                        Double.NaN, ""));
                continue;
            }
            rivals.add(new Rival(kit, "pack", Optional.empty(), ClassicConfig.pack(kit), "reglas del paquete",
                    Double.NaN, ""));
            var equal = ClassicSpace.legalEqual(kit, movement);
            rivals.add(new Rival(kit, "legal-equal", Optional.empty(), equal, "escrito a mano", Double.NaN, ""));
            var best = legalBest(settings, runner, kit, equal, legal, legalOut);
            for (var timing : settings.timings()) {
                adversarial(settings, runner, kit, timing, List.of(equal, best), adversarial, adversarialOut);
            }
        }
        freeze(settings, output, legalOut, adversarialOut);
    }

    private ClassicConfig legalBest(BenchSettings settings, BenchRunner runner, Kit kit, ClassicConfig equal,
                                    Map<String, BenchFrozen.Entry> frozen, Map<String, BenchFrozen.Entry> out) {
        var entry = frozen.get(kit.id());
        if (entry == null) {
            requireSearch(settings, kit.id());
            var found = ClassicSearch.legalBest(new ClassicSpace(kit), Scenario.bench(kit, Arena.OPEN18, 60), runner,
                    equal, settings.sizes());
            System.out.println("[banco] legal-best de " + kit.id() + ": " + found.winner().code());
            searches.add(new SearchLog("legal-best de " + kit.id(), found.steps()));
            entry = new BenchFrozen.Entry(kit.id(), found.winner(), found.score(), "1-" + seedsOf(settings.sizes()),
                    "");
        }
        out.put(kit.id(), entry);
        rivals.add(new Rival(kit, "legal-best", Optional.empty(), entry.config(), frozen.containsKey(kit.id())
                ? "congelado" : "búsqueda nueva", entry.score(), entry.seeds()));
        return entry.config();
    }

    private void adversarial(BenchSettings settings, BenchRunner runner, Kit kit, BenchProfiles.Timing timing,
                             List<ClassicConfig> starts, Map<String, BenchFrozen.Entry> frozen,
                             Map<String, BenchFrozen.Entry> out) {
        var key = kit.id() + "-" + timing.name();
        var profile = BenchProfiles.sha256(kit, timing);
        var entry = frozen.get(key);
        var reused = entry != null && entry.profile().equals(profile);
        if (!reused) {
            requireSearch(settings, key);
            var spec = new BenchCell.Spec("search", starts.getFirst(), Scenario.bench(kit, Arena.OPEN18, 60), timing,
                    "NONE", Optional.empty());
            var found = ClassicSearch.adversarial(new ClassicSpace(kit), spec, runner, starts, settings.sizes());
            System.out.println("[banco] adversarial de " + kit.id() + " contra " + timing.label + ": "
                    + found.winner().code() + " (ThePredict " + found.score() + ")");
            searches.add(new SearchLog("adversarial de " + kit.id() + " contra " + timing.label, found.steps()));
            entry = new BenchFrozen.Entry(key, found.winner(), found.score(), ClassicSearch.FINAL_SEED + "-"
                    + (ClassicSearch.FINAL_SEED + settings.sizes().finalSeeds() - 1), profile);
        }
        out.put(key, entry);
        rivals.add(new Rival(kit, "adversarial", Optional.of(timing), entry.config(), reused ? "congelado"
                : "búsqueda nueva", entry.score(), entry.seeds()));
    }

    private static void requireSearch(BenchSettings settings, String key) {
        if (settings.search() == BenchSettings.Search.NEVER) {
            throw new IllegalStateException("no frozen rival for " + key + " and the run forbids a search");
        }
    }

    private static int seedsOf(ClassicSearch.Sizes sizes) {
        var total = 0;
        for (var seeds : sizes.stageSeeds()) {
            total += seeds;
        }
        return total;
    }

    private static void freeze(BenchSettings settings, Path output, Map<String, BenchFrozen.Entry> legal,
                               Map<String, BenchFrozen.Entry> adversarial) {
        var folders = new ArrayList<Path>();
        folders.add(output.resolve("congelados"));
        if (settings.freeze()) {
            folders.add(settings.frozen());
        }
        for (var folder : folders) {
            BenchFrozen.write(folder.resolve(BenchFrozen.LEGAL_BEST), "Rivales legal-best congelados (ClassicSearch"
                    + ".legalBest): el mejor clásico legal de cada kit contra una liga de clásicos.", legal);
            BenchFrozen.write(folder.resolve(BenchFrozen.ADVERSARIAL), "Rivales adversarial congelados (ClassicSearch"
                    + ".adversarial): el clásico legal que más baja a ThePredict, por kit y configuración.", adversarial);
        }
    }

    List<Rival> rivals() {
        return List.copyOf(rivals);
    }

    List<SearchLog> searches() {
        return List.copyOf(searches);
    }

    MovementSettings movement() {
        return movement;
    }

    /** The rivals of the cells of a kit, in report order. */
    List<String> names(Kit kit, BenchProfiles.Timing timing) {
        return kit.skills().isEmpty() ? List.of("vanilla") : List.of("pack", "legal-equal", "legal-best", "adversarial");
    }

    /** The classic of a rival that does not depend on the ThePredict configuration. */
    ClassicConfig config(Kit kit, String name) {
        return find(kit, name, Optional.empty()).config();
    }

    /** The classic of a rival against one ThePredict configuration. */
    ClassicConfig config(Kit kit, String name, BenchProfiles.Timing timing) {
        return find(kit, name, Optional.of(timing)).config();
    }

    private Rival find(Kit kit, String name, Optional<BenchProfiles.Timing> timing) {
        for (var rival : rivals) {
            if (rival.kit() == kit && rival.name().equals(name)
                    && (rival.timing().isEmpty() || rival.timing().equals(timing))) {
                return rival;
            }
        }
        throw new IllegalArgumentException("no rival " + name + " for " + kit.id() + " " + timing);
    }
}
