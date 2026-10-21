package akyrion.thepredict.domain.learn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Canonical artifact bytes (M28.9, rule C23). Integers are big-endian. Floats are quantized before they arrive.
 * The same bytes always have the same SHA-256.
 */
public final class ArtifactFormat {
    private ArtifactFormat() {
    }

    /**
     * One value cell, already quantized.
     *
     * @param profile profile code
     * @param tactic tactic code
     * @param level hierarchy level
     * @param context packed context
     * @param valueMillis mean
     * @param sdMillis deviation
     * @param nEff effective count
     * @param jevSeen questions seen in the fine cell
     * @param jevAgree agreements in the fine cell
     * @param deltaMillis stored disagreement delta
     */
    public record ValueCell(int profile, int tactic, int level, long context, int valueMillis, int sdMillis,
                            int nEff, int jevSeen, int jevAgree, int deltaMillis) {
        /**
         * A cell without Jev counts. The counts and the delta are zero.
         *
         * @param profile profile code
         * @param tactic tactic code
         * @param level hierarchy level
         * @param context packed context
         * @param valueMillis mean
         * @param sdMillis deviation
         * @param nEff effective count
         */
        public ValueCell(int profile, int tactic, int level, long context, int valueMillis, int sdMillis, int nEff) {
            this(profile, tactic, level, context, valueMillis, sdMillis, nEff, 0, 0, 0);
        }
    }

    /**
     * Writes a value artifact. The caller supplies cells in canonical order.
     *
     * @param day epoch day, part of the hash
     * @param batch batch the build read through
     * @param spec reward spec
     * @param cells ordered cells
     * @return the file bytes
     */
    public static byte[] valueArtifact(int day, int batch, String spec, ValueCell[] cells) {
        var specBytes = spec.getBytes(StandardCharsets.UTF_8);
        var buffer = ByteBuffer.allocate(4 + 16 + 4 + specBytes.length + cells.length * 44);
        buffer.put(ArtifactHeader.VALUE_MAGIC.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(ArtifactHeader.FORMAT);
        buffer.putInt(day);
        buffer.putInt(batch);
        buffer.putInt(cells.length);
        buffer.putInt(specBytes.length);
        buffer.put(specBytes);
        for (var cell : cells) {
            putCell(buffer, cell);
        }
        return buffer.array();
    }

    /**
     * Writes a calibration artifact. Slope and bias are millionths.
     *
     * @param day epoch day
     * @param model model name
     * @param slopeMillionths quantized slope
     * @param biasMillionths quantized bias
     * @return the file bytes
     */
    public static byte[] calibrationArtifact(int day, String model, int slopeMillionths, int biasMillionths) {
        var modelBytes = model.getBytes(StandardCharsets.UTF_8);
        var buffer = ByteBuffer.allocate(4 + 16 + modelBytes.length);
        buffer.put(ArtifactHeader.CALIBRATION_MAGIC.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(ArtifactHeader.FORMAT);
        buffer.putInt(day);
        buffer.putInt(slopeMillionths);
        buffer.putInt(biasMillionths);
        buffer.put(modelBytes);
        return buffer.array();
    }

    /**
     * One calibration file.
     *
     * @param day epoch day
     * @param model model name
     * @param slopeMillionths quantized slope
     * @param biasMillionths quantized bias
     */
    public record CalibrationArtifact(int day, String model, int slopeMillionths, int biasMillionths) {
    }

    /**
     * Reads a calibration artifact written by {@link #calibrationArtifact}.
     *
     * @param bytes the file
     * @return the header and the model name
     */
    public static CalibrationArtifact readCalibration(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes);
        var magic = new byte[4];
        buffer.get(magic);
        if (!ArtifactHeader.CALIBRATION_MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("calibration artifact");
        }
        if (buffer.getInt() != ArtifactHeader.FORMAT) {
            throw new IllegalArgumentException("calibration format");
        }
        var day = buffer.getInt();
        var slope = buffer.getInt();
        var bias = buffer.getInt();
        var model = new String(bytes, buffer.position(), bytes.length - buffer.position(), StandardCharsets.UTF_8);
        return new CalibrationArtifact(day, model, slope, bias);
    }

    /**
     * SHA-256 of the whole file, lowercase hex.
     *
     * @param bytes the file
     * @return 64 hex characters
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256", exception);
        }
    }

    private static void putCell(ByteBuffer buffer, ValueCell cell) {
        buffer.putInt(cell.profile());
        buffer.putInt(cell.tactic());
        buffer.putInt(cell.level());
        buffer.putLong(cell.context());
        buffer.putInt(cell.valueMillis());
        buffer.putInt(cell.sdMillis());
        buffer.putInt(cell.nEff());
        buffer.putInt(cell.jevSeen());
        buffer.putInt(cell.jevAgree());
        buffer.putInt(cell.deltaMillis());
    }

    /**
     * Reads the cells of a format 1 value artifact. The magic, the format and the exact length are checked before
     * anything is allocated, so a truncated file or an oversized count is refused. Format 2 is read by
     * {@link ValueArtifactFormat#read}.
     *
     * @param bytes the file
     * @return the cells in file order
     * @throws IllegalArgumentException when the file is not a format 1 value artifact of the declared length
     */
    public static ValueCell[] readValue(byte[] bytes) {
        if (bytes.length < 24 || !ArtifactHeader.VALUE_MAGIC.equals(new String(bytes, 0, 4,
                StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("not a value artifact");
        }
        var buffer = ByteBuffer.wrap(bytes);
        buffer.position(4);
        if (buffer.getInt() != ArtifactHeader.FORMAT) {
            throw new IllegalArgumentException("value artifact format");
        }
        buffer.position(16);
        var count = buffer.getInt();
        var specLength = buffer.getInt();
        if (count < 0 || specLength < 0 || (long) count * 44L + specLength != buffer.remaining()) {
            throw new IllegalArgumentException("value artifact length does not match its cell count");
        }
        buffer.position(buffer.position() + specLength);
        var cells = new ValueCell[count];
        for (var index = 0; index < count; index++) {
            cells[index] = new ValueCell(buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getLong(),
                    buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt(),
                    buffer.getInt());
        }
        return cells;
    }
}
