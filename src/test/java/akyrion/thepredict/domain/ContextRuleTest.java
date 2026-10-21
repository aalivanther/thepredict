package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.context;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextRuleTest {
    @Test
    void directTacticMustBeAllowed() {
        var error = assertThrows(IllegalArgumentException.class, () -> new ContextRule(context("low_health"),
                List.of("healthpercent{p=<30%}"), 10, Set.of(tactic("retreat_now")), Optional.of(tactic("hold_ground"))));
        assertTrue(error.getMessage().contains("directTactic"));
    }

    @Test
    void allowedTacticsMustNotBeEmpty() {
        var error = assertThrows(IllegalArgumentException.class, () -> new ContextRule(context("low_health"),
                List.of("healthpercent{p=<30%}"), 10, Set.of(), Optional.empty()));
        assertTrue(error.getMessage().contains("allowedTactics"));
    }

    @Test
    void blankConditionLinesAreRejected() {
        var error = assertThrows(IllegalArgumentException.class, () -> new ContextRule(context("low_health"),
                List.of(" "), 10, Set.of(tactic("retreat_now")), Optional.empty()));
        assertTrue(error.getMessage().contains("nativeConditions"));
    }

    @Test
    void conditionsAreCopiedAndKeptOpaque() {
        var lines = new ArrayList<>(List.of("healthpercent{p=<30%} true"));
        var rule = new ContextRule(context("low_health"), lines, 10, Set.of(tactic("retreat_now")), Optional.empty());
        lines.add("inwater");
        assertEquals(List.of("healthpercent{p=<30%} true"), rule.nativeConditions());
    }

    @Test
    void theFourShortcutContextsAreRecognized() {
        for (var name : List.of("idle", "attacking_phase", "tracking_enemy", "low_health_phase")) {
            assertTrue(new ContextRule(context(name), List.of(), 10, Set.of(tactic("hold_ground")), Optional.empty())
                    .isShortcut(), name);
        }
        assertEquals(4, ContextRule.SHORTCUTS.size());
        assertFalse(new ContextRule(context("low_health"), List.of("healthpercent{p=<30%}"), 10, Set.of(tactic("hold_ground")),
                Optional.empty()).isShortcut());
    }
}
