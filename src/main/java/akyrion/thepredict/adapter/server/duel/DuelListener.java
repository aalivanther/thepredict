package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.adapter.server.context.Blocking;
import akyrion.thepredict.adapter.server.events.ServerThread;
import io.lumine.mythic.bukkit.events.MythicSkillEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The events the duel recorder reads by itself (M18.4), at {@code MONITOR} so it sees what the other plugins decided:
 * damage taken or dealt by a followed duelist (final damage, fraction of maximum health, cause and whether the victim
 * blocked), deaths and removals, which end rounds, and each {@code MythicSkillEvent} not cancelled. Registered once;
 * without a recording every handler returns after one {@code volatile} read. An asynchronous death, removal or skill
 * event moves to the server thread first (rule T6); Paper fires damage on the server thread, and a damage event that is
 * not is ignored.
 */
public final class DuelListener implements Listener {
    private final DuelRecorder recorder;
    private final ServerThread thread;

    /**
     * Creates the listener.
     *
     * @param recorder the recorder the events go to
     * @param thread guard of the server thread
     */
    public DuelListener(DuelRecorder recorder, ServerThread thread) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.thread = Objects.requireNonNull(thread, "thread");
    }

    /**
     * A damage taken or dealt by a followed duelist.
     *
     * @param event the damage, not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!recorder.recording() || !(event.getEntity() instanceof LivingEntity victim)
                || !thread.runsNow(event.isAsynchronous())) {
            return;
        }
        var attacker = event instanceof EntityDamageByEntityEvent byEntity ? damager(byEntity.getDamager())
                : Optional.<UUID>empty();
        var victimId = victim.getUniqueId();
        if (!recorder.follows(victimId) && attacker.filter(recorder::follows).isEmpty()) {
            return;
        }
        var cause = event.getCause().name();
        var fraction = fraction(event.getFinalDamage(), victim);
        var blocking = Blocking.blocking(victim);
        if (attacker.isPresent()) {
            recorder.damaged(attacker.get(), victimId, cause, event.getFinalDamage(), fraction, blocking);
        } else {
            recorder.damaged(victimId, cause, event.getFinalDamage(), fraction, blocking);
        }
    }

    /**
     * A death, which may end a round.
     *
     * @param event the death
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (recorder.recording()) {
            var entityId = event.getEntity().getUniqueId();
            thread.run(event.isAsynchronous(), () -> recorder.gone(entityId, true));
        }
    }

    /**
     * A removal, such as {@code DuelTimeout} or {@code /mm mobs kill}, which ends a round as a draw.
     *
     * @param event the removal
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (recorder.recording()) {
            var entityId = event.getEntity().getUniqueId();
            thread.run(event.isAsynchronous(), () -> recorder.gone(entityId, false));
        }
    }

    /**
     * A MythicMobs skill that was not cancelled; only the counted skills of followed duelists are written.
     *
     * @param event the skill event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkill(MythicSkillEvent event) {
        if (!recorder.recording()) {
            return;
        }
        var caster = event.getSkillMetadata().getCaster().getEntity().getUniqueId();
        var skill = event.getSkill().getInternalName();
        thread.run(event.isAsynchronous(), () -> recorder.skillEvent(caster, skill));
    }

    /** The damager, or the living shooter of a projectile. */
    static Optional<UUID> damager(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return Optional.of(shooter.getUniqueId());
        }
        return damager instanceof LivingEntity living ? Optional.of(living.getUniqueId()) : Optional.empty();
    }

    /** Final damage over the maximum health of the victim, in {@code [0, 1]}. */
    static double fraction(double finalDamage, LivingEntity victim) {
        var attribute = victim.getAttribute(Attribute.MAX_HEALTH);
        var maximum = attribute == null ? 0.0 : attribute.getValue();
        return maximum > 0.0 && finalDamage > 0.0 ? Math.min(1.0, finalDamage / maximum) : 0.0;
    }
}
