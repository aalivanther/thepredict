package akyrion.thepredict.config;

import akyrion.thepredict.domain.TacticalProfile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of reading the {@code AI} directory: the valid profiles, where each one came from and every
 * problem found. Profiles with errors are excluded; the others are kept (M03.2).
 *
 * @param profiles valid profiles keyed by exact MythicMobs mob ID, in file and declaration order
 * @param files file name of every valid profile, keyed by profile ID
 * @param problems errors and warnings, each with file and full path
 * @param fileCount profile files found ({@code .yml} and {@code .yaml})
 * @param unreadableFiles files whose YAML could not be read at all
 * @param fingerprints {@link ProfileNormalForm#fingerprint} of every valid profile, keyed by profile ID
 */
public record ProfileLoadResult(Map<String, TacticalProfile> profiles, Map<String, String> files,
                                List<ConfigProblem> problems, int fileCount, int unreadableFiles,
                                Map<String, String> fingerprints) {
    /**
     * Validates the counters and copies the collections, keeping the order of {@code profiles}.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if a counter is negative or larger than {@code fileCount}
     */
    public ProfileLoadResult {
        profiles = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(profiles, "profiles")));
        files = Map.copyOf(Objects.requireNonNull(files, "files"));
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        fingerprints = Map.copyOf(Objects.requireNonNull(fingerprints, "fingerprints"));
        if (fileCount < 0 || unreadableFiles < 0 || unreadableFiles > fileCount) {
            throw new IllegalArgumentException("invalid file counters " + unreadableFiles + "/" + fileCount);
        }
    }

    /**
     * A result without fingerprints, for a read that has none (rule W5).
     *
     * @param profiles valid profiles keyed by exact MythicMobs mob ID
     * @param files file name of every valid profile, keyed by profile ID
     * @param problems errors and warnings
     * @param fileCount profile files found
     * @param unreadableFiles files whose YAML could not be read at all
     */
    public ProfileLoadResult(Map<String, TacticalProfile> profiles, Map<String, String> files,
                             List<ConfigProblem> problems, int fileCount, int unreadableFiles) {
        this(profiles, files, problems, fileCount, unreadableFiles, Map.of());
    }

    /**
     * Tells whether the read failed globally: there were profile files and none of them could be read.
     * A global failure keeps the previous catalog (M03.5).
     *
     * @return {@code true} when every file was unreadable
     */
    public boolean unreadable() {
        return fileCount > 0 && unreadableFiles == fileCount;
    }
}
