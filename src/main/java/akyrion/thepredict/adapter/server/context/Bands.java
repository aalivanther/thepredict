package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MovementSettings;

/**
 * Pure conversions of values read from Paper into the bands of 01 §4 (M07.3, M07.4). Stateless, allocation free
 * and safe on any thread; the thresholds are documented constants that M13 calibrates.
 */
public final class Bands {
    /** Armor points from which a player counts as {@link ArmorBand#MEDIUM}. */
    public static final double MEDIUM_ARMOR_POINTS = 8.0;
    /** Armor points from which a player counts as {@link ArmorBand#HEAVY}. */
    public static final double HEAVY_ARMOR_POINTS = 16.0;
    /** Armor toughness from which the armor band rises by one. */
    public static final double RAISING_TOUGHNESS_POINTS = 4.0;

    private Bands() {
    }

    /**
     * Health band over health plus absorption: {@code CRITICAL} up to and including the critical ratio,
     * {@code WOUNDED} up to and including the wounded ratio, {@code HEALTHY} above.
     *
     * @param health current health
     * @param absorption absorption amount
     * @param maxHealth value of the {@code MAX_HEALTH} attribute
     * @param thresholds ratios of the profile
     * @return the band
     * @throws IllegalArgumentException if {@code maxHealth} is not positive and finite
     */
    public static HealthBand health(double health, double absorption, double maxHealth, HealthThresholds thresholds) {
        if (!isValidMaxHealth(maxHealth)) {
            throw new IllegalArgumentException("maxHealth must be positive and finite, was " + maxHealth);
        }
        var ratio = (Math.max(0.0, health) + Math.max(0.0, absorption)) / maxHealth;
        if (ratio <= thresholds.criticalRatio()) {
            return HealthBand.CRITICAL;
        }
        return ratio <= thresholds.woundedRatio() ? HealthBand.WOUNDED : HealthBand.HEALTHY;
    }

    /**
     * Tells whether a maximum health can be used as a ratio denominator.
     *
     * @param maxHealth value of the {@code MAX_HEALTH} attribute
     * @return {@code true} if positive and finite
     */
    public static boolean isValidMaxHealth(double maxHealth) {
        return maxHealth > 0.0 && Double.isFinite(maxHealth);
    }

    /**
     * Distance band: {@code MELEE} up to and including the melee reach, {@code RANGED} up to and including the
     * ranged reach, {@code BEYOND} above.
     *
     * @param distanceBlocks distance to the target
     * @param movement movement settings of the profile
     * @return the band
     */
    public static DistanceBand distance(double distanceBlocks, MovementSettings movement) {
        if (distanceBlocks <= movement.meleeReachBlocks()) {
            return DistanceBand.MELEE;
        }
        return distanceBlocks <= movement.rangedReachBlocks() ? DistanceBand.RANGED : DistanceBand.BEYOND;
    }

    /**
     * Distance band from a squared distance, compared with the squared reaches so the probe needs no square root
     * (M07.6). Same bands as {@link #distance}.
     *
     * @param distanceSquared squared distance to the target
     * @param movement movement settings of the profile
     * @return the band
     */
    public static DistanceBand distanceSquared(double distanceSquared, MovementSettings movement) {
        var melee = movement.meleeReachBlocks();
        if (distanceSquared <= melee * melee) {
            return DistanceBand.MELEE;
        }
        var ranged = movement.rangedReachBlocks();
        return distanceSquared <= ranged * ranged ? DistanceBand.RANGED : DistanceBand.BEYOND;
    }

    /**
     * Armor band: below {@value #MEDIUM_ARMOR_POINTS} points {@code LIGHT}, below {@value #HEAVY_ARMOR_POINTS}
     * {@code MEDIUM}, from there {@code HEAVY}; a toughness of {@value #RAISING_TOUGHNESS_POINTS} or more raises
     * the band by one.
     *
     * @param armorPoints value of the {@code ARMOR} attribute
     * @param toughnessPoints value of the {@code ARMOR_TOUGHNESS} attribute
     * @return the band
     */
    public static ArmorBand armor(double armorPoints, double toughnessPoints) {
        var band = armorPoints >= HEAVY_ARMOR_POINTS ? ArmorBand.HEAVY
                : armorPoints >= MEDIUM_ARMOR_POINTS ? ArmorBand.MEDIUM : ArmorBand.LIGHT;
        if (toughnessPoints >= RAISING_TOUGHNESS_POINTS && band != ArmorBand.HEAVY) {
            return ArmorBand.values()[band.ordinal() + 1];
        }
        return band;
    }
}
