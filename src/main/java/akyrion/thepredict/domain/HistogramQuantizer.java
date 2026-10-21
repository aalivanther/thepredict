package akyrion.thepredict.domain;

/**
 * Largest-remainder quantizer for a 16-bin shape that must sum to 255 (M27.2). Ties keep the lower index.
 */
public final class HistogramQuantizer {
    /** Cells of one shape. */
    public static final int BINS = 16;
    /** Sum of a stored shape. */
    public static final int SUM = 255;

    private HistogramQuantizer() {
    }

    /**
     * Scales a stored shape, adds a 16-bin fight and quantizes.
     *
     * @param mass stored mass
     * @param shape stored cells, length 16
     * @param factor scale in {@code [0, 1]}
     * @param added fight bins, length 16, already counts
     * @return mass and cells
     */
    public static Shape add(float mass, byte[] shape, float factor, float[] added) {
        var raw = new double[BINS];
        var next = mass * factor;
        for (var index = 0; index < BINS; index++) {
            raw[index] = mass * (shape[index] & 0xFF) / (double) SUM * factor + added[index];
            next += added[index];
        }
        return new Shape((float) next, quantize(raw));
    }

    private static byte[] quantize(double[] raw) {
        var total = 0.0;
        for (var value : raw) {
            total += Math.max(0.0, value);
        }
        var cells = new byte[BINS];
        if (total <= 0.0) {
            return cells;
        }
        var floors = new int[BINS];
        var used = 0;
        var remainders = new double[BINS];
        for (var index = 0; index < BINS; index++) {
            var share = Math.max(0.0, raw[index]) / total * SUM;
            floors[index] = (int) StrictMath.floor(share);
            remainders[index] = share - floors[index];
            used += floors[index];
        }
        var order = order(remainders);
        for (var extra = 0; extra < SUM - used; extra++) {
            floors[order[extra]]++;
        }
        for (var index = 0; index < BINS; index++) {
            cells[index] = (byte) floors[index];
        }
        return cells;
    }

    private static int[] order(double[] remainders) {
        var order = new int[BINS];
        for (var index = 0; index < BINS; index++) {
            order[index] = index;
        }
        for (var left = 0; left < BINS; left++) {
            for (var right = left + 1; right < BINS; right++) {
                var swap = remainders[order[right]] > remainders[order[left]]
                        || (remainders[order[right]] == remainders[order[left]] && order[right] < order[left]);
                if (swap) {
                    var held = order[left];
                    order[left] = order[right];
                    order[right] = held;
                }
            }
        }
        return order;
    }

    /**
     * A quantized histogram.
     *
     * @param mass the mass
     * @param shape 16 cells summing to 255, or zeros when the mass is zero
     */
    public record Shape(float mass, byte[] shape) {
        /**
         * Copies the cells.
         */
        public Shape {
            shape = shape.clone();
        }
    }
}
