package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.adapter.server.context.EntityDoubles.FakeEntity;
import akyrion.thepredict.adapter.server.context.EntityDoubles.FakeItem;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.TargetCue;
import com.destroystokyo.paper.entity.RangedEntity;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.AttackRange;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.ChargedProjectiles;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reads of a participant that is not a player (M17.4) with doubles of entities, equipment and items with data
 * components: the fields that only exist for players are neutral and produce no cue, each item-based row of the fact
 * matrix gives its cue, and the reach of a target comes from the profile of a rival of ThePredict, the vanilla melee
 * check of a mob with the {@code ATTACK_RANGE} of its weapon (DT-37), or nothing. A player is read on the server
 * (script of M07 and M17).
 */
class PaperEntityReadsTest {
    private final CaptureDoubles doubles = new CaptureDoubles();
    private final EntityDoubles.Lookup lookup = new EntityDoubles.Lookup();
    private final PaperEntityReads reads = new PaperEntityReads(lookup);

    private FakeEntity entity(int value, Class<? extends LivingEntity> kind, EntityType type) {
        return new FakeEntity(new UUID(0x4EADL, value), kind, type, SpawnCategory.MONSTER, doubles.world, 0, 0);
    }

    private Set<TargetCue> cues(FakeEntity entity) {
        return TargetCueReader.cues(reads.participant(entity.entity), true, false);
    }

    private static AttackRange attackRange(float min, float max, float mobFactor) {
        return EntityDoubles.component(AttackRange.class, Map.of("minReach", min, "maxReach", max, "mobFactor",
                mobFactor));
    }

    @Test
    void anEntityWithoutItemsReadsNeutralValuesThatGiveNoPlayerCue() {
        var zombie = entity(1, Zombie.class, EntityType.ZOMBIE);
        var reading = reads.participant(zombie.entity);
        assertEquals(ParticipantKind.ENTITY, reading.kind());
        assertEquals(1.0f, reading.attackCooldownRatio());
        assertEquals(0, reading.pearlCooldownTicks());
        assertFalse(reading.shieldOnCooldown());
        assertFalse(reading.blocking());
        assertFalse(reading.activeBlocksAttacks());
        assertEquals(Set.of(), cues(zombie));
    }

    @Test
    void aBowInUseIsDrawnAndFullyDrawnAfterItsTicks() {
        var skeleton = entity(1, Skeleton.class, EntityType.SKELETON);
        skeleton.active = new FakeItem(Material.BOW);
        skeleton.usedTicks = 5;
        assertEquals(Set.of(TargetCue.DRAWING_BOW), cues(skeleton));
        skeleton.usedTicks = TargetCueReader.FULL_DRAW_TICKS;
        assertEquals(Set.of(TargetCue.DRAWING_BOW, TargetCue.BOW_FULLY_DRAWN), cues(skeleton));
    }

    @Test
    void aLoadedCrossbowInEitherHandIsLoaded() {
        var pillager = entity(1, Zombie.class, EntityType.PILLAGER);
        var charged = EntityDoubles.component(ChargedProjectiles.class, Map.of("projectiles",
                List.of(new FakeItem(Material.ARROW))));
        pillager.offHand = new FakeItem(Material.CROSSBOW).with(DataComponentTypes.CHARGED_PROJECTILES, charged);
        assertEquals(Set.of(TargetCue.CROSSBOW_LOADED), cues(pillager));
        var empty = EntityDoubles.component(ChargedProjectiles.class, Map.of("projectiles", List.of()));
        pillager.offHand = new FakeItem(Material.CROSSBOW).with(DataComponentTypes.CHARGED_PROJECTILES, empty);
        assertEquals(Set.of(), cues(pillager));
    }

    @Test
    void anActiveConsumableIsConsumingAndATotemInHandIsHeld() {
        var witch = entity(1, Zombie.class, EntityType.WITCH);
        witch.active = new FakeItem(Material.POTION).with(DataComponentTypes.CONSUMABLE, Boolean.TRUE);
        assertEquals(Set.of(TargetCue.CONSUMING), cues(witch));
        witch.active = null;
        witch.offHand = new FakeItem(Material.TOTEM_OF_UNDYING).with(DataComponentTypes.DEATH_PROTECTION, Boolean.TRUE);
        assertEquals(Set.of(TargetCue.HOLDS_TOTEM), cues(witch));
    }

    @Test
    void aLongReachWeaponAndAMaceDiveComeFromTheMainHand() {
        var zombie = entity(1, Zombie.class, EntityType.ZOMBIE);
        zombie.mainHand = new FakeItem(Material.IRON_SPEAR).with(DataComponentTypes.KINETIC_WEAPON, Boolean.TRUE);
        assertEquals(Set.of(TargetCue.LONG_REACH_WEAPON), cues(zombie));
        zombie.mainHand = new FakeItem(Material.MACE);
        zombie.fallDistance = TargetCueReader.MACE_DIVE_FALL_BLOCKS + 0.5f;
        assertEquals(Set.of(TargetCue.MACE_DIVE), cues(zombie));
        zombie.fallDistance = 1.0f;
        assertEquals(Set.of(), cues(zombie));
    }

    @Test
    void anActiveShieldRaisesAndBlocksAfterTheRoundedDelay() {
        var zombie = entity(1, Zombie.class, EntityType.ZOMBIE);
        var blocks = EntityDoubles.component(BlocksAttacks.class, Map.of("blockDelaySeconds", 0.25f));
        zombie.active = new FakeItem(Material.SHIELD).with(DataComponentTypes.BLOCKS_ATTACKS, blocks);
        zombie.usedTicks = 4;
        assertEquals(Set.of(TargetCue.RAISING_SHIELD), cues(zombie));
        zombie.usedTicks = 5;
        assertEquals(Set.of(TargetCue.BLOCKING), cues(zombie));
    }

    @Test
    void theVanillaMeleeReachOfAMobIsTheDefaultOrThatOfTheAttackRangeOfItsWeapon() {
        assertEquals(Math.sqrt(2.04f) - 0.6f, TargetReach.MOB_DEFAULT_ATTACK_REACH_BLOCKS);
        assertEquals(0.8283, TargetReach.MOB_DEFAULT_ATTACK_REACH_BLOCKS, 1e-4);
        var zombie = entity(1, Zombie.class, EntityType.ZOMBIE);
        assertEquals(new TargetReach(TargetReach.Kind.VANILLA_MELEE, TargetReach.MOB_DEFAULT_ATTACK_REACH_BLOCKS, 0.0),
                reads.targetReach(zombie.entity));
        zombie.mainHand = new FakeItem(Material.IRON_SPEAR).with(DataComponentTypes.ATTACK_RANGE,
                attackRange(2.0f, 4.5f, 0.5f));
        assertEquals(new TargetReach(TargetReach.Kind.VANILLA_MELEE, 4.5f * 0.5f, 2.0f * 0.5f),
                reads.targetReach(zombie.entity), "maxReach and minReach times mobFactor, in float");
        zombie.active = new FakeItem(Material.WOODEN_SPEAR).with(DataComponentTypes.ATTACK_RANGE,
                attackRange(0.0f, 3.0f, 1.0f));
        assertEquals(3.0, reads.targetReach(zombie.entity).maxBlocks(), "the item in use comes first, as vanilla");
    }

    @Test
    void nothingIsClaimedForARangedWeaponANativeRangedMobAMythicMobOrANonMob() {
        var zombie = entity(1, Zombie.class, EntityType.ZOMBIE);
        for (var weapon : List.of(Material.BOW, Material.CROSSBOW, Material.TRIDENT)) {
            zombie.mainHand = new FakeItem(weapon);
            assertEquals(TargetReach.UNKNOWN, reads.targetReach(zombie.entity), weapon.name());
        }
        for (var ranged : List.of(entity(2, Skeleton.class, EntityType.SKELETON), entity(3, Drowned.class,
                EntityType.DROWNED), entity(4, Witch.class, EntityType.WITCH), entity(5, Blaze.class, EntityType.BLAZE),
                entity(6, Ghast.class, EntityType.GHAST), entity(7, Evoker.class, EntityType.EVOKER))) {
            assertEquals(TargetReach.UNKNOWN, reads.targetReach(ranged.entity), ranged.type.name());
        }
        assertTrue(VanillaMobReach.nativelyRanged((RangedEntity) entity(8, Drowned.class, EntityType.DROWNED)
                .entity));
        var mythic = entity(9, Zombie.class, EntityType.ZOMBIE);
        lookup.types.put(mythic.id, "SomeMythicMob");
        assertEquals(TargetReach.UNKNOWN, reads.targetReach(mythic.entity), "its skills are unknown");
        var stand = entity(10, ArmorStand.class, EntityType.ARMOR_STAND);
        assertEquals(TargetReach.UNKNOWN, reads.targetReach(stand.entity), "not a mob");
    }

    @Test
    void theReachOfARivalOfThePredictComesFromItsProfile() {
        var rival = entity(2, Zombie.class, EntityType.ZOMBIE);
        lookup.bound.put(rival.id, new BoundProfile(ContextFixtures.declaredProfile(), Map.of(ContextFixtures.STRIKE,
                CaptureDoubles.skill("Strike"), ContextFixtures.BOLT, CaptureDoubles.skill("Bolt"),
                ContextFixtures.SLAM, CaptureDoubles.skill("Slam"), ContextFixtures.GUARD,
                CaptureDoubles.skill("Guard")), Map.of(), "ZOMBIE", false));
        assertEquals(TargetReach.profile(12.0), reads.targetReach(rival.entity));
    }
}
