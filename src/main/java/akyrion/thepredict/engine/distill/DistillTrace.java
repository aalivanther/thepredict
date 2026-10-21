package akyrion.thepredict.engine.distill;

/**
 * Fields written into {@code planned.distill} (M29.4).
 *
 * @param artifact artifact token
 * @param regime regime key
 * @param leaf cell or leaf id
 * @param student student tactic
 * @param source student source
 * @param covered whether the cell is covered
 * @param ood out-of-coverage cause, empty when covered
 * @param cellN outcome comparisons in the cell
 * @param cellLbMillis lower bound in milliseconds
 * @param audit whether this capture is the audit
 * @param delegated whether the student held the intent
 */
public record DistillTrace(String artifact, String regime, String leaf, String student, String source,
                           boolean covered, String ood, int cellN, long cellLbMillis, boolean audit,
                           boolean delegated) {
    /** A capture that wrote nothing. */
    public static final DistillTrace EMPTY = new DistillTrace("", "", "", "", "", false, "", 0, 0L, false, false);

    /**
     * The same trace with the delegation flag of the capture.
     *
     * @param value whether the student held the intent
     * @return the copy
     */
    public DistillTrace withDelegated(boolean value) {
        return new DistillTrace(artifact, regime, leaf, student, source, covered, ood, cellN, cellLbMillis, audit,
                value);
    }

    /**
     * JSON object with every planned field.
     *
     * @return the object
     */
    public String json() {
        return "{\"artifact\":\"" + escape(artifact) + "\",\"regime\":\"" + escape(regime) + "\",\"leaf\":\""
                + escape(leaf) + "\",\"student\":\"" + escape(student) + "\",\"source\":\"" + escape(source)
                + "\",\"covered\":" + covered + ",\"ood\":\"" + escape(ood) + "\",\"cell_n\":" + cellN
                + ",\"cell_lb_millis\":" + cellLbMillis + ",\"audit\":" + audit + ",\"delegated\":" + delegated + "}";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
