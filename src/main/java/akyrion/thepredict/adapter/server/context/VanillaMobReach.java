package akyrion.thepredict.adapter.server.context;

import com.destroystokyo.paper.entity.RangedEntity;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Breeze;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Warden;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The reach of a vanilla {@code Mob} as a target (M17.4, DT-37), read as {@code Mob.isWithinMeleeAttackRange} reads it
 * on the patched Paper 26.2 server (javap): the weapon is the item in use, or else the main hand item
 * ({@code LivingEntity.getActiveItem} of the server), and its {@code ATTACK_RANGE} gives the reach; without it,
 * {@link TargetReach#MOB_DEFAULT_ATTACK_REACH_BLOCKS}. Nothing is claimed for a mob with a native ranged attack or a
 * ranged weapon in hand. Server thread only.
 */
final class VanillaMobReach {
    /** Weapons whose reach is not a melee one: with one of them in hand or in use, nothing is claimed (DT-37). */
    private static final Set<Material> RANGED_WEAPONS = EnumSet.of(Material.BOW, Material.CROSSBOW, Material.TRIDENT);
    /** Mobs with a native ranged attack whatever they hold ({@link #nativelyRanged}). */
    private static final List<Class<? extends Mob>> NATIVELY_RANGED = List.of(RangedEntity.class, Spellcaster.class,
            Blaze.class, Breeze.class, Creeper.class, EnderDragon.class, Ghast.class, Guardian.class, Shulker.class,
            Warden.class);

    private VanillaMobReach() {
    }

    /**
     * The reach of a mob without a profile of ThePredict and outside MythicMobs.
     *
     * @param mob the target
     * @return its vanilla melee reach, or {@link TargetReach#UNKNOWN}
     */
    static TargetReach of(Mob mob) {
        if (nativelyRanged(mob)) {
            return TargetReach.UNKNOWN;
        }
        var weapon = mob.hasActiveItem() ? mob.getActiveItem() : mainHand(mob);
        if (weapon != null && RANGED_WEAPONS.contains(weapon.getType())) {
            return TargetReach.UNKNOWN;
        }
        var range = weapon == null ? null : weapon.getData(DataComponentTypes.ATTACK_RANGE);
        return range == null ? TargetReach.vanillaMelee(false, 0.0f, 0.0f, 0.0f)
                : TargetReach.vanillaMelee(true, range.maxReach(), range.minReach(), range.mobFactor());
    }

    /**
     * Tells whether a mob has a native ranged attack whatever it holds (M17.4, DT-37): Paper's {@code RangedEntity}
     * (skeletons, drowned, illusioner, llama, piglin, pillager, snow golem, witch, wither), a spellcaster (evoker,
     * illusioner), and the blaze, breeze, creeper, ender dragon, ghast, guardian, shulker and warden. Their reach is
     * not a melee one, so nothing is claimed.
     *
     * @param mob a mob without a profile of ThePredict
     * @return {@code true} when its reach is unknown
     */
    static boolean nativelyRanged(Mob mob) {
        for (var kind : NATIVELY_RANGED) {
            if (kind.isInstance(mob)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack mainHand(Mob mob) {
        var equipment = mob.getEquipment();
        return equipment == null ? null : equipment.getItemInMainHand();
    }
}
