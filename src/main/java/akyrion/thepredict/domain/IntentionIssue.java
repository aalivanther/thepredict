package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * One issue found in an {@code Intention} line (M19.1). Immutable.
 *
 * @param kind what is wrong
 * @param index position of the primitive in the raw line, from 0; {@value #WHOLE_LINE} when the issue is about the whole
 *        line, such as a missing role
 * @param token the normalised primitive the issue is about; empty for an issue about the whole line
 */
public record IntentionIssue(IntentionIssueKind kind, int index, String token) {
    /** The index of an issue about the whole line. */
    public static final int WHOLE_LINE = -1;

    /**
     * Validates the issue.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the index is below {@value #WHOLE_LINE}
     */
    public IntentionIssue {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(token, "token");
        if (index < WHOLE_LINE) {
            throw new IllegalArgumentException("index must be >= " + WHOLE_LINE + ", was " + index);
        }
    }

    /**
     * An issue about the whole line.
     *
     * @param kind what is wrong
     * @return the issue, with index {@value #WHOLE_LINE} and no token
     */
    public static IntentionIssue line(IntentionIssueKind kind) {
        return new IntentionIssue(kind, WHOLE_LINE, "");
    }
}
