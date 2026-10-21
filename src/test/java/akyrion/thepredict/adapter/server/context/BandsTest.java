package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MovementSettings;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandsTest {
    private static final HealthThresholds THRESHOLDS = new HealthThresholds(0.3, 0.6);
    private static final MovementSettings MOVEMENT = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15,
            OptionalDouble.empty());

    @Test
    void healthBandsSwitchExactlyAtTheDefaultRatios() {
        assertEquals(HealthBand.CRITICAL, Bands.health(0, 0, 20, THRESHOLDS));
        assertEquals(HealthBand.CRITICAL, Bands.health(6, 0, 20, THRESHOLDS));
        assertEquals(HealthBand.WOUNDED, Bands.health(Math.nextUp(6.0), 0, 20, THRESHOLDS));
        assertEquals(HealthBand.WOUNDED, Bands.health(12, 0, 20, THRESHOLDS));
        assertEquals(HealthBand.HEALTHY, Bands.health(Math.nextUp(12.0), 0, 20, THRESHOLDS));
        assertEquals(HealthBand.HEALTHY, Bands.health(20, 0, 20, THRESHOLDS));
    }

    @Test
    void absorptionCountsWithHealthAndMayExceedTheMaximum() {
        assertEquals(HealthBand.WOUNDED, Bands.health(4, 4, 20, THRESHOLDS));
        assertEquals(HealthBand.HEALTHY, Bands.health(20, 8, 20, THRESHOLDS));
        assertEquals(HealthBand.CRITICAL, Bands.health(-3, 0, 20, THRESHOLDS));
    }

    @Test
    void customThresholdsOfTheProfileApply() {
        var strict = new HealthThresholds(0.1, 0.2);
        assertEquals(HealthBand.HEALTHY, Bands.health(6, 0, 20, strict));
        assertEquals(HealthBand.CRITICAL, Bands.health(2, 0, 20, strict));
    }

    @Test
    void anInvalidMaximumHealthIsRejected() {
        assertFalse(Bands.isValidMaxHealth(0));
        assertFalse(Bands.isValidMaxHealth(-1));
        assertFalse(Bands.isValidMaxHealth(Double.NaN));
        assertFalse(Bands.isValidMaxHealth(Double.POSITIVE_INFINITY));
        assertTrue(Bands.isValidMaxHealth(Double.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> Bands.health(1, 0, 0, THRESHOLDS));
        assertThrows(IllegalArgumentException.class, () -> Bands.health(1, 0, Double.NaN, THRESHOLDS));
    }

    @Test
    void distanceBandsIncludeEachReachAndSwitchJustAbove() {
        assertEquals(DistanceBand.MELEE, Bands.distance(0, MOVEMENT));
        assertEquals(DistanceBand.MELEE, Bands.distance(2.5, MOVEMENT));
        assertEquals(DistanceBand.RANGED, Bands.distance(Math.nextUp(2.5), MOVEMENT));
        assertEquals(DistanceBand.RANGED, Bands.distance(16, MOVEMENT));
        assertEquals(DistanceBand.BEYOND, Bands.distance(Math.nextUp(16.0), MOVEMENT));
    }

    @Test
    void theSquaredDistanceGivesTheSameBandWithoutASquareRoot() {
        for (var tenths = 0; tenths <= 400; tenths++) {
            var distance = tenths / 10.0;
            assertEquals(Bands.distance(distance, MOVEMENT), Bands.distanceSquared(distance * distance, MOVEMENT),
                    "distance " + distance);
        }
        assertEquals(DistanceBand.MELEE, Bands.distanceSquared(6.25, MOVEMENT));
        assertEquals(DistanceBand.RANGED, Bands.distanceSquared(Math.nextUp(6.25), MOVEMENT));
        assertEquals(DistanceBand.RANGED, Bands.distanceSquared(256, MOVEMENT));
        assertEquals(DistanceBand.BEYOND, Bands.distanceSquared(Math.nextUp(256.0), MOVEMENT));
    }

    @Test
    void armorBandsSwitchAtEightAndSixteenPoints() {
        assertEquals(ArmorBand.LIGHT, Bands.armor(0, 0));
        assertEquals(ArmorBand.LIGHT, Bands.armor(Math.nextDown(8.0), 0));
        assertEquals(ArmorBand.MEDIUM, Bands.armor(8, 0));
        assertEquals(ArmorBand.MEDIUM, Bands.armor(Math.nextDown(16.0), 0));
        assertEquals(ArmorBand.HEAVY, Bands.armor(16, 0));
        assertEquals(ArmorBand.HEAVY, Bands.armor(30, 0));
    }

    @Test
    void toughnessOfFourRaisesTheBandByOneButNeverBeyondHeavy() {
        assertEquals(ArmorBand.LIGHT, Bands.armor(5, Math.nextDown(4.0)));
        assertEquals(ArmorBand.MEDIUM, Bands.armor(5, 4));
        assertEquals(ArmorBand.HEAVY, Bands.armor(12, 4));
        assertEquals(ArmorBand.HEAVY, Bands.armor(20, 12));
    }
}
