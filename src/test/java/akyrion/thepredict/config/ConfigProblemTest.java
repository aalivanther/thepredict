package akyrion.thepredict.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigProblemTest {
    @Test
    void formatsFileProfilePathAndMessage() {
        var problem = ConfigProblem.error("guardian.yml", "guardian_ai.Tactics.press_and_strike.Choice",
                "unknown choice 'mele'");

        assertEquals("guardian.yml: guardian_ai.Tactics.press_and_strike.Choice: unknown choice 'mele'", problem.toString());
        assertTrue(problem.isError());
    }

    @Test
    void omitsAnEmptyPathAndMarksWarnings() {
        var warning = ConfigProblem.warning("broken.yml", "", "is not valid YAML");

        assertEquals("broken.yml: is not valid YAML", warning.toString());
        assertFalse(warning.isError());
    }

    @Test
    void rejectsBlankFileOrMessage() {
        assertThrows(IllegalArgumentException.class, () -> ConfigProblem.error(" ", "path", "message"));
        assertThrows(IllegalArgumentException.class, () -> ConfigProblem.error("config.yml", "path", ""));
        assertThrows(NullPointerException.class, () -> new ConfigProblem(null, "config.yml", "", "message"));
    }

    @Test
    void configurationExceptionKeepsPathAndReasonApart() {
        var exception = new ConfigurationException("decision.maxRequestsPerMinute", "must be between 1 and 1200, was 0");

        assertEquals("decision.maxRequestsPerMinute must be between 1 and 1200, was 0", exception.getMessage());
        assertEquals("config.yml: decision.maxRequestsPerMinute: must be between 1 and 1200, was 0",
                exception.toProblem("config.yml").toString());
        assertEquals("uses schema 1", new ConfigurationException("", "uses schema 1").getMessage());
    }
}
