package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.learn.LearnCause;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON tokens for the {@code credit}, {@code explore} and {@code learn} lines (M28.1). Empty when nothing is passed,
 * so existing record lines stay byte-identical until a caller opts in.
 */
public final class LearnEvents {
    private LearnEvents() {
    }

    /**
     * Fields of a credit line.
     *
     * @param windowId window id
     * @param reward reward
     * @param truncated whether the ledger overflowed
     * @return tokens
     */
    public static Map<String, String> credit(String windowId, double reward, boolean truncated) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("window_id", quote(windowId));
        fields.put("reward", Double.toString(reward));
        fields.put("reward_spec", quote("r1"));
        fields.put("truncated", truncated ? "true" : "false");
        return fields;
    }

    /**
     * Fields of an explore line.
     *
     * @param policy behavior
     * @param epsilon epsilon
     * @param chosen chosen index
     * @return tokens
     */
    public static Map<String, String> explore(BehaviorPolicy policy, double epsilon, int chosen) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("policy", quote(policy.name()));
        fields.put("epsilon", Double.toString(epsilon));
        fields.put("chosen", Integer.toString(chosen));
        return fields;
    }

    /**
     * Fields of a learn line. Entropy uses event {@code DRIFT_ALARM} and cause {@code ENTROPY} (errata E28-15 applied).
     *
     * @param sha256 artifact
     * @param event event name
     * @param cause cause
     * @return tokens
     */
    public static Map<String, String> learn(String sha256, String event, LearnCause cause) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("artifact", quote(sha256));
        fields.put("event", quote(event));
        fields.put("cause", quote(cause.name()));
        return fields;
    }

    private static String quote(String text) {
        return "\"" + text + "\"";
    }
}
