package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M11.2 and M11.3: every row of the fact table, the limiter and their boundaries (rule P1). */
class ThreatPolicyTest {
    private static final ThreatWeights DEFAULTS = new ThreatWeights(1.5, 1.0, 0.25, 2.0, 20.0);
    private static final ThreatWeights OFF = new ThreatWeights(0, 0, 0, 0, 20.0);

    @Test
    void anAssistAddsTheWeightForEveryOtherParticipantAssisted() {
        assertEquals(1.5, ThreatPolicy.change(EventType.ALLY_ASSISTED, 1, DEFAULTS));
        assertEquals(4.5, ThreatPolicy.change(EventType.ALLY_ASSISTED, 3, DEFAULTS));
    }

    @Test
    void aBlockedHitAddsTheBlockedHitWeight() {
        assertEquals(0.25, ThreatPolicy.change(EventType.MELEE_BLOCKED, 1, DEFAULTS));
    }

    @Test
    void rangedHarassMultipliesTheFinalDamage() {
        assertEquals(6.5, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, 6.5, DEFAULTS));
        assertEquals(13.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, 6.5,
                new ThreatWeights(1.5, 2.0, 0.25, 2.0, 20.0)));
    }

    @Test
    void anUnreachableTargetLosesTheDecayPerSecondForTheSecondsElapsed() {
        assertEquals(-2.0, ThreatPolicy.unreachableDecay(20, DEFAULTS));
        assertEquals(-3.0, ThreatPolicy.unreachableDecay(30, DEFAULTS));
        assertEquals(-0.1, ThreatPolicy.unreachableDecay(1, DEFAULTS), 1e-12);
        assertEquals(0.0, ThreatPolicy.unreachableDecay(0, DEFAULTS));
    }

    @Test
    void theOtherEventTypesChangeNothing() {
        for (var type : EventType.values()) {
            if (type != EventType.ALLY_ASSISTED && type != EventType.MELEE_BLOCKED
                    && type != EventType.RANGED_HIT_ON_MOB && type != EventType.GROUP_ASSIGNMENT) {
                assertEquals(0.0, ThreatPolicy.change(type, 5, DEFAULTS), type.name());
            }
        }
        assertEquals(5.0, ThreatPolicy.change(EventType.GROUP_ASSIGNMENT, 5, DEFAULTS));
        assertEquals(20.0, ThreatPolicy.change(EventType.GROUP_ASSIGNMENT, 40, DEFAULTS));
    }

    @Test
    void aZeroWeightOrAZeroMagnitudeDisablesTheFact() {
        for (var type : EventType.values()) {
            if (type == EventType.GROUP_ASSIGNMENT) {
                assertEquals(7.0, ThreatPolicy.change(type, 7, OFF), type.name());
            } else {
                assertEquals(0.0, ThreatPolicy.change(type, 7, OFF), type.name());
            }
            assertEquals(0.0, ThreatPolicy.change(type, 0, DEFAULTS), type.name());
        }
        assertEquals(0.0, ThreatPolicy.unreachableDecay(400, OFF));
    }

    @Test
    void negativeOrNotANumberMagnitudesAreNoObservationAndNegativeTimesAreRejected() {
        assertEquals(0.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, -0.5, DEFAULTS));
        assertEquals(0.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, Double.NaN, DEFAULTS));
        assertEquals(0.0, ThreatPolicy.change(EventType.ALLY_ASSISTED, Double.NEGATIVE_INFINITY, DEFAULTS));
        assertThrows(IllegalArgumentException.class, () -> ThreatPolicy.unreachableDecay(-1, DEFAULTS));
        assertThrows(IllegalArgumentException.class, () -> new ThreatWeights(Double.NaN, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ThreatWeights(1, -1, 1, 1, 1));
    }

    @Test
    void aProductBelowTheCapIsExactEvenWhenTheMagnitudeAloneIsAboveIt() {
        assertEquals(15.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, 30,
                new ThreatWeights(1.5, 0.5, 0.25, 2.0, 20.0)));
        assertEquals(4.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, 8,
                new ThreatWeights(1.5, 0.5, 0.25, 2.0, 5.0)));
        assertEquals(5.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, 12,
                new ThreatWeights(1.5, 0.5, 0.25, 2.0, 5.0)), "a product above the cap gives the cap");
    }

    @Test
    void aHugeMagnitudeOrWeightNeverOverflowsAndNeverPassesTheCap() {
        var doubled = new ThreatWeights(1.5, 2.0, 0.25, 2.0, 20.0);
        assertEquals(20.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, Double.MAX_VALUE, doubled));
        assertEquals(20.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, 1e308, doubled));
        assertEquals(20.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, Double.POSITIVE_INFINITY, doubled));
        assertEquals(20.0, ThreatPolicy.change(EventType.ALLY_ASSISTED, 3,
                new ThreatWeights(Double.MAX_VALUE, 1, 1, 1, 20)));
        assertEquals(0.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, Double.MAX_VALUE,
                new ThreatWeights(1, 1, 1, 1, 0)));
        assertEquals(0.0, ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, Double.POSITIVE_INFINITY,
                new ThreatWeights(1, 0, 1, 1, 20)), "a zero weight never multiplies an infinite magnitude");
        assertEquals(20.0, ThreatPolicy.admitted(ThreatPolicy.change(EventType.RANGED_HIT_ON_MOB, 1e308, doubled), 0,
                20), "the capped change is always a valid input of the limiter");
        var loss = ThreatPolicy.unreachableDecay(Long.MAX_VALUE, new ThreatWeights(1, 1, 1, Double.MAX_VALUE, 20));
        assertTrue(Double.isFinite(loss) && loss < 0.0);
    }

    @Test
    void theLimiterAdmitsWhatIsLeftOfTheWindowAndNothingOnceItIsSpent() {
        assertEquals(5.0, ThreatPolicy.admitted(5, 0, 20));
        assertEquals(2.0, ThreatPolicy.admitted(5, 18, 20));
        assertEquals(5.0, ThreatPolicy.admitted(5, 15, 20), "a gain that exactly reaches the cap is whole");
        assertEquals(0.0, ThreatPolicy.admitted(5, 20, 20));
        assertEquals(0.0, ThreatPolicy.admitted(0.25, 20, 20));
        assertEquals(0.0, ThreatPolicy.admitted(0, 3, 20));
        assertThrows(IllegalArgumentException.class, () -> ThreatPolicy.admitted(-1, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> ThreatPolicy.admitted(1, Double.NaN, 20));
    }

    @Test
    void theWindowFollowsTheDamageStatusResetOfTheVanillaCombatTracker() {
        assertEquals(100, ThreatPolicy.WINDOW_TICKS);
        assertEquals(20, ThreatPolicy.TICKS_PER_SECOND);
    }
}
