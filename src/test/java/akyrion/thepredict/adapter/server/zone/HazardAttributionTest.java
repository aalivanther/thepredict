package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.HazardOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.HazardLedger;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A member push stays attributable for the configured window (M24.10). */
class HazardAttributionTest {
    @Test
    void aPushAtThirtyTicksAttributesAndAPushAtSeventyDoesNot() {
        var ledger = new HazardLedger();
        var victim = UUID.randomUUID();
        ledger.note(victim, UUID.randomUUID(), DisplacementKind.KNOCKBACK, 0L);
        var recent = ledger.attribute(victim, 4, true, 30L, 60, false).orElseThrow();
        assertEquals(HazardOutcome.PUSHED_INTO_HAZARD, recent.outcome());
        assertTrue(recent.died());
        assertEquals(30, recent.ticks());
        var edge = ledger.attribute(victim, 4, true, 60L, 60, false).orElseThrow();
        assertEquals(HazardOutcome.PUSHED_INTO_HAZARD, edge.outcome());
        assertTrue(ledger.attribute(victim, 4, true, 70L, 60, false).isEmpty());
    }

    @Test
    void aKnockbackWhoseHitterIsNotAMemberLeavesTheBookUnchanged() {
        var ledger = new HazardLedger();
        var member = UUID.randomUUID();
        var hazards = new HazardAttribution(ledger, DecisionTap.NONE,
                id -> id.equals(member) ? Optional.of(new MobKey(member, 1)) : Optional.empty(), () -> 0L, 60);
        var victim = living(UUID.randomUUID());
        hazards.onKnockback(new EntityKnockbackByEntityEvent(victim, living(UUID.randomUUID()),
                EntityKnockbackEvent.Cause.ENTITY_ATTACK, 1f, new Vector(1, 0, 0)));
        assertTrue(ledger.attribute(victim.getUniqueId(), 4, false, 30L, 60, false).isEmpty());
    }

    @Test
    void anAcceptedThrowIsAttributedWithinSixtyTicks() {
        var ledger = new HazardLedger();
        var hazards = new HazardAttribution(ledger, DecisionTap.NONE, id -> Optional.empty(), () -> 0L, 60);
        var target = UUID.randomUUID();
        hazards.acceptedCast(new MobKey(UUID.randomUUID(), 1), target, DisplacementKind.STRONG_PUSH, 0L);
        var recent = ledger.attribute(target, 4, false, 60L, 60, false).orElseThrow();
        assertEquals(HazardOutcome.PUSHED_INTO_HAZARD, recent.outcome());
        assertEquals(DisplacementKind.STRONG_PUSH, recent.kind());
        assertTrue(ledger.attribute(target, 4, false, 70L, 60, false).isEmpty());
    }

    private static LivingEntity living(UUID id) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "entity";
                    default -> plain(method.getReturnType());
                });
    }

    private static Object plain(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
