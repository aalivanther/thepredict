package akyrion.thepredict.config;

import akyrion.thepredict.adapter.server.binding.MythicProfileBinder;
import akyrion.thepredict.adapter.server.binding.MythicYaml;
import akyrion.thepredict.adapter.server.vocabulary.PredictorPlaceholders;
import akyrion.thepredict.adapter.server.vocabulary.TacticSignals;
import akyrion.thepredict.adapter.server.vocabulary.VocabularyRegistrar;
import akyrion.thepredict.admin.DebugCommandParser;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.UseCondition;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserGuidesTest {
    private static final Path AUTHOR = Path.of("Guias/Guia-del-autor.md");
    private static final Path ADMINISTRATOR = Path.of("Guias/Guia-del-administrador.md");
    private static final Pattern YAML_BLOCK = Pattern.compile("(?m)^( *)```yaml\\n(.*?)^\\1```", Pattern.DOTALL);
    private static final List<String> SECTIONS = List.of("jev", "decision", "debug", "profileDefaults", "participants",
            "participants.entities", "decision.tickLayer", "records", "storage", "difficulty", "difficulty.presets",
            "difficulty.worlds", "difficulty.presets.facil", "difficulty.presets.normal", "difficulty.presets.dificil",
            "difficulty.presets.caos", "difficulty.presets.vacio", "telegraph", "telegraph.minTicks",
            "telegraph.answerTicks", "stance", "zone", "group", "patterns", "memory", "learning",
            "learning.exploration", "learning.calibration", "learning.voi",
            "distill", "distill.autonomous", "distill.regime", "distill.drift", "autoconfig");
    private static final List<String> BUNDLED_EXAMPLE = List.of("/examples/MythicMobs/Mobs/ThePredictGuardian.yml",
            "/examples/MythicMobs/Skills/ThePredictGuardianSkills.yml", "/examples/AI/ThePredictGuardian.yml");

    @TempDir
    Path directory;

    @Test
    void everyYamlBlockOfBothGuidesIsValidYaml() throws Exception {
        for (var guide : List.of(AUTHOR, ADMINISTRATOR)) {
            var blocks = yamlBlocks(Files.readString(guide));
            assertFalse(blocks.isEmpty(), guide.toString());
            for (var block : blocks) {
                new YamlConfiguration().loadFromString(block);
            }
        }
    }

    @Test
    void everyProfileOfTheAuthorGuideLoadsAndBindsWithoutProblemsAgainstTheGuideMobsAndSkills() throws Exception {
        var blocks = yamlBlocks(Files.readString(AUTHOR));
        var mythic = new ArrayList<YamlConfiguration>();
        var profiles = new ArrayList<String>();
        for (var block : blocks) {
            if (block.contains("schemaVersion: 2")) {
                profiles.add(block);
            } else {
                mythic.add(enabled(block));
            }
        }
        assertTrue(profiles.size() >= 4, "profiles in the guide: " + profiles.size());
        var binder = new MythicProfileBinder(MythicYaml.definitions(mythic));
        for (var index = 0; index < profiles.size(); index++) {
            var folder = Files.createDirectories(directory.resolve("guide-" + index));
            Files.writeString(folder.resolve("profile.yml"), profiles.get(index));
            var loaded = new ProfileLoader().load(folder);
            assertEquals(List.of(), loaded.problems(), profiles.get(index));
            assertFalse(loaded.profiles().isEmpty(), profiles.get(index));
            var bound = binder.bind(loaded.profiles());
            var own = bound.problems().stream().filter(problem -> problem.profileId().isPresent()
                    || loaded.profiles().keySet().stream().anyMatch(mob -> problem.path().startsWith(mob + "."))).toList();
            assertTrue(own.stream().allMatch(problem -> problem.message().contains(
                    "telegraph has no visual (or audio) cue")), own + "\n" + profiles.get(index));
            assertEquals(loaded.profiles().keySet(), bound.profiles().keySet(), profiles.get(index));
        }
    }

    @Test
    void theAuthorGuideShowsTheThreeBundledExampleFilesVerbatim() throws Exception {
        var blocks = yamlBlocks(Files.readString(AUTHOR)).stream().map(UserGuidesTest::withoutComments).toList();
        for (var resource : BUNDLED_EXAMPLE) {
            assertTrue(blocks.contains(withoutComments(resource(resource))), resource);
        }
    }

    @Test
    void theAuthorGuideNamesEveryProfileFieldValueAndVocabularyName() throws Exception {
        var guide = Files.readString(AUTHOR);
        var names = new ArrayList<String>();
        Stream.of(ProfileSchema.PROFILE, ProfileSchema.TACTIC, ProfileSchema.CONTEXT, ProfileSchema.HEALTH,
                ProfileSchema.THREAT, ProfileSchema.TIMING, ProfileSchema.PARTICIPANTS,
                ProfileSchema.PARTICIPANT_ENTITIES, ProfilePostureParser.STANCES, ProfilePostureParser.TELEGRAPH,
                ProfilePostureParser.CHOICE_TELEGRAPH, ProfilePostureParser.POISE, ProfileZoneParser.ZONE,
                ProfileGroupParser.GROUP, ProfileSchema.DIFFICULTY).forEach(names::addAll);
        ProfileSchema.CHOICE.stream().filter(name -> !name.equals("Purpose")).forEach(names::add);
        names.addAll(List.of("MeleeReach", "RangedReach", "Band.Inner", "Band.Outer", "Retreat.Distance",
                "Retreat.Speed", "Approach.Speed", "Arena.Radius"));
        Stream.of(Movement.values(), PredictValue.values(), ChoiceKind.values(), SkillRole.values(), Delivery.values(),
                        UseCondition.values(), SkillRisk.values(), DamageTier.values(), CooldownClass.values())
                .flatMap(Stream::of).map(Enum::name).forEach(names::add);
        ContextRule.SHORTCUTS.forEach(shortcut -> names.add(shortcut.value()));
        names.addAll(VocabularyRegistrar.NAMES);
        for (var name : names) {
            if (name.equals("Memory")) {
                continue;
            }
            assertTrue(guide.contains("`" + name + "`") || guide.contains("`" + name + ":"), name);
        }
        for (var field : PredictorPlaceholders.Field.values()) {
            assertTrue(guide.contains("<" + field.placeholderName() + ">"), field.placeholderName());
        }
        assertTrue(guide.contains("`" + TacticSignals.PREFIX + "<TACTIC>`"));
        assertTrue(guide.contains("~onSignal:" + TacticSignals.PREFIX + "RETREAT_AND_DEFEND"));
        var administrator = Files.readString(ADMINISTRATOR);
        assertTrue(administrator.contains("`Memory.Mode`"), "Memory");
        assertTrue(administrator.contains("`Purpose`") || administrator.contains("`Purpose:`"), "Purpose");
        for (var name : ProfileSchema.PURPOSE) {
            assertTrue(administrator.contains("`" + name + "`") || administrator.contains("`" + name + ":"), name);
        }
    }

    @Test
    void theAuthorGuideExplainsPurposesTheLintAndThatTheSlamHitsPlayersOnly() throws Exception {
        var guide = Files.readString(AUTHOR);
        for (var text : List.of("`@PlayersInRadius`", "**no daña a otros mobs**",
                "usa `@target` o un targeter de entidades", "(pendiente de servidor, DT-34)")) {
            assertTrue(guide.contains(text), text);
        }
        var administrator = Files.readString(ADMINISTRATOR);
        for (var text : List.of("### 6.6 Propósito interno (`Purpose`) y migración a `Intention`",
                "is beyond RangedReach; Jev reads it as beyond ranged reach",
                "Tactics.keep_distance_and_strike: Movement KEEP_DISTANCE keeps the target beyond Band.Inner 5 but skill"
                        + " 'melee' reaches 2.5; this tactic never casts",
                "PredictValues say MELEE_ATTACK but Delivery is PROJECTILE; the declared purpose is used",
                "Nada se infiere de `PredictValues`")) {
            assertTrue(administrator.contains(text), text);
        }
    }

    @Test
    void theAdministratorGuideDocumentsEveryConfigurationKeyWithTheBundledDefault() throws Exception {
        var guide = Files.readString(ADMINISTRATOR);
        var bundled = bundledValues(resource("/AIConfig/config.yml"));
        var leaves = PluginConfigurationLoader.ALLOWED_PATHS.stream().filter(path -> !SECTIONS.contains(path)).toList();
        for (var path : leaves) {
            var row = guide.lines().filter(line -> line.startsWith("| `" + path + "` |")).toList();
            assertEquals(1, row.size(), path);
            var cells = row.getFirst().split(" \\| ");
            assertEquals(5, cells.length, row.getFirst());
            if (bundled.containsKey(path)) {
                assertEquals("`" + bundled.get(path) + "`", cells[3], path);
            }
        }
        assertFalse(bundled.containsKey("decision.maxReadyChecksPerTick"), "the obsolete key is not shipped");
        assertEquals(leaves.size(), bundled.size() + 2,
                "only decision.maxDecisionAgeMillis and the obsolete decision.maxReadyChecksPerTick are absent");
    }

    @Test
    void theAdministratorGuideGivesACauseAndAnActionForEveryServiceStateAndFallbackReason() throws Exception {
        var guide = Files.readString(ADMINISTRATOR);
        var names = new ArrayList<String>();
        Stream.of(ServiceState.values(), FallbackReason.values()).flatMap(Stream::of).map(Enum::name).forEach(names::add);
        for (var name : names) {
            var row = guide.lines().filter(line -> line.startsWith("| `" + name + "` |")).toList();
            assertEquals(1, row.size(), name);
            var cells = row.getFirst().substring(2, row.getFirst().length() - 2).split(" \\| ");
            assertEquals(3, cells.length, row.getFirst());
            assertFalse(cells[1].isBlank() || cells[2].isBlank(), row.getFirst());
        }
    }

    @Test
    void theAdministratorGuideListsEveryCommandAndPermission() throws Exception {
        var guide = Files.readString(ADMINISTRATOR).replace("\\|", "|");
        for (var usage : DebugCommandParser.USAGE) {
            assertTrue(guide.contains("`" + usage + "`"), usage);
        }
        for (var text : List.of("`/thepredict status`", "`/thepredict check`", "`/thepredict inspect <nearest|uuid>`",
                "**`thepredict.admin`** tiene `default: op`", "**`thepredict.debug` tiene `default: false`",
                "You need the thepredict.debug permission to use debug tools")) {
            assertTrue(guide.contains(text), text);
        }
    }

    /**
     * The review of the guides of 2026-10-17 (DOC-09 and section B): every key of {@code config.yml} this build does
     * not read is marked as such, the per-tick table of the author guide lists every reaction, and the causes that
     * {@code status} shows are all named.
     */
    @Test
    void theGuidesNameTheKeysWithoutEffectEveryReactionAndEveryCauseOfStatus() throws Exception {
        var administrator = Files.readString(ADMINISTRATOR);
        for (var path : InertKeys.PATHS) {
            var row = administrator.lines().filter(line -> line.startsWith("| `" + path + "` |")).toList();
            assertEquals(1, row.size(), path);
            assertTrue(row.getFirst().contains("Sin efecto en esta versión"), path);
        }
        assertTrue(administrator.contains("`" + InertKeys.NO_EFFECT + "`"));
        var author = Files.readString(AUTHOR);
        for (var reaction : akyrion.thepredict.domain.Reaction.values()) {
            assertTrue(author.contains("| `" + reaction.name() + "` |"), reaction.name());
        }
        Stream.of(akyrion.thepredict.domain.DiscardReason.values(), akyrion.thepredict.domain.FailureKind.values(),
                        akyrion.thepredict.engine.DecisionDispatcher.Outcome.values()).flatMap(Stream::of)
                .map(Enum::name).forEach(name -> assertTrue(administrator.contains("`" + name + "`"), name));
        assertFalse(administrator.contains("`debug/`") || administrator.contains("records/<día>"));
    }

    /**
     * The storage answers the administrator reads in the guide are the ones the code gives: the overlay codes of
     * {@code autoconfig}, the reserved memory backend, {@code eval report} and the verdicts-only writer (CAR-03,
     * CAR-08, CAR-13, JS-13). {@code author-base.txt} is gone.
     */
    @Test
    void theAdministratorGuideNamesTheStorageAnswersAsTheCodeGivesThem() throws Exception {
        var guide = Files.readString(ADMINISTRATOR);
        for (var code : List.of("OVERLAY_RAISES_GLOBAL", "OVERLAY_UNMATCHED", "OVERLAY_DUPLICATE", "OVERLAY_STALE",
                "OVERLAY_INVALID", "OVERLAY_UNREADABLE")) {
            assertTrue(guide.contains("`" + code + "`"), code);
        }
        assertTrue(guide.contains("`" + akyrion.thepredict.adapter.storage.AdminTasks.EVAL_REPORT_UNAVAILABLE + "`"));
        assertTrue(guide.contains(MemoryConfiguration.MYSQL_UNAVAILABLE));
        assertTrue(guide.contains("`DISABLED " + akyrion.thepredict.adapter.storage.StorageService.VERDICTS_ONLY + "`"));
        assertTrue(guide.contains("<huella>-<etapa>.json"));
        assertFalse(guide.contains("author-base.txt") || guide.contains("no disponible en esta versión"));
    }

    @Test
    void theUserDocumentsAndTheTestPackNeverHoldAKeyOrAnEnvironmentValue() throws Exception {
        var secrets = List.of(Pattern.compile("TYPESAFE_API_KEY=[A-Za-z0-9]"), Pattern.compile("Bearer [A-Za-z0-9._~+/-]{16,}"),
                Pattern.compile("apiKey: *['\"][^'\"<]{8,}['\"]"));
        var documents = new ArrayList<Path>(List.of(AUTHOR, ADMINISTRATOR, Path.of("README.md"), Path.of("CHANGELOG.md")));
        try (var pack = Files.walk(Path.of("DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas"))) {
            pack.filter(Files::isRegularFile).forEach(documents::add);
        }
        for (var document : documents) {
            var text = Files.readString(document);
            for (var secret : secrets) {
                assertFalse(secret.matcher(text).find(), document + " " + secret);
            }
        }
        assertTrue(documents.size() > 20);
    }

    private static YamlConfiguration enabled(String block) throws InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(block);
        for (var id : yaml.getKeys(false)) {
            if (yaml.isSet(id + ".Type")) {
                yaml.set(id + ".Predictor", true);
            }
        }
        return yaml;
    }

    static List<String> yamlBlocks(String markdown) {
        var blocks = new ArrayList<String>();
        var matcher = YAML_BLOCK.matcher(markdown);
        while (matcher.find()) {
            var indent = matcher.group(1).length();
            blocks.add(matcher.group(2).lines().map(line -> line.length() >= indent ? line.substring(indent) : line)
                    .reduce("", (text, line) -> text + line + "\n"));
        }
        return blocks;
    }

    static String withoutComments(String yaml) {
        return yaml.lines().filter(line -> !line.startsWith("#")).reduce("", (text, line) -> text + line + "\n").strip();
    }

    /**
     * The leaf values of {@code config.yml} by full path; nested sections, such as {@code participants.entities} since
     * M17, are followed by their indentation of two spaces per level.
     */
    private static Map<String, String> bundledValues(String configuration) {
        var values = new LinkedHashMap<String, String>();
        var sections = new ArrayList<String>();
        for (var line : configuration.lines().toList()) {
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            var depth = (line.length() - line.stripLeading().length()) / 2;
            var key = line.trim().substring(0, line.trim().indexOf(':'));
            var value = line.trim().substring(line.trim().indexOf(':') + 1).trim();
            while (sections.size() > depth) {
                sections.removeLast();
            }
            var path = sections.isEmpty() ? key : String.join(".", sections) + "." + key;
            if (value.isEmpty()) {
                sections.add(key);
            } else {
                values.put(path, value);
            }
        }
        return values;
    }

    private String resource(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream(name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theGuidesExplainTheQuestionDetailTheCostTableTheEffectivenessAndTheWarningsOfM16() throws Exception {
        var administrator = Files.readString(ADMINISTRATOR);
        for (var text : List.of("### 7.1 Costo y capacidad por nivel de detalle (`decision.questionDetail`)",
                "| `compact` |", "| `standard` |", "| `full` (default) |", "| `full` + idoneidad (`fitChecks`) |",
                "`outcomes: level=JEV", "`contingencies: asked=", "`NOTHING_TO_ASK`", "`PREPARE_FAILED`",
                "`options_merged=`", "`Tactics.<a> and Tactics.<b> describe the same option to Jev; Jev only sees '<a>'`",
                "### 7.2 Umbral de confianza (`minimumConfidence`)", "`Outcomes:`")) {
            assertTrue(administrator.contains(text), text);
        }
        var author = Files.readString(AUTHOR);
        for (var text : List.of("### 4.8 Cómo ve Jev cada opción (`QuestionDetail`)",
                "is ignored: fit checks are only sent with QuestionDetail full, and this profile uses compact",
                "must be false while decision.fitChecksAllowed is false", "`note:`")) {
            assertTrue(author.contains(text), text);
        }
    }

    @Test
    void theGuidesDocumentEverySelectorOfM17WithItsNativeApiAndWhatJevReceivesOfAnEntity() throws Exception {
        var administrator = Files.readString(ADMINISTRATOR);
        var author = Files.readString(AUTHOR);
        for (var guide : List.of(administrator, author)) {
            for (var selector : List.of("`type:", "`tag:", "`category:", "`kind:", "`mythic:", "`faction:", "`team:",
                    "`tagged:", "`predictor`", "Registry.ENTITY_TYPE", "Tag.REGISTRY_ENTITY_TYPES", "getSpawnCategory",
                    "getActiveMob", "getFaction", "getEntityTeam", "getScoreboardTags")) {
                assertTrue(guide.contains(selector), selector);
            }
            assertTrue(guide.contains("/mm reload"), "DT-39");
            assertFalse(guide.contains("/thepredict reload") || guide.contains("/thepredict setup"),
                    "the command has no reload nor setup subcommand");
        }
        for (var text : List.of("### 4.4 `participants` (quién puede ser participante u objetivo)",
                "`perceiveNonTargets`", "≈ 1 884", "≈ 0,28 USD", "0,45")) {
            assertTrue(administrator.contains(text), text);
        }
        for (var text : List.of("### 4.9 Participantes y objetivos (`Participants`)", "`entity_1 is a zombie`",
                "`one player, one other creature and one mob are involved`", "Qué ve un mob de otro mob",
                "DT-36", "DT-37", "DT-38")) {
            assertTrue(author.contains(text), text);
        }
    }
}
