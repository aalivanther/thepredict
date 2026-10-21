package akyrion.thepredict.domain;

/**
 * How many group encounters can ask at once under the delivered M09 caps (M25). The caps themselves do not change.
 * {@code t} for a group of five with {@code plan_and_leader} stays the published estimate 3114. A measured body
 * outside that band is published beside it and does not replace this constant.
 */
public final class GroupCapacity {
    /** Delivered server requests per minute. */
    public static final int SERVER_PER_MINUTE = 600;
    /** Delivered requests per minute of one world. */
    public static final int WORLD_PER_MINUTE = 300;
    /** Delivered input tokens per minute. */
    public static final int TOKENS_PER_MINUTE = 600_000;
    /** Published token estimate of {@code plan_and_leader} for five members. */
    public static final int PLAN_AND_LEADER_TOKENS = 3_114;
    /** Asks per minute of one encounter on {@code STANDARD}: one every 20 ticks. */
    public static final int STANDARD_ASKS_PER_MINUTE = 60;
    /** Asks per minute of one encounter on {@code REACTIVE} in the worst case: one every 6 ticks. */
    public static final int REACTIVE_ASKS_PER_MINUTE = 200;

    private GroupCapacity() {
    }

    /**
     * Encounters that fit.
     *
     * @param asksPerMinute asks per minute of one encounter
     * @param tokens tokens of one ask
     * @param worlds worlds that are asking
     * @return the minimum of the three caps
     */
    public static double fits(int asksPerMinute, int tokens, int worlds) {
        var perWorld = WORLD_PER_MINUTE * (double) worlds / asksPerMinute;
        var server = SERVER_PER_MINUTE / (double) asksPerMinute;
        var budget = TOKENS_PER_MINUTE / (asksPerMinute * (double) tokens);
        return Math.min(perWorld, Math.min(server, budget));
    }
}
