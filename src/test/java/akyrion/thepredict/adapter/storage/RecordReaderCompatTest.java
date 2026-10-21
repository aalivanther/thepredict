package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A v1 line from before distillation still parses. Schema v stays 1. */
class RecordReaderCompatTest {
    @Test
    void anOlderPlannedLineParsesAndADistillFieldDoesNotRaiseV() {
        var older = "{\"v\":1,\"type\":\"planned\",\"run\":\"m28\",\"encounter\":1,\"mob\":2,\"sequence\":3}";
        var read = RecordReader.parse(List.of(older, ""));
        assertEquals(1, read.size());
        assertEquals("planned", read.getFirst().type());
        assertEquals(1, read.getFirst().object().get("v").getAsInt());
        assertFalse(read.getFirst().object().has("distill"));
        var shadow = older.substring(0, older.length() - 1) + ",\"distill\":{\"delegated\":false}}";
        var with = RecordReader.parse(List.of(shadow));
        assertEquals(1, with.getFirst().object().get("v").getAsInt());
        assertTrue(with.getFirst().object().getAsJsonObject("distill").get("delegated").getAsBoolean() == false);
    }
}
