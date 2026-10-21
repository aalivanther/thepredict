package akyrion.thepredict.adapter.server.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the lifecycle listeners read from the MythicMobs mob manager, so the listeners are tested with doubles
 * (rule P4). Server thread only.
 */
public interface MobDirectory {
    /**
     * Returns the MythicMobs type of an active instance.
     *
     * @param entityId UUID of the entity
     * @return exact MythicMobs mob ID, empty if the entity has no active instance
     */
    Optional<String> mobTypeOf(UUID entityId);

    /**
     * Tells whether a mob still has an active MythicMobs instance with a valid, living entity (M06.6).
     *
     * @param entityId UUID of the entity
     * @return {@code true} if the instance is registered and its entity is valid and alive
     */
    boolean isActive(UUID entityId);

    /**
     * Copies the UUIDs of every active MythicMobs instance, for the bounded scan.
     *
     * @return UUIDs of the active instances
     */
    List<UUID> activeMobIds();
}
