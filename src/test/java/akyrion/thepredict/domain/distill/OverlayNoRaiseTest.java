package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayNoRaiseTest {
    @Test
    void anOverlayStaysInsideAutoconfigAndCannotRaiseTheGlobal() {
        assertTrue(OverlayPath.allowed("plugins/ThePredict/autoconfig/guardian.yml"));
        assertFalse(OverlayPath.allowed("plugins/ThePredict/autoconfig/../Profiles/guardian.yml"));
        assertFalse(OverlayPath.allowed("plugins/MythicMobs/Mobs/Guardian.yml"));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayStages.check("Zone.Mode", "assist", 0, 2));
        assertEquals("off", AutoconfigSettings.DEFAULT.autoApply());
        assertEquals(81, AutoconfigSettings.DEFAULT.initialConfigs());
    }
}
