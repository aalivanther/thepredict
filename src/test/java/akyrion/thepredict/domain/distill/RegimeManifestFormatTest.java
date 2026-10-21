package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Canonical .tplr bytes stay within 4 KiB. */
class RegimeManifestFormatTest {
    @Test
    void oneRuleWritesThePublishedSizeAndAHugeKeyIsRefused() {
        var rule = new RegimeManifest.Rule(1, 1, "r1:ALL", "abc");
        var bytes = RegimeManifestFormat.write(new RegimeManifest(List.of(rule)));
        var again = RegimeManifestFormat.write(new RegimeManifest(List.of(rule)));
        assertArrayEquals(bytes, again);
        assertEquals(RegimeManifestFormat.MAGIC, new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(12 + 16 + "r1:ALL".length() + "abc".length(), bytes.length);
        assertThrows(IllegalArgumentException.class, () -> RegimeManifestFormat.write(new RegimeManifest(List.of(
                new RegimeManifest.Rule(1, 1, "r1:ALL", "a".repeat(5000))))));
    }
}
