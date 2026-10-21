package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.adapter.server.context.ParticipantPolicy;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.engine.ContextEvents;
import akyrion.thepredict.engine.OutcomeEvents;
import akyrion.thepredict.engine.ThreatEnrichment;
import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Listens to the outcome of hits, projectiles and potions between members and participants, players or any other living
 * entity (M07.5, M17.8): each event records an {@code EncounterEvent} or marks context pending through {@link
 * ContextEvents}, and never sends a request. In the same pass, a blocked hit, a ranged hit on a member and an assist
 * hand their magnitude to {@link ThreatEnrichment} (M11.2), only once {@link ContextEvents} found the member or the
 * participant. Handlers use {@code MONITOR} with {@code ignoreCancelled = true}; the events are synchronous in Paper. A
 * hit or a miss that ever arrives off the server thread moves there first (rule T6); a potion, cloud or path event
 * would have to read entities off the server thread, so one that ever arrives there is ignored.
 *
 * <p>{@link #onDamage} has three explicit cases: a member hits a participant ({@code MELEE_HIT} or {@code
 * MELEE_BLOCKED}, with {@code Blocking}); a participant's projectile hits a member ({@code RANGED_HIT_ON_MOB}); and,
 * for every hit, the damage dealt of M16.8. A player victim or shooter is handled exactly as in 0.0.1; any other entity
 * only after the O(1) participant check of {@link ContextEvents#isParticipant}, so an unrelated entity does no further
 * work. Filters are O(1): a hit whose attacker and victim are not a member and a participant, or a projectile or potion
 * of an entity outside every encounter, does no further work. Affected entities and potion effects are read only for a
 * participant, and the target of a hit mob only for a member. The same hit also reaches the outcome window of M16.8 as
 * damage dealt by its member ({@link OutcomeEvents#dealt}): the member is the direct damager, the shooter of the
 * projectile, or the caster of a MythicMobs {@code damage} mechanic, which Paper is expected to report with the caster
 * as damager (DT-35, open until session 16.1 checks it on a server); the fraction is the final damage over the victim's
 * maximum health and is computed only when that member has an open window. The magnitude of a ranged hit is {@code
 * EntityDamageEvent#getFinalDamage()} at {@code MONITOR}: after armor, resistance and absorption, so it is at most the
 * damage MythicMobs added as native threat at {@code HIGHEST} for {@code ThreatTable.UseDamageTaken}. The shooter is
 * the mob's target when {@code Mob#getTarget()}, which MythicMobs sets from its threat table, is that player; it is
 * read after the native threat of the same hit, which may already have switched the target.
 */
public final class CombatOutcomeListener implements Listener {
    private final ContextEvents events;
    private final ThreatEnrichment threat;
    private final OutcomeEvents outcomes;
    private final ServerThread thread;
    private final LongSupplier currentTick;
    private final Predicate<LivingEntity> blocking;

    /**
     * Creates the listener.
     *
     * @param events engine entry point of context events
     * @param threat engine entry point of the enriched threat
     * @param outcomes engine entry point of the outcome windows (M16.8)
     * @param thread moves work onto the server thread
     * @param currentTick server tick
     * @param blocking whether a participant that is not a player blocks now ({@code Blocking::blocking}); a player's
     *                 {@code isBlocking()} is read directly, as in 0.0.1
     */
    public CombatOutcomeListener(ContextEvents events, ThreatEnrichment threat, OutcomeEvents outcomes,
                                 ServerThread thread, LongSupplier currentTick, Predicate<LivingEntity> blocking) {
        this.events = Objects.requireNonNull(events, "events");
        this.threat = Objects.requireNonNull(threat, "threat");
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes");
        this.thread = Objects.requireNonNull(thread, "thread");
        this.currentTick = Objects.requireNonNull(currentTick, "currentTick");
        this.blocking = Objects.requireNonNull(blocking, "blocking");
    }

    /**
     * Case 1: a member hits a participant ({@code MELEE_HIT}, or {@code MELEE_BLOCKED} with pending context and
     * blocked-hit threat when the participant blocks). Case 2: a participant's projectile hits a member ({@code
     * RANGED_HIT_ON_MOB} with pending context and, unless the shooter is the mob's target, harass threat from the final
     * damage). Case 3, for every hit: the damage dealt of M16.8.
     *
     * @param event damage that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();
        var victim = event.getEntity();
        dealt(event, damager, victim);
        if (victim instanceof Player player) {
            var mobId = damager.getUniqueId();
            var playerId = player.getUniqueId();
            var blocked = player.isBlocking();
            if (thread.runsNow(event.isAsynchronous())) {
                hit(mobId, playerId, ParticipantKind.PLAYER, blocked);
            } else {
                thread.runNextTick(() -> hit(mobId, playerId, ParticipantKind.PLAYER, blocked));
            }
        } else if (victim instanceof LivingEntity living && !(damager instanceof Projectile)) {
            if (participant(event.isAsynchronous(), living)) {
                hit(damager.getUniqueId(), living.getUniqueId(), ParticipantKind.ENTITY, blocking.test(living));
            }
        } else if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity shooter) {
            shot(event, victim, shooter);
        }
    }

    /** Case 2: a player shooter as in 0.0.1; any other shooter only when it is a participant. */
    private void shot(EntityDamageByEntityEvent event, Entity victim, LivingEntity shooter) {
        var shooterId = shooter.getUniqueId();
        var damage = event.getFinalDamage();
        if (!(shooter instanceof Player)) {
            if (participant(event.isAsynchronous(), shooter)) {
                ranged(victim, shooterId, ParticipantKind.ENTITY, damage);
            }
        } else if (thread.runsNow(event.isAsynchronous())) {
            ranged(victim, shooterId, ParticipantKind.PLAYER, damage);
        } else {
            thread.runNextTick(() -> ranged(victim, shooterId, ParticipantKind.PLAYER, damage));
        }
    }

    /**
     * A participant's arrow or trident hits no entity: {@code RANGED_MISS}, without pending context. The shooter may be
     * a player or any other living entity; the engine keeps only a participant.
     *
     * @param event projectile hit that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitEntity() == null && event.getEntity() instanceof AbstractArrow arrow
                && arrow.getShooter() instanceof LivingEntity shooter) {
            var worldId = shooter.getWorld().getUID();
            var shooterId = shooter.getUniqueId();
            var kind = ParticipantPolicy.kind(shooter);
            if (thread.runsNow(event.isAsynchronous())) {
                events.participantEvent(worldId, shooterId, kind, EventType.RANGED_MISS, false);
            } else {
                thread.runNextTick(() -> events.participantEvent(worldId, shooterId, kind, EventType.RANGED_MISS,
                        false));
            }
        }
    }

    /**
     * A participant's splash potion with a beneficial effect reaches another participant: {@code ALLY_ASSISTED}. The
     * source and the affected participants may be players or any other living entity.
     *
     * @param event splash that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (event.getPotion().getShooter() instanceof LivingEntity source && participant(event.isAsynchronous(), source)
                && beneficial(event.getPotion().getEffects())) {
            assisted(source, living(event.getAffectedEntities()));
        }
    }

    /**
     * A participant's lingering cloud with a beneficial effect reaches another participant: {@code ALLY_ASSISTED};
     * repeats within ten ticks are dropped by the engine.
     *
     * @param event cloud application that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCloudApply(AreaEffectCloudApplyEvent event) {
        var cloud = event.getEntity();
        if (!(cloud.getSource() instanceof LivingEntity source) || !participant(event.isAsynchronous(), source)) {
            return;
        }
        var effects = new ArrayList<>(cloud.getCustomEffects());
        var base = cloud.getBasePotionType();
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        if (beneficial(effects)) {
            assisted(source, living(event.getAffectedEntities()));
        }
    }

    /**
     * A member computes a path: a diagnostic counter only.
     *
     * @param event path computation that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPathfind(EntityPathfindEvent event) {
        if (thread.runsNow(event.isAsynchronous())) {
            events.memberPathfind(event.getEntity().getUniqueId());
        }
    }

    /**
     * Tells whether any effect is beneficial by its native category.
     *
     * @param effects potion effects
     * @return {@code true} if one effect has {@code PotionEffectTypeCategory.BENEFICIAL}
     */
    static boolean beneficial(Collection<PotionEffect> effects) {
        for (var effect : effects) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.BENEFICIAL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands the damage of a member to its outcome window: direct damager, shooter or MythicMobs caster (M16.8). Any
     * member counts, with or without an open window, so the fight clock and the motivated cast see the hit
     * (arreglos-engine-FO §3.6).
     */
    private void dealt(EntityDamageByEntityEvent event, Entity damager, Entity victim) {
        var attackerId = attackerId(damager);
        if (!(victim instanceof LivingEntity living) || !thread.runsNow(event.isAsynchronous())
                || !outcomes.tracksMember(attackerId)) {
            return;
        }
        outcomes.dealt(attackerId, victim.getUniqueId(), OutcomeListener.fraction(event.getFinalDamage(), living),
                currentTick.getAsLong());
    }

    /**
     * The entity a hit counts for: the shooter of a projectile when it is an entity, otherwise the damager itself,
     * which for the {@code damage} mechanic of MythicMobs is expected to be the caster (DT-35, open).
     *
     * @param damager damager of the event
     * @return the UUID of the attacker
     */
    static UUID attackerId(Entity damager) {
        var attacker = damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter : damager;
        return attacker.getUniqueId();
    }

    private void hit(UUID mobId, UUID participantId, ParticipantKind kind, boolean blocked) {
        var member = events.memberEvent(mobId, participantId, kind, blocked ? EventType.MELEE_BLOCKED
                : EventType.MELEE_HIT, blocked);
        if (member && blocked) {
            threat.blockedHit(mobId, participantId);
        }
    }

    private void ranged(Entity victim, UUID shooterId, ParticipantKind kind, double finalDamage) {
        var mobId = victim.getUniqueId();
        if (events.memberEvent(mobId, shooterId, kind, EventType.RANGED_HIT_ON_MOB, true)) {
            threat.rangedHit(mobId, shooterId, finalDamage, targets(victim, shooterId));
        }
    }

    private void assisted(LivingEntity source, List<UUID> affected) {
        var worldId = source.getWorld().getUID();
        if (events.allyAssisted(worldId, source.getUniqueId(), ParticipantPolicy.kind(source), affected) > 0) {
            threat.assisted(worldId, source.getUniqueId(), affected);
        }
    }

    /** Tells whether a mob's current target is the given participant; read only for a member. */
    private static boolean targets(Entity victim, UUID participantId) {
        if (!(victim instanceof Mob mob)) {
            return false;
        }
        var target = mob.getTarget();
        return target != null && target.getUniqueId().equals(participantId);
    }

    private boolean participant(boolean asynchronous, LivingEntity entity) {
        return thread.runsNow(asynchronous) && events.isParticipant(entity.getWorld().getUID(), entity.getUniqueId());
    }

    /** The UUIDs of the affected living entities; the engine keeps only the participants among them. */
    private static List<UUID> living(Collection<? extends Entity> affected) {
        var living = new ArrayList<UUID>();
        for (var entity : affected) {
            if (entity instanceof LivingEntity) {
                living.add(entity.getUniqueId());
            }
        }
        return living;
    }
}
