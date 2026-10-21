package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rule C16: the participants of an encounter are visited in order of arrival, the same on every start. */
class EncounterTest {
    @Test
    void participantsKeepTheirArrivalOrderInEveryView() {
        var encounter = new Encounter(new EncounterId(1), new UUID(0, 1), 0);
        var arrived = new ArrayList<UUID>();
        for (var index = 0; index < EncounterRegistry.MAX_PARTICIPANTS; index++) {
            var id = new UUID(0x5151_0000L + index * 7919L, index);
            encounter.addParticipant(id);
            arrived.add(id);
        }
        assertEquals(arrived, List.copyOf(encounter.participants()));
        var visited = new ArrayList<UUID>();
        encounter.forEachParticipant(visited::add);
        assertEquals(arrived, visited);
        assertEquals(EncounterRegistry.MAX_PARTICIPANTS, encounter.participantCount());
        encounter.removeParticipant(arrived.get(3));
        arrived.remove(3);
        assertEquals(arrived, List.copyOf(encounter.participants()));
    }
}
