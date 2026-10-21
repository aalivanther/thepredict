package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.IntentBoard;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static akyrion.thepredict.adapter.server.goals.GoalFixtures.KEYS;
import static akyrion.thepredict.adapter.server.goals.GoalFixtures.MOVEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalOwnershipTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final TacticalProfile PROFILE = GoalFixtures.profile(MOVEMENT, List.of("1 meleeAttack"),
            Movement.APPROACH, Movement.FLANK, Movement.HOLD);

    private final FakeMobGoals goals = new FakeMobGoals();
    private final FakeAiHandler ai = new FakeAiHandler(goals);
    private final AtomicLong ticks = new AtomicLong(1_000);
    private final AtomicLong revision = new AtomicLong(4);
    private final AtomicBoolean serverThread = new AtomicBoolean(true);
    private final AtomicInteger restores = new AtomicInteger();
    private final List<String> warnings = new ArrayList<>();
    private final GoalOwnership ownership = new GoalOwnership(id -> Optional.empty(),
            new GoalSetInstaller(ai, goals, KEYS, new IntentBoard(), GoalFixtures.clock(ticks, new AtomicLong())),
            new GoalLineGenerator(), new BaseAiResolver(), revision::get, serverThread::get,
            GoalFixtures.clock(ticks, new AtomicLong()), warnings::add);

    private GoalSetInstaller installer() {
        return new GoalSetInstaller(ai, goals, KEYS, new IntentBoard(), GoalFixtures.clock(ticks, new AtomicLong()));
    }

    private LiveMob live(EntityType type) {
        Mob mob = GoalFixtures.mob(ID, type, null, () -> null, new Location(null, 0, 64, 0));
        return new LiveMob(mob, GoalFixtures.mythicMob(List.of()), restores::incrementAndGet);
    }

    private InstallResult install(LiveMob live, int generation) {
        return ownership.ensureInstalled(new MobKey(ID, generation), PROFILE, live, 32);
    }

    @Test
    void installingTwiceWithTheSameRevisionLeavesTheGoalsUntouched() {
        var live = live(EntityType.ZOMBIE);
        var first = install(live, 0);
        var snapshot = List.copyOf(goals.goals);
        var second = install(live, 0);
        assertEquals(new InstallResult.Installed(7), first);
        assertEquals(first, second);
        assertEquals(1, ai.calls.size());
        assertEquals(snapshot, goals.goals);
    }

    @Test
    void aNewRevisionReinstallsTheWholeSetWithoutDuplicatingPaperGoals() {
        var live = live(EntityType.ZOMBIE);
        install(live, 0);
        revision.set(5);
        assertEquals(new InstallResult.Installed(7), install(live, 0));
        assertEquals(2, ai.calls.size());
        assertEquals(1, goals.count(KEYS.hold()));
        assertEquals(1, goals.count(KEYS.flank()));
        assertEquals(1, goals.count(KEYS.owner()));
        assertEquals(new InstallDecision.Marker.Owned(5, 0, 7), installer().marker(live.entity()));
    }

    @Test
    void aNewGenerationOrAMissingMarkerReinstalls() {
        var live = live(EntityType.ZOMBIE);
        install(live, 0);
        install(live, 1);
        assertEquals(2, ai.calls.size());
        goals.removeGoal(live.entity(), KEYS.owner());
        install(live, 1);
        assertEquals(3, ai.calls.size());
        assertEquals(1, goals.count(KEYS.hold()));
    }

    @Test
    void brainSpeciesAreUnsupportedAndKeepTheirGoals() {
        assertEquals(new InstallResult.Unsupported(SpeciesSupport.BRAIN_SPECIES), install(live(EntityType.PIGLIN), 0));
        assertEquals(new InstallResult.Unsupported("brain species"), install(live(EntityType.WARDEN), 0));
        assertTrue(ai.calls.isEmpty());
        assertTrue(goals.goals.isEmpty());
    }

    @Test
    void aPaperFailureRestoresTheMythicMobsAiAndLogsItWithALimit() {
        var live = live(EntityType.ZOMBIE);
        goals.addGoal(live.entity(), 8, GoalFixtures.goal(KEYS.hold()));
        goals.addFailure = new IllegalStateException("secret text that must not be logged");
        assertEquals(new InstallResult.Unsupported("install failed: IllegalStateException"), install(live, 0));
        assertEquals(0, goals.count(KEYS.hold()));
        assertEquals(1, restores.get());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains(ID.toString()) && warnings.getFirst().contains("IllegalStateException"));
        assertFalse(warnings.getFirst().contains("secret"));
        ticks.addAndGet(FailureLog.INTERVAL_TICKS - 1);
        install(live, 0);
        assertEquals(1, warnings.size());
        ticks.incrementAndGet();
        install(live, 0);
        assertEquals(2, warnings.size());
        assertTrue(warnings.getLast().contains("similar failures were not logged"), warnings.getLast());
    }

    @Test
    void aSetCutShortIsUnsupportedRestoredAndNotReinstalledForTheSameRevisionAndGeneration() {
        var live = live(EntityType.ZOMBIE);
        ai.brokenGoal = "meleeattack";
        var incomplete = new InstallResult.Unsupported("install incomplete: 2 MythicMobs goals missing");
        assertEquals(incomplete, install(live, 0));
        assertEquals(1, ai.loggedErrors.size());
        assertEquals(0, goals.count(KEYS.hold()) + goals.count(KEYS.flank()));
        assertEquals(new InstallDecision.Marker.Refused(4, 0, incomplete.reason()), installer().marker(live.entity()));
        assertEquals(1, restores.get());
        assertTrue(warnings.getFirst().contains("2 MythicMobs goals missing"), warnings.getFirst());
        assertEquals(incomplete, install(live, 0));
        assertEquals(1, ai.calls.size());
        assertEquals(1, restores.get());
        ai.brokenGoal = "";
        revision.set(5);
        assertEquals(new InstallResult.Installed(7), install(live, 0));
        assertEquals(2, ai.calls.size());
        assertEquals(new InstallDecision.Marker.Owned(5, 0, 7), installer().marker(live.entity()));
    }

    @Test
    void aBaseAiLineMythicMobsSkipsForTheSpeciesMakesTheSetUnsupportedUntilTheGenerationChanges() {
        var profile = GoalFixtures.profile(MOVEMENT, List.of("1 meleeAttack", "2 bowAttack", "3 randomStroll"),
                Movement.HOLD);
        var live = live(EntityType.ZOMBIE);
        ai.invalidGoals.add("bowattack");
        var skipped = new InstallResult.Unsupported("install incomplete: 1 MythicMobs goals missing");
        assertEquals(skipped, ownership.ensureInstalled(new MobKey(ID, 0), profile, live, 32));
        assertEquals(List.of("AI pathfinder bowattack is not valid for mob type"), ai.loggedErrors);
        assertEquals(1, restores.get());
        assertEquals(skipped, ownership.ensureInstalled(new MobKey(ID, 0), profile, live, 32));
        assertEquals(1, ai.calls.size());
        assertEquals(skipped, ownership.ensureInstalled(new MobKey(ID, 1), profile, live, 32));
        assertEquals(2, ai.calls.size());
    }

    @Test
    void aMobThatIsNotACreatureIsUnsupportedWithoutAnyInstallAttempt() {
        var slime = new LiveMob(GoalFixtures.nonCreature(ID, EntityType.SLIME), GoalFixtures.mythicMob(List.of()),
                restores::incrementAndGet);
        assertEquals(new InstallResult.Unsupported(SpeciesSupport.NOT_A_CREATURE), install(slime, 0));
        assertEquals(new InstallResult.Unsupported("species without MythicMobs movement goals"),
                install(new LiveMob(GoalFixtures.nonCreature(ID, EntityType.PHANTOM), GoalFixtures.mythicMob(List.of()),
                        restores::incrementAndGet), 0));
        assertTrue(ai.calls.isEmpty());
        assertTrue(goals.goals.isEmpty());
        assertEquals(0, restores.get());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aLinkageErrorIsAlsoUnsupported() {
        var live = new LiveMob(live(EntityType.ZOMBIE).entity(), GoalFixtures.proxy(MythicMob.class,
                (instance, method, args) -> {
                    throw new NoSuchMethodError("getAIGoalSelectors");
                }), restores::incrementAndGet);
        assertEquals(new InstallResult.Unsupported("install failed: NoSuchMethodError"), install(live, 0));
        assertEquals(1, restores.get());
    }

    @Test
    void anUnavailableMobIsDeferredAndHasNoProgressGoalsOrRelease() {
        var key = new MobKey(ID, 0);
        assertEquals(new InstallResult.Deferred(GoalOwnership.ENTITY_UNAVAILABLE), ownership.ensureInstalled(key, PROFILE));
        assertEquals(new MovementProgress(false, false, false, OptionalDouble.empty()), ownership.progress(key));
        assertTrue(ownership.describeGoals(key).isEmpty());
        ownership.release(key);
        assertEquals(0, restores.get());
    }

    @Test
    void everyPortMethodRejectsOtherThreads() {
        serverThread.set(false);
        var key = new MobKey(ID, 0);
        assertThrows(IllegalStateException.class, () -> ownership.ensureInstalled(key, PROFILE));
        assertThrows(IllegalStateException.class, () -> ownership.progress(key));
        assertThrows(IllegalStateException.class, () -> ownership.describeGoals(key));
        assertThrows(IllegalStateException.class, () -> ownership.release(key));
    }

    @Test
    void theInstalledLinesAreTheEscapedGeneratedSet() {
        install(live(EntityType.ZOMBIE), 0);
        var plan = new GoalLineGenerator().generate(PROFILE, new BaseAiResolver().resolve(PROFILE, List.of()), 32, 4);
        assertEquals(GoalSetInstaller.selectorLines(plan.mythicLines()), ai.calls.getFirst());
    }
}
