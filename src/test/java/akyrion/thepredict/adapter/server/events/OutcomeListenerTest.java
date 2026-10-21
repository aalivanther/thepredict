package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.engine.GoldenWindows;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The listener of M16.8 for damage taken and deaths: O(1) filters and the health fraction computed in the adapter. */
class OutcomeListenerTest {
    private static final DamageSource SOURCE = (DamageSource) Proxy.newProxyInstance(
            DamageSource.class.getClassLoader(), new Class<?>[] {DamageSource.class}, (proxy, method, arguments) -> null);

    private final ContextListenerFixtures fixture = new ContextListenerFixtures();
    private final OutcomeListener listener = new OutcomeListener(fixture.outcomes, fixture.events, fixture.thread,
            () -> 7L);

    /**
     * A living entity that answers only its identity and its world, the key of the O(1) participant index that a death
     * of any entity is checked against since M17.8: reading anything else fails the test.
     */
    private LivingEntity living(UUID id) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> fixture.world;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void theFractionIsTheFinalDamageOverTheMaximumHealthOfTheVictim() {
        assertEquals(0.25, OutcomeListener.fraction(5, 20), 1e-12);
        assertEquals(0.0, OutcomeListener.fraction(5, 0), "without the attribute");
        assertEquals(0.0, OutcomeListener.fraction(0, 20));
        assertEquals(0.0, OutcomeListener.fraction(-1, 20));
    }

    @Test
    void theFractionIsClampedToTheWholeHealthOfTheVictim() {
        assertEquals(1.0, OutcomeListener.fraction(20, 20), 1e-12);
        assertEquals(1.0, OutcomeListener.fraction(500, 20), "an overkill counts one whole health");
        assertEquals(1.0, OutcomeListener.fraction(Double.MAX_VALUE, Double.MIN_VALUE));
        assertEquals(0.0, OutcomeListener.fraction(Double.NaN, 20));
    }

    @SuppressWarnings("removal")
    @Test
    void damageAndDeathsOfEntitiesWithoutAWindowOrEncounterAreIgnoredReadingOnlyTheirIdentityAndWorld() {
        var stranger = living(new UUID(5, 5));
        listener.onDamage(new EntityDamageEvent(stranger, EntityDamageEvent.DamageCause.FALL, SOURCE, 4.0));
        listener.onDeath(new EntityDeathEvent(stranger, SOURCE, List.of()));
        assertTrue(fixture.outcomes.metrics().byLevel().isEmpty());
        assertTrue(fixture.nextTick.isEmpty(), "nothing waits for the next tick");
    }

    @Test
    void theDeathOfAnEntityParticipantReachesTheWindowsThatTargetIt() {
        var zombie = new UUID(0x2AL, 1);
        fixture.encounterAgainstEntity(1, zombie);
        GoldenWindows.open(fixture.outcomes, fixture.sessions, LifecycleFixtures.id(1), zombie, 0);
        listener.onDeath(new EntityDeathEvent(fixture.zombie(zombie), SOURCE, List.of()));
        assertTrue(GoldenWindows.state(fixture.sessions, LifecycleFixtures.id(1), 5).contains("targetDied=true"));
    }

    @Test
    void aMobThatIsAMemberAndARivalsParticipantClosesItsWindowAndEndsTheRivalsTarget() {
        var first = LifecycleFixtures.id(1);
        var second = LifecycleFixtures.id(2);
        fixture.encounterAgainstEntity(1, second);
        fixture.encounterAgainstEntity(2, first);
        GoldenWindows.open(fixture.outcomes, fixture.sessions, first, second, 0);
        GoldenWindows.open(fixture.outcomes, fixture.sessions, second, first, 0);
        listener.onDeath(new EntityDeathEvent(fixture.zombie(second), SOURCE, List.of()));
        assertTrue(GoldenWindows.state(fixture.sessions, first, 5).contains("targetDied=true"));
        assertEquals("window closed", GoldenWindows.state(fixture.sessions, second, 5));
        assertEquals(1, fixture.outcomes.metrics().byLevel().getFirst().windows());
    }
}
