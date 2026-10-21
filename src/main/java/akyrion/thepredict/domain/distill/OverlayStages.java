package akyrion.thepredict.domain.distill;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gate stage of an overlay, one per key (M29.8). The stage of the overlay is the union, not a single preset.
 */
public final class OverlayStages {
    private OverlayStages() {
    }

    /**
     * Refuses a value that would raise a global mode, and refuses {@code off} for zone and group.
     *
     * @param key profile key
     * @param value proposed value
     * @param globalRank rank of the global mode, or {@code -1} when the key is not a mode
     * @param valueRank rank of the proposed mode
     */
    public static void check(String key, String value, int globalRank, int valueRank) {
        if (("Zone.Mode".equals(key) || "Group.Mode".equals(key)) && "off".equals(value)) {
            throw new IllegalArgumentException("OVERLAY_RAISES_GLOBAL");
        }
        if (globalRank >= 0 && valueRank > globalRank) {
            throw new IllegalArgumentException("OVERLAY_RAISES_GLOBAL");
        }
    }

    /**
     * The stage recorded for each key. Keys keep their own stage.
     *
     * @param stages key to stage
     * @return a copy
     */
    public static Map<String, String> perKey(Map<String, String> stages) {
        return Map.copyOf(new LinkedHashMap<>(stages));
    }
}
