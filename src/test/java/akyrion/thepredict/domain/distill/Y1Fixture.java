package akyrion.thepredict.domain.distill;

import java.io.IOException;

/** Little-endian rows exported from the Y1 probe. The teacher is synthetic. */
final class Y1Fixture {
    final TreeRows[] train;
    final int[][] bands;
    final int[] teacher;
    final int[] mask;
    final boolean[] fine;
    final float[][] table;

    private Y1Fixture(TreeRows[] train, int[][] bands, int[] teacher, int[] mask, boolean[] fine, float[][] table) {
        this.train = train;
        this.bands = bands;
        this.teacher = teacher;
        this.mask = mask;
        this.fine = fine;
        this.table = table;
    }

    static Y1Fixture load(String resource) throws IOException {
        byte[] raw;
        try (var input = Y1Fixture.class.getResourceAsStream(resource)) {
            raw = input.readAllBytes();
        }
        var at = new int[] {0};
        var nTrain = i32(raw, at);
        var nTest = i32(raw, at);
        var tactics = i32(raw, at);
        var train = new TreeRows[nTrain];
        for (var row = 0; row < nTrain; row++) {
            train[row] = new TreeRows(bands(raw, at), raw[at[0]++] & 0xff, f32(raw, at));
        }
        var bands = new int[nTest][];
        var teacher = new int[nTest];
        var mask = new int[nTest];
        var fine = new boolean[nTest];
        var table = new float[nTest][];
        for (var row = 0; row < nTest; row++) {
            bands[row] = bands(raw, at);
            teacher[row] = raw[at[0]++] & 0xff;
            mask[row] = raw[at[0]++] & 0xff;
            fine[row] = raw[at[0]++] != 0;
            table[row] = scores(raw, at, tactics);
        }
        return new Y1Fixture(train, bands, teacher, mask, fine, table);
    }

    private static int[] bands(byte[] raw, int[] at) {
        var bands = new int[7];
        for (var band = 0; band < bands.length; band++) {
            bands[band] = raw[at[0]++] & 0xff;
        }
        return bands;
    }

    private static float[] scores(byte[] raw, int[] at, int tactics) {
        var scores = new float[tactics];
        for (var tactic = 0; tactic < tactics; tactic++) {
            scores[tactic] = f32(raw, at);
        }
        return scores;
    }

    private static int i32(byte[] raw, int[] at) {
        var value = (raw[at[0]] & 0xff) | ((raw[at[0] + 1] & 0xff) << 8) | ((raw[at[0] + 2] & 0xff) << 16)
                | ((raw[at[0] + 3] & 0xff) << 24);
        at[0] += 4;
        return value;
    }

    private static float f32(byte[] raw, int[] at) {
        return Float.intBitsToFloat(i32(raw, at));
    }
}
