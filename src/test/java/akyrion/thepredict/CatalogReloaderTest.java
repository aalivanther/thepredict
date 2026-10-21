package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.MythicDefinitionLookup;
import akyrion.thepredict.adapter.server.binding.MythicFixtures;
import akyrion.thepredict.adapter.server.binding.MythicProfileBinder;
import akyrion.thepredict.adapter.server.binding.NativeConditions;
import akyrion.thepredict.config.CatalogStatus;
import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.ProfileLoader;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.core.skills.SkillCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogReloaderTest {
    private static final String GUARDIAN = """
            schemaVersion: 2
            guardian_ai:
              MythicID: Guardian
              Objective: Survive
              Choices:
                melee: { MythicID: GuardianMelee, CD: 20 }
              Tactics:
                press_and_strike: { Movement: APPROACH, Choice: melee }
                hold: { Movement: HOLD }
              Contexts:
                wounded: { Conditions: ['healthpercent{p=<30%}'], Tactics: [hold] }
            """;

    @TempDir
    Path directory;

    private final Map<String, Object> guardianConfig = new HashMap<>(Map.of("Predictor", true));
    private final MythicFixtures.Definitions definitions = MythicFixtures.definitions()
            .add(MythicFixtures.mob("Guardian", "ZOMBIE", guardianConfig))
            .add(MythicFixtures.skill("GuardianMelee", Map.of("PredictValues", List.of("MELEE_ATTACK"))));

    @Test
    void emptyDirectoryIsSetupPendingAndEveryReloadRaisesTheRevision() {
        guardianConfig.put("Predictor", false);
        var reloader = reloader();
        assertEquals(0, reloader.catalog().catalogRevision());

        var first = reloader.reload();
        assertEquals(CatalogStatus.SETUP_PENDING, first.status());
        assertEquals(1, first.catalogRevision());
        assertEquals(2, reloader.reload().catalogRevision());
    }

    @Test
    void validReloadExchangesProfilesAndBindingsTogether() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        var reloader = reloader();
        var catalog = reloader.reload();

        assertEquals(CatalogStatus.LOADED, catalog.status());
        assertEquals(Set.of("Guardian"), catalog.profiles().keySet());
        assertSame(reloader.bindings().get("Guardian").profile(), catalog.profiles().get("Guardian"));
    }

    @Test
    void aBrokenProfileLeavesTheOthersLoaded() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        Files.writeString(directory.resolve("broken.yml"), GUARDIAN.replace("guardian_ai", "broken_ai")
                .replace("MythicID: Guardian\n", "MythicID: Broken\n").replace("Choice: melee", "Choice: mele"));
        var catalog = reloader().reload();

        assertEquals(CatalogStatus.INVALID, catalog.status());
        assertEquals(Set.of("Guardian"), catalog.profiles().keySet());
        assertEquals("AIConfig/Profiles/broken.yml: broken_ai.Tactics.press_and_strike.Choice: unknown choice 'mele'",
                catalog.problems().getFirst().toString());
    }

    @Test
    void globalReadFailureKeepsThePreviousProfilesAndBindsThemAgain() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        var failing = new AtomicBoolean();
        var loader = new ProfileLoader();
        var reloader = new CatalogReloader(() -> {
            if (failing.get()) {
                throw new IOException("disk gone");
            }
            return loader.load(directory);
        }, new MythicProfileBinder(definitions));
        var before = reloader.reload();
        var bindingBefore = reloader.bindings().get("Guardian");

        failing.set(true);
        var after = reloader.reload();
        assertEquals(before.profiles().keySet(), after.profiles().keySet());
        assertEquals(before.catalogRevision() + 1, after.catalogRevision());
        assertEquals(CatalogStatus.INVALID, after.status());
        assertEquals("AIConfig/Profiles: the directory could not be read; the profiles read last are kept",
                after.problems().getFirst().toString());
        assertNotSame(bindingBefore, reloader.bindings().get("Guardian"));
    }

    @Test
    void everyFileUnreadableKeepsThePreviousProfiles() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        var reloader = reloader();
        reloader.reload();

        Files.writeString(directory.resolve("guardian.yml"), "guardian_ai: [broken\n");
        var after = reloader.reload();
        assertEquals(Set.of("Guardian"), after.profiles().keySet());
        assertTrue(after.problems().stream().anyMatch(problem -> problem.toString()
                .equals("AIConfig/Profiles: no profile file could be read; the profiles read last are kept")));
        assertTrue(after.problems().getFirst().toString().startsWith("AIConfig/Profiles/guardian.yml: is not valid YAML"));
    }

    @Test
    void globalFailureOnTheFirstLoadLeavesAnInvalidEmptyCatalog() {
        var reloader = new CatalogReloader(() -> {
            throw new IOException("no directory");
        }, new MythicProfileBinder(definitions));
        var catalog = reloader.reload();

        assertTrue(catalog.profiles().isEmpty());
        assertEquals(CatalogStatus.INVALID, catalog.status());
    }

    @Test
    void mythicMobsReloadRebindsAndDropsMobsWhosePredictorWasTurnedOff() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        var reloader = reloader();
        reloader.reload();

        guardianConfig.put("Predictor", false);
        var after = reloader.reload();
        assertTrue(after.profiles().isEmpty());
        assertEquals(CatalogStatus.SETUP_PENDING, after.status());
        assertTrue(reloader.bindings().isEmpty());
    }

    @Test
    void bindingProblemsGetTheFileOfTheirProfileOrMythicMobs() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN.replace("GuardianMelee", "Missing"));
        definitions.add(MythicFixtures.mob("Orphan", "ZOMBIE", Map.of("Predictor", true)));
        var problems = reloader().reload().problems().stream().map(ConfigProblem::toString).toList();

        assertEquals(List.of("AIConfig/Profiles/guardian.yml: guardian_ai.Choices.melee.MythicID: unknown skill 'Missing'",
                "MythicMobs: Orphan.Predictor: is true but no valid AI profile targets this mob"), problems);
    }

    @Test
    void conditionVerificationDisablesUnresolvedContextsOnlyForTheCurrentRevision() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        definitions.unresolved("healthpercent{p=<30%}");
        var reloader = reloader();
        var loaded = reloader.reload();

        assertSame(loaded, reloader.verifyConditions(loaded.catalogRevision() - 1));
        var verified = reloader.verifyConditions(loaded.catalogRevision());
        assertEquals(loaded.catalogRevision() + 1, verified.catalogRevision());
        assertEquals(CatalogStatus.INVALID, verified.status());
        assertTrue(verified.profiles().get("Guardian").contexts().isEmpty());
        assertEquals("AIConfig/Profiles/guardian.yml: guardian_ai.Contexts.wounded.Conditions: no plugin resolved the condition "
                + "'healthpercent{p=<30%}'; context 'wounded' is disabled", verified.problems().getLast().toString());
        assertSame(verified, reloader.verifyConditions(verified.catalogRevision()));
    }

    @Test
    void readProfilesWithoutAnEnabledMobAreReportedAsInactive() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        guardianConfig.put("Predictor", false);
        var reloader = reloader();
        reloader.reload();

        assertEquals(CatalogStatus.SETUP_PENDING, reloader.catalog().status());
        assertTrue(reloader.summary().contains("1 profiles read, 0 active"), reloader.summary());
        assertTrue(reloader.summary().contains("Predictor: true"), reloader.summary());
    }

    @Test
    void anUnexpectedErrorEmptiesTheCatalogInsteadOfKeepingStaleBindings() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        definitions.unresolved("healthpercent{p=<30%}");
        var broken = new AtomicBoolean();
        var reported = new ArrayList<RuntimeException>();
        var reloader = new CatalogReloader(new ProfileLoader(), directory, new MythicProfileBinder(failing(broken)),
                reported::add);
        var loaded = reloader.reload();
        assertEquals(Set.of("Guardian"), loaded.profiles().keySet());

        broken.set(true);
        var failed = reloader.reload();
        assertTrue(failed.profiles().isEmpty());
        assertTrue(reloader.bindings().isEmpty());
        assertEquals(CatalogStatus.INVALID, failed.status());
        assertEquals("AIConfig/Profiles: the reload failed unexpectedly (IllegalStateException); no profile is active until the next"
                + " successful reload", failed.problems().getFirst().toString());
        assertEquals(1, reported.size());
        assertEquals("MythicMobs objects are stale", reported.getFirst().getMessage());

        broken.set(false);
        var recovered = reloader.reload();
        assertEquals(Set.of("Guardian"), recovered.profiles().keySet());
        broken.set(true);
        assertTrue(reloader.verifyConditions(recovered.catalogRevision()).profiles().isEmpty());
        assertTrue(reloader.bindings().isEmpty());
        assertEquals(2, reported.size());
    }

    @Test
    void blockingReportsTheProblemWithoutProfiles() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), GUARDIAN);
        var reloader = reloader();
        reloader.reload();
        var problem = ConfigProblem.error("config.yml", "", "uses schema 1; review the migration proposal under migration/");
        var blocked = reloader.block(problem);

        assertEquals(List.of(problem), blocked.problems());
        assertTrue(blocked.profiles().isEmpty());
        assertTrue(reloader.bindings().isEmpty());
        assertEquals(CatalogStatus.INVALID, blocked.status());
    }

    private MythicDefinitionLookup failing(AtomicBoolean broken) {
        return new MythicDefinitionLookup() {
            @Override
            public Optional<MythicMob> mob(String id) {
                check();
                return definitions.mob(id);
            }

            @Override
            public Collection<MythicMob> mobs() {
                check();
                return definitions.mobs();
            }

            @Override
            public Optional<Skill> skill(String id) {
                return definitions.skill(id);
            }

            @Override
            public Collection<String> skillNames() {
                return definitions.skillNames();
            }

            @Override
            public NativeConditions conditions(List<String> lines) {
                var converted = definitions.conditions(lines);
                return new NativeConditions() {
                    @Override
                    public List<String> lines() {
                        return converted.lines();
                    }

                    @Override
                    public List<SkillCondition> conditions() {
                        return converted.conditions();
                    }

                    @Override
                    public List<String> invalidLines() {
                        return converted.invalidLines();
                    }

                    @Override
                    public List<String> unresolvedLines() {
                        check();
                        return converted.unresolvedLines();
                    }
                };
            }

            private void check() {
                if (broken.get()) {
                    throw new IllegalStateException("MythicMobs objects are stale");
                }
            }
        };
    }

    private CatalogReloader reloader() {
        return new CatalogReloader(new ProfileLoader(), directory, new MythicProfileBinder(definitions));
    }
}
