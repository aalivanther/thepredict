package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.adapter.server.context.ParticipantPolicy;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.engine.ContextEvents;
import com.destroystokyo.paper.event.player.PlayerReadyArrowEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.entity.EntityLungeEvent;
import io.papermc.paper.event.player.PlayerItemGroupCooldownEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

/**
 * Listens to what a participant does that the mobs could react to (M07.5, M17.8, plan §6, "señales por evento"):
 * every event only records a history entry or marks context pending through {@link ContextEvents}, and never sends a
 * request. All handlers use {@code MONITOR} with {@code ignoreCancelled = true} to see the final outcome. Every event
 * is synchronous in Paper; one that is not, or arrives off the server thread, moves there first (rule T6).
 *
 * <p>The {@code Player*} events stay events of players. The entity events (crossbow, totem, equipment, smash, lunge)
 * accept any living participant, a non-player shooting a bow marks its encounters pending, and the death or removal of
 * a participant that is not a player marks them pending through {@link ContextEvents#participantGone} (M17.6); a
 * player who dies or leaves is handled as in 0.0.1, by the next capture. An entity outside every encounter costs one
 * O(1) index lookup and nothing more. {@code EntityMoveEvent} and the deprecated
 * {@code PlayerAttackEntityCooldownResetEvent} are not used. Outcomes of hits, projectiles and potions are in
 * {@link CombatOutcomeListener}.
 */
public final class CombatContextListener implements Listener {
    private final ContextEvents events;
    private final ServerThread thread;

    /**
     * Creates the listener.
     *
     * @param events engine entry point of context events
     * @param thread moves work onto the server thread
     */
    public CombatContextListener(ContextEvents events, ServerThread thread) {
        this.events = Objects.requireNonNull(events, "events");
        this.thread = Objects.requireNonNull(thread, "thread");
    }

    /**
     * A participant readies an arrow: pending context.
     *
     * @param event bow draw that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReadyArrow(PlayerReadyArrowEvent event) {
        changed(event, event.getPlayer());
    }

    /**
     * A participant stops using an item (bow, shield, food): pending context.
     *
     * @param event end of item use
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        changed(event, event.getPlayer());
    }

    /**
     * A participant loads a crossbow: pending context.
     *
     * @param event crossbow load that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoadCrossbow(EntityLoadCrossbowEvent event) {
        changed(event, event.getEntity());
    }

    /**
     * A participant that is not a player shoots a bow or crossbow: pending context (M17.8); a player's draw is
     * {@link #onReadyArrow}.
     *
     * @param event shot that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            changed(event, event.getEntity());
        }
    }

    /**
     * A participant's shield is disabled: {@code SHIELD_DISABLED} and pending context.
     *
     * @param event shield disable that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent event) {
        recorded(event, event.getPlayer(), EventType.SHIELD_DISABLED);
    }

    /**
     * A participant consumes an item: {@code POTION_CONSUMED} for an item with {@code POTION_CONTENTS}, pending
     * context in both cases. The item is read only for a participant.
     *
     * @param event consumption that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        var player = event.getPlayer();
        if (!thread.runsNow(event.isAsynchronous()) || !events.isParticipant(player.getWorld().getUID(), player.getUniqueId())) {
            changed(event, player);
            return;
        }
        if (event.getItem().hasData(DataComponentTypes.POTION_CONTENTS)) {
            recorded(event, player, EventType.POTION_CONSUMED);
        } else {
            changed(event, player);
        }
    }

    /**
     * A participant survives with a totem: {@code TOTEM_USED} and pending context.
     *
     * @param event resurrection that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        recorded(event, event.getEntity(), EventType.TOTEM_USED);
    }

    /**
     * A participant changes equipment: pending context.
     *
     * @param event equipment change
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEquipmentChanged(EntityEquipmentChangedEvent event) {
        changed(event, event.getEntity());
    }

    /**
     * An item group of a participant goes on cooldown (pearls, shields): pending context.
     *
     * @param event cooldown that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGroupCooldown(PlayerItemGroupCooldownEvent event) {
        changed(event, event.getPlayer());
    }

    /**
     * A participant lands an ender pearl: {@code PEARL_THROWN} and pending context; other causes are ignored.
     *
     * @param event teleport that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            recorded(event, event.getPlayer(), EventType.PEARL_THROWN);
        }
    }

    /**
     * A player starts an attack on a member mob: pending context of the mob's encounter.
     *
     * @param event attack that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        var mobId = event.getAttacked().getUniqueId();
        if (thread.runsNow(event.isAsynchronous())) {
            events.memberChanged(mobId);
        } else {
            thread.runNextTick(() -> events.memberChanged(mobId));
        }
    }

    /**
     * A participant attempts a mace smash: pending context.
     *
     * @param event smash attempt
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmashAttempt(EntityAttemptSmashAttackEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            changed(event, living);
        }
    }

    /**
     * A participant lunges with a spear: pending context.
     *
     * @param event lunge that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLunge(EntityLungeEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            changed(event, living);
        }
    }

    /**
     * A participant that is not a player dies (M17.6): its encounters get pending context, and the next capture drops
     * it. A player's death keeps the path of 0.0.1.
     *
     * @param event the death
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        gone(event, event.getEntity());
    }

    /**
     * A participant that is not a player leaves the world (despawn, unload, removal) (M17.6): pending context, as for
     * a death.
     *
     * @param event the removal
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            gone(event, living);
        }
    }

    private void changed(Event event, LivingEntity participant) {
        var worldId = participant.getWorld().getUID();
        var participantId = participant.getUniqueId();
        if (thread.runsNow(event.isAsynchronous())) {
            events.participantChanged(worldId, participantId);
        } else {
            thread.runNextTick(() -> events.participantChanged(worldId, participantId));
        }
    }

    private void recorded(Event event, LivingEntity participant, EventType type) {
        var worldId = participant.getWorld().getUID();
        var participantId = participant.getUniqueId();
        var kind = ParticipantPolicy.kind(participant);
        if (thread.runsNow(event.isAsynchronous())) {
            events.participantEvent(worldId, participantId, kind, type, true);
        } else {
            thread.runNextTick(() -> events.participantEvent(worldId, participantId, kind, type, true));
        }
    }

    private void gone(Event event, LivingEntity entity) {
        if (entity instanceof Player) {
            return;
        }
        var worldId = entity.getWorld().getUID();
        var entityId = entity.getUniqueId();
        if (thread.runsNow(event.isAsynchronous())) {
            events.participantGone(worldId, entityId);
        } else {
            thread.runNextTick(() -> events.participantGone(worldId, entityId));
        }
    }
}
