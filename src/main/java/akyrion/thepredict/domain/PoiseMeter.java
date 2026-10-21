package akyrion.thepredict.domain;

/**
 * Pure poise meter (M23.7). The session stores the damage fraction and the last hit tick. Regen is computed on read
 * and this type allocates nothing.
 */
public final class PoiseMeter {
    private PoiseMeter() {
    }

    /**
     * Damage still on the meter.
     *
     * @param stored damage stored at {@code lastHit}
     * @param lastHit tick of that store
     * @param now current tick
     * @param settings profile poise
     * @return the fraction, never negative
     */
    public static double value(double stored, long lastHit, long now, PoiseSettings settings) {
        long idle = now - lastHit - settings.regenDelayTicks();
        if (idle <= 0) {
            return stored;
        }
        double decay = settings.regenPerSecond() * idle / 20.0;
        return Math.max(0.0, stored - decay);
    }

    /**
     * Adds a hit unless immunity holds.
     *
     * @param stored damage stored at {@code lastHit}
     * @param lastHit tick of that store
     * @param now current tick
     * @param incoming fraction of maximum health
     * @param windup whether the tell is in wind-up
     * @param immuneUntil tick immunity ends
     * @param settings profile poise
     * @return the new stored fraction, or the decayed value when the hit is ignored
     */
    public static double hit(double stored, long lastHit, long now, double incoming, boolean windup, long immuneUntil,
                             PoiseSettings settings) {
        double current = value(stored, lastHit, now, settings);
        if (now < immuneUntil || !(incoming > 0.0)) {
            return current;
        }
        double gained = windup ? incoming * settings.windUpMultiplier() : incoming;
        return current + gained;
    }

    /**
     * Whether the meter is full.
     *
     * @param stored current fraction
     * @param settings profile poise
     * @return whether it broke
     */
    public static boolean broken(double stored, PoiseSettings settings) {
        return stored >= settings.max();
    }
}
