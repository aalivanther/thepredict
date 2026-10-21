package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.MemoryMode;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M27GoldenTest {
    @Test
    void frozenGoldensStayPutAndRecordMatchesOff() throws Exception {
        var root = Path.of("src/test/resources/golden");
        var encounter = new JsonObject();
        var off = new StateDraft(encounter, List.of(), List.of(new StateDraft.ParticipantEntry("player_1",
                List.of("healthy"), List.of(), 1, false)), List.of());
        var record = new StateDraft(encounter, List.of(), List.of(new StateDraft.ParticipantEntry("player_1",
                List.of("healthy"), List.of(), 1, false, List.of())), List.of());
        var offText = Files.readString(root.resolve("m27/off.json")).trim();
        var recordText = Files.readString(root.resolve("m27/record.json")).trim();
        assertEquals(offText, off.render().toString());
        assertEquals(recordText, record.render().toString());
        assertEquals(off.render().toString(), record.render().toString());
        var remembered = new StateDraft(encounter, List.of(), List.of(new StateDraft.ParticipantEntry("player_1",
                List.of("healthy"), List.of(), 1, false, List.of("player_1 has beaten this foe before"))), List.of());
        assertTrue(!remembered.render().toString().equals(off.render().toString()));
        assertTrue(remembered.render().toString().contains("memory"));
        assertTrue(MemoryMode.OFF.ordinal() < MemoryMode.RECORD.ordinal());
    }
}
