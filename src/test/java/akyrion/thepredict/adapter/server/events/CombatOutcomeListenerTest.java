package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.ParticipantAlias;
import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static akyrion.thepredict.adapter.server.events.ContextListenerFixtures.arrow;
import static akyrion.thepredict.adapter.server.events.ContextListenerFixtures.playerId;
import static akyrion.thepredict.adapter.server.events.ContextListenerFixtures.snowball;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatOutcomeListenerTest {
    private static final DamageSource SOURCE = (DamageSource) Proxy.newProxyInstance(
            DamageSource.class.getClassLoader(), new Class<?>[] {DamageSource.class}, (proxy, method, arguments) -> null);

    private final ContextListenerFixtures fixture = new ContextListenerFixtures();

    /** The only event constructor without the deprecated {@code DamageModifier} is marked for removal. */
    @SuppressWarnings("removal")
    private static EntityDamageByEntityEvent hit(Entity damager, Entity victim) {
        return new EntityDamageByEntityEvent(damager, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, SOURCE, 3.0);
    }

    /** A member mob whose current target is {@code target}, or none. */
    private static Mob mob(UUID id, LivingEntity target) {
        return (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(), new Class<?>[] {Mob.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getTarget" -> target;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void aMemberHitOnAParticipantIsRecordedWithoutPendingContext() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(1)), fixture.player(playerId(1), false)));
        var event = encounter.recentEvents().getFirst();
        assertEquals(EventType.MELEE_HIT, event.type());
        assertEquals(Optional.of(new ParticipantAlias(1)), event.participant());
        assertTrue(event.mob().isPresent());
        assertFalse(encounter.contextPending());
    }

    @Test
    void aBlockedMemberHitIsRecordedAndMarksPendingContext() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(1)), fixture.player(playerId(1), true)));
        assertEquals(EventType.MELEE_BLOCKED, encounter.recentEvents().getFirst().type());
        assertTrue(encounter.contextPending());
    }

    @Test
    void aParticipantArrowOnAMemberIsARangedHitThatMarksPendingContext() {
        var encounter = fixture.encounter(1, playerId(1));
        var shooter = fixture.player(playerId(1), false);
        fixture.outcome.onDamage(hit(arrow(shooter), LifecycleFixtures.entity(LifecycleFixtures.id(1))));
        assertEquals(EventType.RANGED_HIT_ON_MOB, encounter.recentEvents().getFirst().type());
        assertTrue(encounter.contextPending());
    }

    @Test
    void hitsBetweenStrangersDoNothing() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(9)), fixture.player(playerId(1), true)));
        fixture.outcome.onDamage(hit(arrow(fixture.player(playerId(1), false)), LifecycleFixtures.entity(LifecycleFixtures.id(9))));
        assertTrue(encounter.recentEvents().isEmpty());
        assertFalse(encounter.contextPending());
    }

    @Test
    void aMissedArrowIsRecordedWithoutPendingContextAndOtherProjectilesAreIgnored() {
        var encounter = fixture.encounter(1, playerId(1));
        var shooter = fixture.player(playerId(1), false);
        fixture.outcome.onProjectileHit(new ProjectileHitEvent(snowball(shooter), null, null, null));
        assertTrue(encounter.recentEvents().isEmpty());
        fixture.outcome.onProjectileHit(new ProjectileHitEvent(arrow(shooter), null, null, null));
        assertEquals(EventType.RANGED_MISS, encounter.recentEvents().getFirst().type());
        assertFalse(encounter.contextPending());
    }

    @Test
    void aPotionOfAPlayerOutsideEveryEncounterIsNeverRead() {
        var encounter = fixture.encounter(1, playerId(1));
        var stranger = fixture.player(playerId(2), false);
        var potion = (ThrownPotion) Proxy.newProxyInstance(ThrownPotion.class.getClassLoader(),
                new Class<?>[] {ThrownPotion.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getShooter" -> stranger;
                    case "hashCode" -> 3;
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        fixture.outcome.onPotionSplash(new PotionSplashEvent(potion, null, null, null, Map.of()));
        assertTrue(encounter.recentEvents().isEmpty());
    }

    @Test
    void aPathOfAMemberIsCountedOnly() {
        var encounter = fixture.encounter(1, playerId(1));
        var mob = LifecycleFixtures.entity(LifecycleFixtures.id(1));
        fixture.outcome.onPathfind(new EntityPathfindEvent(mob, new Location(fixture.world, 0, 64, 0), null));
        fixture.outcome.onPathfind(new EntityPathfindEvent(LifecycleFixtures.entity(LifecycleFixtures.id(9)),
                new Location(fixture.world, 0, 64, 0), null));
        assertEquals(1, fixture.events.metrics().memberPathfinds());
        assertFalse(encounter.contextPending());
    }

    @Test
    void aBlockedMemberHitGivesTheBlockingPlayerThreatAndAnUnblockedOneDoesNot() {
        fixture.encounter(1, playerId(1));
        var member = LifecycleFixtures.entity(LifecycleFixtures.id(1));
        fixture.outcome.onDamage(hit(member, fixture.player(playerId(1), false)));
        assertTrue(fixture.threats.isEmpty());
        fixture.outcome.onDamage(hit(member, fixture.player(playerId(1), true)));
        assertEquals(List.of("1 1.0"), fixture.threats);
    }

    @Test
    void anArrowOfAParticipantWhoIsNotTheTargetAddsHarassFromTheFinalDamage() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.encounters.addParticipant(encounter.id(), playerId(2));
        var target = fixture.player(playerId(1), false);
        var member = mob(LifecycleFixtures.id(1), target);
        fixture.outcome.onDamage(hit(arrow(fixture.player(playerId(2), false)), member));
        fixture.outcome.onDamage(hit(arrow(target), member));
        assertEquals(List.of("2 3.0"), fixture.threats, "the target's own hits only gain the native threat");
        assertEquals(2, encounter.recentEvents().size(), "both hits stay in the history");
    }

    @Test
    void hitsBetweenStrangersNeverReachTheThreatTable() {
        fixture.encounter(1, playerId(1));
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(9)), fixture.player(playerId(1), true)));
        fixture.outcome.onDamage(hit(arrow(fixture.player(playerId(1), false)), mob(LifecycleFixtures.id(9), null)));
        fixture.outcome.onDamage(hit(arrow(fixture.player(playerId(5), false)), mob(LifecycleFixtures.id(1), null)));
        assertTrue(fixture.threats.isEmpty());
        assertEquals(0L, fixture.threat.metrics().limited());
    }

    @Test
    void aHitSeenOffTheServerThreadChangesThreatOnlyOnTheServerThreadNextTick() {
        fixture.encounter(1, playerId(1));
        fixture.primaryThread = false;
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(1)), fixture.player(playerId(1), true)));
        assertTrue(fixture.threats.isEmpty());
        fixture.primaryThread = true;
        fixture.runNextTick();
        assertEquals(List.of("1 1.0"), fixture.threats);
    }

    @Test
    void theDamageOfAHitCountsForTheDamagerTheShooterOrTheCasterMythicMobsReports() {
        var mobId = LifecycleFixtures.id(1);
        assertEquals(mobId, CombatOutcomeListener.attackerId(LifecycleFixtures.entity(mobId)));
        var shooter = fixture.player(playerId(1), false);
        assertEquals(playerId(1), CombatOutcomeListener.attackerId(arrow(shooter)));
        assertEquals(playerId(1), CombatOutcomeListener.attackerId(snowball(shooter)));
    }

    /**
     * A member without an open window still hands its hit to the engine, which feeds the fight clock and the
     * motivated cast (arreglos-engine-FO §3.6); no outcome window opens or counts, and a stranger's hit is refused in
     * constant time before any read.
     */
    @Test
    void aHitOfAMemberWithoutAnOutcomeWindowCountsNoOutcomeAndAStrangerReadsNothing() {
        fixture.encounter(1, playerId(1));
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(1)), fixture.player(playerId(1),
                false)));
        assertTrue(fixture.outcomes.metrics().byLevel().isEmpty());
        assertFalse(fixture.outcomes.tracks(LifecycleFixtures.id(1)));
        var reads = new java.util.ArrayList<String>();
        var victim = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class}, (proxy, method, arguments) -> {
                    reads.add(method.getName());
                    return switch (method.getName()) {
                        case "getUniqueId" -> playerId(7);
                        case "getWorld" -> fixture.world;
                        case "hashCode" -> 7;
                        case "equals" -> proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(9)), victim));
        assertFalse(reads.contains("getAttribute"));
    }

    private static final UUID ZOMBIE = new UUID(0x2AL, 1);

    @Test
    void aMemberHitOnAnEntityParticipantIsRecordedUnderItsEntityAlias() {
        var encounter = fixture.encounterAgainstEntity(1, ZOMBIE);
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(1)), fixture.zombie(ZOMBIE)));
        var event = encounter.recentEvents().getFirst();
        assertEquals(EventType.MELEE_HIT, event.type());
        assertEquals("entity_1", event.participant().orElseThrow().text());
        assertFalse(encounter.contextPending());
        assertTrue(fixture.threats.isEmpty());
    }

    @Test
    void aBlockedHitOnAnEntityParticipantIsBlockedAndGivesItThreat() {
        var encounter = fixture.encounterAgainstEntity(1, ZOMBIE);
        fixture.blockingEntities.add(ZOMBIE);
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(1)), fixture.zombie(ZOMBIE)));
        assertEquals(EventType.MELEE_BLOCKED, encounter.recentEvents().getFirst().type());
        assertTrue(encounter.contextPending());
        assertEquals(List.of(ZOMBIE.getLeastSignificantBits() + " 1.0"), fixture.threats);
    }

    @Test
    void theProjectileOfAnEntityParticipantIsARangedHitAndOneOfAStrangerDoesNothing() {
        var encounter = fixture.encounterAgainstEntity(1, ZOMBIE);
        var member = mob(LifecycleFixtures.id(1), null);
        fixture.outcome.onDamage(hit(arrow(fixture.zombie(new UUID(0x2AL, 2))), member));
        assertTrue(encounter.recentEvents().isEmpty(), "an entity outside the encounter causes no work");
        assertFalse(encounter.contextPending());
        fixture.outcome.onDamage(hit(arrow(fixture.zombie(ZOMBIE)), member));
        assertEquals(EventType.RANGED_HIT_ON_MOB, encounter.recentEvents().getFirst().type());
        assertTrue(encounter.contextPending());
        assertEquals(List.of(ZOMBIE.getLeastSignificantBits() + " 3.0"), fixture.threats);
        fixture.outcome.onProjectileHit(new ProjectileHitEvent(arrow(fixture.zombie(ZOMBIE)), null, null, null));
        assertEquals(EventType.RANGED_MISS, encounter.recentEvents().get(1).type());
    }

    @Test
    void aHitOnAnEntityThatIsNoParticipantDoesNothing() {
        var encounter = fixture.encounter(1, playerId(1));
        fixture.outcome.onDamage(hit(LifecycleFixtures.entity(LifecycleFixtures.id(1)), fixture.zombie(ZOMBIE)));
        assertTrue(encounter.recentEvents().isEmpty());
        assertFalse(encounter.contextPending());
    }
}
