package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.MobExecutor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@link ParticipantLookup} over the MythicMobs mob manager and the main scoreboard (M17.2). Server thread only.
 *
 * <p>Evidence (javap, MythicMobs 5.13.1 and {@code paper-api} 26.2): {@code MobExecutor.getActiveMob(UUID)} is an
 * O(1) map lookup; {@code ActiveMob.hasFaction()}, {@code getFaction()} and {@code getType().getInternalName()};
 * {@code Scoreboard#getEntityTeam(Entity)} looks up the scoreboard entry of the entity.
 */
public final class MythicParticipantLookup implements ParticipantLookup {
    private final MobExecutor mobs;
    private final Function<String, Optional<BoundProfile>> profiles;

    /**
     * Creates the lookup.
     *
     * @param mobs {@code MythicBukkit.inst().getMobManager()}
     * @param profiles bound profile of a MythicMobs mob ID in the current catalog
     */
    public MythicParticipantLookup(MobExecutor mobs, Function<String, Optional<BoundProfile>> profiles) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @Override
    public Optional<String> mythicType(UUID entityId) {
        return mobs.getActiveMob(entityId).map(mob -> mob.getType().getInternalName());
    }

    @Override
    public Optional<String> faction(UUID entityId) {
        return mobs.getActiveMob(entityId).filter(ActiveMob::hasFaction).map(ActiveMob::getFaction);
    }

    @Override
    public Optional<String> team(Entity entity) {
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return Optional.empty();
        }
        var team = manager.getMainScoreboard().getEntityTeam(entity);
        return team == null ? Optional.empty() : Optional.of(team.getName());
    }

    @Override
    public Optional<BoundProfile> bound(UUID entityId) {
        return mythicType(entityId).flatMap(profiles);
    }
}
