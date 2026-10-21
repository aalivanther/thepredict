package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Exact chamfer windows and the 19 by 19 counterexample (M24.3). */
class ZoneLayersTest {
    @Test
    void narrowPatchDoesNotMatchTheExactWindow() {
        var width = 49;
        var source = new boolean[width * width];
        source[24 * width + 36] = true;
        var exact = new short[width * width];
        var narrow = new short[width * width];
        var scratch = new int[width * width];
        ZoneLayers.rebuild(source, exact, width, scratch);
        ZoneLayers.patch(source, new short[width * width], width, 24, 24, scratch);
        var patched = new short[width * width];
        ZoneLayers.patch(source, patched, width, 24, 24, scratch);
        ZoneLayers.patchNarrow(source, narrow, width, 24, 24, scratch);
        var index = 24 * width + 33;
        assertEquals(exact[index], patched[index]);
        assertNotEquals(exact[index], narrow[index]);
    }

    @Test
    void chamferStaysWithinEightPercentOfBreadthFirst() {
        var width = 104;
        var cells = width * width;
        var source = new boolean[cells];
        for (var z = 0; z < width; z += 6) {
            for (var x = 0; x < width; x += 6) {
                source[z * width + x] = true;
            }
        }
        var layer = new short[cells];
        var scratch = new int[cells];
        ZoneLayers.rebuild(source, layer, width, scratch);
        var exact = breadth(source, width);
        var compared = 0;
        for (var index = 0; index < cells; index++) {
            if (exact[index] == 0 || layer[index] >= ZoneLayers.CAP) {
                continue;
            }
            var error = Math.abs(layer[index] - exact[index]) / (double) exact[index];
            org.junit.jupiter.api.Assertions.assertTrue(error <= 0.08, error + " at " + index);
            compared++;
        }
        org.junit.jupiter.api.Assertions.assertTrue(compared >= 10_000, "compared " + compared);
    }

    @Test
    void aPatchMatchesARebuildWhenAnyCellGainsOrLosesASource() {
        var width = 40;
        var cells = width * width;
        var base = new boolean[cells];
        for (var index = 0; index < cells; index++) {
            base[index] = StableRandom.below(StableRandom.mix(24L ^ index), 17) == 0;
        }
        var scratch = new int[cells];
        var baseLayer = new short[cells];
        ZoneLayers.rebuild(base, baseLayer, width, scratch);
        var added = 0;
        var removed = 0;
        for (var at = 0; at < cells; at++) {
            var source = base.clone();
            source[at] = !source[at];
            if (source[at]) {
                added++;
            } else {
                removed++;
            }
            var patched = baseLayer.clone();
            ZoneLayers.patch(source, patched, width, at % width, at / width, scratch);
            var rebuilt = new short[cells];
            ZoneLayers.rebuild(source, rebuilt, width, scratch);
            assertWindowEquals(rebuilt, patched, width, at % width, at / width);
            assertOutsideUntouched(baseLayer, patched, width, at % width, at / width);
        }
        org.junit.jupiter.api.Assertions.assertTrue(added > 0 && removed > 0, added + " added, " + removed + " removed");
    }

    private static void assertOutsideUntouched(short[] before, short[] patched, int width, int x, int z) {
        for (var index = 0; index < before.length; index++) {
            var xx = index % width;
            var zz = index / width;
            if (Math.abs(xx - x) > ZoneLayers.OUTPUT_RADIUS || Math.abs(zz - z) > ZoneLayers.OUTPUT_RADIUS) {
                assertEquals(before[index], patched[index], "outside cell " + xx + "," + zz);
            }
        }
    }

    private static void assertWindowEquals(short[] rebuilt, short[] patched, int width, int x, int z) {
        var x0 = Math.max(0, x - ZoneLayers.OUTPUT_RADIUS);
        var z0 = Math.max(0, z - ZoneLayers.OUTPUT_RADIUS);
        var x1 = Math.min(width - 1, x + ZoneLayers.OUTPUT_RADIUS);
        var z1 = Math.min(width - 1, z + ZoneLayers.OUTPUT_RADIUS);
        for (var zz = z0; zz <= z1; zz++) {
            for (var xx = x0; xx <= x1; xx++) {
                var index = zz * width + xx;
                assertEquals(rebuilt[index], patched[index], "cell " + xx + "," + zz + " around " + x + "," + z);
            }
        }
    }

    private static int[] breadth(boolean[] source, int width) {
        var cells = width * width;
        var distance = new int[cells];
        java.util.Arrays.fill(distance, Integer.MAX_VALUE / 4);
        var queue = new int[cells];
        var head = 0;
        var tail = 0;
        for (var index = 0; index < cells; index++) {
            if (source[index]) {
                distance[index] = 0;
                queue[tail++] = index;
            }
        }
        var dx = new int[] {1, -1, 0, 0, 1, 1, -1, -1};
        var dz = new int[] {0, 0, 1, -1, 1, -1, 1, -1};
        var cost = new int[] {3, 3, 3, 3, 4, 4, 4, 4};
        while (head < tail) {
            var index = queue[head++];
            var x = index % width;
            var z = index / width;
            for (var step = 0; step < 8; step++) {
                var nx = x + dx[step];
                var nz = z + dz[step];
                if (nx < 0 || nz < 0 || nx >= width || nz >= width) {
                    continue;
                }
                var next = nz * width + nx;
                var value = distance[index] + cost[step];
                if (value < distance[next]) {
                    distance[next] = value;
                    queue[tail++] = next;
                }
            }
        }
        return distance;
    }
}
