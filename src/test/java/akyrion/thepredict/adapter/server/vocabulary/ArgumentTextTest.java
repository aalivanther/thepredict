package akyrion.thepredict.adapter.server.vocabulary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArgumentTextTest {
    private static final String GENERIC = "a value that is not a plain name";

    @Test
    void plainNamesAreQuoted() {
        assertEquals("'KEEP_distance_2'", ArgumentText.describe("KEEP_distance_2"));
        assertEquals("'" + "a".repeat(40) + "'", ArgumentText.describe("a".repeat(40)));
    }

    @Test
    void blankArgumentsAreDescribedAsMissing() {
        assertEquals("no value", ArgumentText.describe(""));
        assertEquals("no value", ArgumentText.describe("   "));
    }

    @Test
    void longOrSymbolArgumentsAreNeverEchoed() {
        assertEquals(GENERIC, ArgumentText.describe("a".repeat(41)));
        assertEquals(GENERIC, ArgumentText.describe("sk-live-123"));
        assertEquals(GENERIC, ArgumentText.describe("hold now"));
        assertEquals(GENERIC, ArgumentText.describe("<caster.name>"));
    }

    @Test
    void missingArgumentIsAProgrammingError() {
        assertThrows(NullPointerException.class, () -> ArgumentText.describe(null));
    }
}
