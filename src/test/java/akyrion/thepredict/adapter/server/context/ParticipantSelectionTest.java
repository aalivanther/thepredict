package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.CombatFacts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantSelectionTest {
    private static UUID player(int value) {
        return new UUID(0x9L, value);
    }

    private static Map<UUID, Double> distances(int count) {
        var distances = new LinkedHashMap<UUID, Double>();
        for (var index = count; index >= 1; index--) {
            distances.put(player(index), (double) index * index);
        }
        return distances;
    }

    @Test
    void theSixteenNearestAreChosenWhenMorePlayersArePerceived() {
        var chosen = ParticipantSelection.select(List.of(), distances(20), CombatFacts.MAX_PARTICIPANTS);
        assertEquals(CombatFacts.MAX_PARTICIPANTS, chosen.size());
        var expected = new ArrayList<UUID>();
        for (var index = 1; index <= 16; index++) {
            expected.add(player(index));
        }
        assertEquals(expected, chosen);
    }

    @Test
    void exactlySixteenPlayersAreAllKept() {
        assertEquals(16, ParticipantSelection.select(List.of(), distances(16), CombatFacts.MAX_PARTICIPANTS).size());
        assertEquals(15, ParticipantSelection.select(List.of(), distances(15), CombatFacts.MAX_PARTICIPANTS).size());
    }

    @Test
    void targetsComeFirstEvenWhenFartherAndCountOnce() {
        var chosen = ParticipantSelection.select(List.of(player(20), player(20), player(19)), distances(20), 16);
        assertEquals(List.of(player(20), player(19), player(1)), chosen.subList(0, 3));
        assertEquals(16, chosen.size());
        assertEquals(16, new HashSet<>(chosen).size());
        assertTrue(!chosen.contains(player(15)));
    }

    @Test
    void targetsBeyondTheCapAreCut() {
        var targets = new ArrayList<UUID>();
        for (var index = 1; index <= 20; index++) {
            targets.add(player(index));
        }
        var chosen = ParticipantSelection.select(targets, distances(20), 16);
        assertEquals(targets.subList(0, 16), chosen);
    }

    @Test
    void ranksFollowThreatThenDistanceThenSelectionOrderAndAreUnique() {
        var chosen = List.of(player(1), player(2), player(3), player(4));
        var threat = Map.of(player(1), 0.0, player(2), 5.0, player(3), 5.0, player(4), 0.0);
        var distance = Map.of(player(1), 9.0, player(2), 4.0, player(3), 1.0, player(4), 9.0);
        var ranks = ParticipantSelection.ranks(chosen, threat::get, distance::get);
        assertEquals(Map.of(player(3), 1, player(2), 2, player(1), 3, player(4), 4), ranks);
    }

    @Test
    void noPlayerGivesNoRank() {
        assertTrue(ParticipantSelection.ranks(List.of(), player -> 0, player -> 0).isEmpty());
        assertTrue(ParticipantSelection.select(List.of(), Map.of(), 16).isEmpty());
    }
}
