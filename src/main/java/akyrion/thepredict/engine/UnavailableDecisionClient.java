package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.ServiceCheck;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Null {@link DecisionClient}: reports a fixed check, prepares nothing ({@link Preparation.Invalid}, a local failure
 * without any effect on the service tracker) and fails every decision without any I/O.
 */
record UnavailableDecisionClient(ServiceCheck state) implements DecisionClient {
    UnavailableDecisionClient {
        Objects.requireNonNull(state, "state");
    }

    @Override
    public Preparation prepare(DecisionRequest request) {
        Objects.requireNonNull(request, "request");
        return new Preparation.Invalid(detail());
    }

    @Override
    public CompletableFuture<DecisionResult> decide(PreparedRequest prepared) {
        Objects.requireNonNull(prepared, "prepared");
        return CompletableFuture.completedFuture(new DecisionResult.Failed(failureKind(), Optional.empty(), detail()));
    }

    private String detail() {
        return "decision service unavailable: " + state.state().name().toLowerCase(Locale.ROOT);
    }

    @Override
    public CompletableFuture<ServiceCheck> check() {
        return CompletableFuture.completedFuture(state);
    }

    @Override
    public void close() {
    }

    private FailureKind failureKind() {
        return switch (state.state()) {
            case UNCONFIGURED, INVALID_CREDENTIALS -> FailureKind.AUTHENTICATION;
            case INVALID_MODEL -> FailureKind.MODEL_UNAVAILABLE;
            case INVALID_CONFIGURATION -> FailureKind.INVALID_REQUEST;
            case CHECKING, READY, PAUSED -> FailureKind.SERVICE_ERROR;
        };
    }
}
