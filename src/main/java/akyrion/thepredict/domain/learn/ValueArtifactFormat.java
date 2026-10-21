package akyrion.thepredict.domain.learn;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical bytes of a {@link ValueArtifact} (M28.9, rule C23, format {@value ValueArtifact#FORMAT}). Big-endian
 * integers; a text is its UTF-8 length and bytes. Header: magic {@code TPLV}, format, day, batch, cell count, reward
 * spec, context version, half-life, weeks kept, κ of the four levels, then the source, profile and tactic tables.
 * Body: one 44-byte record per cell. The same artifact always gives the same bytes and SHA-256. The reader checks
 * every length before it allocates, so a truncated or oversized file is refused instead of read.
 */
public final class ValueArtifactFormat {
    /** Longest text the reader accepts, in bytes. */
    public static final int MAX_TEXT_BYTES = 1024;
    private static final int CELL_BYTES = 44;

    private ValueArtifactFormat() {
    }

    /**
     * Writes the canonical bytes.
     *
     * @param artifact the artifact
     * @return the file bytes
     */
    public static byte[] write(ValueArtifact artifact) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(ArtifactHeader.VALUE_MAGIC.getBytes(StandardCharsets.US_ASCII));
        putInt(out, ValueArtifact.FORMAT);
        putInt(out, artifact.day());
        putInt(out, artifact.batch());
        putInt(out, artifact.cells().size());
        putText(out, artifact.spec());
        putInt(out, artifact.contextVersion());
        putInt(out, artifact.halfLifeDays());
        putInt(out, artifact.weeksKept());
        for (var kappa : artifact.kappaMillis()) {
            putInt(out, kappa);
        }
        putTexts(out, artifact.sources());
        putTexts(out, artifact.profiles());
        putTexts(out, artifact.tactics());
        var body = ByteBuffer.allocate(artifact.cells().size() * CELL_BYTES);
        for (var cell : artifact.cells()) {
            putCell(body, cell);
        }
        out.writeBytes(body.array());
        return out.toByteArray();
    }

    /**
     * Reads and checks the canonical bytes.
     *
     * @param bytes the file
     * @return the artifact
     * @throws IllegalArgumentException when the magic, the format, a length or the canonical form is wrong
     */
    public static ValueArtifact read(byte[] bytes) {
        try {
            return parse(ByteBuffer.wrap(bytes));
        } catch (BufferUnderflowException exception) {
            throw new IllegalArgumentException("value artifact is truncated", exception);
        }
    }

    private static ValueArtifact parse(ByteBuffer buffer) {
        var magic = new byte[4];
        buffer.get(magic);
        if (!ArtifactHeader.VALUE_MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("not a value artifact");
        }
        if (buffer.getInt() != ValueArtifact.FORMAT) {
            throw new IllegalArgumentException("value artifact format");
        }
        var day = buffer.getInt();
        var batch = buffer.getInt();
        var count = count(buffer, CELL_BYTES);
        var spec = text(buffer);
        var contextVersion = buffer.getInt();
        var halfLife = buffer.getInt();
        var weeks = buffer.getInt();
        var kappas = new ArrayList<Integer>(ValueArtifact.LEVELS);
        for (var level = 0; level < ValueArtifact.LEVELS; level++) {
            kappas.add(buffer.getInt());
        }
        var sources = texts(buffer);
        var profiles = texts(buffer);
        var tactics = texts(buffer);
        return new ValueArtifact(day, batch, spec, contextVersion, sources, halfLife, weeks, kappas, profiles,
                tactics, cells(buffer, count));
    }

    private static List<ArtifactFormat.ValueCell> cells(ByteBuffer buffer, int count) {
        if (buffer.remaining() != count * CELL_BYTES) {
            throw new IllegalArgumentException("value artifact length does not match its cell count");
        }
        var cells = new ArrayList<ArtifactFormat.ValueCell>(count);
        for (var index = 0; index < count; index++) {
            cells.add(new ArtifactFormat.ValueCell(buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getLong(),
                    buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt(),
                    buffer.getInt()));
        }
        return cells;
    }

    private static int count(ByteBuffer buffer, int minimumBytes) {
        var count = buffer.getInt();
        if (count < 0 || count > buffer.remaining() / minimumBytes) {
            throw new IllegalArgumentException("value artifact count out of range");
        }
        return count;
    }

    private static String text(ByteBuffer buffer) {
        var length = buffer.getInt();
        if (length < 0 || length > MAX_TEXT_BYTES || length > buffer.remaining()) {
            throw new IllegalArgumentException("value artifact text out of range");
        }
        var bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> texts(ByteBuffer buffer) {
        var count = count(buffer, 4);
        var texts = new ArrayList<String>(count);
        for (var index = 0; index < count; index++) {
            texts.add(text(buffer));
        }
        return texts;
    }

    private static void putTexts(ByteArrayOutputStream out, List<String> texts) {
        putInt(out, texts.size());
        for (var text : texts) {
            putText(out, text);
        }
    }

    private static void putText(ByteArrayOutputStream out, String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("value artifact text too long");
        }
        putInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void putInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void putCell(ByteBuffer buffer, ArtifactFormat.ValueCell cell) {
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
}
