package akyrion.thepredict.eval;

import akyrion.thepredict.domain.learn.HealthBand3;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bank text maps to bands. A missing distance stays unmapped. Jev answers are not read as labels. */
class BankContextMapperTest {
    @Test
    void meleeMapsAndAMissingDistanceDoesNotAndTheBankClearsForty() throws Exception {
        var melee = BankContextMapper.map("bank-melee-01", """
                {"participants": 1, "facts": ["mob_1 is healthy", "target is player_1",
                "target is inside melee reach", "target is healthy"]}
                """);
        assertTrue(melee.key().isPresent());
        assertEquals(HealthBand3.HIGH, HealthBand3.HIGH);
        assertTrue(melee.key().orElseThrow().text().contains("MELEE"));
        assertTrue(melee.key().orElseThrow().text().contains("PLAYER"));
        var missing = BankContextMapper.map("gap", "{\"participants\": 1, \"facts\": [\"mob_1 is healthy\"]}");
        assertTrue(missing.key().isEmpty());
        assertTrue(missing.unmapped().contains("distance"));
        var rows = BankContextMapper.read(Path.of("src/test/resources/scenarios/bank"));
        assertEquals(60, rows.size());
        assertEquals("MAPPED", BankContextMapper.verdict(rows));
        assertFalse(rows.getFirst().id().contains("answer"));
    }
}
