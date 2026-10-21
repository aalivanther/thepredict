package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.vocabulary.PredictorMoveCondition;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

import static akyrion.thepredict.adapter.server.goals.GoalFixtures.MOVEMENT;
import static akyrion.thepredict.adapter.server.goals.GoalFixtures.profile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalLineGeneratorTest {
    private static final GoalLineGenerator GENERATOR = new GoalLineGenerator();
    private static final BaseAiSource GENERIC = new BaseAiSource(BaseAiResolver.GENERIC, BaseAiOrigin.GENERIC);
    private static final String RETREAT = "3 fleeConditional{distance=10;speed=1.2;mc=[ - predictormove{m=RETREAT} true ];"
            + "targetconditions=[ - isTarget true ]}";
    private static final String KEEP_INNER = "4 fleeConditional{distance=5;speed=1.15;"
            + "mc=[ - predictormove{m=KEEP_DISTANCE} true ];targetconditions=[ - isTarget true ]}";
    private static final String KEEP_OUTER =
            "5 moveWithinConditional{range=9;speed=1.15;mc=[ - predictormove{m=KEEP_DISTANCE} true ]}";
    private static final String APPROACH = "6 moveTowardsTargetConditional{speed=1.15;minrange=2.5;maxrange=32;"
            + "mc=[ - predictormove{m=APPROACH} true ]}";

    @Test
    void bundledExampleProfileProducesThePlanSetWithTheSpeedsOfTheModuleTable(@TempDir Path directory) throws IOException {
        try (var example = getClass().getResourceAsStream("/examples/AI/ThePredictGuardian.yml")) {
            Files.copy(Objects.requireNonNull(example, "bundled example"), directory.resolve("ThePredictGuardian.yml"));
        }
        var guardian = new ProfileLoader().load(directory).profiles().get("ThePredictGuardian");
        var baseAi = new BaseAiResolver().resolve(guardian, List.of());
        var plan = GENERATOR.generate(guardian, baseAi, 32, 7);
        assertEquals(BaseAiOrigin.PROFILE, baseAi.origin());
        assertEquals(List.of("0 clear", "1 float", "2 goToSpawnLocation{maxrange=24;speed=1.15}", RETREAT, KEEP_INNER,
                KEEP_OUTER, APPROACH, "11 meleeAttack", "12 randomStroll", "13 lookAtTarget"), plan.mythicLines());
        assertEquals(List.of(new PaperGoalSlot(PaperGoalKind.FLANK, 7), new PaperGoalSlot(PaperGoalKind.HOLD, 8),
                new PaperGoalSlot(PaperGoalKind.OWNER, 14)), plan.paperGoals());
        assertEquals(7, plan.revision());
        assertEquals(12, plan.goalCount());
    }

    @Test
    void theSameInputAlwaysProducesTheSameSetWhateverTheTacticOrder() {
        var first = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.FLANK, Movement.APPROACH, Movement.RETREAT),
                GENERIC, 32, 3);
        var again = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.FLANK, Movement.APPROACH, Movement.RETREAT),
                GENERIC, 32, 3);
        var reordered = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.RETREAT, Movement.FLANK, Movement.APPROACH),
                GENERIC, 32, 3);
        assertEquals(first, again);
        assertEquals(first, reordered);
    }

    @Test
    void everyCombinationOfMovementsGeneratesExactlyTheGoalsOfTheUsedMovements() {
        var movements = List.of(Movement.APPROACH, Movement.KEEP_DISTANCE, Movement.RETREAT, Movement.HOLD, Movement.FLANK);
        for (var mask = 0; mask < 1 << movements.size(); mask++) {
            var used = new ArrayList<Movement>(List.of(Movement.NONE));
            for (var bit = 0; bit < movements.size(); bit++) {
                if ((mask & 1 << bit) != 0) {
                    used.add(movements.get(bit));
                }
            }
            var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), used.toArray(Movement[]::new)), GENERIC, 32, 0);
            var expected = new ArrayList<>(List.of("0 clear", "1 float"));
            if (used.contains(Movement.RETREAT)) {
                expected.add(RETREAT);
            }
            if (used.contains(Movement.KEEP_DISTANCE)) {
                expected.addAll(List.of(KEEP_INNER, KEEP_OUTER));
            }
            if (used.contains(Movement.APPROACH)) {
                expected.add(APPROACH);
            }
            expected.addAll(List.of("10 meleeAttack", "11 randomStroll", "12 lookAtTarget"));
            assertEquals(expected, plan.mythicLines(), used.toString());
            var kinds = plan.paperGoals().stream().map(PaperGoalSlot::kind).toList();
            assertEquals(used.contains(Movement.FLANK), kinds.contains(PaperGoalKind.FLANK), used.toString());
            assertEquals(used.size() > 1, kinds.contains(PaperGoalKind.HOLD), used.toString());
            assertEquals(new PaperGoalSlot(PaperGoalKind.OWNER, 13), plan.paperGoals().getLast(), used.toString());
        }
    }

    @Test
    void aProfileWithoutArenaHasNoSpawnGoalAndOneWithArenaUsesTheApproachSpeed() {
        var withoutArena = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.APPROACH), GENERIC, 32, 0);
        assertTrue(withoutArena.mythicLines().stream().noneMatch(line -> line.contains("goToSpawnLocation")));
        var arena = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.3, OptionalDouble.of(9.5));
        var withArena = GENERATOR.generate(profile(arena, List.of(), Movement.NONE), GENERIC, 32, 0);
        assertEquals("2 goToSpawnLocation{maxrange=9.5;speed=1.3}", withArena.mythicLines().get(2));
    }

    @Test
    void aHoldOnlyProfileGetsHoldAndTheBaseAiButNoTacticalMythicGoal() {
        var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.HOLD), GENERIC, 32, 0);
        assertEquals(List.of("0 clear", "1 float", "10 meleeAttack", "11 randomStroll", "12 lookAtTarget"), plan.mythicLines());
        assertEquals(List.of(new PaperGoalSlot(PaperGoalKind.HOLD, 8), new PaperGoalSlot(PaperGoalKind.OWNER, 13)),
                plan.paperGoals());
    }

    @Test
    void aProfileWhoseTacticsOnlyUseNoneGetsNoHoldGoal() {
        var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.NONE), GENERIC, 32, 0);
        assertEquals(List.of(new PaperGoalSlot(PaperGoalKind.OWNER, 13)), plan.paperGoals());
        assertEquals(5, plan.goalCount());
    }

    @Test
    void aFlankProfileGetsTheFlankGoalBeforeHold() {
        var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.FLANK), GENERIC, 32, 0);
        assertEquals(List.of(new PaperGoalSlot(PaperGoalKind.FLANK, 7), new PaperGoalSlot(PaperGoalKind.HOLD, 8),
                new PaperGoalSlot(PaperGoalKind.OWNER, 13)), plan.paperGoals());
    }

    @Test
    void baseAiWithLookAtTargetGetsNoSecondOneAndTheMarkerFollowsIt() {
        var baseAi = new BaseAiSource(List.of("1 meleeAttack", "2 LOOKATTARGET"), BaseAiOrigin.PROFILE);
        var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.HOLD), baseAi, 32, 0);
        assertEquals(List.of("0 clear", "1 float", "11 meleeAttack", "12 LOOKATTARGET"), plan.mythicLines());
        assertEquals(new PaperGoalSlot(PaperGoalKind.OWNER, 13), plan.paperGoals().getLast());
    }

    @Test
    void baseAiPrioritiesAreShiftedAboveNineAndKeepTheirRelativeOrder() {
        var baseAi = new BaseAiSource(List.of("5 meleeAttack", "randomStroll", "0 float", "leapAtTarget"),
                BaseAiOrigin.MOB_CONFIG);
        var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.HOLD), baseAi, 32, 0);
        var base = plan.mythicLines().subList(2, plan.mythicLines().size());
        assertEquals(List.of("15 meleeAttack", "12 randomStroll", "10 float", "14 leapAtTarget", "16 lookAtTarget"), base);
        assertEquals(new PaperGoalSlot(PaperGoalKind.OWNER, 17), plan.paperGoals().getLast());
    }

    @Test
    void noLineOrPaperGoalRepeatsAPriority() {
        var plan = GENERATOR.generate(profile(new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.of(24)),
                List.of(), Movement.values()), GENERIC, 32, 0);
        var priorities = new HashSet<Integer>();
        plan.mythicLines().forEach(line -> assertTrue(priorities.add(Integer.parseInt(line.split(" ")[0])), line));
        plan.paperGoals().forEach(slot -> assertTrue(priorities.add(slot.priority()), slot.toString()));
    }

    @Test
    void numbersUseADotAtMostTwoDecimalsAndNoExponentInAnyLocale() {
        var original = Locale.getDefault();
        try {
            for (var locale : List.of(Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("ar-EG"), Locale.ROOT)) {
                Locale.setDefault(locale);
                assertEquals("24", GoalLineGenerator.number(24.0), locale.toString());
                assertEquals("1.16", GoalLineGenerator.number(1.155), locale.toString());
                assertEquals("0.5", GoalLineGenerator.number(0.5), locale.toString());
                assertEquals("0", GoalLineGenerator.number(0.004), locale.toString());
                assertEquals("12345678.9", GoalLineGenerator.number(12_345_678.9), locale.toString());
                assertEquals("0.01", GoalLineGenerator.number(0.005), locale.toString());
            }
            Locale.setDefault(Locale.GERMANY);
            var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.APPROACH), GENERIC, 35.25, 0);
            assertTrue(plan.mythicLines().contains("6 moveTowardsTargetConditional{speed=1.15;minrange=2.5;maxrange=35.25;"
                    + "mc=[ - predictormove{m=APPROACH} true ]}"), plan.mythicLines().toString());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void theGateUsesTheVocabularyConstantAndEveryMovementName() {
        var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.values()), GENERIC, 32, 0);
        var gated = plan.mythicLines().stream().filter(line -> line.contains(PredictorMoveCondition.NAME + "{m=")).toList();
        assertEquals(4, gated.size());
        var named = EnumSet.noneOf(Movement.class);
        gated.forEach(line -> named.add(Movement.valueOf(line.substring(line.indexOf("{m=") + 3, line.indexOf("} true")))));
        assertEquals(EnumSet.of(Movement.APPROACH, Movement.KEEP_DISTANCE, Movement.RETREAT), named);
    }

    @Test
    void theFleeLinesOnlyAvoidTheAiTargetThroughTheNativeIsTargetCondition() {
        var plan = GENERATOR.generate(profile(MOVEMENT, List.of(), Movement.RETREAT, Movement.KEEP_DISTANCE), GENERIC,
                32, 0);
        var flee = plan.mythicLines().stream().filter(line -> line.contains("fleeConditional")).toList();
        assertEquals(2, flee.size());
        flee.forEach(line -> assertTrue(line.endsWith(";targetconditions=[ - isTarget true ]}"), line));
        assertTrue(plan.mythicLines().stream().filter(line -> !line.contains("fleeConditional"))
                .noneMatch(line -> line.contains("isTarget")), "only a flee goal picks whom to avoid");
    }

    @Test
    void theFullTickLayerAddsOnlyAPaperMicroGoalAndShiftsTheTacticalPrioritiesUpByOne() {
        var profile = profile(MOVEMENT, List.of(), Movement.values());
        var plan = GENERATOR.generate(profile, GENERIC, 32, 4, akyrion.thepredict.domain.TickLayerMode.FULL);
        assertTrue(plan.mythicLines().stream().noneMatch(line -> line.contains("thepredict")),
                "MythicMobs skips a goal it does not know, so the micro goal is never one of its lines");
        assertEquals(List.of("0 clear", "1 float", "4 " + RETREAT.substring(2), "5 " + KEEP_INNER.substring(2),
                "6 " + KEEP_OUTER.substring(2), "7 " + APPROACH.substring(2), "10 meleeAttack", "11 randomStroll",
                "12 lookAtTarget"), plan.mythicLines());
        assertEquals(List.of(new PaperGoalSlot(PaperGoalKind.MICRO, 3), new PaperGoalSlot(PaperGoalKind.FLANK, 8),
                new PaperGoalSlot(PaperGoalKind.HOLD, 9), new PaperGoalSlot(PaperGoalKind.OWNER, 13)), plan.paperGoals());
        var plain = GENERATOR.generate(profile, GENERIC, 32, 4);
        assertEquals(plain.goalCount() + 1, plan.goalCount());
        assertEquals(plain, GENERATOR.generate(profile, GENERIC, 32, 4, akyrion.thepredict.domain.TickLayerMode.REACTIONS));
    }

    @Test
    void invalidFollowRangeOrRevisionIsRejected() {
        var profile = profile(MOVEMENT, List.of(), Movement.APPROACH);
        for (var range : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> GENERATOR.generate(profile, GENERIC, range, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> GENERATOR.generate(profile, GENERIC, 32, -1));
        assertFalse(GENERATOR.generate(profile, GENERIC, Double.MIN_VALUE, 0).mythicLines().isEmpty());
    }
}
