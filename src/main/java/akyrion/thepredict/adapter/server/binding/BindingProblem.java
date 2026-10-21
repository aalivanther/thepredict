package akyrion.thepredict.adapter.server.binding;

import java.util.Objects;
import java.util.Optional;

/**
 * One binding error or warning. The root adds the file name, which the binder does not know (D5).
 *
 * @param severity whether something was rejected or only reported
 * @param profileId profile the problem belongs to; empty for a MythicMobs mob without a profile
 * @param path full path, starting with the profile ID or, without a profile, with the mob ID
 * @param message explanation without the path
 */
public record BindingProblem(Severity severity, Optional<String> profileId, String path, String message) {
    /** How serious a binding problem is. */
    public enum Severity {
        /** The profile or the context was excluded. */
        ERROR,
        /** The profile loaded; the author should review it. */
        WARNING
    }

    /**
     * Validates the components.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public BindingProblem {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Creates a problem of a profile.
     *
     * @param severity error or warning
     * @param profileId profile ID
     * @param relativePath path below the profile, for example {@code Choices.melee.MythicID}
     * @param message explanation
     * @return the problem, with path {@code <profileId>.<relativePath>}
     */
    public static BindingProblem of(Severity severity, String profileId, String relativePath, String message) {
        return new BindingProblem(severity, Optional.of(profileId), profileId + "." + relativePath, message);
    }
}
