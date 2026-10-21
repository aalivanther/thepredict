package akyrion.thepredict.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A reviewable schema 1 to schema 2 conversion: proposed files and a report (M03.6). Applying it is manual.
 *
 * @param files proposed file contents keyed by path relative to the proposal directory, for example
 *              {@code AIConfig/config.yml.proposed} or {@code AIConfig/Profiles/guardian.yml.proposed}
 * @param report Markdown report listing every conversion decision; empty when there is nothing to migrate
 */
public record MigrationProposal(Map<String, String> files, String report) {
    /**
     * Copies the files, keeping their order.
     *
     * @throws NullPointerException if a component or element is {@code null}
     */
    public MigrationProposal {
        files = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(files, "files")));
        Objects.requireNonNull(report, "report");
    }

    /**
     * Returns the proposal of an installation without schema 1 files.
     *
     * @return a proposal without files
     */
    public static MigrationProposal none() {
        return new MigrationProposal(Map.of(), "");
    }

    /**
     * Tells whether there is nothing to migrate.
     *
     * @return {@code true} without proposed files
     */
    public boolean isEmpty() {
        return files.isEmpty();
    }
}
