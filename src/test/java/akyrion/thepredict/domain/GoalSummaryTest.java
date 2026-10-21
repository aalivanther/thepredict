package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoalSummaryTest {
    @Test
    void typesAreCopiedAndBlankKeysRejected() {
        var types = new HashSet<>(Set.of("MOVE"));
        var summary = new GoalSummary("thepredict:hold", types, true);
        types.add("LOOK");
        assertEquals(Set.of("MOVE"), summary.types());
        assertThrows(IllegalArgumentException.class, () -> new GoalSummary(" ", Set.of(), false));
    }
}
