package akyrion.thepredict.adapter.jev;

/**
 * A request cannot be sent: too few or too many options, an option the profile does not declare, or a
 * state that does not fit {@code maxStateChars} even after trimming. It is a programming error of the
 * request assembler (M09) or an encounter too large for the limit; the client turns it into
 * {@code Failed(INVALID_REQUEST, message)} without contacting the service.
 */
final class SystemOneRequestException extends RuntimeException {
    SystemOneRequestException(String rule) {
        super(rule, null, false, false);
    }
}
