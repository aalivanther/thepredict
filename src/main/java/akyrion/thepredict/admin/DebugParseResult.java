package akyrion.thepredict.admin;

import java.util.Objects;

/** Outcome of parsing {@code /thepredict debug} arguments; a rejection explains itself (M12.3). */
public sealed interface DebugParseResult permits DebugParseResult.Accepted, DebugParseResult.Rejected {
    /**
     * Arguments that form a valid request.
     *
     * @param request the request to execute
     */
    record Accepted(DebugRequest request) implements DebugParseResult {
        /** Rejects a missing request. */
        public Accepted {
            Objects.requireNonNull(request, "request");
        }
    }

    /**
     * Arguments that were refused; nothing must change.
     *
     * @param reason message for the sender, in English
     */
    record Rejected(String reason) implements DebugParseResult {
        /** Rejects a missing reason. */
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
