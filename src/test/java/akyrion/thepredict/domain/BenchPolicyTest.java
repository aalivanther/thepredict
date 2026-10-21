package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The error of a bad {@code AgainstEntities} names the section the author wrote it in (rule C13). */
class BenchPolicyTest {
    @Test
    void bothTokensParseUnderAnyKey() {
        assertEquals(BenchPolicy.FREE, BenchPolicy.parse("free"));
        assertEquals(BenchPolicy.FAIR, BenchPolicy.parse("fair", "Patterns.AgainstEntities"));
        assertEquals(BenchPolicy.FREE, BenchPolicy.DEFAULT);
    }

    @Test
    void aBadTokenNamesTheKeyItCameFrom() {
        var patterns = assertThrows(IllegalArgumentException.class,
                () -> BenchPolicy.parse("frie", "Patterns.AgainstEntities"));
        assertTrue(patterns.getMessage().startsWith("Patterns.AgainstEntities "), patterns.getMessage());
        assertTrue(patterns.getMessage().endsWith("was frie"), patterns.getMessage());
        var group = assertThrows(IllegalArgumentException.class, () -> BenchPolicy.parse("frie"));
        assertTrue(group.getMessage().startsWith("Group.AgainstEntities "), group.getMessage());
    }
}
