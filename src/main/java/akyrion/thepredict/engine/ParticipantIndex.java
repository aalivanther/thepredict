package akyrion.thepredict.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Index of the open encounters that list each participant, keyed by world and participant (M06.3, M17): a player
 * or any other living entity. A lookup is O(1); each key holds, in order of formation, only the open encounters that
 * list the participant in that world,
 * so its size stays at {@code ceil(mobs / 16)} plus one per group out of follow range. An empty key is removed at
 * once. Server thread only.
 */
final class ParticipantIndex {
    private final Map<Key, List<Encounter>> encounters = new HashMap<>();

    /** Returns the open encounters of a participant in a world, oldest first; empty when there is none. */
    List<Encounter> of(UUID worldId, UUID participantId) {
        return List.copyOf(encounters.getOrDefault(new Key(worldId, participantId), List.of()));
    }

    /** Lists an encounter under one of its participants once. */
    void add(Encounter encounter, UUID participantId) {
        var listed = encounters.computeIfAbsent(new Key(encounter.worldId(), participantId), key -> new ArrayList<>(2));
        if (!listed.contains(encounter)) {
            listed.add(encounter);
        }
    }

    /** Removes an encounter from the key of one participant and drops the key when it becomes empty. */
    void remove(Encounter encounter, UUID participantId) {
        var key = new Key(encounter.worldId(), participantId);
        var listed = encounters.get(key);
        if (listed != null && listed.remove(encounter) && listed.isEmpty()) {
            encounters.remove(key);
        }
    }

    /** Counts the keys, for tests that check no key is leaked. */
    int keys() {
        return encounters.size();
    }

    /** One participant in one world. */
    private record Key(UUID worldId, UUID participantId) {
    }
}
