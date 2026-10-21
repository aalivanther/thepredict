package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The same rows build the same tree, and the leaf cap holds. */
class PreferenceTreeBuilderTest {
    @Test
    void twoBuildsMatchAndStayWithinSixtyFourLeaves() {
        var rows = new TreeRows[40];
        for (var index = 0; index < rows.length; index++) {
            rows[index] = new TreeRows(new int[] {index % 4, index % 3, 0, 0, 0, 0, 0}, index % 3, 1.0);
        }
        var names = new String[] {"a", "b", "c"};
        var cards = new int[] {4, 3, 1, 1, 1, 1, 1};
        var first = PreferenceTreeBuilder.build(rows, cards, 3, names, 6, 64, 10);
        var second = PreferenceTreeBuilder.build(rows, cards, 3, names, 6, 64, 10);
        var bands = new int[] {1, 1, 0, 0, 0, 0, 0};
        assertEquals(first.path(bands), second.path(bands));
        assertTrue(first.leaves() <= 64);
        assertTrue(first.depth() <= 6);
    }

    @Test
    void aLeafRanksByWeightedLabelsAndNeverOffersAnUnlabelledTactic() {
        var rows = new TreeRows[] {
                new TreeRows(new int[] {0, 0, 0, 0, 0, 0, 0}, 0, 1.0),
                new TreeRows(new int[] {0, 0, 0, 0, 0, 0, 0}, 0, 1.0),
                new TreeRows(new int[] {0, 0, 0, 0, 0, 0, 0}, 0, 1.0),
                new TreeRows(new int[] {0, 0, 0, 0, 0, 0, 0}, 1, 5.0)};
        var tree = PreferenceTreeBuilder.build(rows, new int[] {1, 1, 1, 1, 1, 1, 1}, 3, new String[] {"a", "b", "c"},
                6, 64, 10);
        var bands = new int[] {0, 0, 0, 0, 0, 0, 0};
        assertEquals(java.util.Optional.of("b"), tree.choose(bands, java.util.List.of("a", "b", "c")));
        assertEquals(java.util.Optional.of("a"), tree.choose(bands, java.util.List.of("a", "c")));
        assertEquals(java.util.Optional.empty(), tree.choose(bands, java.util.List.of("c")));
    }
}
