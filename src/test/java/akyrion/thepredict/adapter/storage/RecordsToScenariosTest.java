package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordsToScenariosTest {
    @Test
    void eachJoinedRequestIsOneScenario() {
        var lines = List.of(line(1), line(2), "{\"v\":1");
        assertEquals(2, RecordsToScenarios.scenarios(lines));
    }

    private static String line(long sequence) {
        return RecordLine.format(new RecordEvent("planned", "20261006T120000Z-0a1b", 1L, 2L, 3L, "arm",
                java.util.Map.of("encounter", "7", "sequence", Long.toString(sequence))));
    }
}
