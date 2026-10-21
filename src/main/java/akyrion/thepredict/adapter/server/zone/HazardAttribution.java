package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.HazardRecord;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.engine.HazardNotes;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.HazardLedger;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Attributes a knockback to a later hazard (M24.10). The knockback event is Paper's
 * {@link EntityKnockbackByEntityEvent}. Damage uses {@code getHitBy} and {@code getPushedBy} only on that event.
 * The book is {@link HazardLedger}, capped at {@link HazardLedger#CAP}.
 */
public final class HazardAttribution implements Listener, HazardNotes {
    private static final Set<EntityDamageEvent.DamageCause> CAUSES = Set.of(
            EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.HOT_FLOOR,
            EntityDamageEvent.DamageCause.CAMPFIRE, EntityDamageEvent.DamageCause.CONTACT,
            EntityDamageEvent.DamageCause.FALL, EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.WORLD_BORDER, EntityDamageEvent.DamageCause.FREEZE,
            EntityDamageEvent.DamageCause.DROWNING, EntityDamageEvent.DamageCause.FLY_INTO_WALL);

    private final HazardLedger ledger;
    private final DecisionTap tap;
    private final Function<UUID, Optional<MobKey>> members;
    private final LongSupplier tick;
    private final int window;
    private UUID pendingVictim;
    private MobKey pendingKey;
    private HazardRecord pending;
    private long pendingTick = Long.MIN_VALUE;

    /**
     * Creates the listener.
     *
     * @param ledger the engine ledger
     * @param tap where a {@code hazard} line is written
     * @param members session key of an entity, empty when it is not a member
     * @param tick current server tick
     * @param window {@code zone.pushAttributionTicks}
     */
    public HazardAttribution(HazardLedger ledger, DecisionTap tap, Function<UUID, Optional<MobKey>> members,
                             LongSupplier tick, int window) {
        this.ledger = ledger;
        this.tap = tap;
        this.members = members;
        this.tick = tick;
        this.window = window;
    }

    /**
     * Notes an accepted cast that moves a target. A {@link DisplacementKind#NONE} reading is ignored.
     *
     * @param mob the caster
     * @param target the entity that was moved
     * @param kind how the choice moves the target
     * @param tick the server tick of the cast
     */
    @Override
    public void acceptedCast(MobKey mob, UUID target, DisplacementKind kind, long tick) {
        if (kind == DisplacementKind.NONE) {
            return;
        }
        ledger.note(target, mob.entityId(), kind, tick);
    }

    /**
     * Records one knockback. The hitter is {@code getHitBy}, or {@code getPushedBy} when the hit is absent.
     *
     * @param event the Paper knockback event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        var hit = event.getHitBy();
        var pushedBy = event.getPushedBy();
        var hitter = hit != null ? hit.getUniqueId() : pushedBy == null ? null : pushedBy.getUniqueId();
        if (hitter == null || members.apply(hitter).isEmpty()) {
            return;
        }
        ledger.note(event.getEntity().getUniqueId(), hitter, DisplacementKind.KNOCKBACK, tick.getAsLong());
    }

    /**
     * Attributes hazard damage. A member push inside the window is {@code PUSHED_INTO_HAZARD}. A member with no
     * push is {@code SELF_HAZARD}.
     *
     * @param event the damage
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!CAUSES.contains(event.getCause())) {
            return;
        }
        var victim = event.getEntity().getUniqueId();
        var died = diedNow(event);
        var damage = (int) Math.floor(event.getFinalDamage());
        var member = members.apply(victim).isPresent();
        var now = tick.getAsLong();
        var record = ledger.attribute(victim, damage, died, now, window, member).orElse(null);
        if (record == null) {
            return;
        }
        var key = members.apply(victim)
                .or(() -> ledger.hitter(victim, now, window).flatMap(members))
                .orElse(null);
        if (key == null) {
            return;
        }
        tap.hazardEvent(key, record);
        pendingVictim = victim;
        pendingKey = key;
        pending = record;
        pendingTick = tick.getAsLong();
    }

    /**
     * A death on this tick or the next marks the pending hazard as fatal.
     *
     * @param event the death
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        var victim = event.getEntity().getUniqueId();
        if (pending == null || pending.died() || pendingKey == null || !victim.equals(pendingVictim)) {
            return;
        }
        if (tick.getAsLong() - pendingTick > 1L) {
            return;
        }
        var fatal = new HazardRecord(pending.damage(), true, pending.ticks(), pending.kind(), pending.outcome());
        tap.hazardEvent(pendingKey, fatal);
        pending = fatal;
    }

    private static boolean diedNow(EntityDamageEvent event) {
        return event.getEntity() instanceof LivingEntity living && living.getHealth() - event.getFinalDamage() <= 0.0;
    }
}
