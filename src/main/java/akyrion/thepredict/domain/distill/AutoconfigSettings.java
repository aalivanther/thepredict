package akyrion.thepredict.domain.distill;

/**
 * The {@code autoconfig} section (M29.8). {@code autoApply} is only {@code off}.
 *
 * @param initialConfigs Latin-hypercube size
 * @param initialPairs pairs on the first rung
 * @param eta reduction factor
 * @param maxPairs pair cap
 * @param maxMinutes minute cap
 * @param autoApply always {@code off}
 */
public record AutoconfigSettings(int initialConfigs, int initialPairs, int eta, int maxPairs, int maxMinutes,
                                 String autoApply) {
    /** A fresh installation. The measured simulator rate of M22.0 is above 3.67 rounds per second, so 81 stays. */
    public static final AutoconfigSettings DEFAULT = new AutoconfigSettings(81, 20, 3, 8000, 90, "off");

    /**
     * Checks ranges.
     */
    public AutoconfigSettings {
        if (initialConfigs < 9 || initialConfigs > 729) {
            throw new IllegalArgumentException("autoconfig.initialConfigs");
        }
        if (initialPairs < 5 || initialPairs > 100) {
            throw new IllegalArgumentException("autoconfig.initialPairs");
        }
        if (eta < 2 || eta > 4) {
            throw new IllegalArgumentException("autoconfig.eta");
        }
        if (maxPairs < 1000 || maxPairs > 50_000) {
            throw new IllegalArgumentException("autoconfig.maxPairs");
        }
        if (maxMinutes < 10 || maxMinutes > 600) {
            throw new IllegalArgumentException("autoconfig.maxMinutes");
        }
        if (!"off".equals(autoApply)) {
            throw new IllegalArgumentException("autoconfig.autoApply");
        }
    }
}
