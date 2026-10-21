package akyrion.thepredict.domain.distill;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Canonical {@code .tpld} bytes (M29.6, rule C23). The reserve stores {@code n}, {@code sumDelta} and
 * {@code sumDeltaSq}; the two sums are quantized to millionths before they are written, so two builds that add the
 * same deltas in another order still give the same bytes. Cells are written in context order and a context appears
 * once. The same bytes always have the same SHA-256. A file is at most 64 KiB, and {@link #read} checks every length.
 */
public final class DistillArtifactFormat {
    /** Magic {@code TPLD}. */
    public static final String MAGIC = "TPLD";
    /** Format version. */
    public static final int FORMAT = 1;
    /** Published size cap. */
    public static final int MAX_BYTES = 65_536;
    private static final double QUANTUM = 1_000_000.0;

    private DistillArtifactFormat() {
    }

    /**
     * One reserve cell.
     *
     * @param context fine context
     * @param n comparisons
     * @param sumDelta sum of differences
     * @param sumDeltaSq sum of squares
     */
    public record Reserve(String context, int n, double sumDelta, double sumDeltaSq) {
    }

    /**
     * A file read back.
     *
     * @param fingerprint profile fingerprint
     * @param cells reserve cells in context order, sums in millionths
     */
    public record Read(String fingerprint, List<Reserve> cells) {
        /**
         * Copies the cells.
         */
        public Read {
            cells = List.copyOf(cells);
        }
    }

    /**
     * Writes an artifact. Cells are sorted by context.
     *
     * @param fingerprint profile fingerprint
     * @param cells reserve cells
     * @return the file bytes
     * @throws IllegalArgumentException when a context repeats or the file would pass {@value #MAX_BYTES} bytes
     */
    public static byte[] write(String fingerprint, Reserve[] cells) {
        var ordered = cells.clone();
        Arrays.sort(ordered, Comparator.comparing(Reserve::context));
        for (var index = 1; index < ordered.length; index++) {
            if (ordered[index - 1].context().equals(ordered[index].context())) {
                throw new IllegalArgumentException("distill artifact repeats a context");
            }
        }
        var payload = payload(fingerprint.getBytes(StandardCharsets.UTF_8), ordered);
        if (payload.length > MAX_BYTES) {
            throw new IllegalArgumentException("distill artifact");
        }
        return payload;
    }

    /**
     * Reads an artifact written by {@link #write}.
     *
     * @param bytes the file
     * @return the fingerprint and the cells
     * @throws IllegalArgumentException when the magic, the format or a length is wrong
     */
    public static Read read(byte[] bytes) {
        if (bytes.length > MAX_BYTES || bytes.length < 16
                || !MAGIC.equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("not a distill artifact");
        }
        try {
            var buffer = ByteBuffer.wrap(bytes, 4, bytes.length - 4);
            if (buffer.getInt() != FORMAT) {
                throw new IllegalArgumentException("distill artifact format");
            }
            var fingerprint = text(buffer);
            var count = buffer.getInt();
            if (count < 0 || count > buffer.remaining() / 24) {
                throw new IllegalArgumentException("distill artifact count");
            }
            var cells = new ArrayList<Reserve>(count);
            for (var index = 0; index < count; index++) {
                cells.add(new Reserve(text(buffer), buffer.getInt(), buffer.getLong() / QUANTUM,
                        buffer.getLong() / QUANTUM));
            }
            if (buffer.hasRemaining()) {
                throw new IllegalArgumentException("distill artifact length");
            }
            return new Read(fingerprint, cells);
        } catch (BufferUnderflowException exception) {
            throw new IllegalArgumentException("distill artifact is truncated", exception);
        }
    }

    /**
     * SHA-256 of the file bytes.
     *
     * @param bytes the file
     * @return the hex fingerprint
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256", exception);
        }
    }

    private static String text(ByteBuffer buffer) {
        var length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalArgumentException("distill artifact text");
        }
        var bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] payload(byte[] finger, Reserve[] cells) {
        var size = 16 + finger.length;
        for (var cell : cells) {
            size += 24 + cell.context().getBytes(StandardCharsets.UTF_8).length;
        }
        var buffer = ByteBuffer.allocate(size);
        buffer.put(MAGIC.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(FORMAT);
        buffer.putInt(finger.length);
        buffer.put(finger);
        buffer.putInt(cells.length);
        for (var cell : cells) {
            putCell(buffer, cell);
        }
        return buffer.array();
    }

    private static void putCell(ByteBuffer buffer, Reserve cell) {
        var text = cell.context().getBytes(StandardCharsets.UTF_8);
        buffer.putInt(text.length);
        buffer.put(text);
        buffer.putInt(cell.n());
        buffer.putLong(Math.round(cell.sumDelta() * QUANTUM));
        buffer.putLong(Math.round(cell.sumDeltaSq() * QUANTUM));
    }
}
