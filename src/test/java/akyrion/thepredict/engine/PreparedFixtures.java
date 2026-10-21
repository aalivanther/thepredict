package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionRequest;

import java.util.Set;

/**
 * Prepared requests for the decision client doubles of the engine tests (M16.1): the request as planned and an opaque
 * body whose size, at the starting 1.5 characters per token, reserves the tokens the estimate of 0.0.1 reserved
 * ({@code 400 + 120 × questions + 60 × participants}), so the budget numbers of M09 and M13 do not move.
 */
final class PreparedFixtures {
    /** Tokens of the state and instructions in the estimate of 0.0.1. */
    static final int BASE_TOKENS = 400;
    /** Tokens of one question in the estimate of 0.0.1. */
    static final int TOKENS_PER_QUESTION = 120;
    /** Tokens of one participant in the estimate of 0.0.1. */
    static final int TOKENS_PER_PARTICIPANT = 60;

    private PreparedFixtures() {
    }

    /** The tokens of 0.0.1 for a request. */
    static int legacyTokens(DecisionRequest request) {
        return BASE_TOKENS + TOKENS_PER_QUESTION * request.questions().size()
                + TOKENS_PER_PARTICIPANT * request.facts().participants().size();
    }

    /** A request prepared as planned, with a body of {@code 1.5 ×} its tokens of 0.0.1. */
    static PreparedRequest prepared(DecisionRequest request) {
        var body = "x".repeat((int) Math.ceil(legacyTokens(request) * RequestBudget.INITIAL_CHARS_PER_TOKEN));
        return new PreparedRequest(request, body, Set.of(), Set.of(), body.length());
    }

    /** The preparation of a request with nothing dropped. */
    static Preparation ready(DecisionRequest request) {
        return new Preparation.Ready(prepared(request));
    }
}
