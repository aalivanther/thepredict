package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.BindingProblem;
import akyrion.thepredict.adapter.server.binding.BindingResult;
import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.adapter.server.binding.MythicProfileBinder;
import akyrion.thepredict.config.ConfigLocations;
import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.ProfileCatalog;
import akyrion.thepredict.config.ProfileLoadResult;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickLayerMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builds a complete new catalog (read, validate, bind) and exchanges it in a single assignment, so a
 * failed reload never leaves the server without profiles (M03.5). Server thread only.
 *
 * <p>If reading fails globally (the directory cannot be listed, or no profile file can be read) the
 * profiles read last are kept and bound again to the current MythicMobs objects, because a MythicMobs
 * reload discards the old ones.
 */
final class CatalogReloader {
    /** File name used for problems of a MythicMobs mob without a profile. */
    public static final String MYTHICMOBS_FILE = "MythicMobs";
    /** Path cited by problems of the profile directory as a whole. */
    public static final String DIRECTORY_FILE = ConfigLocations.profilesLabel();

    private final ProfileSource source;
    private final MythicProfileBinder binder;
    private final Consumer<RuntimeException> failures;
    private final TickLayerMode tickLayerCeiling;
    private State current = new State(ProfileCatalog.empty(), Map.of(), emptyRead());

    /**
     * Creates the reloader over the {@code AI} directory.
     *
     * @param loader profile loader with the values of {@code config.yml}
     * @param directory {@code AI} directory
     * @param binder binder over the loaded MythicMobs definitions
     */
    public CatalogReloader(ProfileLoader loader, Path directory, MythicProfileBinder binder) {
        this(loader, directory, binder, exception -> { });
    }

    /**
     * Creates the reloader over the {@code AI} directory, reporting unexpected failures.
     *
     * @param loader profile loader with the values of {@code config.yml}
     * @param directory {@code AI} directory
     * @param binder binder over the loaded MythicMobs definitions
     * @param failures receives each unexpected exception once, so the root can log its stack trace
     */
    public CatalogReloader(ProfileLoader loader, Path directory, MythicProfileBinder binder,
                           Consumer<RuntimeException> failures) {
        this(() -> loader.load(directory), binder, failures, loader.tickLayerCeiling());
        Objects.requireNonNull(directory, "directory");
    }

    /**
     * Creates the reloader over any profile source.
     *
     * @param source reads the profiles
     * @param binder binder over the loaded MythicMobs definitions
     */
    public CatalogReloader(ProfileSource source, MythicProfileBinder binder) {
        this(source, binder, exception -> { });
    }

    /**
     * Creates the reloader over any profile source, reporting unexpected failures.
     *
     * @param source reads the profiles
     * @param binder binder over the loaded MythicMobs definitions
     * @param failures receives each unexpected exception once, so the root can log its stack trace
     */
    public CatalogReloader(ProfileSource source, MythicProfileBinder binder, Consumer<RuntimeException> failures) {
        this(source, binder, failures, TickLayerMode.DEFAULT);
    }

    /**
     * Creates the reloader with the global {@code decision.tickLayer.mode}, the ceiling of an overlay's
     * {@code Timing.TickLayer} (DOC-04).
     */
    private CatalogReloader(ProfileSource source, MythicProfileBinder binder, Consumer<RuntimeException> failures,
                            TickLayerMode tickLayerCeiling) {
        this.source = Objects.requireNonNull(source, "source");
        this.binder = Objects.requireNonNull(binder, "binder");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.tickLayerCeiling = Objects.requireNonNull(tickLayerCeiling, "tickLayerCeiling");
    }

    /** Reads the profiles of the {@code AI} directory. */
    @FunctionalInterface
    public interface ProfileSource {
        /**
         * Reads every profile file.
         *
         * @return the valid profiles and the problems found
         * @throws IOException when the directory cannot be read
         */
        ProfileLoadResult load() throws IOException;
    }

    /**
     * Returns the catalog in use.
     *
     * @return the current catalog
     */
    public ProfileCatalog catalog() {
        return current.catalog();
    }

    /**
     * Returns the bindings of the catalog in use, keyed by MythicMobs mob ID.
     *
     * @return the current bindings
     */
    public Map<String, BoundProfile> bindings() {
        return current.bindings();
    }

    /**
     * Returns the read in use: the profiles before any overlay and their fingerprints.
     *
     * @return the last read
     */
    ProfileLoadResult read() {
        return current.read();
    }

    /**
     * Counts the valid profiles read last from the {@code AI} directory, active or not.
     *
     * @return profiles read
     */
    public int profilesRead() {
        return current.read().profiles().size();
    }

    /**
     * Summarizes the catalog in use, telling apart profiles that were read from profiles that are active.
     *
     * @return the summary for logs and {@code /thepredict status}
     */
    public String summary() {
        return current.catalog().summary(current.read().profiles().size());
    }

    /**
     * Applies each active overlay to the binding of its own profile only (M29.8), as {@link OverlayBinding} says, and
     * exchanges the catalog with the next revision. The author file is not written.
     *
     * @param read the active overlays and the problems found reading them
     * @return the catalog now in use
     */
    public ProfileCatalog noteOverlays(ActiveOverlays.Read read) {
        var result = OverlayBinding.apply(read, current.bindings(), current.read(), tickLayerCeiling);
        var profiles = new LinkedHashMap<String, TacticalProfile>();
        result.bindings().forEach((mobId, bound) -> profiles.put(mobId, bound.profile()));
        var catalog = current.catalog().withOverlay(profiles, result.keys(), result.problems());
        current = new State(catalog, result.bindings(), current.read());
        return catalog;
    }

    /**
     * Reads, validates and binds the profiles, then exchanges the catalog with the next revision. An
     * unexpected error leaves an empty catalog, so no stale MythicMobs object stays in use (plan section 8).
     *
     * @return the new catalog
     */
    public ProfileCatalog reload() {
        try {
            return reloadOrThrow();
        } catch (RuntimeException exception) {
            return failed(exception);
        }
    }

    private ProfileCatalog reloadOrThrow() {
        var problems = new ArrayList<ConfigProblem>();
        var read = current.read();
        try {
            var loaded = source.load();
            if (loaded.unreadable()) {
                problems.addAll(loaded.problems());
                problems.add(ConfigProblem.error(DIRECTORY_FILE, "",
                        "no profile file could be read; the profiles read last are kept"));
            } else {
                read = loaded;
            }
        } catch (IOException exception) {
            problems.add(ConfigProblem.error(DIRECTORY_FILE, "",
                    "the directory could not be read; the profiles read last are kept"));
        }
        problems.addAll(read.problems());
        var binding = binder.bind(read.profiles());
        problems.addAll(convert(binding.problems(), read.files()));
        exchange(binding, problems, read, Map.of());
        return current.catalog();
    }

    /**
     * Disables the contexts whose custom conditions did not resolve, if the catalog did not change since.
     * An unexpected error leaves an empty catalog.
     *
     * @param catalogRevision revision of the catalog to verify
     * @return the catalog in use afterwards; a new revision only when a context was disabled
     */
    public ProfileCatalog verifyConditions(long catalogRevision) {
        try {
            return verifyOrThrow(catalogRevision);
        } catch (RuntimeException exception) {
            return failed(exception);
        }
    }

    private ProfileCatalog verifyOrThrow(long catalogRevision) {
        if (current.catalog().catalogRevision() != catalogRevision) {
            return current.catalog();
        }
        var verified = binder.verifyConditions(current.bindings());
        if (verified.problems().isEmpty()) {
            return current.catalog();
        }
        var problems = new ArrayList<>(current.catalog().problems());
        problems.addAll(convert(verified.problems(), current.read().files()));
        exchange(verified, problems, current.read(), current.catalog().overlay());
        return current.catalog();
    }

    /**
     * Replaces the catalog with an empty one that only reports why profiles cannot load, for example an
     * invalid {@code config.yml}.
     *
     * @param problem the blocking problem
     * @return the new catalog
     */
    public ProfileCatalog block(ConfigProblem problem) {
        Objects.requireNonNull(problem, "problem");
        current = new State(current.catalog().next(Map.of(), List.of(problem), Map.of()), Map.of(), emptyRead());
        return current.catalog();
    }

    private ProfileCatalog failed(RuntimeException exception) {
        current = new State(current.catalog().next(Map.of(), List.of(ConfigProblem.error(DIRECTORY_FILE, "",
                "the reload failed unexpectedly (" + exception.getClass().getSimpleName()
                        + "); no profile is active until the next successful reload")), Map.of()), Map.of(),
                current.read());
        failures.accept(exception);
        return current.catalog();
    }

    private void exchange(BindingResult binding, List<ConfigProblem> problems, ProfileLoadResult read,
                          Map<String, String> overlay) {
        var profiles = new LinkedHashMap<String, TacticalProfile>();
        binding.profiles().forEach((mobId, bound) -> profiles.put(mobId, bound.profile()));
        current = new State(current.catalog().next(profiles, problems, overlay), binding.profiles(), read);
    }

    private static List<ConfigProblem> convert(List<BindingProblem> problems, Map<String, String> files) {
        var converted = new ArrayList<ConfigProblem>(problems.size());
        for (var problem : problems) {
            var file = problem.profileId().map(id -> files.getOrDefault(id, DIRECTORY_FILE)).orElse(MYTHICMOBS_FILE);
            converted.add(problem.severity() == BindingProblem.Severity.ERROR
                    ? ConfigProblem.error(file, problem.path(), problem.message())
                    : ConfigProblem.warning(file, problem.path(), problem.message()));
        }
        return converted;
    }

    private static ProfileLoadResult emptyRead() {
        return new ProfileLoadResult(Map.of(), Map.of(), List.of(), 0, 0);
    }

    private record State(ProfileCatalog catalog, Map<String, BoundProfile> bindings, ProfileLoadResult read) {
    }
}
