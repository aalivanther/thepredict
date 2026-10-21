package akyrion.thepredict.adapter.server.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatSummaryReaderTest {
    @Test
    void combatDurationTicksBecomeWholeSeconds() {
        assertEquals(0, CombatSummaryReader.combatSeconds(0));
        assertEquals(0, CombatSummaryReader.combatSeconds(19));
        assertEquals(1, CombatSummaryReader.combatSeconds(20));
        assertEquals(15, CombatSummaryReader.combatSeconds(300));
        assertEquals(0, CombatSummaryReader.combatSeconds(-40));
    }
}
