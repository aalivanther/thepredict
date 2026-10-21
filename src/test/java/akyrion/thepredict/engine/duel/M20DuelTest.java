package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class M20DuelTest {
    @Test
    void bothProfilesPinCastsAndStandard() throws Exception {
        var text = Files.readString(Path.of("DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/"
                + "archivos/13-duelo/plugins/MythicMobs/AIConfig/Profiles/ThePredictDuel.yml"));
        assertTrue(text.contains("TickLayer: casts"));
        assertTrue(text.contains("Cadence: standard"));
    }
}
