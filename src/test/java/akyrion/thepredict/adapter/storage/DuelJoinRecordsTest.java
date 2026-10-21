package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.adapter.server.duel.DuelJoin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuelJoinRecordsTest {
    @Test
    void twoInterleavedRunsStayApartWhenTheKeyIncludesTheRun() {
        var first = line("20261006T120000Z-aaaa", 1, 1, "planned");
        var second = line("20261006T120000Z-bbbb", 1, 1, "planned");
        var again = line("20261006T120000Z-aaaa", 1, 1, "result");
        var lines = List.of(first, second, again);
        var joined = RecordReader.join(RecordReader.parse(lines));
        assertEquals(2, joined.size());
        assertEquals("20261006T120000Z-aaaa", joined.get(0).run());
        assertEquals(List.of("planned", "result"), joined.get(0).lines().stream().map(RecordReader.EventLine::type).toList());
        assertEquals("20261006T120000Z-bbbb", joined.get(1).run());
        assertEquals(1, DuelJoin.join(lines).size());
    }

    private static String line(String run, long encounter, long sequence, String type) {
        return RecordLine.format(new RecordEvent(type, run, 1L, 2L, 3L, "", java.util.Map.of(
                "encounter", Long.toString(encounter), "sequence", Long.toString(sequence))));
    }
}
