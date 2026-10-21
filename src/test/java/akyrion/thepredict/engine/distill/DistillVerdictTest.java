package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rules W8 and A17: a distill verdict is read field by field and only for the artifact it names. */
class DistillVerdictTest {
    private static final String SHA = "ab".repeat(32);

    @Test
    void aNestedPassOrTapeDoesNotRaiseTheCeiling() throws Exception {
        var file = write("{\"sha256\":\"" + SHA + "\",\"stages\":{\"G1\":{\"status\":\"PASS\"}},\"status\":\"FAIL\","
                + "\"notes\":{\"tape\":true}}");
        assertEquals(DistillMode.OFF, DistillVerdict.load(file, SHA));
        var tapeInNotes = write("{\"sha256\":\"" + SHA + "\",\"status\":\"PASS\",\"notes\":{\"tape\":true}}");
        assertEquals(DistillMode.ASSIST, DistillVerdict.load(tapeInNotes, SHA));
    }

    @Test
    void theTopLevelFieldsOfTheNamedArtifactGiveTheCeiling() throws Exception {
        assertEquals(DistillMode.AUTONOMOUS, DistillVerdict.load(write(verdict("PASS", true)), SHA));
        assertEquals(DistillMode.ASSIST, DistillVerdict.load(write(verdict("PASS", false)), SHA));
        assertEquals(DistillMode.ASSIST, DistillVerdict.load(write(verdict("PASS_SIM_ONLY", true)), SHA));
        assertEquals(DistillMode.SHADOW, DistillVerdict.load(write(verdict("G0", false)), SHA));
    }

    @Test
    void anotherArtifactAMalformedFileOrNoFingerprintStaysOff() throws Exception {
        var pass = write(verdict("PASS", true));
        assertEquals(DistillMode.OFF, DistillVerdict.load(pass, "cd".repeat(32)));
        assertEquals(DistillMode.OFF, DistillVerdict.load(pass, ""));
        assertEquals(DistillMode.OFF, DistillVerdict.load(pass));
        assertEquals(DistillMode.OFF, DistillVerdict.load(write("{\"sha256\":\"" + SHA + "\",\"status\":\"PASS\""), SHA));
        assertEquals(DistillMode.OFF, DistillVerdict.load(write("{\"status\":\"PASS\",\"tape\":true}"), SHA));
        assertEquals(DistillMode.OFF, DistillVerdict.load(Path.of("missing-verdict.json"), SHA));
    }

    private static String verdict(String status, boolean tape) {
        return "{\"sha256\":\"" + SHA + "\",\"status\":\"" + status + "\",\"tape\":" + tape + "}";
    }

    private static Path write(String text) throws Exception {
        var file = Files.createTempFile("verdict", ".json");
        Files.writeString(file, text);
        return file;
    }
}
