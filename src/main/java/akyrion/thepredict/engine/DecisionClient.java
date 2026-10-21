package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.ServiceCheck;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Port to the remote decision service (00 §4, amended by M16). The engine calls it on the server thread: first
 * {@link #prepare}, which encodes and deduplicates the request without any I/O (rule T2), then, once the budget is
 * reserved, {@link #decide}, which sends the prepared body. The returned futures complete on HTTP threads, so their
 * callbacks may only hand an immutable result to the inbox (rules T4 and T5).
 *
 * <p>Implemented by {@code adapter.jev.SystemOneClient}; {@link #unavailable(ServiceCheck)} is the null
 * implementation used when the service is not configured.
 */
public interface DecisionClient {
    /**
     * Encodes a request as it would be sent: renders and deduplicates the options by their final text, drops the
     * questions left with fewer than two distinct options and checks the size limits. Pure and without I/O; the
     * dispatcher calls it on the server thread before it reserves any budget. Never throws for an invalid request: it
     * answers {@link Preparation.Invalid}.
     *
     * @param request immutable request of one encounter, as the engine planned it
     * @return the prepared request, nothing to ask, or why it cannot be sent
     */
    Preparation prepare(DecisionRequest request);

    /**
     * Sends one prepared request without encoding it again. Never blocks the caller and never completes
     * exceptionally: every failure is a {@link DecisionResult.Failed}. Cancelling the returned future cancels the
     * transport, which does not guarantee that the remote charge is cancelled.
     *
     * @param prepared request returned by {@link #prepare} inside {@link Preparation.Ready}
     * @return a future that completes with the answers or a typed failure
     */
    CompletableFuture<DecisionResult> decide(PreparedRequest prepared);

    /**
     * Checks credentials and model without paying for an inference. Never completes exceptionally.
     *
     * @return a future that completes with the state of the service
     */
    CompletableFuture<ServiceCheck> check();

    /**
     * Releases threads and connections. Pending futures complete with a failure; later calls answer
     * immediately with a failure. Idempotent.
     */
    void close();

    /**
     * Returns a client that never contacts a service, for starting without configuration (C4 forbids an
     * optional client). {@link #check()} answers {@code check}; {@link #prepare} answers
     * {@link Preparation.Invalid}; {@link #decide(PreparedRequest)} fails at once.
     *
     * @param check state reported by every check, such as {@code UNCONFIGURED}
     * @return the null client
     * @throws NullPointerException if {@code check} is {@code null}
     */
    static DecisionClient unavailable(ServiceCheck check) {
        return new UnavailableDecisionClient(Objects.requireNonNull(check, "check"));
    }
}
