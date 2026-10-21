package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalIntentTest {
    static TacticalIntent intent(List<TacticId> alternatives, Map<Situation, TacticId> contingencies, long expiresAtNanos) {
        return new TacticalIntent(MOB, tactic("press_attack"), Movement.APPROACH, Optional.of(new ChoiceId("melee")),
                0.8, alternatives, contingencies, ControlLevel.JEV, 10, expiresAtNanos, false);
    }

    @Test
    void withSkillConsumedReturnsACopyAndLeavesTheOriginal() {
        var original = intent(List.of(), Map.of(), 1_000);
        var consumed = original.withSkillConsumed();
        assertFalse(original.skillConsumed());
        assertTrue(consumed.skillConsumed());
        assertEquals(original.tactic(), consumed.tactic());
        assertEquals(original.expiresAtNanos(), consumed.expiresAtNanos());
    }

    @Test
    void expiresExactlyAtTheDeadline() {
        var intent = intent(List.of(), Map.of(), 1_000);
        assertFalse(intent.expired(999));
        assertTrue(intent.expired(1_000));
    }

    @Test
    void expiryToleratesNanoTimeOverflow() {
        var intent = intent(List.of(), Map.of(), Long.MIN_VALUE + 5);
        assertFalse(intent.expired(Long.MAX_VALUE - 5));
        assertTrue(intent.expired(Long.MIN_VALUE + 5));
        assertTrue(intent.expired(Long.MIN_VALUE + 6));
    }

    @Test
    void toViewUsesTheSessionGeneration() {
        var view = intent(List.of(), Map.of(), 1_000).toView();
        assertEquals(MOB.generation(), view.generation());
        assertEquals(ControlLevel.JEV, view.level());
        assertEquals(Movement.APPROACH, view.movement());
        assertEquals(0.8f, view.confidence());
        assertTrue(view.controls(999));
    }

    @Test
    void alternativesExcludeTheTacticRepetitionsAndExcess() {
        assertThrows(IllegalArgumentException.class, () -> intent(List.of(tactic("press_attack")), Map.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> intent(List.of(tactic("keep_range"), tactic("keep_range")), Map.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> intent(List.of(tactic("aaa"), tactic("bbb"), tactic("ccc"),
                tactic("ddd")), Map.of(), 0));
    }

    @Test
    void contingenciesNeverUseNow() {
        assertThrows(IllegalArgumentException.class, () -> intent(List.of(), Map.of(Situation.NOW, tactic("hold_ground")), 0));
        assertEquals(1, intent(List.of(), Map.of(Situation.IF_TARGET_IN_MELEE, tactic("hold_ground")), 0)
                .contingencies().size());
    }

    @Test
    void collectionsAreCopied() {
        var alternatives = new ArrayList<>(List.of(tactic("keep_range")));
        var intent = intent(alternatives, Map.of(), 0);
        alternatives.add(tactic("hold_ground"));
        assertEquals(List.of(tactic("keep_range")), intent.alternatives());
    }
}
