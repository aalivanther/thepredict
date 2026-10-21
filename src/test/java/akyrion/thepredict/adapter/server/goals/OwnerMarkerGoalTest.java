package akyrion.thepredict.adapter.server.goals;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnerMarkerGoalTest {
    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("thepredict", "owner"));

    @Test
    void markerNeverActivatesAndDeclaresOnlyLook() {
        var marker = new OwnerMarkerGoal(KEY, 1, 0, 0);
        assertFalse(marker.shouldActivate());
        assertEquals(EnumSet.of(GoalType.LOOK), marker.getTypes());
        assertEquals(EnumSet.of(GoalType.LOOK), OwnerMarkerGoal.refused(KEY, 1, 0, "unsupported").getTypes());
        assertEquals("thepredict:owner", marker.getKey().getNamespacedKey().toString());
    }

    @Test
    void generatedMarkerDeclaresLookAndKeepsRevisionGenerationAndCount() {
        var marker = new OwnerMarkerGoal(KEY, 42, 3, 12);
        assertFalse(marker.shouldActivate());
        assertEquals(EnumSet.of(GoalType.LOOK), marker.getTypes());
        assertEquals(42, marker.revision());
        assertEquals(3, marker.generation());
        assertEquals(12, marker.goalCount());
    }

    @Test
    void negativeGoalCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OwnerMarkerGoal(KEY, 1, 0, -1));
        assertEquals(0, new OwnerMarkerGoal(KEY, 1, 0, 0).goalCount());
    }

    @Test
    void refusalMarkerKeepsItsReasonNeverRunsAndNeedsAReason() {
        var marker = OwnerMarkerGoal.refused(KEY, 4, 1, "install failed: IllegalStateException");
        assertEquals("install failed: IllegalStateException", marker.refusal());
        assertEquals(4, marker.revision());
        assertFalse(marker.shouldActivate());
        assertEquals("", new OwnerMarkerGoal(KEY, 4, 1, 3).refusal());
        assertThrows(IllegalArgumentException.class, () -> OwnerMarkerGoal.refused(KEY, 4, 1, " "));
    }
}
