package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ParticipantKind;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * {@link EntityReads} over the Paper 26.2 API, with the sources of M07.3, M07.4 and M07.8. Server thread only;
 * the attribute registry needs a running server, so this class is verified in the server script.
 *
 * <p>The pearl cooldown is read by its item group, {@code minecraft:ender_pearl}: {@code getCooldown(Material)}
 * builds an {@code ItemStack} on every call and resolves the same group, since
 * {@code ItemCooldowns.getCooldownGroup} returns the {@code USE_COOLDOWN} group or else the item ID (javap).
 *
 * <p>Since M17.4 a participant may be any living entity. A player is read exactly as in 0.0.1; any other entity with
 * the {@code LivingEntity} API of Paper ({@code hasActiveItem}, {@code getActiveItem}, {@code getActiveItemUsedTime},
 * {@code getEquipment}), its shield with {@link Blocking}, and the fields that only exist for players with neutral
 * values: no shield cooldown, attack fully recharged and no pearl cooldown.
 */
public final class PaperEntityReads implements EntityReads {
    /** Vanilla player reach when {@code ENTITY_INTERACTION_RANGE} is missing, in blocks. */
    public static final double DEFAULT_REACH_BLOCKS = 3.0;
    private static final NamespacedKey PEARL_GROUP = NamespacedKey.minecraft("ender_pearl");

    private final ParticipantLookup lookup;

    /**
     * Creates the reads.
     *
     * @param lookup native identities, for the reach of a rival bound to ThePredict (M17.4)
     */
    public PaperEntityReads(ParticipantLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    @Override
    public double followRangeBlocks(LivingEntity mob) {
        var attribute = mob.getAttribute(Attribute.FOLLOW_RANGE);
        return attribute == null ? 0.0 : attribute.getValue();
    }

    @Override
    public double maxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Double.NaN : attribute.getValue();
    }

    @Override
    public ParticipantReading participant(LivingEntity participant) {
        return participant instanceof Player player ? player(player) : entity(participant);
    }

    private ParticipantReading player(Player player) {
        var active = player.getActiveItem();
        var using = player.hasActiveItem();
        var main = player.getInventory().getItemInMainHand();
        var off = player.getInventory().getItemInOffHand();
        return new ParticipantReading(ParticipantKind.PLAYER, player.getHealth(), player.getAbsorptionAmount(),
                maxHealth(player),
                attribute(player, Attribute.ARMOR), attribute(player, Attribute.ARMOR_TOUGHNESS), player.isBlocking(),
                using && active.hasData(DataComponentTypes.BLOCKS_ATTACKS), shieldOnCooldown(player, main, off),
                player.getAttackCooldown(), using && active.getType() == Material.BOW, player.getActiveItemUsedTime(),
                loaded(main) || loaded(off), using && active.hasData(DataComponentTypes.CONSUMABLE),
                main.hasData(DataComponentTypes.DEATH_PROTECTION) || off.hasData(DataComponentTypes.DEATH_PROTECTION),
                player.getCooldown(PEARL_GROUP), player.isGliding(), longReach(main), player.getFallDistance(),
                main.getType() == Material.MACE, player.isInvisible());
    }

    /** Any living entity that is not a player: the same components on its active item and equipment. */
    private ParticipantReading entity(LivingEntity entity) {
        var using = entity.hasActiveItem();
        var active = entity.getActiveItem();
        var equipment = entity.getEquipment();
        var main = equipment == null ? null : equipment.getItemInMainHand();
        var off = equipment == null ? null : equipment.getItemInOffHand();
        var blocks = using ? active.getData(DataComponentTypes.BLOCKS_ATTACKS) : null;
        var used = using ? entity.getActiveItemUsedTime() : 0;
        var block = Blocking.state(blocks != null, blocks == null ? 0.0f : blocks.blockDelaySeconds(), used);
        return new ParticipantReading(ParticipantKind.ENTITY, entity.getHealth(), entity.getAbsorptionAmount(),
                maxHealth(entity), attribute(entity, Attribute.ARMOR), attribute(entity, Attribute.ARMOR_TOUGHNESS),
                block.blocking(), block.raised(), false, 1.0f, using && active.getType() == Material.BOW, used,
                loaded(main) || loaded(off), using && active.hasData(DataComponentTypes.CONSUMABLE),
                totem(main) || totem(off), 0, entity.isGliding(), main != null && longReach(main),
                entity.getFallDistance(), main != null && main.getType() == Material.MACE, entity.isInvisible());
    }

    @Override
    public TargetReach targetReach(LivingEntity target) {
        if (target instanceof Player player) {
            var attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
            var interaction = attribute == null ? DEFAULT_REACH_BLOCKS : attribute.getValue();
            var range = player.getInventory().getItemInMainHand().getData(DataComponentTypes.ATTACK_RANGE);
            return TargetReach.player(interaction, range != null, range == null ? 0.0f : range.maxReach());
        }
        var bound = lookup.bound(target.getUniqueId());
        if (bound.isPresent()) {
            return TargetReach.profile(ReachGeometry.profileReachBlocks(bound.get().profile()));
        }
        if (!(target instanceof Mob mob) || lookup.mythicType(mob.getUniqueId()).isPresent()) {
            return TargetReach.UNKNOWN;
        }
        return VanillaMobReach.of(mob);
    }

    @Override
    public OptionalDouble raisedShieldAngleDegrees(LivingEntity target) {
        return ShieldGeometry.raisedShieldAngleDegrees(target);
    }

    @Override
    public CurrentPath currentPath(LivingEntity mob) {
        if (!(mob instanceof Mob pathing) || !pathing.getPathfinder().hasPath()) {
            return CurrentPath.NONE;
        }
        var path = pathing.getPathfinder().getCurrentPath();
        if (path == null) {
            return CurrentPath.NONE;
        }
        return path.canReachFinalPoint() ? CurrentPath.REACHES : CurrentPath.FALLS_SHORT;
    }

    @Override
    public boolean findPathReaches(LivingEntity mob, LivingEntity target) {
        if (!(mob instanceof Mob pathing)) {
            return false;
        }
        var path = pathing.getPathfinder().findPath(target);
        return path != null && path.canReachFinalPoint();
    }

    @Override
    public int combatDurationTicks(LivingEntity entity) {
        return entity.getCombatTracker().getCombatDuration();
    }

    @Override
    public boolean trackerInCombat(LivingEntity entity) {
        return entity.getCombatTracker().isInCombat();
    }

    private static double attribute(LivingEntity entity, Attribute attribute) {
        var instance = entity.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getValue();
    }

    private static boolean shieldOnCooldown(Player player, ItemStack main, ItemStack off) {
        return main.hasData(DataComponentTypes.BLOCKS_ATTACKS) && player.hasCooldown(main)
                || off.hasData(DataComponentTypes.BLOCKS_ATTACKS) && player.hasCooldown(off);
    }

    private static boolean loaded(ItemStack item) {
        if (item == null) {
            return false;
        }
        var charged = item.getData(DataComponentTypes.CHARGED_PROJECTILES);
        return charged != null && !charged.projectiles().isEmpty();
    }

    private static boolean totem(ItemStack item) {
        return item != null && item.hasData(DataComponentTypes.DEATH_PROTECTION);
    }

    private static boolean longReach(ItemStack main) {
        return main.hasData(DataComponentTypes.KINETIC_WEAPON) || main.hasData(DataComponentTypes.PIERCING_WEAPON)
                || main.hasData(DataComponentTypes.ATTACK_RANGE);
    }
}
