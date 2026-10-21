package akyrion.thepredict.domain.distill;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Canonical {@code .tplr} bytes (M29.5). At most 4 KiB.
 */
public final class RegimeManifestFormat {
    /** Magic {@code TPLR}. */
    public static final String MAGIC = "TPLR";
    /** Format version. */
    public static final int FORMAT = 1;
    /** Published size cap. */
    public static final int MAX_BYTES = 4096;

    private RegimeManifestFormat() {
    }

    /**
     * Writes a manifest.
     *
     * @param manifest the rules
     * @return the file bytes
     */
    public static byte[] write(RegimeManifest manifest) {
        var size = 12;
        for (var rule : manifest.rules()) {
            size += 16 + rule.key().getBytes(StandardCharsets.UTF_8).length
                    + rule.artifactSha256().getBytes(StandardCharsets.UTF_8).length;
        }
        var buffer = ByteBuffer.allocate(size);
        buffer.put(MAGIC.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(FORMAT);
        buffer.putInt(manifest.rules().size());
        for (var rule : manifest.rules()) {
            put(buffer, rule);
        }
        if (buffer.position() > MAX_BYTES) {
            throw new IllegalArgumentException("regime manifest");
        }
        return buffer.array();
    }

    private static void put(ByteBuffer buffer, RegimeManifest.Rule rule) {
        buffer.putInt(rule.enemyMask());
        buffer.putInt(rule.phaseMask());
        putText(buffer, rule.key());
        putText(buffer, rule.artifactSha256());
    }

    private static void putText(ByteBuffer buffer, String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }
}
