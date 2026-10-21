package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import org.bukkit.entity.Entity;

import java.util.Optional;
import java.util.UUID;

/**
 * The native identities the participant policy reads beyond Paper's own entity (M17.2): the MythicMobs instance and
 * faction of an entity, its team on the main scoreboard, and whether it is a mob bound to ThePredict. Kept apart so
 * the policy is tested with doubles (rule P4); the live implementation is {@link MythicParticipantLookup}. Server
 * thread only; every lookup is O(1) by UUID or by scoreboard entry.
 */
public interface ParticipantLookup {
    /**
     * Reads the MythicMobs type of an entity.
     *
     * @param entityId UUID of the entity
     * @return the exact internal name of its {@code ActiveMob} type, empty for any other entity
     */
    Optional<String> mythicType(UUID entityId);

    /**
     * Reads the MythicMobs faction of an entity ({@code ActiveMob.getFaction()} when {@code hasFaction()}).
     *
     * @param entityId UUID of the entity
     * @return the faction, empty without an active mob or a faction
     */
    Optional<String> faction(UUID entityId);

    /**
     * Reads the team of an entity on the main scoreboard ({@code Scoreboard#getEntityTeam}).
     *
     * @param entity the entity
     * @return the name of its team, empty without one
     */
    Optional<String> team(Entity entity);

    /**
     * Finds the profile of a mob bound to ThePredict: an {@code ActiveMob} whose type has a bound profile in the
     * current catalog (the binder only keeps mobs with {@code Predictor: true}).
     *
     * @param entityId UUID of the entity
     * @return the bound profile of its MythicMobs type, empty for any other entity
     */
    Optional<BoundProfile> bound(UUID entityId);

    /**
     * Tells whether an entity is a mob bound to ThePredict, the {@code predictor} selector. Not
     * {@code CombatReader.isActive}, which only says the {@code ActiveMob} is alive.
     *
     * @param entityId UUID of the entity
     * @return {@code true} for a bound mob
     */
    default boolean predictor(UUID entityId) {
        return bound(entityId).isPresent();
    }

    /** A lookup that finds nothing: no MythicMobs instance, faction, team or bound mob. */
    ParticipantLookup NONE = new ParticipantLookup() {
        @Override
        public Optional<String> mythicType(UUID entityId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> faction(UUID entityId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> team(Entity entity) {
            return Optional.empty();
        }

        @Override
        public Optional<BoundProfile> bound(UUID entityId) {
            return Optional.empty();
        }
    };
}
