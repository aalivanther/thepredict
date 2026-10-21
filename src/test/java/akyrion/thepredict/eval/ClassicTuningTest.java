package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The frozen classic grid winner (M22.3). The full 36×200 search is outside check. */
class ClassicTuningTest {
    @Test
    void theFrozenFileHasThePublishedFingerprint() throws Exception {
        var path = Path.of("src/test/resources/eval/classic-tuned-v1.yml");
        var bytes = Files.readAllBytes(path);
        var hex = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertEquals("a973c33730f28e0b410b4a3b80e214a634418089be27dda196dd9fb5fc7e478f", hex);
        var text = Files.readString(path);
        assertEquals("delay=10\nband=5\nratio=0.30\nguard=true\n", text);
    }
}
