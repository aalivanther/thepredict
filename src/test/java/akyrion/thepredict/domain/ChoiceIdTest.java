package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChoiceIdTest {
    @Test
    void lengthsOneAndFortyAreAccepted() {
        assertEquals("a", new ChoiceId("a").value());
        assertEquals(40, new ChoiceId("a" + "1".repeat(39)).value().length());
    }

    @Test
    void emptyAndFortyOneCharactersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ChoiceId(""));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceId("a".repeat(41)));
    }

    @Test
    void upperCaseHyphensSpacesLeadingDigitsAndNullAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ChoiceId("Melee"));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceId("low-health"));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceId("low health"));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceId("1st"));
        assertThrows(NullPointerException.class, () -> new ChoiceId(null));
    }

    @Test
    void equalByValueAndPrintedRaw() {
        assertEquals(new ChoiceId("choice_one"), new ChoiceId("choice_one"));
        assertEquals("choice_one", new ChoiceId("choice_one").toString());
    }
}
