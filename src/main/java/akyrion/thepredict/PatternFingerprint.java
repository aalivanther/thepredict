package akyrion.thepredict;

import akyrion.thepredict.domain.CounterMap;
import akyrion.thepredict.domain.PatternRules;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.engine.CandidateFingerprint;

import java.util.Map;
import java.util.TreeMap;

/**
 * Fingerprint of the pattern policy (M26.6, W8): the {@code CODE_PRESET} candidate a pattern-mode verdict names. It is
 * the SHA-256 of a canonical JSON object with the versions of {@link PatternRules} and {@link CounterMap} and every limit
 * of {@link PatternSettings} that changes what is detected or exploited, keys in name order. The mode stays out, as in
 * {@code MemoryPolicy}, because the verdict rows tell {@code bias} from {@code full}; so do the two budgets
 * ({@code maxReadsPerTick}, {@code maxMicrosPerTick}), as the cache size stays out of the memory fingerprint. Changing
 * any other value asks for a new verdict. {@code evalGate} has to emit this same text.
 */
final class PatternFingerprint {
    private PatternFingerprint() {
    }

    /**
     * SHA-256 of {@link #canonical}.
     *
     * @param settings the global pattern limits in use
     * @return lower-case hex
     */
    static String of(PatternSettings settings) {
        return CandidateFingerprint.of(canonical(settings));
    }

    /**
     * Canonical JSON of the policy: one member per key, in name order; numbers bare, names quoted.
     *
     * @param settings the global pattern limits in use
     * @return the JSON object
     */
    static String canonical(PatternSettings settings) {
        var keys = new TreeMap<String, String>();
        keys.put("rules", Integer.toString(PatternRules.VERSION));
        keys.put("counters", Integer.toString(CounterMap.VERSION));
        sensing(settings, keys);
        detection(settings, keys);
        exploitation(settings, keys);
        var text = new StringBuilder("{");
        for (var entry : keys.entrySet()) {
            if (text.length() > 1) {
                text.append(',');
            }
            text.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        return text.append('}').toString();
    }

    private static void sensing(PatternSettings settings, Map<String, String> keys) {
        keys.put("hearingBlocks", Integer.toString(settings.hearingBlocks()));
        keys.put("sampleTicks", Integer.toString(settings.sampleTicks()));
        keys.put("maxWatchedPerEncounter", Integer.toString(settings.maxWatchedPerEncounter()));
        keys.put("strafeBlocks", Double.toString(settings.strafeBlocks()));
        keys.put("airSwings", "\"" + settings.airSwings().name() + "\"");
        keys.put("mergeTicks", Integer.toString(settings.mergeTicks()));
        keys.put("idleTicks", Integer.toString(settings.idleTicks()));
        keys.put("maxTokensPerSecond", Integer.toString(settings.maxTokensPerSecond()));
    }

    private static void detection(PatternSettings settings, Map<String, String> keys) {
        keys.put("order2Contexts", Integer.toString(settings.order2Contexts()));
        keys.put("forgetting", Double.toString(settings.forgetting()));
        keys.put("evidenceForgetting", Double.toString(settings.evidenceForgetting()));
        keys.put("confidenceQuantile", Double.toString(settings.confidenceQuantile()));
        keys.put("minTrials", Integer.toString(settings.minTrials()));
        keys.put("persistUpdates", Integer.toString(settings.persistUpdates()));
        keys.put("exitMargin", Double.toString(settings.exitMargin()));
        keys.put("changepointH", Double.toString(settings.changepointH()));
        keys.put("forecastMinAccuracy", Double.toString(settings.forecastMinAccuracy()));
        keys.put("histogramBinTicks", Integer.toString(settings.histogramBinTicks()));
    }

    private static void exploitation(PatternSettings settings, Map<String, String> keys) {
        keys.put("maxExploitShare", Double.toString(settings.maxExploitShare()));
        keys.put("maxCounterDelayTicks", Integer.toString(settings.maxCounterDelayTicks()));
        keys.put("exploitWindowTicks", Integer.toString(settings.exploitWindowTicks()));
        keys.put("restAfterSuccesses", Integer.toString(settings.restAfterSuccesses()));
        keys.put("restTicks", Integer.toString(settings.restTicks()));
        keys.put("maxExploitedPatterns", Integer.toString(settings.maxExploitedPatterns()));
        keys.put("factOnTicks", Integer.toString(settings.factOnTicks()));
        keys.put("factOffTicks", Integer.toString(settings.factOffTicks()));
        keys.put("maxFactsPerParticipant", Integer.toString(settings.maxFactsPerParticipant()));
        keys.put("maxFactsPerRequest", Integer.toString(settings.maxFactsPerRequest()));
    }
}
