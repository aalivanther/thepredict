package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.engine.ContextEvents;
import akyrion.thepredict.engine.OutcomeEvents;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Listens to the damage members take and to the deaths of members and participants for the outcome windows of M16.8
 * (principle A9). The damage handler uses {@code MONITOR}, the death handler {@code LOW}; both are O(1): damage counts only for a member with an open window,
 * and a death only for a member or a participant of an encounter. The damage is {@code getFinalDamage()} over the
 * {@code MAX_HEALTH} attribute of the victim, computed here so the engine never reads entities (rule D2). Paper fires
 * both events on the server thread; one that ever arrives elsewhere moves there first (rule T6). One responsibility,
 * kept apart from {@link CombatOutcomeListener} (rule C10).
 */
public final class OutcomeListener implements Listener {
    private final OutcomeEvents outcomes;
    private final ContextEvents events;
    private final ServerThread thread;
    private final LongSupplier currentTick;

    /**
     * Creates the listener.
     *
     * @param outcomes engine entry point of the outcome windows
     * @param events engine entry point of context events, for the O(1) participant check
     * @param thread moves work onto the server thread
     * @param currentTick server tick
     */
    public OutcomeListener(OutcomeEvents outcomes, ContextEvents events, ServerThread thread, LongSupplier currentTick) {
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes");
        this.events = Objects.requireNonNull(events, "events");
        this.thread = Objects.requireNonNull(thread, "thread");
        this.currentTick = Objects.requireNonNull(currentTick, "currentTick");
    }

    /**
     * A member with an open window takes damage of any cause.
     *
     * @param event damage that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        var victimId = victim.getUniqueId();
        if (!thread.runsNow(event.isAsynchronous()) || !outcomes.tracksMember(victimId)) {
            return;
        }
        var max = maxHealth(victim);
        var amount = fraction(event.getFinalDamage(), max);
        var tick = currentTick.getAsLong();
        var cause = event.getDamageSource().getCausingEntity();
        if (cause == null) {
            outcomes.taken(victimId, amount, max, tick);
        } else {
            outcomes.takenFrom(victimId, cause.getUniqueId(), amount, max, tick);
        }
    }

    /**
     * A member or a participant dies. {@code LOW}, not {@code MONITOR}: MythicMobs reports the death of its mob from
     * this same event and the lifecycle then closes the session, so the window must see the death first (M06.4). A
     * participant may be a player or any other living entity (M17.8); a mob of ThePredict that is a participant of one
     * encounter and a member of another closes its own window too, and a player, who is never a member, dies as in
     * 0.0.1. A member counts with or without an open window, so its fight clock closes as {@code MOB_DIED}
     * (arreglos-engine-FO §3.6).
     *
     * @param event the death
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        var entity = event.getEntity();
        var entityId = entity.getUniqueId();
        var worldId = entity.getWorld().getUID();
        var participant = events.isParticipant(worldId, entityId);
        var member = outcomes.tracksMember(entityId);
        Runnable died;
        if (participant && member) {
            died = () -> {
                outcomes.participantDied(worldId, entityId);
                outcomes.died(entityId, currentTick.getAsLong());
            };
        } else if (participant) {
            died = () -> outcomes.participantDied(worldId, entityId);
        } else if (member) {
            died = () -> outcomes.died(entityId, currentTick.getAsLong());
        } else {
            return;
        }
        if (thread.runsNow(event.isAsynchronous())) {
            died.run();
        } else {
            thread.runNextTick(died);
        }
    }

    /**
     * Final damage as a fraction of the victim's maximum health, read from its {@code MAX_HEALTH} attribute.
     *
     * @param finalDamage {@code getFinalDamage()} of the event
     * @param victim damaged entity
     * @return the fraction, within {@code [0, 1]}
     */
    static double fraction(double finalDamage, LivingEntity victim) {
        return fraction(finalDamage, maxHealth(victim));
    }

    /**
     * Maximum health of a living entity, {@code 0} when the attribute is absent.
     *
     * @param victim damaged entity
     * @return the attribute value
     */
    static double maxHealth(LivingEntity victim) {
        var attribute = victim.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 0.0 : attribute.getValue();
    }

    /**
     * Final damage as a fraction of a maximum health, clamped to {@code [0, 1]}: {@code 0} when either is not positive,
     * and {@code 1} when the final damage exceeds the maximum health (an overkill or a lowered maximum).
     *
     * @param finalDamage final damage
     * @param maxHealth maximum health of the victim
     * @return the fraction, within {@code [0, 1]}
     */
    static double fraction(double finalDamage, double maxHealth) {
        return maxHealth > 0.0 && finalDamage > 0.0 ? Math.min(1.0, finalDamage / maxHealth) : 0.0;
    }
}
