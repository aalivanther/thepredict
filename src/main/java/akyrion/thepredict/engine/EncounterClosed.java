package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One encounter that closed (M21.1). Errata E21-2: the record is not named {@code EncounterEnd}, because that name is
 * {@link SessionMetrics.EncounterEnd}. The reason reuses that enum: {@code NO_MEMBERS} and {@code COMBAT_ENDED} are
 * the only causes {@link EncounterRegistry} closes for.
 *
 * @param id encounter
 * @param worldId world
 * @param startTick tick the encounter was created
 * @param endTick tick it closed
 * @param reason why it closed
 * @param members members at the close
 * @param profiles profile id of each member
 * @param participants participant ids, without a kind
 * @param mobsDied mobs that died in the encounter, 0 when the registry does not count them
 * @param participantsDied participants that died, 0 when the registry does not count them
 * @param groupSummary clock close text, empty when the encounter had no group clock
 */
public record EncounterClosed(EncounterId id, UUID worldId, long startTick, long endTick,
                              SessionMetrics.EncounterEnd reason, List<MobKey> members, Map<MobKey, String> profiles,
                              Set<UUID> participants, int mobsDied, int participantsDied, String groupSummary) {
    /**
     * A close from before group clocks wrote a summary (rule W5). The summary is empty.
     *
     * @param id encounter
     * @param worldId world
     * @param startTick tick the encounter was created
     * @param endTick tick it closed
     * @param reason why it closed
     * @param members members at the close
     * @param profiles profile id of each member
     * @param participants participant ids
     * @param mobsDied mobs that died
     * @param participantsDied participants that died
     */
    public EncounterClosed(EncounterId id, UUID worldId, long startTick, long endTick, SessionMetrics.EncounterEnd reason,
                           List<MobKey> members, Map<MobKey, String> profiles, Set<UUID> participants, int mobsDied,
                           int participantsDied) {
        this(id, worldId, startTick, endTick, reason, members, profiles, participants, mobsDied, participantsDied, "");
    }

    /**
     * Copies the collections.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the end precedes the start or a death count is negative
     */
    public EncounterClosed {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(reason, "reason");
        members = List.copyOf(members);
        profiles = Map.copyOf(profiles);
        participants = Set.copyOf(participants);
        Objects.requireNonNull(groupSummary, "groupSummary");
        if (endTick < startTick || mobsDied < 0 || participantsDied < 0) {
            throw new IllegalArgumentException("invalid encounter close " + id.value());
        }
    }
}
