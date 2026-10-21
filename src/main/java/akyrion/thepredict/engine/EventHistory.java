package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Fixed ring of the last events of one encounter (M06.3); the oldest is replaced when full. Server thread only. */
final class EventHistory {
    private final EncounterEvent[] events;
    private int next;
    private int size;

    EventHistory(int capacity) {
        events = new EncounterEvent[capacity];
    }

    /** Adds an event, replacing the oldest one when the ring is full. */
    void record(EncounterEvent event) {
        events[next] = Objects.requireNonNull(event, "event");
        next = (next + 1) % events.length;
        size = Math.min(size + 1, events.length);
    }

    /**
     * Tells whether the ring already holds an event of the same type, participant and mob less than
     * {@code windowTicks} before the given one, scanning the ring without copying it.
     */
    boolean hasRepeat(EncounterEvent event, long windowTicks) {
        for (var index = 0; index < size; index++) {
            var held = events[Math.floorMod(next - 1 - index, events.length)];
            if (held.type() == event.type() && held.participant().equals(event.participant())
                    && held.mob().equals(event.mob()) && held.tick() > event.tick() - windowTicks) {
                return true;
            }
        }
        return false;
    }

    /** Returns a copy of the events, oldest first. */
    List<EncounterEvent> recent() {
        var copy = new ArrayList<EncounterEvent>(size);
        var start = Math.floorMod(next - size, events.length);
        for (var offset = 0; offset < size; offset++) {
            copy.add(events[(start + offset) % events.length]);
        }
        return copy;
    }

    /** Forgets every event. */
    void clear() {
        Arrays.fill(events, null);
        size = 0;
    }
}
