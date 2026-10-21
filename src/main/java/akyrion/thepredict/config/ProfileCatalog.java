package akyrion.thepredict.config;

import akyrion.thepredict.domain.TacticalProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable catalog of bound tactical profiles, replaced as a whole on every reload (M03.5).
 *
 * @param catalogRevision revision that grows with every exchange of the catalog (rule I11); 0 before the first load
 * @param profiles bound profiles keyed by exact MythicMobs mob ID, in load order
 * @param problems errors and warnings of the load that produced this catalog
 * @param overlay keys of the active overlays that applied, each as {@code <profile ID>:<dotted key>}
 */
public record ProfileCatalog(long catalogRevision, Map<String, TacticalProfile> profiles,
                             List<ConfigProblem> problems, Map<String, String> overlay) {
    /**
     * A catalog whose overlay is still empty.
     *
     * @param catalogRevision revision
     * @param profiles bound profiles
     * @param problems load problems
     */
    public ProfileCatalog(long catalogRevision, Map<String, TacticalProfile> profiles,
                          List<ConfigProblem> problems) {
        this(catalogRevision, profiles, problems, Map.of());
    }
    /**
     * Validates the components and copies the collections.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if the revision is negative or a key differs from its profile mob ID
     */
    public ProfileCatalog {
        if (catalogRevision < 0) {
            throw new IllegalArgumentException("catalogRevision must be >= 0, was " + catalogRevision);
        }
        var copy = new LinkedHashMap<String, TacticalProfile>();
        Objects.requireNonNull(profiles, "profiles").forEach((mobId, profile) -> {
            if (!mobId.equals(profile.mythicMobId())) {
                throw new IllegalArgumentException("profiles key " + mobId + " does not match " + profile.mythicMobId());
            }
            copy.put(mobId, profile);
        });
        profiles = Collections.unmodifiableMap(copy);
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        overlay = Map.copyOf(Objects.requireNonNull(overlay, "overlay"));
    }

    /**
     * Returns the catalog that exists before the first load.
     *
     * @return revision 0 without profiles or problems
     */
    public static ProfileCatalog empty() {
        return new ProfileCatalog(0, Map.of(), List.of());
    }

    /**
     * Builds the catalog that replaces this one, with the next revision.
     *
     * @param nextProfiles bound profiles of the new catalog
     * @param nextProblems problems of the new load
     * @return the new catalog with {@code catalogRevision + 1}
     */
    public ProfileCatalog next(Map<String, TacticalProfile> nextProfiles, List<ConfigProblem> nextProblems) {
        return next(nextProfiles, nextProblems, overlay);
    }

    /**
     * Builds the catalog that replaces this one with its own overlay keys: a fresh read starts without them.
     *
     * @param nextProfiles bound profiles of the new catalog
     * @param nextProblems problems of the new load
     * @param nextOverlay overlay keys of the new catalog
     * @return the new catalog with {@code catalogRevision + 1}
     */
    public ProfileCatalog next(Map<String, TacticalProfile> nextProfiles, List<ConfigProblem> nextProblems,
                               Map<String, String> nextOverlay) {
        return new ProfileCatalog(catalogRevision + 1, nextProfiles, nextProblems, nextOverlay);
    }

    /**
     * Replaces the profiles and the effective keys, and advances the revision. The author file is not written.
     *
     * @param effective loader profiles plus the unfixed overlay keys
     * @param keys the merged dotted keys
     * @return the next catalog
     */
    public ProfileCatalog withOverlay(Map<String, TacticalProfile> effective, Map<String, String> keys) {
        return withOverlay(effective, keys, List.of());
    }

    /**
     * Replaces the profiles and the effective keys, adds the problems of the overlays, and advances the revision.
     *
     * @param effective loader profiles plus the overlays that applied
     * @param keys the applied keys
     * @param overlayProblems warnings of the overlays that did not apply, after the problems of the load
     * @return the next catalog
     */
    public ProfileCatalog withOverlay(Map<String, TacticalProfile> effective, Map<String, String> keys,
                                      List<ConfigProblem> overlayProblems) {
        var all = new ArrayList<>(problems);
        all.addAll(overlayProblems);
        return new ProfileCatalog(catalogRevision + 1, effective, all, keys);
    }

    /**
     * Summarizes the catalog: {@code SETUP_PENDING} without profiles or errors, {@code INVALID} with any
     * error, {@code LOADED} otherwise.
     *
     * @return the status
     */
    public CatalogStatus status() {
        if (problems.stream().anyMatch(ConfigProblem::isError)) {
            return CatalogStatus.INVALID;
        }
        return profiles.isEmpty() ? CatalogStatus.SETUP_PENDING : CatalogStatus.LOADED;
    }

    /**
     * Summarizes the catalog for logs and {@code /thepredict status}.
     *
     * @param readProfiles valid profiles read from the {@code AI} directory, active or not
     * @return status, revision and counts, plus the first error or what setup still needs
     */
    public String summary(int readProfiles) {
        var errors = problems.stream().filter(ConfigProblem::isError).toList();
        var summary = status() + " (revision " + catalogRevision + ", " + readProfiles + " profiles read, "
                + profiles.size() + " active, " + errors.size() + " errors, " + (problems.size() - errors.size())
                + " warnings)";
        return switch (status()) {
            case SETUP_PENDING -> summary + (readProfiles > 0
                    ? ": no mob targeted by a profile has Predictor: true; set it in MythicMobs and reload MythicMobs"
                    : ": configuration pending; copy AIConfig/Profiles/example.yml.example to a .yml file");
            case LOADED -> summary;
            case INVALID -> summary + ": first error " + errors.getFirst();
        };
    }
}
