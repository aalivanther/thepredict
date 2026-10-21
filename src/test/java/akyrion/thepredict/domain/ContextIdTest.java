package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextIdTest {
    @Test
    void lengthsOneAndFortyAreAccepted() {
        assertEquals("a", new ContextId("a").value());
        assertEquals(40, new ContextId("a" + "1".repeat(39)).value().length());
    }

    @Test
    void emptyAndFortyOneCharactersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContextId(""));
        assertThrows(IllegalArgumentException.class, () -> new ContextId("a".repeat(41)));
    }

    @Test
    void upperCaseHyphensSpacesLeadingDigitsAndNullAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContextId("Melee"));
        assertThrows(IllegalArgumentException.class, () -> new ContextId("low-health"));
        assertThrows(IllegalArgumentException.class, () -> new ContextId("low health"));
        assertThrows(IllegalArgumentException.class, () -> new ContextId("1st"));
        assertThrows(NullPointerException.class, () -> new ContextId(null));
    }

    @Test
    void equalByValueAndPrintedRaw() {
        assertEquals(new ContextId("context_one"), new ContextId("context_one"));
        assertEquals("context_one", new ContextId("context_one").toString());
    }
}
