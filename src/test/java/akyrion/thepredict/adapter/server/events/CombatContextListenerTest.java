package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.ParticipantAlias;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static akyrion.thepredict.adapter.server.events.ContextListenerFixtures.playerId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatContextListenerTest {
    private final ContextListenerFixtures fixture = new ContextListenerFixtures();

    private PlayerTeleportEvent teleport(PlayerTeleportEvent.TeleportCause cause) {
        return new PlayerTeleportEvent(fixture.player(playerId(1), false), new Location(fixture.world, 0, 64, 0),
                new Location(fixture.world, 8, 64, 0), cause);
    }

    @Test
    void aDisabledShieldOfAParticipantIsRecordedAndMarksPendingContext() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.context.onShieldDisable(new PlayerShieldDisableEvent(fixture.player(playerId(1), false),
                LifecycleFixtures.entity(LifecycleFixtures.id(1)), 100));
        var event = encounter.recentEvents().getFirst();
        assertEquals(EventType.SHIELD_DISABLED, event.type());
        assertEquals(Optional.of(new ParticipantAlias(1)), event.participant());
        assertTrue(encounter.contextPending());
    }

    @Test
    void aPlayerOutsideEveryEncounterCausesNoWork() {
        var encounter = fixture.encounter(1, playerId(1));
        var stranger = fixture.player(playerId(2), false);
        fixture.context.onShieldDisable(new PlayerShieldDisableEvent(stranger, stranger, 100));
        fixture.context.onResurrect(new EntityResurrectEvent(stranger, EquipmentSlot.HAND));
        assertTrue(encounter.recentEvents().isEmpty());
        assertFalse(encounter.contextPending());
        assertEquals(0, fixture.events.metrics().pendingMarks());
        assertTrue(fixture.nextTick.isEmpty());
    }

    @Test
    void anEnderPearlTeleportIsRecordedAndOtherCausesAreIgnored() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.context.onTeleport(teleport(PlayerTeleportEvent.TeleportCause.COMMAND));
        assertTrue(encounter.recentEvents().isEmpty());
        fixture.context.onTeleport(teleport(PlayerTeleportEvent.TeleportCause.ENDER_PEARL));
        assertEquals(EventType.PEARL_THROWN, encounter.recentEvents().getFirst().type());
        assertTrue(encounter.contextPending());
    }

    @Test
    void aTotemUsedByAParticipantIsRecordedAndOneOfAnEntityOutsideEveryEncounterIsNot() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.context.onResurrect(new EntityResurrectEvent(fixture.player(playerId(1), false), EquipmentSlot.HAND));
        assertEquals(EventType.TOTEM_USED, encounter.recentEvents().getFirst().type());
        var mob = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getWorld" -> fixture.world;
                    case "getUniqueId" -> new UUID(0x2AL, 9);
                    default -> null;
                });
        fixture.context.onResurrect(new EntityResurrectEvent(mob, EquipmentSlot.OFF_HAND));
        assertEquals(1, encounter.recentEvents().size());
    }

    @Test
    void anAttackOnAMemberMarksItsEncounter() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.context.onPreAttack(new PrePlayerAttackEntityEvent(fixture.player(playerId(3), false),
                LifecycleFixtures.entity(LifecycleFixtures.id(1)), true));
        assertTrue(encounter.contextPending());
        assertTrue(encounter.recentEvents().isEmpty());
    }

    @Test
    void anEventOffTheServerThreadIsHandledOnTheNextTick() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.primaryThread = false;
        fixture.context.onShieldDisable(new PlayerShieldDisableEvent(fixture.player(playerId(1), false),
                LifecycleFixtures.entity(LifecycleFixtures.id(1)), 100));
        assertTrue(encounter.recentEvents().isEmpty());
        assertEquals(1, fixture.nextTick.size());
        fixture.runNextTick();
        assertEquals(EventType.SHIELD_DISABLED, encounter.recentEvents().getFirst().type());
    }

    private static final UUID ZOMBIE = new UUID(0x2AL, 1);

    @Test
    void theEntityEventsOfAnEntityParticipantMarkOrRecordLikeThoseOfAPlayer() {
        var encounter = fixture.encounterAgainstEntity(1, ZOMBIE);
        fixture.context.onResurrect(new EntityResurrectEvent(fixture.zombie(ZOMBIE), EquipmentSlot.HAND));
        assertEquals(EventType.TOTEM_USED, encounter.recentEvents().getFirst().type());
        assertEquals("entity_1", encounter.recentEvents().getFirst().participant().orElseThrow().text());
        assertTrue(encounter.contextPending());
        var marks = fixture.events.metrics().pendingMarks();
        fixture.context.onShootBow(new EntityShootBowEvent(fixture.zombie(ZOMBIE), null, null, null, EquipmentSlot.HAND,
                1.0f, false));
        fixture.context.onLoadCrossbow(new EntityLoadCrossbowEvent(fixture.zombie(ZOMBIE), null, EquipmentSlot.HAND));
        assertEquals(marks + 2, fixture.events.metrics().pendingMarks());
    }

    @Test
    void theDeathOrRemovalOfAnEntityParticipantMarksItsEncounterAndAPlayersDeathKeepsThePathOf001() {
        var encounter = fixture.encounterAgainstEntity(1, ZOMBIE);
        fixture.context.onEntityDeath(new EntityDeathEvent(fixture.zombie(ZOMBIE), SOURCE, List.of()));
        assertTrue(encounter.contextPending());
        assertEquals(1, fixture.events.metrics().pendingMarks());
        fixture.context.onEntityRemove(new EntityRemoveEvent(fixture.zombie(ZOMBIE), EntityRemoveEvent.Cause.DESPAWN));
        assertEquals(2, fixture.events.metrics().pendingMarks());
        fixture.context.onEntityDeath(new EntityDeathEvent(fixture.zombie(new UUID(0x2AL, 2)), SOURCE, List.of()));
        assertEquals(2, fixture.events.metrics().pendingMarks(), "an entity outside every encounter does no work");
        var players = fixture.encounter(2, playerId(1));
        fixture.context.onEntityDeath(new EntityDeathEvent(fixture.player(playerId(1), false), SOURCE, List.of()));
        assertFalse(players.contextPending(), "a player who dies is dropped by the next capture, as in 0.0.1");
    }

    private static final DamageSource SOURCE = (DamageSource) Proxy.newProxyInstance(
            DamageSource.class.getClassLoader(), new Class<?>[] {DamageSource.class}, (proxy, method, arguments) -> null);
}
