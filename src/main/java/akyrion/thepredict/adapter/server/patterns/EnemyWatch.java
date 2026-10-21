package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.domain.AirSwingPolicy;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.engine.PatternDiag;
import akyrion.thepredict.engine.PatternInbox;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Admits a witnessed hit into the encounter ring (M26.1). Monitor and ignore-cancelled. The damager is tokenized
 * only when the victim is an encounter member. Air swings call {@link AirSwingGate}; observe records a diagnosis.
 */
public final class EnemyWatch implements Listener {
    private final PatternInbox inbox;
    private final LongSupplier tick;
    private final ChargeRing charges = new ChargeRing();
    private Predicate<UUID> member = id -> false;
    private AirSwingPolicy air = AirSwingPolicy.OBSERVE;
    private PatternDiag diag;

    /**
     * Creates the listener. No victim is a member until {@link #useMember} says so.
     *
     * @param inbox rings of the open encounters
     * @param tick current server tick
     */
    public EnemyWatch(PatternInbox inbox, LongSupplier tick) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    /**
     * Members of an open encounter. The predicate is not this package's session type.
     *
     * @param member whether an entity id is a member
     */
    public void useMember(Predicate<UUID> member) {
        this.member = Objects.requireNonNull(member, "member");
    }

    /**
     * Air-swing policy. The default observes.
     *
     * @param air the policy
     */
    public void useAir(AirSwingPolicy air) {
        this.air = Objects.requireNonNull(air, "air");
    }

    /**
     * Diagnosis flushed by the pattern step.
     *
     * @param diag the slot
     */
    public void useDiag(PatternDiag diag) {
        this.diag = diag;
    }

    /**
     * Classifies one landed hit. A spear is never a critical or a sprint token.
     *
     * @param critical {@code isCritical()}
     * @param sprinting the attacker is sprinting
     * @param charge attack cooldown before the reset
     * @param spear the weapon is a spear
     * @return the strike token
     */
    public static EnemyAction classifyHit(boolean critical, boolean sprinting, double charge, boolean spear) {
        return StrikeClassifier.classify(critical, sprinting, charge, spear);
    }

    /**
     * Stores a received hit on the damager when the victim is a member.
     *
     * @param event a damage event the server accepted
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        var victim = event.getEntity();
        if (victim == null || !member.test(victim.getUniqueId())) {
            return;
        }
        var actor = actor(event.getDamager());
        if (actor == null) {
            return;
        }
        var now = (int) tick.getAsLong();
        var shot = charges.take(actor, now);
        var action = hitAction(event.getDamager(), event.isCritical(), shot);
        if (action < 0) {
            return;
        }
        inbox.admit(actor);
        consider(actor, true, action, now, event.getDamager() instanceof Player && shot.spear());
    }

    /**
     * The strike token of a landed hit, or {@code -1} when the hit is not a melee strike. M26.1 reserves
     * {@code STRIKE*} for melee: a projectile a player shot is not a strike and never a critical one, whatever
     * {@code isCritical()} says about the arrow (SP-11). A hit by another entity is a plain strike.
     *
     * @param damager the damager of the event
     * @param critical {@code isCritical()}
     * @param shot the charge noted before the attack
     * @return an {@link EnemyAction} ordinal, or {@code -1}
     */
    static int hitAction(Entity damager, boolean critical, ChargeRing.Shot shot) {
        if (damager instanceof Projectile) {
            return -1;
        }
        if (damager instanceof Player) {
            return classifyHit(critical, shot.sprinting(), shot.charge(), shot.spear()).ordinal();
        }
        return EnemyAction.STRIKE.ordinal();
    }

    /**
     * Snapshots charge before the attack resets it, and only for a member.
     *
     * @param event the attack that is about to land
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack() || !member.test(event.getAttacked().getUniqueId())) {
            return;
        }
        var player = event.getPlayer();
        charges.note(player.getUniqueId(), (int) tick.getAsLong(), player.getAttackCooldown(), player.isSprinting(),
                HeldWeapon.spear(player));
    }

    /**
     * An arm swing of a participant. {@code observe} never tokens it. A swing of any other player costs one
     * constant-time lookup and reads nothing (SP-18).
     *
     * @param event the swing
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent event) {
        var player = event.getPlayer();
        var actor = player.getUniqueId();
        if (event.getHand() != EquipmentSlot.HAND || !inbox.participates(actor)) {
            return;
        }
        var now = (int) tick.getAsLong();
        var flags = charges.flags(actor, now);
        var kind = HeldWeapon.kind(player);
        var weapon = !kind.isEmpty();
        var wouldToken = AirSwingGate.tokens(true, true, weapon, flags.block(), flags.right(), flags.projectile());
        if (wouldToken && air == AirSwingPolicy.ON) {
            consider(actor, true, EnemyAction.STRIKE.ordinal(), now);
            return;
        }
        if (diag != null) {
            diag.note(actor, kind, 0, true, flags.blocked(), wouldToken);
        }
    }

    /**
     * A right click blocks an air swing on this tick and the next. The event is not filtered by cancellation: a
     * click in the air is born {@code DENY} on its block side, which {@code isCancelled()} reports as cancelled, so an
     * ignore-cancelled handler would never see a {@code RIGHT_CLICK_AIR} (SP-18).
     *
     * @param event the interaction
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        var action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        charges.flag(event.getPlayer().getUniqueId(), (int) tick.getAsLong(), false, true, false);
    }

    /**
     * A placed block blocks an air swing on this tick and the next.
     *
     * @param event the placement
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        charges.flag(event.getPlayer().getUniqueId(), (int) tick.getAsLong(), true, false, false);
    }

    /**
     * A launched projectile blocks an air swing on this tick and the next.
     *
     * @param event the launch
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            charges.flag(player.getUniqueId(), (int) tick.getAsLong(), false, false, true);
        }
    }

    /**
     * Feeds the ring after the two constant-time rejections.
     *
     * @param participant the actor
     * @param witnessed whether a member perceived it
     * @param action token ordinal
     * @param at the server tick
     * @return whether a token was stored
     */
    public boolean consider(UUID participant, boolean witnessed, int action, int at) {
        return consider(participant, witnessed, action, at, false);
    }

    /**
     * Feeds the ring after the two constant-time rejections, and marks a spear.
     *
     * @param participant the actor
     * @param witnessed whether a member perceived it
     * @param action token ordinal
     * @param at the server tick
     * @param spear whether the weapon was a spear
     * @return whether a token was stored
     */
    public boolean consider(UUID participant, boolean witnessed, int action, int at, boolean spear) {
        if (!inbox.participates(participant) || !witnessed) {
            return false;
        }
        inbox.offer(participant, action, at, spear);
        return true;
    }

    private static UUID actor(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter.getUniqueId();
        }
        if (damager instanceof Player player) {
            return player.getUniqueId();
        }
        return damager == null ? null : damager.getUniqueId();
    }
}
