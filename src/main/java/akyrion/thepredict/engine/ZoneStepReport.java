package akyrion.thepredict.engine;

/**
 * What one zone step did (M24.3).
 *
 * @param visited zones whose clock was read
 * @param patched zones that published a patch
 * @param deferred zones left because the budget or the cap ran out
 */
public record ZoneStepReport(int visited, int patched, int deferred) {
    /** A step that did nothing. */
    public static final ZoneStepReport EMPTY = new ZoneStepReport(0, 0, 0);

    /**
     * Checks the counts.
     *
     * @throws IllegalArgumentException when a count is negative
     */
    public ZoneStepReport {
        if (visited < 0 || patched < 0 || deferred < 0) {
            throw new IllegalArgumentException("zone step counts must be >= 0");
        }
    }
}
