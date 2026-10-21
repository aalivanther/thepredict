package akyrion.thepredict.domain;

/**
 * Group confidence from the ratio of life times nominal damage (M25). Thresholds 0.5, 0.8, 1.25 and 2,
 * with a small hysteresis so a ratio on the boundary does not flicker.
 */
public final class ConfidencePolicy {
    private ConfidencePolicy() {
    }

    /**
     * The band of a ratio.
     *
     * @param ratio life times nominal damage of the group over the other side
     * @param previous the band already published
     * @return the band
     */
    public static GroupConfidence band(double ratio, GroupConfidence previous) {
        var raw = raw(ratio);
        if (previous == null || raw == previous) {
            return raw;
        }
        var boundary = boundary(previous, raw);
        if (Math.abs(ratio - boundary) < 0.02) {
            return previous;
        }
        return raw;
    }

    private static GroupConfidence raw(double ratio) {
        if (ratio < 0.5) {
            return GroupConfidence.PANICKED;
        }
        if (ratio < 0.8) {
            return GroupConfidence.WORRIED;
        }
        if (ratio < 1.25) {
            return GroupConfidence.NEUTRAL;
        }
        if (ratio < 2.0) {
            return GroupConfidence.CONFIDENT;
        }
        return GroupConfidence.HEROIC;
    }

    private static double boundary(GroupConfidence previous, GroupConfidence next) {
        var edge = Math.max(previous.ordinal(), next.ordinal());
        return switch (edge) {
            case 1 -> 0.5;
            case 2 -> 0.8;
            case 3 -> 1.25;
            default -> 2.0;
        };
    }
}
