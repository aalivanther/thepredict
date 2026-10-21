package akyrion.thepredict.adapter.server.events;

import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.MobExecutor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link MobDirectory} over the MythicMobs mob manager. Server thread only.
 *
 * <p>Evidence (javap, MythicMobs 5.13.1): {@code MobExecutor.getActiveMob(UUID)} reads the {@code MobRegistry}
 * map, from which {@code ActiveMob.setUnloaded()} removes an unloaded mob with {@code setInactive}, and
 * {@code getActiveMobs()} returns that map's values.
 */
public final class MythicMobDirectory implements MobDirectory {
    private final MobExecutor mobs;

    /**
     * Creates the directory.
     *
     * @param mobs {@code MythicBukkit.inst().getMobManager()}
     */
    public MythicMobDirectory(MobExecutor mobs) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
    }

    @Override
    public Optional<String> mobTypeOf(UUID entityId) {
        return mobs.getActiveMob(entityId).map(mob -> mob.getType().getInternalName());
    }

    @Override
    public boolean isActive(UUID entityId) {
        return mobs.getActiveMob(entityId).filter(MythicMobDirectory::alive).isPresent();
    }

    @Override
    public List<UUID> activeMobIds() {
        return mobs.getActiveMobs().stream().map(ActiveMob::getUniqueId).toList();
    }

    private static boolean alive(ActiveMob mob) {
        var entity = mob.getEntity();
        return !mob.isDead() && entity != null && entity.isValid() && !entity.isDead();
    }
}
