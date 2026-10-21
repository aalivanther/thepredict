package akyrion.thepredict.domain.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactFormatTest {
    @Test
    void theSameCellsHashTheSameAndSecretsStayOutOfTheFingerprint() {
        var cell = new ArtifactFormat.ValueCell(1, 2, 3, 9L, 400, 10, 20);
        var first = ArtifactFormat.valueArtifact(20_000, 4, RewardSpec.ID, new ArtifactFormat.ValueCell[] {cell});
        var second = ArtifactFormat.valueArtifact(20_000, 4, RewardSpec.ID, new ArtifactFormat.ValueCell[] {cell});
        assertEquals("1a3c6b272d04affe9207e1918a89735c8c7fba957655bede6a72e23ed2e92fc0",
                ArtifactFormat.sha256(first));
        assertEquals(ArtifactFormat.sha256(first), ArtifactFormat.sha256(second));
        assertTrue(new String(first, java.nio.charset.StandardCharsets.US_ASCII).startsWith("TPLV"));
        assertEquals(64, ArtifactFormat.sha256(first).length());
        assertFalse(ArtifactHeader.included("jev.apiKey"));
        assertFalse(ArtifactHeader.included("records.directory"));
        assertTrue(ArtifactHeader.included("learning.collect"));
        var calibration = ArtifactFormat.calibrationArtifact(20_000, "jev-1.13.0", 1_000_000, 0);
        assertTrue(new String(calibration, java.nio.charset.StandardCharsets.US_ASCII).startsWith("TPLC"));
        var counted = new ArtifactFormat.ValueCell(1, 2, 3, 9L, 400, 10, 20, 80, 70, 15);
        var round = ArtifactFormat.valueArtifact(20_000, 4, RewardSpec.ID, new ArtifactFormat.ValueCell[] {counted});
        var read = ArtifactFormat.readValue(round)[0];
        assertEquals(80, read.jevSeen());
        assertEquals(70, read.jevAgree());
        assertEquals(15, read.deltaMillis());
        assertEquals(counted, read);
    }
}
