package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Canonical .tpld bytes. The same cells always have the same SHA-256. */
class DistillArtifactFormatTest {
    @Test
    void theHeaderAndTheSizeMatchAndAnOversizeFileIsRefused() {
        var cells = new DistillArtifactFormat.Reserve[] {new DistillArtifactFormat.Reserve("fine", 50, -0.1, 0.01)};
        var bytes = DistillArtifactFormat.write("finger", cells);
        var again = DistillArtifactFormat.write("finger", cells);
        assertArrayEquals(bytes, again);
        assertEquals(DistillArtifactFormat.sha256(bytes), DistillArtifactFormat.sha256(again));
        assertTrue(new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals(DistillArtifactFormat.MAGIC));
        assertEquals(16 + "finger".length() + 24 + "fine".length(), bytes.length);
        assertTrue(bytes.length <= DistillArtifactFormat.MAX_BYTES);
        var huge = "x".repeat(70_000);
        assertThrows(IllegalArgumentException.class,
                () -> DistillArtifactFormat.write(huge, new DistillArtifactFormat.Reserve[0]));
    }

    @Test
    void sumsAreQuantizedAndCellsSortedSoTheSameEvidenceHashesTheSame() {
        var sum = 0.1 + 0.2 + 0.3;
        var reordered = 0.3 + 0.2 + 0.1;
        assertTrue(sum != reordered);
        var first = DistillArtifactFormat.write("finger", new DistillArtifactFormat.Reserve[] {
                new DistillArtifactFormat.Reserve("b", 3, sum, 0.14), new DistillArtifactFormat.Reserve("a", 2, 0.5, 0.25)});
        var second = DistillArtifactFormat.write("finger", new DistillArtifactFormat.Reserve[] {
                new DistillArtifactFormat.Reserve("a", 2, 0.5, 0.25), new DistillArtifactFormat.Reserve("b", 3, reordered,
                0.14)});
        assertArrayEquals(first, second);
        var read = DistillArtifactFormat.read(first);
        assertEquals("finger", read.fingerprint());
        assertEquals("a", read.cells().getFirst().context());
        assertEquals(0.6, read.cells().getLast().sumDelta(), 1.0e-6);
        assertEquals(3, read.cells().getLast().n());
    }

    @Test
    void aRepeatedContextOrABrokenFileIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DistillArtifactFormat.write("f",
                new DistillArtifactFormat.Reserve[] {new DistillArtifactFormat.Reserve("a", 1, 0.0, 0.0),
                        new DistillArtifactFormat.Reserve("a", 2, 0.0, 0.0)}));
        var bytes = DistillArtifactFormat.write("f", new DistillArtifactFormat.Reserve[] {
                new DistillArtifactFormat.Reserve("a", 1, 0.0, 0.0)});
        assertThrows(IllegalArgumentException.class,
                () -> DistillArtifactFormat.read(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        var extra = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        assertThrows(IllegalArgumentException.class, () -> DistillArtifactFormat.read(extra));
        var count = bytes.clone();
        count[13] = 0x7f;
        assertThrows(IllegalArgumentException.class, () -> DistillArtifactFormat.read(count));
    }
}
