package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticIdTest {
    @Test
    void lengthsThreeAndFortyAreAccepted() {
        assertEquals("abc", new TacticId("abc").value());
        assertEquals(40, new TacticId("a" + "b".repeat(39)).value().length());
    }

    @Test
    void lengthsTwoAndFortyOneAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TacticId("ab"));
        assertThrows(IllegalArgumentException.class, () -> new TacticId("a" + "b".repeat(40)));
    }

    @Test
    void upperCaseHyphensSpacesAndLeadingDigitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TacticId("Retreat"));
        assertThrows(IllegalArgumentException.class, () -> new TacticId("hold-fast"));
        assertThrows(IllegalArgumentException.class, () -> new TacticId("hold fast"));
        assertThrows(IllegalArgumentException.class, () -> new TacticId("1hold"));
        assertThrows(NullPointerException.class, () -> new TacticId(null));
    }

    @Test
    void theDebugPrefixStaysReservedForProfiles() {
        assertEquals("debug_keep_distance", new TacticId("debug_keep_distance").value());
        assertTrue(new TacticId("debug_hold").isReserved());
    }

    @Test
    void noneIsReservedAndAuthorTacticsAreNot() {
        assertTrue(TacticId.NONE.isReserved());
        assertFalse(new TacticId("retreat_and_defend").isReserved());
    }

    @Test
    void toStringReturnsTheRawValue() {
        assertEquals("retreat_and_defend", new TacticId("retreat_and_defend").toString());
    }

    @Test
    void ofChecksTheShapeAndKeepsNothingBetweenCalls() {
        assertEquals(new TacticId("press_attack"), TacticId.of("press_attack"));
        org.junit.jupiter.api.Assertions.assertNotSame(TacticId.of("press_attack"), TacticId.of("press_attack"));
        assertThrows(IllegalArgumentException.class, () -> TacticId.of("Press"));
    }
}
