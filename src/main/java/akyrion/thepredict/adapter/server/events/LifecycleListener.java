package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.adapter.server.binding.NativeLevelAdjuster;
import akyrion.thepredict.domain.NativeLevelMode;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.SessionLifecycle;
import akyrion.thepredict.engine.SessionMetrics;
import akyrion.thepredict.engine.SpawnLevelBook;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobDespawnEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.bukkit.events.MythicReloadedEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Keeps the sessions in step with native lifecycle events (M06.2), without polling every mob. Every change of
 * {@code engine} state runs on the server thread: an event that is asynchronous, or that arrives off the server
 * thread, is moved there first (rule T6). All handlers use {@code MONITOR} priority, so they see the final
 * outcome.
 *
 * <p>{@code EntityRemoveEvent} with cause {@code UNLOAD} comes from a chunk unload and suspends instead of closing.
 * The static evidence behind each row (threads, causes, dimension change) is in the M06 document.
 */
public final class LifecycleListener implements Listener {
    private final SessionLifecycle lifecycle;
    private final MobScan scan;
    private final ServerThread thread;
    private final Runnable rebindCatalog;
    private final Function<String, Optional<TacticalProfile>> boundProfiles;
    private SpawnLevelBook levels = new SpawnLevelBook();

    /**
     * Creates the listener.
     *
     * @param lifecycle session lifecycle of {@code engine}
     * @param scan bounded scan, also used to resolve single mobs
     * @param thread moves work onto the server thread
     * @param rebindCatalog reads and binds the profiles again after a MythicMobs reload (M03.5)
     * @param boundProfiles profile of a MythicMobs mob ID in the current catalog
     */
    public LifecycleListener(SessionLifecycle lifecycle, MobScan scan, ServerThread thread, Runnable rebindCatalog,
                             Function<String, Optional<TacticalProfile>> boundProfiles) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.scan = Objects.requireNonNull(scan, "scan");
        this.thread = Objects.requireNonNull(thread, "thread");
        this.rebindCatalog = Objects.requireNonNull(rebindCatalog, "rebindCatalog");
        this.boundProfiles = Objects.requireNonNull(boundProfiles, "boundProfiles");
    }

    /**
     * Shares the spawn steps noted when a fight ends unreachable (M22.12).
     *
     * @param book the book
     */
    public void useLevels(SpawnLevelBook book) {
        this.levels = Objects.requireNonNull(book, "book");
    }

    /**
     * Opens the session of a bound mob one tick after a successful spawn, when MythicMobs has applied its goals.
     *
     * @param event spawn that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobSpawn(MythicMobSpawnEvent event) {
        adjustSpawn(event.getMob());
        spawned(event.getMob().getUniqueId());
    }

    private void adjustSpawn(io.lumine.mythic.core.mobs.ActiveMob mob) {
        var name = mob.getType().getInternalName();
        boundProfiles.apply(name).ifPresent(profile -> {
            if (profile.difficulty().nativeLevel() != NativeLevelMode.SPAWN) {
                return;
            }
            var delta = levels.delta(profile.id());
            if (delta == 0) {
                return;
            }
            var current = (int) Math.round(mob.getLevel());
            NativeLevelAdjuster.apply(mob, NativeLevelAdjuster.nextLevel(current, delta, true,
                    NativeLevelMode.SPAWN, true));
        });
    }

    /**
     * Resumes or opens the sessions of the bound mobs of a loaded chunk one tick later, when MythicMobs has
     * registered their instances again.
     *
     * @param event entities loaded with a chunk
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        loaded(idsOf(event.getEntities()));
    }

    /**
     * Suspends the sessions of the mobs of an unloaded chunk.
     *
     * @param event entities unloaded with a chunk
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        var ids = idsOf(event.getEntities());
        thread.run(event.isAsynchronous(), () -> ids.forEach(lifecycle::unloaded));
    }

    /**
     * Closes the session of a dead mob.
     *
     * @param event MythicMobs death
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        ended(event.getMob().getUniqueId(), SessionMetrics.SessionEnd.DIED, event.isAsynchronous());
    }

    /**
     * Closes the session of a despawned mob.
     *
     * @param event MythicMobs despawn
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDespawn(MythicMobDespawnEvent event) {
        ended(event.getMob().getUniqueId(), SessionMetrics.SessionEnd.DESPAWNED, event.isAsynchronous());
    }

    /**
     * Closes the session of an entity removed without a MythicMobs event, or suspends it on a chunk unload.
     *
     * @param event entity removal
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        removed(event.getEntity().getUniqueId(), event.getCause() == EntityRemoveEvent.Cause.UNLOAD,
                event.isAsynchronous());
    }

    /**
     * Advances the generation of a mob that changes world by teleport and takes it out of its encounter.
     *
     * @param event teleport that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        var to = event.getTo();
        if (to != null) {
            teleported(event.getEntity().getUniqueId(), event.getFrom(), to, event.isAsynchronous());
        }
    }

    /**
     * Advances the generation of a mob that changes world through a portal; listened to as well until M01 shows
     * which of the two events covers every case.
     *
     * @param event portal travel that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        var to = event.getTo();
        if (to != null) {
            teleported(event.getEntity().getUniqueId(), event.getFrom(), to, event.isAsynchronous());
        }
    }

    /**
     * Binds the new catalog, then advances or unbinds every session and scans for newly bound mobs.
     *
     * @param event MythicMobs reload
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicReloaded(MythicReloadedEvent event) {
        thread.run(event.isAsynchronous(), this::reloaded);
    }

    /**
     * Tells whether a teleport crosses to another world.
     *
     * @param from origin
     * @param to destination
     * @return {@code true} only if both worlds are known and differ
     */
    public static boolean changesWorld(Location from, Location to) {
        var origin = from.getWorld();
        var destination = to.getWorld();
        return origin != null && destination != null && !origin.getUID().equals(destination.getUID());
    }

    void spawned(UUID entityId) {
        thread.runNextTick(() -> scan.discover(entityId));
    }

    void loaded(List<UUID> entityIds) {
        if (!entityIds.isEmpty()) {
            thread.runNextTick(() -> entityIds.forEach(scan::discover));
        }
    }

    void ended(UUID entityId, SessionMetrics.SessionEnd cause, boolean asynchronous) {
        thread.run(asynchronous, () -> lifecycle.ended(entityId, cause));
    }

    void removed(UUID entityId, boolean chunkUnload, boolean asynchronous) {
        if (chunkUnload) {
            thread.run(asynchronous, () -> lifecycle.unloaded(entityId));
        } else {
            ended(entityId, SessionMetrics.SessionEnd.REMOVED, asynchronous);
        }
    }

    void teleported(UUID entityId, Location from, Location to, boolean asynchronous) {
        if (changesWorld(from, to)) {
            thread.run(asynchronous, () -> lifecycle.changedWorld(entityId));
        }
    }

    void reloaded() {
        rebindCatalog.run();
        lifecycle.reloaded(boundProfiles);
        scan.start();
    }

    private static List<UUID> idsOf(List<Entity> entities) {
        return entities.stream().map(Entity::getUniqueId).toList();
    }
}
