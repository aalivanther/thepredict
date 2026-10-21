package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.adapter.server.context.Blocking;
import akyrion.thepredict.adapter.server.context.ParticipantLookup;
import akyrion.thepredict.domain.TacticalProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The {@link DuelScene} of a server: Paper for the entities and the MythicMobs identities of M17 for the factions and
 * the bound profiles. Server thread only (rule T1); one {@code getNearbyLivingEntities} per sample while recording.
 */
public final class LiveDuelScene implements DuelScene {
    private final ParticipantLookup lookup;
    private final Supplier<Collection<TacticalProfile>> profiles;

    /**
     * Creates the scene.
     *
     * @param lookup native identities of M17: faction and bound profile of an entity
     * @param profiles the bound profiles of the current catalog
     */
    public LiveDuelScene(ParticipantLookup lookup, Supplier<Collection<TacticalProfile>> profiles) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @Override
    public List<DuelistReading> duelists(UUID worldId, double centerX, double centerY, double centerZ,
                                         double radiusBlocks, String sideA, String sideB) {
        var world = Bukkit.getWorld(worldId);
        if (world == null) {
            return List.of();
        }
        var center = new Location(world, centerX, centerY, centerZ);
        return world.getNearbyLivingEntities(center, radiusBlocks).stream()
                .filter(entity -> !(entity instanceof Player) && entity.isValid() && !entity.isDead())
                .filter(entity -> lookup.faction(entity.getUniqueId()).filter(faction -> faction.equalsIgnoreCase(sideA)
                        || faction.equalsIgnoreCase(sideB)).isPresent())
                .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(center)))
                .limit(MAX_DUELISTS).map(this::reading).toList();
    }

    private DuelistReading reading(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        var maximum = attribute == null ? 0.0 : attribute.getValue();
        var location = entity.getLocation();
        var target = entity instanceof Mob mob && mob.getTarget() != null
                ? Optional.of(mob.getTarget().getUniqueId()) : Optional.<UUID>empty();
        var block = Blocking.of(entity);
        var item = entity.hasActiveItem() ? entity.getActiveItem().getType().name().toLowerCase(Locale.ROOT) : "";
        return new DuelistReading(entity.getUniqueId(), lookup.faction(entity.getUniqueId()).orElse(""),
                maximum > 0.0 ? Math.min(1.0, entity.getHealth() / maximum) : 0.0, entity.getAbsorptionAmount(),
                location.getX(), location.getY(), location.getZ(), entity.getBodyYaw(), entity.getWidth(),
                entity.getHeight(), target, block.raised(), block.blocking(), item);
    }

    @Override
    public Optional<String> faction(UUID entityId) {
        return lookup.faction(entityId);
    }

    @Override
    public Optional<TacticalProfile> profile(UUID entityId) {
        return lookup.bound(entityId).map(BoundProfile::profile);
    }

    @Override
    public Collection<TacticalProfile> profiles() {
        return profiles.get();
    }

    @Override
    public boolean worldLoaded(UUID worldId) {
        return Bukkit.getWorld(worldId) != null;
    }
}
