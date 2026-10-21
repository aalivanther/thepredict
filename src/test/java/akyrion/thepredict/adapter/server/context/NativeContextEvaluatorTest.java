package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.adapter.server.context.ContextFixtures.context;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.profile;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.rule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeContextEvaluatorTest {
    private static final ShortcutFacts CALM = new ShortcutFacts(HealthBand.HEALTHY, Optional.of(DistanceBand.RANGED));

    private final List<String> warnings = new ArrayList<>();
    private long tick;
    private final NativeContextEvaluator evaluator = new NativeContextEvaluator(warnings::add, () -> tick);

    private Set<ContextId> active(List<ContextRule> contexts, Map<String, Boolean> truth, ShortcutFacts facts) {
        var converted = new HashMap<ContextId, List<String>>();
        contexts.stream().filter(context -> !context.isShortcut())
                .forEach(context -> converted.put(context.id(), context.nativeConditions()));
        return evaluator.active(profile(contexts), id -> converted.getOrDefault(id, List.of()), line -> {
            if (line.startsWith("broken")) {
                throw new IllegalStateException("secret " + line);
            }
            return truth.getOrDefault(line, false);
        }, facts);
    }

    @Test
    void aNativeContextIsActiveOnlyWhenEveryConditionHolds() {
        var contexts = List.of(rule("wounded", List.of("healthpercent{p=<30%}", "onground"), 90, "hold"));
        assertEquals(Set.of(context("wounded")), active(contexts, Map.of("healthpercent{p=<30%}", true, "onground", true), CALM));
        assertEquals(Set.of(), active(contexts, Map.of("healthpercent{p=<30%}", true, "onground", false), CALM));
    }

    @Test
    void aContextWithoutConvertedConditionsNeverHolds() {
        var contexts = List.of(rule("wounded", List.of("healthpercent{p=<30%}"), 90, "hold"));
        var result = evaluator.active(profile(contexts), id -> List.<String>of(), line -> true, CALM);
        assertEquals(Set.of(), result);
    }

    @Test
    void eachContextIsEvaluatedOncePerCapture() {
        var calls = new HashMap<String, Integer>();
        var contexts = new ArrayList<ContextRule>();
        for (var index = 0; index < NativeContextEvaluator.MAX_CONTEXTS; index++) {
            contexts.add(rule("native_" + index, List.of("line_" + index), index, "hold"));
        }
        var result = evaluator.active(profile(contexts), id -> List.of("line_" + id.value().substring(7)), line -> {
            calls.merge(line, 1, Integer::sum);
            return true;
        }, CALM);
        assertEquals(NativeContextEvaluator.MAX_CONTEXTS, result.size());
        assertTrue(calls.values().stream().allMatch(count -> count == 1), calls.toString());
    }

    @Test
    void aFailingConditionDisablesOnlyItsContextAndIsLoggedWithALimitAndWithoutItsMessage() {
        var contexts = List.of(rule("broken_one", List.of("broken{x=1}"), 50, "hold"),
                rule("fine", List.of("fine"), 40, "hold"));
        assertEquals(Set.of(context("fine")), active(contexts, Map.of("fine", true), CALM));
        tick = NativeContextEvaluator.LOG_INTERVAL_TICKS - 1;
        active(contexts, Map.of("fine", true), CALM);
        tick = NativeContextEvaluator.LOG_INTERVAL_TICKS;
        active(contexts, Map.of("fine", true), CALM);
        assertEquals(3, evaluator.failures());
        assertEquals(2, warnings.size());
        assertTrue(warnings.getFirst().contains("'broken_one'") && warnings.getFirst().contains("IllegalStateException"));
        assertTrue(warnings.get(1).contains("(1 similar failures not shown)"), warnings.get(1));
        assertTrue(warnings.stream().noneMatch(line -> line.contains("secret")));
    }

    @Test
    void shortcutsFollowTheBandsWithoutNativeConditions() {
        var contexts = List.of(rule("idle", List.of(), 10, "hold"), rule("attacking_phase", List.of(), 20, "hold"),
                rule("tracking_enemy", List.of(), 30, "hold"), rule("low_health_phase", List.of(), 40, "hold"));
        assertEquals(Set.of(context("idle")), active(contexts, Map.of(),
                new ShortcutFacts(HealthBand.HEALTHY, Optional.empty())));
        assertEquals(Set.of(context("tracking_enemy")), active(contexts, Map.of(),
                new ShortcutFacts(HealthBand.WOUNDED, Optional.of(DistanceBand.BEYOND))));
        assertEquals(Set.of(context("attacking_phase"), context("low_health_phase")), active(contexts, Map.of(),
                new ShortcutFacts(HealthBand.CRITICAL, Optional.of(DistanceBand.MELEE))));
    }

    @Test
    void shortcutsMatchTheCasesOfTheLegacyPlanner() {
        var lowHealthWhileAttacking = new ShortcutFacts(HealthBand.CRITICAL, Optional.of(DistanceBand.MELEE));
        assertTrue(lowHealthWhileAttacking.holds(context("low_health_phase")));
        assertTrue(lowHealthWhileAttacking.holds(context("attacking_phase")));
        var tracking = new ShortcutFacts(HealthBand.HEALTHY, Optional.of(DistanceBand.RANGED));
        assertTrue(tracking.holds(context("tracking_enemy")));
        assertTrue(!tracking.holds(context("attacking_phase")) && !tracking.holds(context("idle")));
    }

    @Test
    void everyShortcutHasAMeaningAndOtherNamesNeverHold() {
        var facts = List.of(new ShortcutFacts(HealthBand.CRITICAL, Optional.of(DistanceBand.MELEE)),
                new ShortcutFacts(HealthBand.HEALTHY, Optional.of(DistanceBand.RANGED)),
                new ShortcutFacts(HealthBand.HEALTHY, Optional.empty()));
        for (var shortcut : ContextRule.SHORTCUTS) {
            assertTrue(facts.stream().anyMatch(value -> value.holds(shortcut)), shortcut.value());
        }
        assertTrue(facts.stream().noneMatch(value -> value.holds(context("wounded"))));
    }
}
