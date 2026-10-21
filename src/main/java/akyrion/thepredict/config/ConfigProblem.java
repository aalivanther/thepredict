package akyrion.thepredict.config;

import java.util.Objects;

/**
 * One configuration error or warning with its file and full YAML path (rule C13). Immutable.
 *
 * <p>{@link #toString()} uses the administrator format {@code <file>: <profile>.<path>: <message>}, for
 * example {@code guardian.yml: guardian_ai.Tactics.press_and_strike.Choice: unknown choice 'mele'}.
 *
 * @param severity whether the problem excludes something or only informs
 * @param file file name, {@code config.yml}, a profile file under {@code AI} or {@code MythicMobs}
 * @param path full YAML path; empty when the problem concerns the whole file
 * @param message explanation without file or path, never containing a credential
 */
public record ConfigProblem(Severity severity, String file, String path, String message) {
    /** How serious a problem is. */
    public enum Severity {
        /** Something was rejected or excluded. */
        ERROR,
        /** Loaded anyway; the administrator should review it. */
        WARNING
    }

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if {@code file} or {@code message} is blank
     */
    public ConfigProblem {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        if (file.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("file and message must not be blank");
        }
    }

    /**
     * Creates an error.
     *
     * @param file file name
     * @param path full YAML path, possibly empty
     * @param message explanation
     * @return the error
     */
    public static ConfigProblem error(String file, String path, String message) {
        return new ConfigProblem(Severity.ERROR, file, path, message);
    }

    /**
     * Creates a warning.
     *
     * @param file file name
     * @param path full YAML path, possibly empty
     * @param message explanation
     * @return the warning
     */
    public static ConfigProblem warning(String file, String path, String message) {
        return new ConfigProblem(Severity.WARNING, file, path, message);
    }

    /**
     * Tells whether this problem is an error.
     *
     * @return {@code true} for {@link Severity#ERROR}
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /**
     * Formats the problem for logs and the status command.
     *
     * @return {@code <file>: <path>: <message>}, or {@code <file>: <message>} without a path
     */
    @Override
    public String toString() {
        return path.isEmpty() ? file + ": " + message : file + ": " + path + ": " + message;
    }
}
