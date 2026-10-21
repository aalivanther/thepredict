package akyrion.thepredict.domain;

/**
 * Capped chamfer distance, orthogonal step 3 and diagonal step 4 (M24.1). Two passes. Package-private.
 */
final class ZoneChamfer {
    static final int INF = 1 << 20;

    private ZoneChamfer() {
    }

    static void window(boolean[] source, int width, int x0, int z0, int x1, int z1, short[] dest, int[] tmp,
                       int writeX0, int writeZ0, int writeX1, int writeZ1) {
        var ww = x1 - x0 + 1;
        var wh = z1 - z0 + 1;
        for (var z = 0; z < wh; z++) {
            var row = (z0 + z) * width;
            var into = z * ww;
            for (var x = 0; x < ww; x++) {
                tmp[into + x] = source[row + x0 + x] ? 0 : INF;
            }
        }
        forward(tmp, ww, wh);
        backward(tmp, ww, wh);
        for (var z = writeZ0; z <= writeZ1; z++) {
            var localZ = z - z0;
            var row = z * width;
            var into = localZ * ww;
            for (var x = writeX0; x <= writeX1; x++) {
                var value = tmp[into + (x - x0)];
                dest[row + x] = (short) (value > ZoneLayers.CAP ? ZoneLayers.CAP : value);
            }
        }
    }

    private static void forward(int[] tmp, int ww, int wh) {
        for (var z = 0; z < wh; z++) {
            for (var x = 0; x < ww; x++) {
                var i = z * ww + x;
                var d = tmp[i];
                if (x > 0) {
                    d = min(d, tmp[i - 1] + 3);
                }
                if (z > 0) {
                    d = min(d, tmp[i - ww] + 3);
                    if (x > 0) {
                        d = min(d, tmp[i - ww - 1] + 4);
                    }
                    if (x < ww - 1) {
                        d = min(d, tmp[i - ww + 1] + 4);
                    }
                }
                tmp[i] = d;
            }
        }
    }

    private static void backward(int[] tmp, int ww, int wh) {
        for (var z = wh - 1; z >= 0; z--) {
            for (var x = ww - 1; x >= 0; x--) {
                var i = z * ww + x;
                var d = tmp[i];
                if (x < ww - 1) {
                    d = min(d, tmp[i + 1] + 3);
                }
                if (z < wh - 1) {
                    d = min(d, tmp[i + ww] + 3);
                    if (x < ww - 1) {
                        d = min(d, tmp[i + ww + 1] + 4);
                    }
                    if (x > 0) {
                        d = min(d, tmp[i + ww - 1] + 4);
                    }
                }
                tmp[i] = d;
            }
        }
    }

    private static int min(int left, int right) {
        return left < right ? left : right;
    }
}
