package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.engine.IntentBoard;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.lumine.mythic.core.config.MythicLineConfigImpl;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static akyrion.thepredict.adapter.server.goals.GoalFixtures.KEYS;
import static akyrion.thepredict.adapter.server.goals.GoalFixtures.MOVEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalSetInstallerTest {
    private final FakeMobGoals goals = new FakeMobGoals();
    private final FakeAiHandler ai = new FakeAiHandler(goals);
    private final GoalSetInstaller installer = new GoalSetInstaller(ai, goals, KEYS, new IntentBoard(),
            GoalFixtures.clock(new AtomicLong(), new AtomicLong()));
    private final Mob mob = GoalFixtures.mob(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), EntityType.ZOMBIE,
            null, () -> null, new Location(null, 0, 64, 0));

    @Test
    void selectorLinesSplitIntoPriorityAndGoalAtTheFirstSpaceLikeMythicMobs() {
        var plan = new GoalLineGenerator().generate(GoalFixtures.profile(MOVEMENT, List.of(), Movement.values()),
                new BaseAiSource(BaseAiResolver.GENERIC, BaseAiOrigin.GENERIC), 32, 1);
        var escaped = GoalSetInstaller.selectorLines(plan.mythicLines());
        assertEquals(plan.mythicLines().size(), escaped.size());
        for (var index = 0; index < escaped.size(); index++) {
            var parts = escaped.get(index).split(" ");
            assertEquals(2, parts.length, escaped.get(index));
            assertEquals(plan.mythicLines().get(index).split(" ")[0], parts[0]);
        }
        assertEquals("3 fleeConditional{distance=10;speed=1.2;mc=[<&csp><&da><&csp>predictormove{m=RETREAT}<&csp>true<&csp>];"
                + "targetconditions=[<&csp><&da><&csp>isTarget<&csp>true<&csp>]}", escaped.get(2));
        assertEquals("0 clear", escaped.getFirst());
    }

    @Test
    void escapingIsIdempotentForLinesMythicMobsAlreadyPrepared() {
        var once = GoalSetInstaller.selectorLines(List.of("4 moveWithinConditional{range=9;mc=[ - predictormove{m=HOLD} true ]}"));
        assertEquals(once, GoalSetInstaller.selectorLines(once));
    }

    @Test
    void installReplacesPaperGoalsWithoutDuplicatesAndPutsThemAtTheirPriorities() {
        goals.addGoal(mob, 3, GoalFixtures.goal(KEYS.hold(), GoalType.MOVE));
        var plan = new GoalLineGenerator().generate(GoalFixtures.profile(MOVEMENT, List.of(), Movement.FLANK),
                new BaseAiSource(BaseAiResolver.GENERIC, BaseAiOrigin.GENERIC), 32, 5);
        assertEquals(0, installer.install(mob, plan, 2, MOVEMENT));
        assertEquals(0, installer.install(mob, plan, 2, MOVEMENT));
        assertEquals(1, goals.count(KEYS.hold()));
        assertEquals(1, goals.count(KEYS.flank()));
        assertEquals(1, goals.count(KEYS.owner()));
        assertEquals(8, goals.priorities.get(goals.goals.indexOf(goals.getGoal(mob, KEYS.hold()))));
        assertEquals(new InstallDecision.Marker.Owned(5, 2, plan.goalCount()), installer.marker(mob));
    }

    @Test
    void markerReadingDistinguishesMissingForeignAndOwned() {
        assertInstanceOf(InstallDecision.Marker.Missing.class, installer.marker(mob));
        goals.addGoal(mob, 20, GoalFixtures.goal(KEYS.owner(), GoalType.LOOK));
        assertInstanceOf(InstallDecision.Marker.Foreign.class, installer.marker(mob));
        installer.removePaperGoals(mob);
        goals.addGoal(mob, 20, new OwnerMarkerGoal(KEYS.owner(), 9, 1, 4));
        assertEquals(new InstallDecision.Marker.Owned(9, 1, 4), installer.marker(mob));
        installer.removePaperGoals(mob);
        installer.markRefused(mob, 9, 1, "install incomplete: 1 MythicMobs goals missing");
        assertEquals(new InstallDecision.Marker.Refused(9, 1, "install incomplete: 1 MythicMobs goals missing"),
                installer.marker(mob));
    }

    @Test
    void onlyAGoalOtherThanHoldRunningWithMoveCountsAsMovement() {
        assertFalse(installer.movementGoalRunning(mob));
        goals.running.add(GoalFixtures.goal(KEYS.hold(), GoalType.MOVE));
        goals.running.add(GoalFixtures.goal(GoalFixtures.vanillaKey("look_at_target"), GoalType.LOOK));
        assertFalse(installer.movementGoalRunning(mob));
        goals.running.add(GoalFixtures.goal(GoalFixtures.vanillaKey("flee_conditional"), GoalType.MOVE));
        assertTrue(installer.movementGoalRunning(mob));
    }

    @Test
    void aSetCutShortByMythicMobsReportsTheMissingGoalsAndGetsNoPaperGoals() {
        var plan = new GoalLineGenerator().generate(GoalFixtures.profile(MOVEMENT, List.of(), Movement.FLANK,
                Movement.RETREAT), new BaseAiSource(BaseAiResolver.GENERIC, BaseAiOrigin.GENERIC), 32, 5);
        ai.brokenGoal = "fleeconditional";
        assertEquals(plan.mythicLines().size() - 2, installer.install(mob, plan, 0, MOVEMENT));
        assertEquals(1, installer.mythicGoalCount(mob));
        assertInstanceOf(InstallDecision.Marker.Missing.class, installer.marker(mob));
        assertEquals(0, goals.count(KEYS.hold()) + goals.count(KEYS.flank()));
    }

    @Test
    void mythicGoalCountIgnoresTargetGoalsAndThePaperGoals() {
        goals.addGoal(mob, 1, GoalFixtures.goal(GoalFixtures.vanillaKey("float"), GoalType.JUMP));
        goals.addGoal(mob, 2, GoalFixtures.goal(GoalFixtures.vanillaKey("hurt_by"), GoalType.TARGET));
        goals.addGoal(mob, 3, GoalFixtures.goal(KEYS.micro(), GoalType.MOVE));
        goals.addGoal(mob, 8, GoalFixtures.goal(KEYS.hold(), GoalType.MOVE));
        goals.addGoal(mob, 9, GoalFixtures.goal(KEYS.owner(), GoalType.LOOK));
        assertEquals(1, installer.mythicGoalCount(mob));
        installer.removePaperGoals(mob);
        assertEquals(0, goals.count(KEYS.micro()), "the micro goal is removed with the other three");
        assertEquals(2, goals.goals.size());
    }

    @Test
    void aFullTickLayerPlanInstallsCompletelyWithTheMicroGoalAsAPaperGoal() {
        var plan = new GoalLineGenerator().generate(GoalFixtures.profile(MOVEMENT, List.of(), Movement.APPROACH,
                Movement.RETREAT), new BaseAiSource(BaseAiResolver.GENERIC, BaseAiOrigin.GENERIC), 32, 5,
                akyrion.thepredict.domain.TickLayerMode.FULL);
        assertEquals(0, installer.install(mob, plan, 2, MOVEMENT));
        assertEquals(1, goals.count(KEYS.micro()));
        assertEquals(3, goals.priorities.get(goals.goals.indexOf(goals.getGoal(mob, KEYS.micro()))));
        assertEquals(plan.mythicLines().size() - 1, installer.mythicGoalCount(mob));
        assertEquals(new InstallDecision.Marker.Owned(5, 2, plan.goalCount()), installer.marker(mob));
        assertEquals(0, installer.install(mob, plan, 2, MOVEMENT), "a second install keeps one micro goal");
        assertEquals(1, goals.count(KEYS.micro()));
    }

    @Test
    void everyGeneratedLineSurvivesTheRealMythicMobsRoundTrip() {
        var lines = new ArrayList<String>();
        var baseAi = new BaseAiSource(List.of("1 moveWithinConditional{range=-2;speed=1;mc=[ - predictormove{m=HOLD} true "
                + "- healthpercent{p=<50%} true ]}", "2 randomStroll{speed=-0.5}"), BaseAiOrigin.PROFILE);
        var arena = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.of(24));
        for (var movement : List.of(MOVEMENT, arena)) {
            lines.addAll(new GoalLineGenerator().generate(GoalFixtures.profile(movement, List.of(), Movement.values()),
                    baseAi, 32, 0).mythicLines());
        }
        for (var line : lines) {
            assertRoundTrip(line);
        }
    }

    private static void assertRoundTrip(String line) {
        var escaped = GoalSetInstaller.selectorLines(List.of(line)).getFirst();
        assertEquals(escaped, GoalSetInstaller.selectorLines(List.of(escaped)).getFirst(), line);
        var numbered = Character.isDigit(line.charAt(0));
        var parts = escaped.split(" ");
        assertEquals(numbered ? 2 : 1, parts.length, escaped);
        var goal = numbered ? line.substring(line.indexOf(' ') + 1) : line;
        var escapedGoal = parts[parts.length - 1];
        assertEquals(goal, MythicLineConfigImpl.parseBlock(escapedGoal), line);
        var config = new MythicLineConfigImpl(escapedGoal);
        var brace = goal.indexOf('{');
        assertEquals(brace < 0 ? goal : goal.substring(0, brace), config.getKey(), line);
        var conditions = goal.indexOf("mc=[");
        if (conditions >= 0) {
            assertEquals(bracketed(goal, conditions + 3), config.getString(new String[] {"mc", "mobconditions"}), line);
        }
    }

    private static String bracketed(String text, int open) {
        var depth = 0;
        for (var index = open; index < text.length(); index++) {
            depth += text.charAt(index) == '[' ? 1 : text.charAt(index) == ']' ? -1 : 0;
            if (depth == 0) {
                return text.substring(open, index + 1);
            }
        }
        throw new AssertionError("unbalanced list in " + text);
    }

    @Test
    void aLineInvalidForTheSpeciesIsSkippedAndTheRestStillCountsAsIncomplete() {
        var plan = new GoalLineGenerator().generate(GoalFixtures.profile(MOVEMENT, List.of(), Movement.RETREAT),
                new BaseAiSource(BaseAiResolver.GENERIC, BaseAiOrigin.GENERIC), 32, 5);
        ai.invalidGoals.add("float");
        assertEquals(1, installer.install(mob, plan, 0, MOVEMENT));
        assertEquals(plan.mythicLines().size() - 2, installer.mythicGoalCount(mob));
        assertEquals(1, ai.loggedErrors.size());
    }
}
