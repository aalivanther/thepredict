package akyrion.thepredict.domain;

import java.io.ByteArrayOutputStream;

/**
 * Big-endian float16 and unsigned LEB128 used only by {@link PlayerMemoryCodec}.
 */
final class MemoryBytes {
    /** Largest finite float16. */
    static final float FLOAT16_MAX = 65504f;

    private MemoryBytes() {
    }

    static void u8(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
    }

    static void u32(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    static void f16(ByteArrayOutputStream out, float value) {
        var bits = Float.floatToFloat16(clip(value));
        out.write((bits >>> 8) & 0xFF);
        out.write(bits & 0xFF);
    }

    static void varint(ByteArrayOutputStream out, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("varint");
        }
        var rest = value;
        while (true) {
            var bite = (int) (rest & 0x7F);
            rest >>>= 7;
            if (rest != 0) {
                out.write(bite | 0x80);
            } else {
                out.write(bite);
                return;
            }
        }
    }

    /**
     * Clips a mass into the finite non-negative float16 range.
     *
     * @param value the mass
     * @return the clipped mass
     */
    static float clip(float value) {
        if (Float.isNaN(value) || value < 0f) {
            return 0f;
        }
        return Math.min(value, FLOAT16_MAX);
    }

    /**
     * Whether clipping changed the value.
     *
     * @param value the mass
     * @return {@code true} when the value was outside the range
     */
    static boolean saturated(float value) {
        return Float.isNaN(value) || value < 0f || value > FLOAT16_MAX;
    }
}
