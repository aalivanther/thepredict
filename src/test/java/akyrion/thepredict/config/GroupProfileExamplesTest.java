package akyrion.thepredict.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The session 25 profiles load under the delivered global mode. */
class GroupProfileExamplesTest {
    @Test
    void theThreeExamplesLoad() throws Exception {
        var directory = Path.of("DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/archivos/"
                + "25-grupo/plugins/MythicMobs/AIConfig/Profiles");
        var loaded = new ProfileLoader().load(directory);
        assertTrue(loaded.problems().stream().noneMatch(problem -> problem.severity() == ConfigProblem.Severity.ERROR),
                loaded.problems().toString());
        assertEquals(3, loaded.profiles().size());
    }
}
