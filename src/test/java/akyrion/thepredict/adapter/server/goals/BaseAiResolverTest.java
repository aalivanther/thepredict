package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.Movement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static akyrion.thepredict.adapter.server.goals.GoalFixtures.MOVEMENT;
import static akyrion.thepredict.adapter.server.goals.GoalFixtures.mythicMob;
import static akyrion.thepredict.adapter.server.goals.GoalFixtures.profile;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseAiResolverTest {
    private static final BaseAiResolver RESOLVER = new BaseAiResolver();

    @Test
    void theProfileBaseAiWinsOverTheMobGoalSelectors() {
        var source = RESOLVER.resolve(profile(MOVEMENT, List.of("1 meleeAttack", "2 randomStroll"), Movement.HOLD),
                mythicMob(List.of("0 clear", "1 leapAtTarget")));
        assertEquals(new BaseAiSource(List.of("1 meleeAttack", "2 randomStroll"), BaseAiOrigin.PROFILE), source);
    }

    @Test
    void theMobGoalSelectorsAreUsedWithoutAnyFormOfClear() {
        var source = RESOLVER.resolve(profile(MOVEMENT, List.of(), Movement.HOLD),
                mythicMob(List.of("0 clear", "clear", "3 CLEAR", "Clear{}", "1 meleeAttack", "  randomStroll  ")));
        assertEquals(new BaseAiSource(List.of("1 meleeAttack", "6 randomStroll"), BaseAiOrigin.MOB_CONFIG), source);
    }

    @Test
    void clearIsAlsoDroppedFromTheProfileBecauseItWouldWipeTheTacticalGoals() {
        var source = RESOLVER.resolve(profile(MOVEMENT, List.of("0 clear", "1 meleeAttack"), Movement.HOLD), List.of());
        assertEquals(new BaseAiSource(List.of("1 meleeAttack"), BaseAiOrigin.PROFILE), source);
    }

    @Test
    void genericMeleeAndStrollWhenNeitherSourceHasAUsableLine() {
        var generic = new BaseAiSource(BaseAiResolver.GENERIC, BaseAiOrigin.GENERIC);
        assertEquals(generic, RESOLVER.resolve(profile(MOVEMENT, List.of(), Movement.HOLD), mythicMob(List.of())));
        assertEquals(generic, RESOLVER.resolve(profile(MOVEMENT, List.of(" ", "0 clear"), Movement.HOLD),
                List.of("0 clear", "", "12")));
        assertEquals(List.of("0 meleeAttack", "1 randomStroll"), BaseAiResolver.GENERIC);
    }

    @Test
    void aMobTypeWithoutGoalSelectorListFallsBackToGeneric() {
        assertEquals(BaseAiOrigin.GENERIC, RESOLVER.resolve(profile(MOVEMENT, List.of(), Movement.HOLD),
                mythicMob(null)).origin());
    }

    @Test
    void aLineWithoutNumberKeepsItsNativePositionAfterClearIsDropped() {
        var source = RESOLVER.resolve(profile(MOVEMENT, List.of(), Movement.HOLD),
                mythicMob(List.of("0 clear", "5 meleeAttack", "randomStroll")));
        assertEquals(new BaseAiSource(List.of("5 meleeAttack", "3 randomStroll"), BaseAiOrigin.MOB_CONFIG), source);
        var plan = new GoalLineGenerator().generate(profile(MOVEMENT, List.of(), Movement.HOLD), source, 32, 0);
        assertEquals(List.of("15 meleeAttack", "13 randomStroll", "16 lookAtTarget"),
                plan.mythicLines().subList(2, plan.mythicLines().size()));
    }
}
