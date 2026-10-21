package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.ServiceCheck;

import java.util.Objects;

/** Immutable result that an HTTP thread deposits in the {@link Inbox} (M09.2, rule T5). */
public sealed interface InboxEntry
        permits InboxEntry.CompletedRequest, InboxEntry.CompletedCheck, StorageReply {
    /**
     * Returns when the result was produced.
     *
     * @return {@code ServerClock.nanoTime()} read on the completing thread
     */
    long completedAtNanos();

    /**
     * A decision request finished, answered or failed.
     *
     * @param request the request as it was sent
     * @param result answers or typed failure
     * @param completedAtNanos monotonic time of the completion
     */
    record CompletedRequest(InFlightRequest request, DecisionResult result, long completedAtNanos)
            implements InboxEntry {
        /**
         * Validates the entry.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public CompletedRequest {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
        }
    }

    /**
     * A service check finished.
     *
     * @param generation generation of the tracker when the check started; an older one is ignored
     * @param check state reported by the client
     * @param completedAtNanos monotonic time of the completion
     */
    record CompletedCheck(long generation, ServiceCheck check, long completedAtNanos) implements InboxEntry {
        /**
         * Validates the entry.
         *
         * @throws NullPointerException if {@code check} is {@code null}
         */
        public CompletedCheck {
            Objects.requireNonNull(check, "check");
        }
    }
}
