package akyrion.thepredict.adapter.jev;

/**
 * A response broke the System One contract. The message names the violated rule and, at most, a question
 * key that ThePredict sent; never a fragment of the response body (rule C8). The client turns it into
 * {@code Failed(INVALID_RESPONSE, message)}.
 */
final class SystemOneProtocolException extends RuntimeException {
    SystemOneProtocolException(String rule) {
        super(rule, null, false, false);
    }
}
