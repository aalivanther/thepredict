package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Y1 hybrid agreement on the exported synthetic teacher. Bands stay 0.76±0.03 and 0.95±0.02.
 * The table alone misses the 2 000 band, so a tree that does not lift uncovered cells fails.
 */
class DistillReproducesY1Test {
    private static final String[] NAMES = {"t0", "t1", "t2", "t3", "t4", "t5"};
    private static final int[] CARDS = {4, 3, 3, 2, 4, 3, 7};

    @Test
    void twoThousandNeedsTheTreeInsideThePublishedBand() throws Exception {
        var rows = Y1Fixture.load("/distill/y1-2000.bin");
        var table = tableOnly(rows);
        var hybrid = hybrid(rows);
        assertTrue(table < 0.73, "table " + table);
        assertTrue(hybrid >= 0.73 && hybrid <= 0.79, "hybrid " + hybrid);
    }

    @Test
    void fiftyThousandStaysInsideThePublishedBand() throws Exception {
        var rows = Y1Fixture.load("/distill/y1-50000.bin");
        var hybrid = hybrid(rows);
        assertTrue(hybrid >= 0.93 && hybrid <= 0.97, "hybrid " + hybrid);
    }

    private static double hybrid(Y1Fixture rows) {
        var tree = PreferenceTreeBuilder.build(rows.train, CARDS, NAMES.length, NAMES, 6, 64, 10);
        var hits = 0;
        for (var row = 0; row < rows.teacher.length; row++) {
            var pick = rows.fine[row] ? argmax(rows.table[row], rows.mask[row]) : treePick(tree, rows, row);
            if (pick == rows.teacher[row]) {
                hits++;
            }
        }
        return hits / (double) rows.teacher.length;
    }

    private static double tableOnly(Y1Fixture rows) {
        var hits = 0;
        for (var row = 0; row < rows.teacher.length; row++) {
            if (argmax(rows.table[row], rows.mask[row]) == rows.teacher[row]) {
                hits++;
            }
        }
        return hits / (double) rows.teacher.length;
    }

    private static int treePick(PreferenceTree tree, Y1Fixture rows, int row) {
        var candidates = new ArrayList<String>();
        for (var tactic = 0; tactic < NAMES.length; tactic++) {
            if ((rows.mask[row] & (1 << tactic)) != 0) {
                candidates.add(NAMES[tactic]);
            }
        }
        var found = tree.leaf(rows.bands[row]).order().firstAmong(candidates);
        return found.map(name -> name.charAt(1) - '0').orElse(-1);
    }

    private static int argmax(float[] scores, int mask) {
        var best = -1;
        var value = Float.NEGATIVE_INFINITY;
        for (var tactic = 0; tactic < scores.length; tactic++) {
            if ((mask & (1 << tactic)) != 0 && scores[tactic] > value) {
                value = scores[tactic];
                best = tactic;
            }
        }
        return best;
    }
}
