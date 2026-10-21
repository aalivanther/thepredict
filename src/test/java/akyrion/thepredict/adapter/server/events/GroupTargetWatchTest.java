package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.engine.Agenda;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.EncounterRegistry;
import akyrion.thepredict.engine.EngineFixtures;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.SessionMetrics;
import akyrion.thepredict.engine.SessionRegistry;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTargetEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vanilla alert listener counts four reasons and does not cancel (M25.11). */
class GroupTargetWatchTest {
    @Test
    void fourReasonsIncrementAndOtherReasonsDoNot() throws Exception {
        assertThrows(NullPointerException.class, () -> new GroupTargetWatch(null, null, null));
        var method = GroupTargetWatch.class.getMethod("onTarget",
                org.bukkit.event.entity.EntityTargetLivingEntityEvent.class);
        var handler = method.getAnnotation(org.bukkit.event.EventHandler.class);
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
        var metrics = new DecisionMetrics(() -> 0L);
        var clock = new EngineFixtures.TestClock();
        var agenda = new Agenda();
        var encounters = new EncounterRegistry(agenda, clock, new SessionMetrics());
        var sessions = new SessionRegistry(new IntentBoard(), encounters, agenda);
        var watch = new GroupTargetWatch(sessions, encounters, metrics);
        var world = new UUID(1, 2);
        var stranger = new UUID(8, 9);
        watch.count(stranger, new UUID(5, 6), world, EntityTargetEvent.TargetReason.FOLLOW_LEADER);
        watch.count(stranger, new UUID(5, 6), world, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);
        assertEquals(0, metrics.total(DecisionMetrics.GroupCounter.NATIVE_GROUP_ALERTS));
        var actor = new UUID(3, 4);
        sessions.open(actor, EngineFixtures.profile("Guardian"));
        assertTrue(sessions.find(new MobKey(actor, 0)).isPresent() || sessions.find(actor).isPresent());
        var reasons = new EntityTargetEvent.TargetReason[] {
                EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY,
                EntityTargetEvent.TargetReason.REINFORCEMENT_TARGET,
                EntityTargetEvent.TargetReason.FOLLOW_LEADER,
                EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET
        };
        for (var index = 0; index < reasons.length; index++) {
            assertEquals(index, GroupTargetWatch.bucket(reasons[index]));
            watch.count(actor, null, world, reasons[index]);
        }
        assertEquals(4, metrics.total(DecisionMetrics.GroupCounter.NATIVE_GROUP_ALERTS));
        for (var index = 0; index < reasons.length; index++) {
            assertEquals(1, metrics.nativeGroupAlert(index));
        }
        assertEquals(-1, GroupTargetWatch.bucket(EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY));
    }
}
