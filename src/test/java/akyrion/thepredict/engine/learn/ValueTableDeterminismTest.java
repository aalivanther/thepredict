package akyrion.thepredict.engine.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValueTableDeterminismTest {
    @Test
    void twoBuildsFromTheSameCellsMatch() {
        assertEquals("0:27:20100:26:21200:26:22300:25:23400:25:24500:25:25600:24:26700:24:27", digest());
    }

    private static String digest() {
        var builder = new ValueTableBuilder();
        for (var tactic = 0; tactic < 8; tactic++) {
            builder.add(3, tactic, 1, 0L, 0.1 * tactic, 0.02, 20 + tactic, 8.0);
        }
        var table = builder.build();
        var text = new StringBuilder();
        for (var tactic = 0; tactic < 8; tactic++) {
            var found = table.lookup(3, tactic, 0L, 0L, 20);
            text.append(found.valueMillis()).append(':').append(found.sdMillis()).append(':').append(found.nEff());
        }
        return text.toString();
    }
}
