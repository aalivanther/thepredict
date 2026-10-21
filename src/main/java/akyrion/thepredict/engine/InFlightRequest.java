package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity and bookkeeping of one request sent for an encounter (M09.8, M16.1, rules I1, I3). Immutable, so it may
 * travel to the HTTP thread and back inside {@link InboxEntry.CompletedRequest} (rule A4). An answer is only ever
 * matched to the request with the same encounter and sequence. It holds only what was really sent
 * ({@link PreparedRequest#sent()}): the questions with their sent options and the fit checks of those options.
 *
 * @param encounter encounter asked about
 * @param worldId world of the encounter, for the per-world budget
 * @param sequence request sequence within the encounter, {@code >= 1}
 * @param catalogRevision catalog revision when the request was built
 * @param questions questions sent, by key, with the mob and generation each one is about
 * @param fitChecks fit checks sent, by key (M16.5); empty without them
 * @param participants participants in the facts
 * @param captureNanos monotonic time of the capture the request is built from
 * @param sentAtNanos monotonic time when the request was handed to the client
 * @param deadlineNanos monotonic time after which the coordinator releases it as {@code TIMEOUT}
 * @param estimatedTokens input tokens reserved in the budget, {@code >= 0}
 * @param bodyChars characters of the body sent, {@code >= 0}; the budget learns its characters per token from it
 * @param captureTick server tick of the capture this request is judged from; {@code 0} for a request built before M21
 * @param sentBody the body that was sent, empty when the caller did not keep it
 */
public record InFlightRequest(EncounterId encounter, UUID worldId, long sequence, long catalogRevision,
                              Map<QuestionKey, QuestionSpec> questions, Map<QuestionKey, FitCheck> fitChecks,
                              int participants, long captureNanos, long sentAtNanos, long deadlineNanos,
                              int estimatedTokens, int bodyChars, long captureTick, String sentBody) {
    /**
     * Validates the request and copies its questions. A request of {@code plan_only} sends the group plan alone, so it
     * has no question here ({@code DecisionRequest} checks that it carries the plan question, M25.10, DOC-02).
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a count is negative or the sequence is not positive
     */
    public InFlightRequest {
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(worldId, "worldId");
        questions = Map.copyOf(Objects.requireNonNull(questions, "questions"));
        fitChecks = Map.copyOf(Objects.requireNonNull(fitChecks, "fitChecks"));
        if (sequence < 1 || participants < 0 || estimatedTokens < 0 || bodyChars < 0 || captureTick < 0) {
            throw new IllegalArgumentException("invalid in-flight request of encounter " + encounter.value());
        }
        Objects.requireNonNull(sentBody, "sentBody");
    }

    /**
     * The form of a request before M21 recorded the capture tick and the body (rule W5).
     *
     * @param encounter encounter asked about
     * @param worldId world of the encounter
     * @param sequence request sequence
     * @param catalogRevision catalog revision
     * @param questions questions sent
     * @param fitChecks fit checks sent
     * @param participants participants in the facts
     * @param captureNanos monotonic time of the capture
     * @param sentAtNanos monotonic time of the send
     * @param deadlineNanos monotonic deadline
     * @param estimatedTokens reserved tokens
     * @param bodyChars characters of the body
     */
    public InFlightRequest(EncounterId encounter, UUID worldId, long sequence, long catalogRevision,
                           Map<QuestionKey, QuestionSpec> questions, Map<QuestionKey, FitCheck> fitChecks,
                           int participants, long captureNanos, long sentAtNanos, long deadlineNanos,
                           int estimatedTokens, int bodyChars) {
        this(encounter, worldId, sequence, catalogRevision, questions, fitChecks, participants, captureNanos,
                sentAtNanos, deadlineNanos, estimatedTokens, bodyChars, 0L, "");
    }
}
