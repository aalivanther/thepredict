package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.domain.MobKey;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.Optional;

/**
 * A member mob read in one capture: valid, alive, bound and in the encounter's world. Package-private; lives for
 * one capture on the server thread.
 *
 * @param key mob session
 * @param entity living entity
 * @param bound bound profile of its type
 * @param maxHealth valid maximum health
 * @param target target in the same world admitted by the participant policy of the profile, if any (M17.5)
 * @param targetDistanceBlocks distance to the target, {@code 0} without one
 */
record LiveMember(MobKey key, LivingEntity entity, BoundProfile bound, double maxHealth, Optional<LivingEntity> target,
                  double targetDistanceBlocks) {
    LiveMember {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(bound, "bound");
        Objects.requireNonNull(target, "target");
    }
}
