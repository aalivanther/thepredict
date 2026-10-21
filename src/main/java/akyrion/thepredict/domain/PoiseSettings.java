package akyrion.thepredict.domain;

/**
 * Profile poise (M23.7). A global {@code poise} key is not a setting: the loader rejects it.
 *
 * @param max fraction of maximum health that breaks poise
 * @param regenDelayTicks ticks after a hit before regen
 * @param regenPerSecond fraction removed each second after the delay
 * @param windUpMultiplier multiplier while a tell is in wind-up
 * @param blockedHitPoise fraction added when an own heavy or severe hit is blocked
 * @param staggerTicks ticks of {@link ActionStance#STAGGERED}
 * @param immunityTicks ticks that ignore poise after a break
 */
public record PoiseSettings(double max, int regenDelayTicks, double regenPerSecond, double windUpMultiplier,
                            double blockedHitPoise, int staggerTicks, int immunityTicks) {
    /**
     * Validates the ranges.
     *
     * @throws IllegalArgumentException if a range fails
     */
    public PoiseSettings {
        if (!(max >= 0.02 && max <= 1.0)) {
            throw new IllegalArgumentException("max must be within [0.02, 1], was " + max);
        }
        DomainChecks.requireRange(regenDelayTicks, 0, 400, "regenDelayTicks");
        if (!(regenPerSecond >= 0.0 && regenPerSecond <= 1.0)) {
            throw new IllegalArgumentException("regenPerSecond must be within [0, 1], was " + regenPerSecond);
        }
        if (!(windUpMultiplier >= 1.0 && windUpMultiplier <= 3.0)) {
            throw new IllegalArgumentException("windUpMultiplier must be within [1, 3], was " + windUpMultiplier);
        }
        if (!(blockedHitPoise >= 0.0 && blockedHitPoise <= 0.5)) {
            throw new IllegalArgumentException("blockedHitPoise must be within [0, 0.5], was " + blockedHitPoise);
        }
        DomainChecks.requireRange(staggerTicks, 5, 100, "staggerTicks");
        DomainChecks.requireRange(immunityTicks, 0, 1200, "immunityTicks");
    }

    /**
     * The profile example and the documented defaults.
     *
     * @return max 0.15, delay 60, regen 0.05, wind-up 1.5, blocked 0.05, stagger 30, immunity 100
     */
    public static PoiseSettings defaults() {
        return new PoiseSettings(0.15, 60, 0.05, 1.5, 0.05, 30, 100);
    }
}
