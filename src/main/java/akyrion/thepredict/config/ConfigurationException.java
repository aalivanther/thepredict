package akyrion.thepredict.config;

import java.util.Objects;

/**
 * An invalid configuration value at a known YAML path. Thrown only while loading (rule C7); the
 * message names the path and never contains a credential.
 */
public final class ConfigurationException extends IllegalArgumentException {
    /** Full YAML path of the invalid value, empty for the whole file. */
    private final String path;
    /** Explanation without the path. */
    private final String reason;

    /**
     * Creates the exception.
     *
     * @param path full YAML path, for example {@code decision.maxRequestsPerMinute}; empty for the whole file
     * @param reason explanation without the path, for example {@code must be between 1 and 1200, was 0}
     */
    public ConfigurationException(String path, String reason) {
        super(path.isEmpty() ? reason : path + " " + reason);
        this.path = Objects.requireNonNull(path, "path");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Returns the full YAML path of the invalid value.
     *
     * @return the path, empty for the whole file
     */
    public String path() {
        return path;
    }

    /**
     * Returns the explanation without the path.
     *
     * @return the reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Converts the exception into an error of the given file.
     *
     * @param file file name shown to the administrator, for example {@code config.yml}
     * @return the error problem
     */
    public ConfigProblem toProblem(String file) {
        return ConfigProblem.error(file, path, reason);
    }
}
