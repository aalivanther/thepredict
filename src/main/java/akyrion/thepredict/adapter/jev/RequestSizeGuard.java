package akyrion.thepredict.adapter.jev;

/**
 * Conservative estimate of the System One token limits before sending (plan §6): at most {@value #REQUEST_TOKENS}
 * tokens per request and {@value #STATE_AND_QUESTION_TOKENS} for {@code state} plus the longest question. Tokens
 * are estimated as characters divided by {@value #CHARS_PER_TOKEN}, rounded up. M13 calibrated the ratio against
 * {@code usage.input_tokens} of the real account (1 200 decisions of {@code jev-1.13.0}, 2026-09-29): 1.59 characters
 * per token at least, 1.73 on average and 1.82 at most, so 1.5 stays below every observation and never
 * underestimates; the former 2 underestimated by about 15 %. Pure and thread-safe.
 */
final class RequestSizeGuard {
    /** Largest request, in estimated tokens. */
    static final int REQUEST_TOKENS = 64_000;
    /** Largest state plus longest question, in estimated tokens. */
    static final int STATE_AND_QUESTION_TOKENS = 32_000;
    /** Characters counted as one token; deliberately below the usual average for English. */
    static final double CHARS_PER_TOKEN = 1.5;

    private RequestSizeGuard() {
    }

    /**
     * Rejects a request whose estimated size exceeds a limit.
     *
     * @param stateChars characters of the compact {@code state}
     * @param longestQuestionChars characters of the longest compact question
     * @param requestChars characters of the whole compact body
     * @throws SystemOneRequestException if a limit is exceeded; nothing is sent
     */
    static void check(int stateChars, int longestQuestionChars, int requestChars) {
        if (tokens((long) stateChars + longestQuestionChars) > STATE_AND_QUESTION_TOKENS) {
            throw new SystemOneRequestException("state plus the longest question exceed the estimated "
                    + STATE_AND_QUESTION_TOKENS + "-token limit");
        }
        if (tokens(requestChars) > REQUEST_TOKENS) {
            throw new SystemOneRequestException("request exceeds the estimated " + REQUEST_TOKENS + "-token limit");
        }
    }

    /** Estimated tokens of a text: {@code ceil(chars / 1.5)}, computed in integers as {@code ceil(2 × chars / 3)}. */
    static long tokens(long chars) {
        return (2 * chars + 2) / 3;
    }
}
