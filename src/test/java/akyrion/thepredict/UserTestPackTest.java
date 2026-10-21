package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.MythicProfileBinder;
import akyrion.thepredict.adapter.server.binding.MythicYaml;
import akyrion.thepredict.adapter.server.context.ParticipantDefaults;
import akyrion.thepredict.adapter.server.context.SelectorRegistry;
import akyrion.thepredict.config.CatalogStatus;
import akyrion.thepredict.config.FirstRunFiles;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.ThreatWeights;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTestPackTest {
    private static final Path DOCUMENTS = Path.of("DocumentacionParaDesarrollar/Implementacion-0.0.1");
    private static final Path PACK = DOCUMENTS.resolve("paquete-pruebas");
    private static final Path FILES = PACK.resolve("archivos");
    private static final String DUEL_MOBS = "13-duelo/plugins/MythicMobs/Mobs/ThePredictDuel.yml";
    private static final String DUEL_SKILLS = "13-duelo/plugins/MythicMobs/Skills/ThePredictDuelSkills.yml";
    private static final String PRESAS = "12-entidades/world/datapacks/mipack/data/mipack/tags/entity_type/presas.json";
    private static final Path SESSIONS = PACK.resolve("Guia-de-pruebas-por-sesiones.md");
    private static final Path SERVER_SCRIPT = DOCUMENTS.resolve("M01-Guion-de-servidor.md");
    private static final Path LOAD_SCRIPT = DOCUMENTS.resolve("Guion-servidor-0.0.1.md");
    private static final Pattern SKILL_REFERENCE = Pattern.compile("skill\\{s=([A-Za-z0-9_]+)");
    private static final Pattern YAML_BLOCK = Pattern.compile("(?m)^```yaml\\n(.*?)^```", Pattern.DOTALL);

    @TempDir
    Path server;

    @Test
    void theVerificationPackLoadsItsFiveProfilesAndThePiglinOnlyWithoutMovement() throws Exception {
        install("01-verificacion");
        var reloader = reloader();
        reloader.reload();
        assertEquals("LOADED (revision 1, 5 profiles read, 5 active, 0 errors, 0 warnings)", reloader.summary(),
                reloader.catalog().problems().toString());
        assertTrue(reloader.bindings().get("TPV_Piglin").brainSpecies());
        assertEquals(Set.of("TPV_Zombie", "TPV_ZombieCustomAI", "TPV_ZombieAsync", "TPV_Skeleton", "TPV_Piglin"),
                reloader.bindings().keySet());
        assertTrue(sessionsGuide().contains("5 profiles read, 5 active, 0 errors"));
    }

    @Test
    void theExampleIsReadButInactiveWithPredictorFalseAndActiveWithTheTestCopies() throws Exception {
        install("01-verificacion");
        installExample();
        var reloader = reloader();
        reloader.reload();
        assertEquals("LOADED (revision 1, 6 profiles read, 5 active, 0 errors, 2 warnings)", reloader.summary());
        install("03-guardian");
        var guardian = reloader();
        guardian.reload();
        assertEquals("LOADED (revision 1, 6 profiles read, 6 active, 0 errors, 2 warnings)", guardian.summary());
        install("07-senales");
        var enabled = reloader();
        enabled.reload();
        assertEquals("LOADED (revision 1, 6 profiles read, 6 active, 0 errors, 2 warnings)", enabled.summary());
        install("08-amenaza");
        var threat = reloader();
        threat.reload();
        assertEquals("LOADED (revision 1, 6 profiles read, 6 active, 0 errors, 2 warnings)", threat.summary());
        var guide = sessionsGuide();
        assertTrue(guide.contains("6 profiles read, 5 active, 0 errors"));
        assertTrue(guide.contains("6 profiles read, 6 active, 0 errors"));
    }

    @Test
    void theSlimeAndTheTemporaryCopiesBindAsProfilesOfTheirOwn() throws Exception {
        install("01-verificacion");
        installExample();
        install("03-guardian");
        install("04-slime");
        var slime = reloader();
        slime.reload();
        assertEquals("LOADED (revision 1, 7 profiles read, 7 active, 0 errors, 4 warnings)", slime.summary());
        assertEquals("SLIME", slime.bindings().get("ThePredictGuardianSlime").entityType());
        assertFalse(slime.bindings().get("ThePredictGuardianSlime").brainSpecies());
        remove("04-slime");
        install("05-temporal");
        var temporary = reloader();
        temporary.reload();
        assertEquals("LOADED (revision 1, 7 profiles read, 7 active, 0 errors, 4 warnings)", temporary.summary());
        assertTrue(temporary.bindings().containsKey("ThePredictGuardianTemp"));
        var guide = sessionsGuide();
        assertTrue(guide.contains("7 profiles read, 7 active, 0 errors"));
    }

    @Test
    void theNativeContextAndTheZeroWeightVariantsLoadWithWhatTheScriptExpects() throws Exception {
        install("01-verificacion");
        installExample();
        install("03-guardian");
        install("06-contexto-nativo");
        var context = reloader();
        context.reload();
        assertEquals(CatalogStatus.LOADED, context.catalog().status());
        var wounded = context.catalog().profiles().get("ThePredictGuardian").contexts().stream()
                .filter(rule -> rule.id().equals(new ContextId("wounded_native"))).findFirst().orElseThrow();
        assertEquals(List.of("healthpercent{p=<30%}"), wounded.nativeConditions());
        assertEquals(95, wounded.priority());
        install("09-amenaza-sin-pesos");
        var zero = reloader();
        zero.reload();
        assertEquals(new ThreatWeights(0, 0, 0, 0, 20), zero.catalog().profiles().get("ThePredictGuardian").threat());
        assertEquals(2, zero.catalog().problems().size());
    }

    @Test
    void decayUnreachableOnTheMobGivesTheWarningTheScriptQuotes() throws Exception {
        install("01-verificacion");
        installExample();
        install("06-contexto-nativo");
        install("08-amenaza");
        var mob = server.resolve("plugins/MythicMobs/Mobs/ThePredictGuardian.yml");
        Files.writeString(mob, Files.readString(mob).replace("DecayUnreachable: false", "DecayUnreachable: true"));
        var reloader = reloader();
        reloader.reload();
        var warning = "Threat.UnreachableDecayPerSecond: is ignored because mob 'ThePredictGuardian' enables"
                + " ThreatTable.DecayUnreachable";
        assertTrue(reloader.catalog().problems().stream().anyMatch(problem -> !problem.isError()
                && problem.toString().contains(warning)), reloader.catalog().problems().toString());
        var script = Files.readString(SERVER_SCRIPT);
        assertTrue(script.contains("`Threat.UnreachableDecayPerSecond: is ignored because mob '")
                && script.contains("' enables ThreatTable.DecayUnreachable`"));
    }

    @Test
    void aBrokenVerificationProfileGivesTheFirstErrorTheSessionsGuideQuotes() throws Exception {
        install("01-verificacion");
        var profile = server.resolve("plugins/MythicMobs/AIConfig/Profiles/ThePredictVerification.yml");
        Files.writeString(profile, Files.readString(profile).replace(
                "    retreat: { Movement: RETREAT }\n    hold: { Movement: HOLD }\n    hold_and_strike",
                "    retreat: { Movement: SIDEWAYS }\n    hold: { Movement: HOLD }\n    hold_and_strike"));
        var reloader = reloader();
        reloader.reload();
        var summary = reloader.summary();
        assertTrue(summary.startsWith("INVALID (revision 1, 4 profiles read, 4 active, "), summary);
        assertTrue(summary.contains("): first error AIConfig/Profiles/ThePredictVerification.yml:"
                + " tpv_zombie.Tactics.retreat.Movement: unknown movement 'SIDEWAYS'"), summary);
        assertTrue(sessionsGuide().contains("first error AIConfig/Profiles/ThePredictVerification.yml:"
                + " tpv_zombie.Tactics.retreat.Movement: unknown movement 'SIDEWAYS'"));
    }

    @Test
    void theVocabularyStageIsTheAuthorGuideExampleAndLoadsNextToTheVerificationPack() throws Exception {
        var guide = yamlBlocks(Files.readString(Path.of("Guias/Guia-del-autor.md")));
        for (var file : files("11-vocabulario")) {
            assertTrue(guide.contains(withoutComments(Files.readString(file))), file.toString());
        }
        install("01-verificacion");
        install("11-vocabulario");
        var reloader = reloader();
        reloader.reload();
        assertEquals("LOADED (revision 1, 6 profiles read, 6 active, 0 errors, 0 warnings)", reloader.summary(),
                reloader.catalog().problems().toString());
        assertTrue(sessionsGuide().contains("6 profiles read, 6 active, 0 errors"));
    }

    @Test
    void bothArmsOfTheComparisonBindAndOnlyArmBHasAProfile() throws Exception {
        installExample();
        install("10-ab");
        var reloader = reloader();
        reloader.reload();
        assertEquals("LOADED (revision 1, 1 profiles read, 1 active, 0 errors, 2 warnings)", reloader.summary());
        assertEquals(Set.of("ThePredictGuardian"), reloader.bindings().keySet());
    }

    @Test
    void everyTestMobKeepsItsChunkAndOnlyTheTemporaryMobDespawns() throws Exception {
        var mobs = folders("Mobs");
        assertFalse(mobs.isEmpty());
        for (var file : mobs) {
            var yaml = YamlConfiguration.loadConfiguration(file.toFile());
            var entries = new LinkedHashMap<String, ConfigurationSection>();
            yaml.getKeys(false).forEach(id -> entries.put(id, yaml.getConfigurationSection(id)));
            for (var id : yaml.getKeys(false)) {
                var despawn = MythicYaml.lineage(id, entries).stream().filter(section -> section.isSet("Options.Despawn"))
                        .map(section -> section.getBoolean("Options.Despawn")).reduce((parent, child) -> child)
                        .orElse(true);
                assertEquals(!id.equals("ThePredictGuardianTemp"), !despawn, file + " " + id);
            }
        }
    }

    @Test
    void everySkillReferenceOfThePackResolvesToAPackOrExampleSkill() throws Exception {
        installExample();
        var known = new HashSet<String>();
        var lines = new ArrayList<String>();
        var skillFiles = new ArrayList<>(folders("Skills"));
        skillFiles.add(server.resolve("plugins/MythicMobs/Skills/ThePredictGuardianSkills.yml"));
        for (var file : skillFiles) {
            var yaml = YamlConfiguration.loadConfiguration(file.toFile());
            for (var id : yaml.getKeys(false)) {
                known.add(id);
                lines.addAll(yaml.getStringList(id + ".Skills"));
            }
        }
        for (var file : folders("Mobs")) {
            var yaml = YamlConfiguration.loadConfiguration(file.toFile());
            yaml.getKeys(false).forEach(id -> lines.addAll(yaml.getStringList(id + ".Skills")));
        }
        var references = 0;
        for (var line : lines) {
            var matcher = SKILL_REFERENCE.matcher(line);
            while (matcher.find()) {
                references++;
                assertTrue(known.contains(matcher.group(1)), line);
            }
        }
        assertTrue(references >= 15, "references: " + references);
    }

    @Test
    void theVerificationFilesAreTheOnesTheServerScriptShows() throws Exception {
        var blocks = yamlBlocks(Files.readString(SERVER_SCRIPT));
        for (var file : Stream.concat(files("01-verificacion").stream(), files("02-permisos").stream()).toList()) {
            assertTrue(blocks.contains(withoutComments(Files.readString(file))), file.toString());
        }
    }

    @Test
    void theVariantsCarryTheLinesTheScriptsAskFor() throws Exception {
        var serverScript = Files.readString(SERVER_SCRIPT);
        var context = Files.readString(FILES.resolve("06-contexto-nativo/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml"));
        assertTrue(context.contains("""
                    wounded_native:
                      Conditions:
                      - healthpercent{p=<30%}
                      Priority: 95
                      Tactics: [retreat_and_defend, keep_distance_and_shoot]
                """));
        assertTrue(trimmedLines(serverScript).contains("wounded_native:\nConditions:\n- healthpercent{p=<30%}\nPriority: 95\n"
                + "Tactics: [retreat_and_defend, keep_distance_and_shoot]\n"));
        var signals = Files.readString(FILES.resolve("07-senales/plugins/MythicMobs/Mobs/ThePredictGuardian.yml"));
        var tactics = List.of("APPROACH", "PRESS_AND_STRIKE", "KEEP_DISTANCE", "KEEP_DISTANCE_AND_SHOOT",
                "RETREAT_AND_DEFEND", "FLANK_AND_STRIKE", "CLOSE_IN_AND_SLAM", "HOLD");
        assertEquals(tactics.size(), signals.lines().filter(line -> tactics.stream()
                .anyMatch(tactic -> line.contains("~onSignal:PREDICTOR_" + tactic))).count());
        for (var tactic : tactics) {
            assertTrue(serverScript.contains("PREDICTOR_" + tactic + "`"), tactic);
            assertTrue(signals.contains("  - message{m=\"signal <caster.predictor.tactic> <caster.predictor.level>\"}"
                    + " @PIR{r=20} ~onSignal:PREDICTOR_" + tactic + "\n"), tactic);
        }
        assertTrue(serverScript.contains("`- message{m=\"signal <caster.predictor.tactic> <caster.predictor.level>\"}"
                + " @PIR{r=20} ~onSignal:PREDICTOR_PRESS_AND_STRIKE`"));
        assertTrue(withoutComments(Files.readString(FILES.resolve("08-amenaza/plugins/MythicMobs/Mobs/ThePredictGuardian.yml")))
                .startsWith(withoutComments(signals)));
        var top = "- message{m=\"top <caster.tt.top>\"} @PIR{r=20} ~onTimer:20";
        assertTrue(Files.readString(FILES.resolve("08-amenaza/plugins/MythicMobs/Mobs/ThePredictGuardian.yml"))
                .contains("  " + top + "\n"));
        assertTrue(serverScript.contains(top));
        var zero = "Threat: { AssistsOthers: 0, RangedHarass: 0, BlockedHits: 0, UnreachableDecayPerSecond: 0 }";
        assertTrue(Files.readString(FILES.resolve("09-amenaza-sin-pesos/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml"))
                .contains("  " + zero + "\n"));
        assertTrue(serverScript.contains(zero));
    }

    @Test
    void theComparisonFilesAreTheOnesTheLoadScriptShows() throws Exception {
        var blocks = yamlBlocks(Files.readString(LOAD_SCRIPT));
        for (var name : List.of("10-ab/plugins/MythicMobs/Mobs/ThePredictGuardianClassic.yml",
                "10-ab/plugins/MythicMobs/Skills/ABClassic.yml")) {
            assertTrue(blocks.contains(withoutComments(Files.readString(FILES.resolve(name)))), name);
        }
        var armB = Files.readString(FILES.resolve("10-ab/plugins/MythicMobs/Mobs/ThePredictGuardian.yml"));
        assertTrue(armB.contains("  - skill{s=ABReach} @self ~onTimer:20\n  - command{c=\"say AB died\"} @self ~onDeath\n"));
        assertTrue(armB.contains("  Predictor: true\n"));
    }

    @Test
    void theGuardianCopiesDifferFromTheBundledExampleOnlyWhereTheScriptsSay() throws Exception {
        installExample();
        var example = withoutComments(Files.readString(server.resolve("plugins/MythicMobs/Mobs/ThePredictGuardian.yml")));
        var enabled = example.replace("    PreventOtherDrops: true\n", "    PreventOtherDrops: true\n    Despawn: false\n")
                .replace("  Predictor: false\n", "  Predictor: true\n");
        assertEquals(enabled, withoutComments(Files.readString(
                FILES.resolve("03-guardian/plugins/MythicMobs/Mobs/ThePredictGuardian.yml"))));
        assertTrue(withoutComments(Files.readString(FILES.resolve("07-senales/plugins/MythicMobs/Mobs/ThePredictGuardian.yml")))
                .startsWith(enabled));
        assertTrue(withoutComments(Files.readString(FILES.resolve("10-ab/plugins/MythicMobs/Mobs/ThePredictGuardian.yml")))
                .startsWith(enabled));
        var profile = withoutComments(Files.readString(server.resolve("plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml")));
        for (var copy : List.of("04-slime/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardianSlime.yml",
                "05-temporal/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardianTemp.yml")) {
            var id = copy.contains("Slime") ? "Slime" : "Temp";
            assertEquals(profile.replace("thepredict_guardian:", "thepredict_guardian_" + id.toLowerCase() + ":")
                    .replace("MythicID: ThePredictGuardian\n", "MythicID: ThePredictGuardian" + id + "\n"),
                    withoutComments(Files.readString(FILES.resolve(copy))), copy);
        }
    }

    @Test
    void theGuardianStageCarriesTheBundledProfileAndSkillsWithTheirPurposes() throws Exception {
        installExample();
        for (var file : List.of("plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml",
                "plugins/MythicMobs/Skills/ThePredictGuardianSkills.yml")) {
            assertEquals(withoutComments(Files.readString(server.resolve(file))),
                    withoutComments(Files.readString(FILES.resolve("03-guardian").resolve(file))), file);
        }
        var profile = withoutComments(Files.readString(server.resolve("plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml")));
        for (var copy : List.of("06-contexto-nativo", "09-amenaza-sin-pesos")) {
            var variant = withoutComments(Files.readString(FILES.resolve(copy + "/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml")));
            assertTrue(variant.contains("    slam:\n") && variant.contains("    close_in_and_slam:\n"), copy);
        }
        assertTrue(profile.contains("    slam:\n      MythicID: ThePredictGuardianSlam\n"));
    }

    @Test
    void thePurposeStageWarnsThatKeepDistanceWithTheDeclaredMeleeSkillNeverCasts() throws Exception {
        install("01-verificacion");
        installExample();
        install("03-guardian");
        var clean = reloader();
        clean.reload();
        assertEquals("LOADED (revision 1, 6 profiles read, 6 active, 0 errors, 2 warnings)", clean.summary());
        install("15-propositos");
        var lint = reloader();
        lint.reload();
        assertEquals("LOADED (revision 1, 6 profiles read, 6 active, 0 errors, 3 warnings)", lint.summary());
        var meleeCue = "AIConfig/Profiles/ThePredictGuardian.yml: thepredict_guardian.Choices.melee: skill"
                + " 'ThePredictGuardianMelee' telegraph has no visual (or audio) cue; ThePredict adds none: add a"
                + " ~onSignal:PREDICTOR_TELL_BLOCK handler";
        var slamCue = "AIConfig/Profiles/ThePredictGuardian.yml: thepredict_guardian.Choices.slam: skill"
                + " 'ThePredictGuardianSlam' telegraph has no visual (or audio) cue; ThePredict adds none: add a"
                + " ~onSignal:PREDICTOR_TELL_BACK_OFF handler";
        var warning = "AIConfig/Profiles/ThePredictGuardian.yml: thepredict_guardian.Tactics.keep_distance_and_strike:"
                + " Movement KEEP_DISTANCE"
                + " keeps the target beyond Band.Inner 5 but skill 'melee' reaches 2.5; this tactic never casts";
        assertEquals(List.of(meleeCue, slamCue, warning), lint.catalog().problems().stream().map(Object::toString).toList());
        assertTrue(lint.catalog().profiles().containsKey("ThePredictGuardian"));
        var guide = sessionsGuide();
        assertTrue(guide.contains("`" + warning + "`"));
        assertTrue(guide.contains("1 profiles read, 1 active, 0 errors, 1 warnings"));
        var example = withoutComments(Files.readString(server.resolve("plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml")));
        var variant = withoutComments(Files.readString(FILES.resolve("15-propositos/plugins/MythicMobs/AIConfig/Profiles/ThePredictGuardian.yml")));
        assertEquals(example.replace("      Priority: 60\n    retreat_and_defend:", "      Priority: 60\n"
                + "    keep_distance_and_strike:\n      Movement: KEEP_DISTANCE\n      Choice: melee\n"
                + "    retreat_and_defend:"), variant);
    }

    @Test
    void theZoneStageBindsTheZoneGuardianWithItsMeleeSkill() throws Exception {
        var reloader = stage("24-zona");
        assertEquals("LOADED (revision 1, 1 profiles read, 1 active, 0 errors, 1 warnings)", reloader.summary(),
                reloader.catalog().problems().toString());
        assertEquals(Set.of("ZoneGuardian"), reloader.bindings().keySet());
        assertEquals(Set.of("ZoneGuardianMelee"), skillsOf(reloader, "ZoneGuardian"));
    }

    @Test
    void theGroupStageBindsTheThreeGroupMobsWithTheirFourSkills() throws Exception {
        var reloader = stage("25-grupo");
        assertEquals("LOADED (revision 1, 3 profiles read, 3 active, 0 errors, 1 warnings)", reloader.summary(),
                reloader.catalog().problems().toString());
        assertEquals(Set.of("JefeInvocador", "Patrulla5", "TrioHechiceros"), reloader.bindings().keySet());
        assertEquals(Set.of("JefeInvocadorMelee"), skillsOf(reloader, "JefeInvocador"));
        assertEquals(Set.of("Patrulla5Melee"), skillsOf(reloader, "Patrulla5"));
        assertEquals(Set.of("TrioHechicerosHeal", "TrioHechicerosBolt"), skillsOf(reloader, "TrioHechiceros"));
    }

    @Test
    void thePatternStageBindsTheBossWithItsMeleeSkill() throws Exception {
        var reloader = stage("26-patrones");
        assertEquals("LOADED (revision 1, 1 profiles read, 1 active, 0 errors, 1 warnings)", reloader.summary(),
                reloader.catalog().problems().toString());
        assertEquals(Set.of("JefePatrones"), reloader.bindings().keySet());
        assertEquals(Set.of("JefePatronesMelee"), skillsOf(reloader, "JefePatrones"));
    }

    @Test
    void theMemoryStageBindsTheBossAndItsMinionEachWithAProfile() throws Exception {
        var reloader = stage("27-memoria");
        assertEquals("LOADED (revision 1, 2 profiles read, 2 active, 0 errors, 1 warnings)", reloader.summary(),
                reloader.catalog().problems().toString());
        assertEquals(Set.of("JefeMemoria", "EsbirroMemoria"), reloader.bindings().keySet());
        assertEquals(Set.of("JefeMemoriaMelee"), skillsOf(reloader, "JefeMemoria"));
        assertEquals(Set.of("EsbirroMemoriaMelee"), skillsOf(reloader, "EsbirroMemoria"));
    }

    private CatalogReloader stage(String name) throws IOException {
        install(name);
        var reloader = reloader();
        reloader.reload();
        assertTrue(reloader.catalog().problems().stream().allMatch(problem -> !problem.isError()
                && problem.toString().contains("telegraph has no visual (or audio) cue; ThePredict adds none")),
                reloader.catalog().problems().toString());
        return reloader;
    }

    private static Set<String> skillsOf(CatalogReloader reloader, String mob) {
        return reloader.bindings().get(mob).profile().choices().values().stream()
                .flatMap(choice -> choice.mythicSkillId().stream()).collect(java.util.stream.Collectors.toSet());
    }

    private CatalogReloader reloader() throws IOException {
        var mythic = new ArrayList<YamlConfiguration>();
        for (var folder : List.of("plugins/MythicMobs/Mobs", "plugins/MythicMobs/Skills")) {
            var directory = Files.createDirectories(server.resolve(folder));
            try (var listing = Files.list(directory)) {
                listing.sorted().forEach(file -> mythic.add(YamlConfiguration.loadConfiguration(file.toFile())));
            }
        }
        return new CatalogReloader(new ProfileLoader(),
                server.resolve("plugins/MythicMobs/AIConfig/Profiles"),
                new MythicProfileBinder(MythicYaml.definitions(mythic), ParticipantDefaults.STANDARD,
                        new PackTags()));
    }

    /**
     * Selector resolution of the pack without a server: no entity type or team is looked up, and the only tag is the
     * {@code mipack:presas} of the datapack of {@code 12-entidades}, read from its JSON file as the server would.
     */
    private static final class PackTags implements SelectorRegistry {
        @Override
        public Optional<EntityType> entityType(NamespacedKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<Set<EntityType>> entityTag(NamespacedKey key) {
            if (!key.asString().equals("mipack:presas")) {
                return Optional.empty();
            }
            try {
                var json = Files.readString(FILES.resolve(PRESAS));
                var types = EnumSet.noneOf(EntityType.class);
                var matcher = Pattern.compile("\"minecraft:([a-z_]+)\"").matcher(json);
                while (matcher.find()) {
                    types.add(EntityType.valueOf(matcher.group(1).toUpperCase(Locale.ROOT)));
                }
                return Optional.of(types);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        @Override
        public Set<String> teamNames() {
            return Set.of();
        }
    }

    private void installExample() throws IOException {
        var data = server.resolve("plugins/ThePredict");
        new FirstRunFiles().installMissing(new akyrion.thepredict.config.ConfigLocations(
                server.resolve("plugins/MythicMobs"), data));
        for (var pair : List.of(List.of("examples/MythicMobs/Mobs/ThePredictGuardian.yml", "../MythicMobs/Mobs"),
                List.of("examples/MythicMobs/Skills/ThePredictGuardianSkills.yml", "../MythicMobs/Skills"),
                List.of("examples/AI/ThePredictGuardian.yml", "../MythicMobs/AIConfig/Profiles"))) {
            var source = data.resolve(pair.getFirst());
            var target = Files.createDirectories(data.resolve(pair.getLast()).normalize());
            Files.copy(source, target.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void install(String stage) throws IOException {
        var root = FILES.resolve(stage);
        for (var file : files(stage)) {
            var target = server.resolve(root.relativize(file).toString());
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void remove(String stage) throws IOException {
        var root = FILES.resolve(stage);
        for (var file : files(stage)) {
            Files.delete(server.resolve(root.relativize(file).toString()));
        }
    }

    private static List<Path> files(String stage) throws IOException {
        try (var walk = Files.walk(FILES.resolve(stage))) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static List<Path> folders(String name) throws IOException {
        try (var walk = Files.walk(FILES)) {
            return walk.filter(Files::isRegularFile).filter(file -> file.getParent().getFileName().toString().equals(name))
                    .sorted().toList();
        }
    }

    private static String trimmedLines(String text) {
        return text.lines().map(String::strip).reduce("", (joined, line) -> joined + line + "\n");
    }

    private static String sessionsGuide() throws IOException {
        return Files.readString(SESSIONS);
    }

    private static List<String> yamlBlocks(String markdown) {
        var blocks = new ArrayList<String>();
        var matcher = YAML_BLOCK.matcher(markdown);
        while (matcher.find()) {
            blocks.add(matcher.group(1).strip());
        }
        return blocks;
    }

    private static String withoutComments(String yaml) {
        return yaml.lines().filter(line -> !line.startsWith("#")).reduce("", (text, line) -> text + line + "\n").strip();
    }

    @Test
    void theSessionSixteenGivesItsCommandsCostsAndTheCheckOfDt35() throws Exception {
        var guide = sessionsGuide();
        for (var text : List.of("## Sesión 16. Efectividad sin clave (M16)", "## Sesión 16 con clave.", "DT-35",
                "0,011 USD", "outcomes: level=LOCAL", "Outcome JEV")) {
            assertTrue(guide.contains(text), text);
        }
        var checklist = Files.readString(PACK.resolve("Checklist-DT-y-casillas.md"));
        assertTrue(checklist.contains("| DT-35 |"));
        var script = Files.readString(LOAD_SCRIPT);
        assertTrue(script.contains("## 16. Efectividad de las respuestas (M16.11)"));
        assertTrue(script.contains("-Parms=A,B,C,D,E -Prepetitions=2 -Pscenarios=all -PmaxCalls=620"));
        assertTrue(script.contains("-Parms=B -Prepetitions=10 -Pscenarios=core12 -PmaxCalls=130"));
    }

    @Test
    void theEntitiesStageBindsTheHunterAndTheDuelistsWithTheirParticipantRules() throws Exception {
        install("12-entidades");
        var reloader = reloader();
        reloader.reload();
        assertEquals("LOADED (revision 1, 4 profiles read, 4 active, 0 errors, 0 warnings)", reloader.summary(),
                reloader.catalog().problems().toString());
        var bindings = reloader.bindings();
        assertTrue(bindings.get("TP_MonsterHunter").participants().admitsEntities());
        assertEquals(List.of("kind:MONSTER"), bindings.get("TP_MonsterHunter").profile().participants().entityAllow());
        var watcher = bindings.get("TP_TagWatcher").profile().participants();
        assertEquals(List.of("tag:mipack:presas"), watcher.entityAllow(), "a tag is its only rule (17.2b, DT-39)");
        assertTrue(watcher.playerGameModes().isEmpty());
        assertTrue(Files.readString(FILES.resolve(PRESAS)).contains("\"minecraft:rabbit\""),
                "the datapack starts with rabbits; the step changes it to sheep");
        assertTrue(Files.readString(FILES.resolve("12-entidades/world/datapacks/mipack/pack.mcmeta"))
                .contains("\"min_format\": 107"));
        for (var duelist : List.of("TP_DuelRed", "TP_DuelBlue")) {
            var rules = bindings.get(duelist).profile().participants();
            assertTrue(rules.playerGameModes().isEmpty(), duelist);
            assertEquals(1, rules.entityAllow().size(), duelist);
        }
    }

    @Test
    void theSessionSeventeenGivesItsStepsAndTheOpenDecisionsOfM17() throws Exception {
        var guide = sessionsGuide();
        for (var text : List.of("## Sesión 17. Participantes y objetivos como entidades (M17)", "DT-36", "DT-37",
                "DT-38", "DT-39", "`cp -R \"$PACK/12-entidades/.\" \"$BASE/\"`", "Target: entity_1 (zombie)",
                "`tick_work`")) {
            assertTrue(guide.contains(text), text);
        }
        var checklist = Files.readString(PACK.resolve("Checklist-DT-y-casillas.md"));
        for (var row : List.of("| DT-36 |", "| DT-37 |", "| DT-38 |", "| DT-39 |", "| M17.10 |")) {
            assertTrue(checklist.contains(row), row);
        }
        var script = Files.readString(LOAD_SCRIPT);
        assertTrue(script.contains("## 17. Participantes y objetivos como entidades (M17.10)"));
        assertTrue(Files.readString(PACK.resolve("README.md")).contains("| `12-entidades` |"));
    }

    @Test
    void theDuelStageBindsBothDuelistsAdmittingOnlyTheRivalWithoutAnyWarning() throws Exception {
        install("13-duelo");
        var reloader = reloader();
        reloader.reload();
        assertEquals("LOADED (revision 1, 2 profiles read, 2 active, 0 errors, 2 warnings)", reloader.summary());
        var bindings = reloader.bindings();
        assertEquals(Set.of("DuelRed", "DuelBlue"), bindings.keySet());
        for (var pair : List.of(List.of("DuelRed", "faction:duel_blue"), List.of("DuelBlue", "faction:duel_red"))) {
            var profile = bindings.get(pair.getFirst()).profile();
            assertTrue(profile.participants().playerGameModes().isEmpty(), pair.getFirst());
            assertEquals(List.of(pair.getLast()), profile.participants().entityAllow(), pair.getFirst());
            assertTrue(profile.baseAi().isEmpty(), "no BaseAI: the AIGoalSelectors of DuelBase are the base AI");
            assertEquals(Set.of("DuelStrike", "DuelBolt", "DuelLeap", "DuelGuard", "DuelHeal"), profile.choices()
                    .values().stream().flatMap(choice -> choice.mythicSkillId().stream()).collect(
                            java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void theFourDuelistsShareTheStatsGoalsRulesAndCooldownsOfTheirTemplate() throws Exception {
        var mobs = YamlConfiguration.loadConfiguration(FILES.resolve(DUEL_MOBS).toFile());
        var base = mobs.getConfigurationSection("DuelBase");
        assertEquals(List.of("0 clear", "1 meleeAttack", "2 randomStroll"), base.getStringList("AIGoalSelectors"));
        assertFalse(base.getBoolean("Modules.ThreatTable", true), "a player's hit never changes the target");
        for (var variant : List.of("DuelRed", "DuelRedClassic", "DuelBlue", "DuelBlueClassic")) {
            var entry = mobs.getConfigurationSection(variant);
            assertEquals("DuelBase", entry.getString("Template"), variant);
            for (var inherited : List.of("Type", "Health", "Damage", "Options", "Modules", "AIGoalSelectors")) {
                assertFalse(entry.isSet(inherited), variant + " must inherit " + inherited);
            }
            var rival = variant.startsWith("DuelRed") ? "duel_blue" : "duel_red";
            assertEquals(List.of("0 clear", "1 nearestSpecificFaction{faction=" + rival + "}"),
                    entry.getStringList("AITargetSelectors"), variant);
            assertEquals(!variant.endsWith("Classic"), entry.getBoolean("Predictor"), variant);
            assertTrue(entry.getStringList("Skills").stream().noneMatch(line -> line.contains("DuelRule")
                    || line.contains("DuelTimeout")), variant + " gets the rules and the timeout from the template");
        }
        var skills = YamlConfiguration.loadConfiguration(FILES.resolve(DUEL_SKILLS).toFile());
        for (var rule : List.of("DuelRuleStrike", "DuelRuleBolt", "DuelRuleLeap", "DuelRuleGuard", "DuelRuleHeal")) {
            assertEquals("predictorcontrol false", skills.getStringList(rule + ".Conditions").getFirst(), rule);
        }
        for (var skill : List.of("DuelStrike", "DuelBolt", "DuelLeap", "DuelGuard", "DuelHeal")) {
            assertTrue(skills.isSet(skill + ".Cooldown"), skill);
            assertTrue(skills.getStringList(skill + ".Skills").stream().filter(line -> line.startsWith("damage"))
                    .allMatch(line -> line.endsWith("@target")), "damage only at @target: " + skill);
        }
    }

    @Test
    void theSignalsOfTheThePredictDuelistsNameTacticsOfTheirProfile() throws Exception {
        install("13-duelo");
        var reloader = reloader();
        reloader.reload();
        var mobs = YamlConfiguration.loadConfiguration(FILES.resolve(DUEL_MOBS).toFile());
        var signal = Pattern.compile("~onSignal:PREDICTOR_([A-Z_]+)$");
        for (var variant : List.of("DuelRed", "DuelBlue")) {
            var tactics = reloader.bindings().get(variant).profile().tactics().keySet().stream()
                    .map(tactic -> tactic.value().toUpperCase(Locale.ROOT)).toList();
            var signals = 0;
            for (var line : mobs.getStringList(variant + ".Skills")) {
                var matcher = signal.matcher(line);
                if (matcher.find()) {
                    signals++;
                    assertTrue(tactics.contains(matcher.group(1)), line);
                }
            }
            assertEquals(5, signals, variant);
            assertTrue(mobs.getString(variant + ".BossBar.Title").contains("<caster.predictor.tactic>"), variant);
        }
    }

    @Test
    void theSessionEighteenGivesTheCommandBlocksOfTheScriptAndTheLinesOfTheCode() throws Exception {
        var guide = sessionsGuide();
        var script = Files.readString(LOAD_SCRIPT);
        for (var text : List.of("## Sesión 18. Duelo de mobs controlados sin clave (M18)",
                "## Sesión 18 con clave. Duelo con Jev (M18, paso 18.4)", "DT-40",
                "`cp -R \"$PACK/13-duelo/.\" \"$BASE/\"`",
                "Profile catalog LOADED (revision <n>, 2 profiles read, 2 active, 0 errors, 0 warnings)",
                "`State: Jev UNCONFIGURED (no TypeSafe API key)`", "Session: DuelRed, profile duel_red, ACTIVE, level LOCAL",
                "Target: entity_1 (zombie) health", "stopped: <límite> reached", "14 000 000", "0,32 USD", "0,19 USD")) {
            assertTrue(guide.contains(text), text);
        }
        assertTrue(script.contains("## 13. Duelo de mobs controlados (M18.6)"));
        var blocks = codeBlocks(script);
        assertTrue(blocks.size() >= 4, "blocks of G13 §13: " + blocks.size());
        for (var block : blocks) {
            assertTrue(codeBlocks(guide).contains(block), "a block of G13 §13 differs in the sessions guide: " + block);
        }
        assertTrue(blocks.stream().anyMatch(block -> block.contains("/mm mobs kill DuelBlueClassic")
                && !block.contains("killall")));
        var checklist = Files.readString(PACK.resolve("Checklist-DT-y-casillas.md"));
        for (var row : List.of("| DT-40 |", "| M18.1 |", "| M18.6 |")) {
            assertTrue(checklist.contains(row), row);
        }
        assertTrue(Files.readString(PACK.resolve("README.md")).contains("| `13-duelo` |"));
        var template = Files.readString(PACK.resolve("Plantilla-informe-duelo.md"));
        assertTrue(template.contains("(../M18-Duelo-de-mobs-controlados.md)"));
        assertTrue(template.contains("(../Guion-servidor-0.0.1.md#13-duelo-de-mobs-controlados-m186)"));
        assertTrue(Files.exists(DOCUMENTS.resolve("evidencia/m18-simulador-2026-09-29/duel-local.md")));
    }

    @Test
    void noShippedPackOrDocumentedYamlChecksHealthInPercentWithTheAbsoluteHealthCondition() throws Exception {
        assertTrue(fencedLines("x\n```yaml\n  - health{h=<30%}\n```\n").contains("  - health{h=<30%}"));
        var absolute = Pattern.compile("(?<![A-Za-z])(health|hp)\\{[^}]*%");
        var roots = List.of(Path.of("src/main/resources"), FILES, Path.of("Guias"), DOCUMENTS);
        var checked = 0;
        for (var root : roots) {
            try (var walk = Files.walk(root)) {
                for (var file : walk.filter(Files::isRegularFile).filter(path -> path.toString().matches(
                        ".*\\.(yml|example|md|txt)$")).toList()) {
                    checked++;
                    var lines = file.toString().endsWith(".md") ? fencedLines(Files.readString(file))
                            : Files.readAllLines(file);
                    for (var index = 0; index < lines.size(); index++) {
                        assertFalse(absolute.matcher(lines.get(index)).find(), file + ":" + (index + 1)
                                + " (in a file or a fenced block) uses health{h=…%}; MythicMobs 5.13.1 compares "
                                + "absolute health there, use healthpercent{p=…%}");
                    }
                }
            }
        }
        assertTrue(checked > 50, "files checked: " + checked);
    }

    /** The lines inside the fenced blocks of a markdown file: the YAML and commands, not the prose that explains them. */
    private static List<String> fencedLines(String markdown) {
        var lines = new ArrayList<String>();
        var inside = false;
        for (var line : markdown.split("\n", -1)) {
            if (line.strip().startsWith("```")) {
                inside = !inside;
            } else if (inside) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The text and bash blocks of the section of G13 that starts at M18.6, or of the whole sessions guide. */
    private static List<String> codeBlocks(String markdown) {
        var start = markdown.indexOf("## 13. Duelo de mobs controlados (M18.6)");
        var text = start < 0 ? markdown : markdown.substring(start, markdown.indexOf("\n## ", start + 5));
        var blocks = new ArrayList<String>();
        var matcher = Pattern.compile("(?m)^```(?:text|bash)\\n(.*?)^```", Pattern.DOTALL).matcher(text);
        while (matcher.find()) {
            blocks.add(matcher.group(1).strip());
        }
        return blocks;
    }
}
