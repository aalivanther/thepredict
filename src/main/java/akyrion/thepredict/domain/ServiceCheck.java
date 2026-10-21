package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * Result of checking the remote decision service. Immutable.
 *
 * @param state service state
 * @param detail short explanation for diagnostics, without keys (rule C8)
 */
public record ServiceCheck(ServiceState state, String detail) {
    /**
     * Validates the check.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public ServiceCheck {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
    }
}
