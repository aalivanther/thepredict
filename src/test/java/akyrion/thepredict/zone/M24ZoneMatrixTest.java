package akyrion.thepredict.zone;

import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.domain.ZoneMode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2 of M24 measured on every build. The report goes to {@code build/reports/m24/e2-zona.md}; the evidence of
 * 2026-10-07 under {@code DocumentacionParaDesarrollar} stays as published and is never rewritten by the suite.
 */
class M24ZoneMatrixTest {
    @Test
    void pairedSeedsPublishFiguresAndLeaveObserve() throws IOException {
        var started = System.nanoTime();
        var text = M24ZoneMatrix.report();
        var seconds = (System.nanoTime() - started) / 1e9;
        assertEquals(text, M24ZoneMatrix.report());
        assertTrue(text.contains("200 semillas pareadas"));
        assertTrue(text.contains("(E7): no cumple"));
        assertTrue(text.contains("No se pasa a guard"));
        assertEquals(ZoneMode.OBSERVE, ZoneCaps.DEFAULT.mode());
        var path = Path.of("build/reports/m24/e2-zona.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, text + String.format(Locale.ROOT, "%nMedido en %.3f s en esta máquina.%n", seconds));
        assertTrue(Files.size(path) > 200);
    }
}
