package akyrion.thepredict.adapter.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The parts of one {@code state} before rendering, so {@link SemanticEncoder} can trim them in the order of M08.2 and
 * M16.4: the zone facts of the mobs (M24), then the memory phrases of the participants (M27); events that concern no
 * asked mob nor the target of one, oldest first; then the relevant events, oldest first;
 * cues of participants no asked mob targets, from the lowest threat up; cues of the targets of asked mobs, which the
 * target facts of their mob already repeat; entries of participants no asked mob targets, from the lowest threat up;
 * and, last, mobs without questions. Mobs with questions (and so the target facts inside them) and the entries of the
 * targets of asked mobs are never removed. After rendering, {@link #indexOfMob} and {@link #indexOfParticipant} give
 * the final position of an entry for the paths of the instructions (M16.3). Not thread-safe; one draft per encoding.
 */
final class StateDraft {
    private final JsonObject encounter;
    private final List<MobEntry> mobs;
    private final List<ParticipantEntry> participants;
    private final List<EventEntry> eventsNewestFirst;

    StateDraft(JsonObject encounter, List<MobEntry> mobs, List<ParticipantEntry> participants,
               List<EventEntry> eventsNewestFirst) {
        this.encounter = encounter;
        this.mobs = new ArrayList<>(mobs);
        this.participants = new ArrayList<>(participants);
        this.eventsNewestFirst = new ArrayList<>(eventsNewestFirst);
    }

    /**
     * Renders the draft with keys in a fixed order: {@code encounter}, {@code mobs}, {@code participants},
     * {@code recent_events}.
     */
    JsonObject render() {
        var state = new JsonObject();
        state.add("encounter", encounter.deepCopy());
        var mobArray = new JsonArray();
        mobs.forEach(mob -> mobArray.add(mob.json().deepCopy()));
        state.add("mobs", mobArray);
        var participantArray = new JsonArray();
        participants.forEach(participant -> participantArray.add(participant.render()));
        state.add("participants", participantArray);
        var events = new JsonArray();
        eventsNewestFirst.forEach(event -> events.add(event.text()));
        state.add("recent_events", events);
        return state;
    }

    /**
     * Removes the next piece in trimming order.
     *
     * @return {@code false} when nothing removable is left
     */
    boolean trimOnce() {
        if (trimMobArray("zone_facts") || trimMemory()) {
            return true;
        }
        if (removeOldestEvent(false) || removeOldestEvent(true)) {
            return true;
        }
        for (var protectedTargets : new boolean[] {false, true}) {
            var cues = leastThreatening(true, protectedTargets);
            if (cues >= 0) {
                participants.set(cues, participants.get(cues).withoutCues());
                return true;
            }
        }
        var participant = leastThreatening(false, false);
        if (participant >= 0) {
            participants.remove(participant);
            return true;
        }
        for (var index = mobs.size() - 1; index >= 0; index--) {
            if (!mobs.get(index).asked()) {
                mobs.remove(index);
                return true;
            }
        }
        return false;
    }

    /**
     * Index of a mob entry in the rendered {@code mobs}.
     *
     * @param ref reference such as {@code mob_1}
     * @return its index, {@code -1} when it was trimmed or never there
     */
    int indexOfMob(String ref) {
        for (var index = 0; index < mobs.size(); index++) {
            if (ref.equals(mobs.get(index).json().get("ref").getAsString())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Index of a participant entry in the rendered {@code participants}.
     *
     * @param ref alias such as {@code player_1}
     * @return its index, {@code -1} when it was trimmed or never there
     */
    int indexOfParticipant(String ref) {
        for (var index = 0; index < participants.size(); index++) {
            if (ref.equals(participants.get(index).ref())) {
                return index;
            }
        }
        return -1;
    }

    /** Whether the participant entry at an index still has cues. */
    boolean participantHasCues(int index) {
        return index >= 0 && !participants.get(index).cues().isEmpty();
    }

    /** Whether the mob entry at an index has a {@code target}. */
    boolean mobHasTarget(int index) {
        return index >= 0 && mobs.get(index).json().has("target");
    }

    private boolean trimMobArray(String key) {
        for (var entry : mobs) {
            var values = entry.json().getAsJsonArray(key);
            if (values != null && !values.isEmpty()) {
                values.remove(values.size() - 1);
                if (values.isEmpty()) {
                    entry.json().remove(key);
                }
                return true;
            }
        }
        return false;
    }

    private boolean trimMemory() {
        var found = -1;
        for (var index = 0; index < participants.size(); index++) {
            var participant = participants.get(index);
            if (!participant.memory().isEmpty()
                    && (found < 0 || participant.rank() > participants.get(found).rank())) {
                found = index;
            }
        }
        if (found < 0) {
            return false;
        }
        participants.set(found, participants.get(found).withoutMemory());
        return true;
    }

    private boolean removeOldestEvent(boolean relevant) {
        for (var index = eventsNewestFirst.size() - 1; index >= 0; index--) {
            if (eventsNewestFirst.get(index).relevant() == relevant) {
                eventsNewestFirst.remove(index);
                return true;
            }
        }
        return false;
    }

    private int leastThreatening(boolean withCues, boolean protectedTargets) {
        var found = -1;
        for (var index = 0; index < participants.size(); index++) {
            var participant = participants.get(index);
            var eligible = participant.protectedTarget() == protectedTargets
                    && (!withCues || !participant.cues().isEmpty());
            if (eligible && (found < 0 || participant.rank() > participants.get(found).rank())) {
                found = index;
            }
        }
        return found;
    }

    /**
     * One mob entry.
     *
     * @param json rendered entry
     * @param asked whether a question asks about this mob
     */
    record MobEntry(JsonObject json, boolean asked) {
    }

    /**
     * One event of the history.
     *
     * @param text event phrase
     * @param relevant whether its mob is asked about or its participant is the target of an asked mob (M16.4)
     */
    record EventEntry(String text, boolean relevant) {
    }

    /**
     * One participant entry.
     *
     * @param ref alias text
     * @param facts the species of an entity (M17.9), then health, armor and threat phrases
     * @param cues cue phrases
     * @param rank threat rank, {@code 1} is the highest
     * @param protectedTarget whether an asked mob targets this participant
     */
    record ParticipantEntry(String ref, List<String> facts, List<String> cues, int rank, boolean protectedTarget,
                            List<String> memory) {
        ParticipantEntry(String ref, List<String> facts, List<String> cues, int rank, boolean protectedTarget) {
            this(ref, facts, cues, rank, protectedTarget, List.of());
        }

        ParticipantEntry {
            facts = List.copyOf(facts);
            cues = List.copyOf(cues);
            memory = List.copyOf(memory);
        }

        ParticipantEntry withoutCues() {
            return new ParticipantEntry(ref, facts, List.of(), rank, protectedTarget, memory);
        }

        ParticipantEntry withoutMemory() {
            return new ParticipantEntry(ref, facts, cues, rank, protectedTarget, List.of());
        }

        JsonObject render() {
            var json = new JsonObject();
            json.addProperty("ref", ref);
            json.add("facts", SemanticEncoder.array(facts));
            if (!memory.isEmpty()) {
                json.add("memory", SemanticEncoder.array(memory));
            }
            json.add("cues", SemanticEncoder.array(cues));
            return json;
        }
    }
}
