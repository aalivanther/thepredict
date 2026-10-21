package akyrion.thepredict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dependency rules D1 to D9 over {@code src/main/java}, plus the T3 rule of {@code adapter.server.vocabulary}. Since
 * M12 no package is exempt: the composition root {@code akyrion.thepredict} has its own rule (D8), may wire every
 * package and is reached by none, and only {@code ThePredictPlugin} is public in it.
 *
 * <p>Every rule is name-based: it checks imports and fully qualified names, not calls. The T3 rule rejects
 * {@code org.bukkit} outside {@code org.bukkit.event} and {@code io.lumine.mythic.bukkit} outside its
 * events, but it cannot catch a Bukkit-backed call reached through an allowed type, such as anything in
 * {@code io.lumine.mythic.core}, {@code AbstractEntity.getBukkitEntity()} or
 * {@code AbstractEntity.getLocation()}. Code review covers those calls.
 */
class ArchitectureRulesTest {
    private static final String BASE_PACKAGE = "akyrion.thepredict";
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;");
    private static final Pattern QUALIFIED = Pattern.compile("(?<![\\w.])((?:org\\.bukkit|io\\.papermc"
            + "|com\\.destroystokyo\\.paper|io\\.lumine\\.mythic|java\\.net\\.http|com\\.google|javax"
            + "|org\\.slf4j|net\\.kyori|akyrion\\.thepredict)\\.[\\w.]*)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern CHAR_LITERAL = Pattern.compile("'(?:\\\\.|[^'\\\\])'");
    private static final String LINE_COMMENT = "/".repeat(2);
    private static final Pattern THREAT_PORT_CALL = Pattern.compile("\\.\\s*addThreat\\s*\\(|::\\s*addThreat\\b");
    private static final Pattern NATIVE_THREAT_WRITE = Pattern.compile(
            "\\bthreat(?:Gain|Loss)\\s*\\(|::\\s*threat(?:Gain|Loss)\\b");
    private static final Pattern FORBIDDEN_THREAT_WRITE = Pattern.compile(
            "\\b(?:threatSet|Taunt|taunt|reduceThreat)\\s*\\(|::\\s*(?:threatSet|Taunt|taunt|reduceThreat)\\b"
            + "|getThreatTable\\s*\\(\\s*\\)\\s*\\.\\s*(?:put\\w*|merge|replace\\w*|remove\\w*|compute\\w*|clear)\\s*\\(");

    private static final Pattern LOOSE_MATH = Pattern.compile("(?<![A-Za-z])Math\\.(sin|cos|tan|asin|acos|atan|atan2"
            + "|sinh|cosh|tanh|exp|expm1|log|log10|log1p|pow|cbrt|hypot|toDegrees|toRadians)\\s*\\(");
    private static final List<String> LOOSE_MATH_WAITING = List.of();
    private static final Pattern STATIC_NOT_FINAL = Pattern.compile("^\\s*(?:(?:public|protected|private)\\s+)?"
            + "static\\s+(?!(?:final|class|interface|enum|record|abstract|sealed|void|synchronized|native)\\b)(?!<)"
            + "[\\w<>\\[\\],.?\\s]+?\\s+\\w+\\s*(?:=|;)", Pattern.MULTILINE);
    private static final Pattern STATIC_MUTABLE_VALUE = Pattern.compile("\\bstatic\\s+final\\b[^;={}()]*=\\s*new\\s+"
            + "(?:ArrayList|LinkedList|HashMap|LinkedHashMap|TreeMap|HashSet|LinkedHashSet|TreeSet|EnumMap|EnumSet"
            + "|ArrayDeque|PriorityQueue|ConcurrentHashMap|CopyOnWriteArrayList|WeakHashMap|IdentityHashMap"
            + "|StringBuilder|Atomic\\w+)\\b");
    private static final Pattern SHARED_ARRAY = Pattern.compile("^\\s*(?:(?:public|protected)\\s+)?static\\s+final"
            + "\\s+[\\w<>.]+\\s*\\[\\]", Pattern.MULTILINE);

    private static final Pattern RETIRED_PROVIDER = Pattern.compile("open[\\s_-]*router", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUBLIC_TYPE = Pattern.compile("^public\\s+(?:final\\s+|abstract\\s+)*"
            + "(?:class|record|interface|enum)\\s+(\\w+)", Pattern.MULTILINE);
    private static final List<String> RETIRED_BY_M09 = List.of("MythicPredictionRunner", "RotatingMobCursor",
            "DecisionReuseCache", "RemoteRequestBudget", "ServerThreadInbox", "MobDecisionStates",
            "PaperCombatSnapshotReader");
    private static final List<String> RETIRED_BY_M10 = List.of("DecisionFreshness", "adapter.mythicmobs",
            "untilExecution");
    private static final List<String> RETIRED_BY_M12 = List.of("ProviderSettings", "RuntimeSettings",
            "PredictorProfile", "DecisionProviderFactory", "ProviderHealth", "ProviderFailure", "PredictionPlanner",
            "DecisionPlan", "CombatSnapshot", "VitalSnapshot", "StatusSnapshot", "ThePredictStatusCommand",
            "DebugCommandHandler", "DebugProfileGoals", "DebugCapture", "TriggeredSkillCast", "DebugGoalPreset",
            "DebugPresetInstaller", "DebugAutoInstaller", "InstallMeasurement", "ConditionTrace", "ProbeLabels",
            "predictorprobe", "PredictorMobLocator", "damageTotals");

    private static final Map<String, Rule> RULES = Map.of(
            "domain", new Rule("D1", List.of("java."), List.of("domain")),
            "engine", new Rule("D2", List.of("java."), List.of("domain", "engine")),
            "config", new Rule("D3", List.of("java.", "org.bukkit.configuration."), List.of("domain", "config")),
            "adapter.jev", new Rule("D4", List.of("java.", "java.net.http.", "com.google.gson."),
                    List.of("domain", "engine", "adapter.jev")),
            "adapter.storage", new Rule("D11", List.of("java.", "org.sqlite."),
                    List.of("domain", "engine", "adapter.storage")),
            "adapter.server", new Rule("D5", List.of("java.", "org.bukkit.", "io.papermc.",
                    "com.destroystokyo.paper.", "io.lumine.mythic."), List.of("domain", "engine", "adapter.server")),
            "adapter.server.vocabulary", new Rule("T3", List.of("java.", "org.bukkit.event.", "io.lumine.mythic.api.",
                    "io.lumine.mythic.core.", "io.lumine.mythic.bukkit.events."),
                    List.of("domain", "engine", "adapter.server.vocabulary")),
            "admin", new Rule("D6", List.of("java.", "org.bukkit.", "net.kyori.adventure."),
                    List.of("domain", "engine", "admin")),
            "", new Rule("D8", List.of("java.", "org.bukkit.", "io.papermc.", "com.destroystokyo.paper.",
                    "io.lumine.mythic."), List.of("domain", "engine", "config", "adapter", "admin")));

    @Test
    void currentSourcesRespectTheDependencyRules() throws IOException {
        var violations = violations(Path.of("src", "main", "java"));
        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void bukkitImportInDomainBreaksRuleD1(@TempDir Path root) throws IOException {
        write(root, "domain", "Leaky", "import org.bukkit.Bukkit;");
        var violations = violations(root);
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("Leaky.java:3"), violations.getFirst());
        assertTrue(violations.getFirst().contains("D1 org.bukkit.Bukkit"), violations.getFirst());
    }

    @Test
    void fullyQualifiedReferenceWithoutImportIsDetected(@TempDir Path root) throws IOException {
        write(root, "domain", "Qualified", "class Qualified { Object server = org.bukkit.Bukkit.getServer(); }");
        var violations = violations(root);
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("D1 org.bukkit.Bukkit.getServer"), violations.getFirst());
    }

    @Test
    void adapterImportingAnotherAdapterBreaksRuleD7(@TempDir Path root) throws IOException {
        write(root, "adapter.server.goals", "Crossing", "import akyrion.thepredict.adapter.jev.SystemOneClient;");
        var violations = violations(root);
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("D7"), violations.getFirst());
    }

    @Test
    void theDuelRecorderFollowsRuleD5AndReachesTheEngineOnlyThroughItsPorts(@TempDir Path root) throws IOException {
        write(root, "adapter.server.duel", "Recorder", String.join("\n",
                "import akyrion.thepredict.engine.DecisionTap;",
                "import akyrion.thepredict.engine.DuelRecording;",
                "import akyrion.thepredict.adapter.server.context.ParticipantLookup;",
                "import akyrion.thepredict.adapter.server.events.ServerThread;",
                "import io.lumine.mythic.bukkit.events.MythicSkillEvent;",
                "import org.bukkit.event.entity.EntityDamageEvent;"));
        write(root, "adapter.server.duel", "Leaking", String.join("\n",
                "import akyrion.thepredict.adapter.jev.SystemOneClient;",
                "import akyrion.thepredict.admin.ThePredictCommand;",
                "import com.google.gson.Gson;"));
        write(root, "admin", "Command", String.join("\n",
                "import akyrion.thepredict.engine.DuelRecording;",
                "import akyrion.thepredict.adapter.server.duel.DuelRecorder;"));
        write(root, "engine", "DuelRecording", "import akyrion.thepredict.adapter.server.duel.DuelRecorder;");
        var violations = violations(root);
        assertEquals(5, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("Leaking.java:3 D7")));
        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("Command.java:4 D6")));
        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("DuelRecording.java:3 D2")));
    }

    @Test
    void vocabularyMayListenToEventsButNeverCallBukkit(@TempDir Path root) throws IOException {
        write(root, "adapter.server.vocabulary", "Listening", String.join("\n",
                "import org.bukkit.event.EventHandler;",
                "import org.bukkit.event.Listener;",
                "import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;",
                "import io.lumine.mythic.api.skills.conditions.IEntityCondition;",
                "import akyrion.thepredict.engine.IntentBoard;"));
        write(root, "adapter.server.vocabulary", "Calling", String.join("\n",
                "import org.bukkit.Bukkit;",
                "import io.lumine.mythic.bukkit.MythicBukkit;",
                "class Calling { Object entity = org.bukkit.entity.EntityType.ZOMBIE; }"));
        var violations = violations(root);
        assertEquals(3, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().allMatch(violation -> violation.startsWith("Calling.java:")
                && violation.contains(" T3 ")), String.join(System.lineSeparator(), violations));
    }

    @Test
    void vocabularyMayNotReachOtherServerCapabilities(@TempDir Path root) throws IOException {
        write(root, "adapter.server.vocabulary", "Reaching", "import akyrion.thepredict.adapter.server.goals.GoalOwnership;");
        write(root, "adapter.server.goals", "Reading", "import akyrion.thepredict.adapter.server.vocabulary.TacticSignals;");
        var violations = violations(root);
        assertEquals(1, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.getFirst().contains("Reaching.java:3 T3"), violations.getFirst());
    }

    @Test
    void goalAndContextPackagesFollowRuleD5AndMayShareServerCapabilities(@TempDir Path root) throws IOException {
        write(root, "adapter.server.goals", "Installer", String.join("\n",
                "import akyrion.thepredict.adapter.server.context.ShieldGeometry;",
                "import akyrion.thepredict.adapter.server.binding.BrainSpecies;",
                "import akyrion.thepredict.adapter.server.vocabulary.PredictorMoveCondition;",
                "import akyrion.thepredict.engine.MobControl;",
                "import com.destroystokyo.paper.entity.ai.MobGoals;",
                "import io.lumine.mythic.core.config.MythicLineConfigImpl;"));
        write(root, "adapter.server.context", "Geometry", String.join("\n",
                "import io.papermc.paper.datacomponent.DataComponentTypes;",
                "import akyrion.thepredict.admin.DebugRequest;",
                "import com.google.gson.Gson;"));
        var violations = violations(root);
        assertEquals(2, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().allMatch(violation -> violation.startsWith("Geometry.java:")
                && violation.contains(" D5 ")), String.join(System.lineSeparator(), violations));
    }

    @Test
    void theMobControlPortInEngineMayNotReachTheGoalAdapterOrPaper(@TempDir Path root) throws IOException {
        write(root, "engine", "MobControl", String.join("\n",
                "import akyrion.thepredict.domain.InstallResult;",
                "import akyrion.thepredict.adapter.server.goals.GoalOwnership;",
                "import com.destroystokyo.paper.entity.ai.MobGoals;"));
        var violations = violations(root);
        assertEquals(2, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().allMatch(violation -> violation.contains(" D2 ")),
                String.join(System.lineSeparator(), violations));
    }

    @Test
    void lifecycleListenersFollowRuleD5AndTheSessionEngineStaysFreeOfBukkit(@TempDir Path root) throws IOException {
        write(root, "adapter.server.events", "Listening", String.join("\n",
                "import akyrion.thepredict.engine.SessionLifecycle;",
                "import akyrion.thepredict.domain.TacticalProfile;",
                "import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;",
                "import io.lumine.mythic.core.mobs.MobExecutor;",
                "import org.bukkit.event.world.EntitiesLoadEvent;",
                "import akyrion.thepredict.adapter.server.goals.GoalOwnership;"));
        write(root, "adapter.server.events", "Leaking", String.join("\n",
                "import akyrion.thepredict.config.ProfileCatalog;",
                "import akyrion.thepredict.adapter.mythicmobs.MythicPredictionRunner;"));
        write(root, "engine", "SessionRegistry", String.join("\n",
                "import akyrion.thepredict.domain.MobKey;",
                "import org.bukkit.event.world.EntitiesLoadEvent;",
                "import io.lumine.mythic.core.mobs.ActiveMob;"));
        var violations = violations(root);
        assertEquals(4, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("Leaking.java:3 D5")));
        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("Leaking.java:4 D7")));
        assertEquals(2, violations.stream().filter(violation -> violation.startsWith("SessionRegistry.java:")
                && violation.contains(" D2 ")).count());
    }

    @Test
    void theCombatCaptureImplementsTheEnginePortWithinRuleD5AndReachesNoOtherAdapter(@TempDir Path root)
            throws IOException {
        write(root, "adapter.server.context", "Capture", String.join("\n",
                "import akyrion.thepredict.engine.CombatReader;",
                "import akyrion.thepredict.engine.ServerClock;",
                "import akyrion.thepredict.adapter.server.binding.BoundProfile;",
                "import akyrion.thepredict.domain.CombatFacts;",
                "import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;",
                "import io.lumine.mythic.core.skills.SkillCondition;",
                "import org.bukkit.entity.Player;"));
        write(root, "adapter.server.context", "Leaking", String.join("\n",
                "import akyrion.thepredict.adapter.jev.SystemOneClient;",
                "import akyrion.thepredict.config.DecisionSettings;",
                "import net.kyori.adventure.key.Key;",
                "import akyrion.thepredict.admin.DebugRequest;"));
        var violations = violations(root);
        assertEquals(4, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().allMatch(violation -> violation.startsWith("Leaking.java:")),
                String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("Leaking.java:3 D7")));
    }

    @Test
    void theCombatReaderPortAndTheContextEventsStayFreeOfTheAdapterAndOfPaper(@TempDir Path root) throws IOException {
        write(root, "engine", "CombatReader", String.join("\n",
                "import akyrion.thepredict.domain.CaptureContext;",
                "import akyrion.thepredict.adapter.server.context.CombatCapture;",
                "import org.bukkit.entity.Player;"));
        write(root, "engine", "ContextEvents", String.join("\n",
                "import akyrion.thepredict.domain.EventType;",
                "import org.bukkit.event.player.PlayerInputEvent;"));
        write(root, "adapter.server.events", "Listening", String.join("\n",
                "import akyrion.thepredict.engine.ContextEvents;",
                "import io.papermc.paper.event.player.PlayerShieldDisableEvent;",
                "import com.destroystokyo.paper.event.entity.EntityPathfindEvent;",
                "import akyrion.thepredict.adapter.server.context.ShieldGeometry;"));
        var violations = violations(root);
        assertEquals(3, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().allMatch(violation -> violation.contains(" D2 ")),
                String.join(System.lineSeparator(), violations));
    }

    @Test
    void engineImportingConfigBreaksRuleD2(@TempDir Path root) throws IOException {
        write(root, "engine", "Reader", "import akyrion.thepredict.config.ProfileLoader;");
        assertTrue(violations(root).getFirst().contains("D2 akyrion.thepredict.config.ProfileLoader"));
    }

    @Test
    void httpClientIsAllowedOnlyInTheJevAdapter(@TempDir Path root) throws IOException {
        write(root, "domain", "Remote", "import java.net.http.HttpClient;");
        write(root, "adapter.jev", "Client", "import java.net.http.HttpClient;\nimport com.google.gson.Gson;");
        var violations = violations(root);
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("Remote.java:3 D1"), violations.getFirst());
    }

    @Test
    void gsonIsAllowedOnlyInTheJevAdapter(@TempDir Path root) throws IOException {
        for (var pkg : List.of("domain", "engine", "config", "adapter.server.context", "admin")) {
            write(root, pkg, "Parsing", "import com.google.gson.JsonParser;");
        }
        write(root, "adapter.jev", "Parsing", "import com.google.gson.JsonParser;\nimport com.google.gson.stream.JsonReader;");
        var violations = violations(root);
        assertEquals(5, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().noneMatch(violation -> violation.contains("adapter/jev")),
                String.join(System.lineSeparator(), violations));
    }

    @Test
    void theDecisionClientPortStaysFreeOfTheJevAdapterAndOfHttp(@TempDir Path root) throws IOException {
        write(root, "engine", "DecisionClient", String.join("\n",
                "import akyrion.thepredict.domain.DecisionRequest;",
                "import akyrion.thepredict.adapter.jev.SystemOneClient;",
                "import java.net.http.HttpClient;"));
        write(root, "adapter.jev", "Client", String.join("\n",
                "import akyrion.thepredict.engine.DecisionClient;",
                "import akyrion.thepredict.engine.ServerClock;",
                "import akyrion.thepredict.adapter.server.context.CombatCapture;",
                "import akyrion.thepredict.config.JevSettings;",
                "import org.bukkit.Bukkit;"));
        var violations = violations(root);
        assertEquals(5, violations.size(), String.join(System.lineSeparator(), violations));
        assertEquals(2, violations.stream().filter(violation -> violation.startsWith("DecisionClient.java:")
                && violation.contains(" D2 ")).count());
        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("Client.java:5 D7")));
        assertEquals(2, violations.stream().filter(violation -> violation.startsWith("Client.java:")
                && violation.contains(" D4 ")).count());
    }

    @Test
    void commentsAndStringLiteralsAreIgnored(@TempDir Path root) throws IOException {
        write(root, "domain", "Quiet", String.join("\n",
                "/** Mentions {@link org.bukkit.Bukkit} in Javadoc. */",
                "class Quiet {",
                "    String text = \"org.bukkit.Bukkit\";",
                "    int value = 1; " + LINE_COMMENT + " org.bukkit.Bukkit",
                "    /* io.lumine.mythic.core",
                "       com.google.gson.Gson */",
                "}"));
        assertTrue(violations(root).isEmpty(), String.join(System.lineSeparator(), violations(root)));
    }

    @Test
    void packageDeclarationMustMatchThePath(@TempDir Path root) throws IOException {
        var directory = root.resolve("akyrion/thepredict/domain");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("Misplaced.java"), "package akyrion.thepredict.engine;\n\nclass Misplaced {}\n");
        var violations = violations(root);
        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("D9 package declaration 'akyrion.thepredict.engine'"),
                violations.getFirst());
    }

    @Test
    void qualifiedGuavaJavaxAndLoggingReferencesAreDetected(@TempDir Path root) throws IOException {
        write(root, "domain", "Libraries", String.join("\n",
                "class Libraries {",
                "    Object list = com.google.common.collect.ImmutableList.of();",
                "    Object logger = org.slf4j.LoggerFactory.getLogger(Libraries.class);",
                "    @javax.annotation.Nullable Object value;",
                "}"));
        var violations = violations(root);
        assertEquals(3, violations.size(), String.join(System.lineSeparator(), violations));
        assertTrue(violations.stream().allMatch(violation -> violation.contains(" D1 ")));
    }

    @Test
    void packageWithoutRuleIsReported(@TempDir Path root) throws IOException {
        write(root, "stance", "Stance", "class Stance {}");
        assertTrue(violations(root).getFirst().contains("no dependency rule"));
    }

    @Test
    void noProductionSourceNamesAClassRetiredByTheCoordinator() throws IOException {
        var found = new ArrayList<String>();
        for (var file : javaSources()) {
            var text = Files.readString(file);
            RETIRED_BY_M09.stream().filter(text::contains).forEach(name -> found.add(file.getFileName() + ": " + name));
        }
        assertTrue(found.isEmpty(), String.join(System.lineSeparator(), found));
    }

    @Test
    void noProductionSourceNamesWhatTheTacticalExecutionRetired() throws IOException {
        var found = new ArrayList<String>();
        for (var file : javaSources()) {
            var text = Files.readString(file);
            RETIRED_BY_M10.stream().filter(text::contains).forEach(name -> found.add(file.getFileName() + ": " + name));
        }
        assertTrue(found.isEmpty(), String.join(System.lineSeparator(), found));
    }

    @Test
    void theExecutionAdapterFollowsRuleD5AndTheCombatActionsPortStaysFreeOfIt(@TempDir Path root) throws IOException {
        write(root, "adapter.server.execution", "Actions", String.join("\n",
                "import akyrion.thepredict.engine.CombatActions;",
                "import akyrion.thepredict.adapter.server.context.ShieldGeometry;",
                "import akyrion.thepredict.adapter.server.vocabulary.TacticSignals;",
                "import io.lumine.mythic.core.mobs.ActiveMob;",
                "import org.bukkit.entity.HumanEntity;"));
        write(root, "adapter.server.execution", "Leaking", String.join("\n",
                "import akyrion.thepredict.adapter.jev.SystemOneClient;",
                "import akyrion.thepredict.config.DecisionSettings;",
                "import akyrion.thepredict.admin.DebugRequest;"));
        write(root, "engine", "CombatActions", String.join("\n",
                "import akyrion.thepredict.domain.SkillChoice;",
                "import akyrion.thepredict.adapter.server.execution.CombatActionsAdapter;",
                "import io.lumine.mythic.api.skills.Skill;",
                "import org.bukkit.entity.Player;"));
        var violations = violations(root);
        assertEquals(6, violations.size(), String.join(System.lineSeparator(), violations));
        assertEquals(3, violations.stream().filter(violation -> violation.startsWith("Leaking.java:")).count());
        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("Leaking.java:3 D7")));
        assertEquals(3, violations.stream().filter(violation -> violation.startsWith("CombatActions.java:")
                && violation.contains(" D2 ")).count());
    }

    @Test
    void theEngineRunsOnOnePeriodicTaskScheduledByTheRoot() throws IOException {
        var timers = new ArrayList<String>();
        for (var file : javaSources()) {
            var lines = Files.readAllLines(file);
            for (var index = 0; index < lines.size(); index++) {
                if (lines.get(index).contains("runTaskTimer")) {
                    timers.add(file.getFileName() + ":" + (index + 1));
                }
            }
        }
        assertEquals(1, timers.size(), String.join(System.lineSeparator(), timers));
        assertTrue(timers.getFirst().startsWith("ThePredictPlugin.java:"), timers.getFirst());
        var schedulers = new ArrayList<String>();
        for (var file : javaSources()) {
            var lines = Files.readAllLines(file);
            for (var index = 0; index < lines.size(); index++) {
                if (lines.get(index).contains("Bukkit.getScheduler()")) {
                    schedulers.add(file.getFileName() + ":" + (index + 1));
                }
            }
        }
        assertTrue(schedulers.stream().allMatch(hit -> hit.startsWith("ThePredictPlugin.java:")
                || hit.startsWith("ServerComponents.java:")), schedulers.toString());
        assertFalse(Files.readString(Path.of("src/main/resources/plugin.yml")).contains("folia-supported"));
    }

    @Test
    void domainAndEngineStayDeterministic() throws IOException {
        var banned = List.of("Math.random", "java.util.Random", "ThreadLocalRandom", "SplittableRandom",
                "UUID.randomUUID", "System.currentTimeMillis", "System.nanoTime", "Instant.now", "LocalDate.now");
        var hits = new ArrayList<String>();
        for (var file : javaSources()) {
            var path = file.toString().replace('\\', '/');
            if (!path.contains("/domain/") && !path.contains("/engine/")) {
                continue;
            }
            var text = codeOnly(Files.readString(file));
            for (var name : banned) {
                if (text.contains(name)) {
                    hits.add(file.getFileName() + " " + name);
                }
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }

    /**
     * C16 over every package of {@code domain} and {@code engine} (EFO-22, FO §3.10): a transcendental function goes
     * through {@link StrictMath}, so the simulator gives the same bytes on every JVM. {@code sqrt}, {@code round},
     * {@code max} and the like are exact. The waiting list names the domain files that still call a loose function;
     * their owner fixes them, and a fixed file has to leave the list, so the list can only shrink.
     */
    @Test
    void domainAndEngineCallOnlyStrictTranscendentalFunctions() throws IOException {
        var hits = new TreeSet<String>();
        for (var file : javaSources()) {
            var path = file.toString().replace('\\', '/');
            if ((path.contains("/domain/") || path.contains("/engine/"))
                    && LOOSE_MATH.matcher(codeOnly(Files.readString(file))).find()) {
                hits.add(file.getFileName().toString());
            }
        }
        assertEquals(new TreeSet<>(LOOSE_MATH_WAITING), hits, "C16: use StrictMath; a fixed file leaves"
                + " the waiting list");
        assertTrue(LOOSE_MATH.matcher("var a = Math.atan2(-dx, dz);").find());
        assertFalse(LOOSE_MATH.matcher("var a = StrictMath.atan2(-dx, dz) + Math.sqrt(x) + Math.max(a, b);").find());
    }

    /** Rule C4 at the composition root (CAR-24): the root never hands {@code null} to another package. */
    @Test
    void theRootNeverHandsNullToAnotherPackage() throws IOException {
        var hits = new ArrayList<String>();
        try (var listing = Files.list(Path.of("src", "main", "java", "akyrion", "thepredict"))) {
            for (var file : listing.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                if (codeOnly(Files.readString(file)).contains("orElse(null)")) {
                    hits.add(file.getFileName().toString());
                }
            }
        }
        assertEquals(List.of(), hits);
    }

    /**
     * D8 in {@code domain} (E-14, MZ-20): no mutable static state. A static field is final, never holds a mutable
     * collection, atomic or builder, and a static array, which any holder could write, stays private.
     */
    @Test
    void domainHoldsNoMutableStaticState() throws IOException {
        var hits = new ArrayList<String>();
        for (var file : javaSources()) {
            if (!file.toString().replace('\\', '/').contains("/domain/")) {
                continue;
            }
            var text = codeOnly(Files.readString(file));
            for (var rule : List.of(STATIC_NOT_FINAL, STATIC_MUTABLE_VALUE, SHARED_ARRAY)) {
                if (rule.matcher(text).find()) {
                    hits.add(file.getFileName() + " " + rule.matcher(text).results().findFirst().orElseThrow().group()
                            .strip());
                }
            }
        }
        assertEquals(List.of(), hits);
        assertTrue(STATIC_NOT_FINAL.matcher("    private static int created;").find());
        assertTrue(STATIC_NOT_FINAL.matcher("    static String last = \"\";").find());
        assertFalse(STATIC_NOT_FINAL.matcher("    private static int created(int a) {").find());
        assertFalse(STATIC_NOT_FINAL.matcher("    public static final int MAX = 3;").find());
        assertTrue(STATIC_MUTABLE_VALUE.matcher("static final Map<String, Integer> SEEN =\n new HashMap<>();").find());
        assertFalse(STATIC_MUTABLE_VALUE.matcher("static final List<String> NAMES = List.of(\"a\");").find());
        assertFalse(STATIC_MUTABLE_VALUE.matcher("static final class Pools {\n final Map<String, Long> m = new HashMap<>();")
                .find());
        assertTrue(SHARED_ARRAY.matcher("    public static final int[] TABLE = {1, 2};").find());
        assertFalse(SHARED_ARRAY.matcher("    private static final int[] TABLE = {1, 2};").find());
    }

    @Test
    void learnPackagesStayOnStrictDraws() throws IOException {
        var banned = List.of("Math.random", "java.util.Random", "ThreadLocalRandom", "SplittableRandom",
                "UUID.randomUUID", "System.currentTimeMillis", "System.nanoTime", "Instant.now", "LocalDate.now");
        var hits = new ArrayList<String>();
        var files = 0;
        for (var file : javaSources()) {
            var path = file.toString().replace('\\', '/');
            if (!path.contains("/domain/learn/") && !path.contains("/engine/learn/")) {
                continue;
            }
            files++;
            var text = codeOnly(Files.readString(file));
            for (var name : banned) {
                if (text.contains(name)) {
                    hits.add(file.getFileName() + " " + name);
                }
            }
        }
        assertTrue(files > 0, "learn packages");
        assertTrue(hits.isEmpty(), hits.toString());
    }

    @Test
    void learnPackagesObeyC23AndC16() throws IOException {
        var clocks = List.of("System.currentTimeMillis", "System.nanoTime", "Instant.now", "LocalDate.now");
        var draws = List.of("Math.random", "new Random", "ThreadLocalRandom", "SplittableRandom");
        var clockHits = new ArrayList<String>();
        var drawHits = new ArrayList<String>();
        var files = 0;
        for (var file : javaSources()) {
            var path = file.toString().replace('\\', '/');
            if (!path.contains("/domain/learn/") && !path.contains("/engine/learn/")) {
                continue;
            }
            files++;
            var text = codeOnly(Files.readString(file));
            for (var name : clocks) {
                if (text.contains(name)) {
                    clockHits.add(file.getFileName() + " " + name);
                }
            }
            for (var name : draws) {
                if (text.contains(name)) {
                    drawHits.add(file.getFileName() + " " + name);
                }
            }
        }
        assertTrue(files > 0, "learn packages");
        assertTrue(clockHits.isEmpty(), "C23 " + clockHits);
        assertTrue(drawHits.isEmpty(), "C16 " + drawHits);
    }

    @Test
    void distillPackagesStayOnStrictDraws() throws IOException {
        var banned = List.of("Math.random", "java.util.Random", "ThreadLocalRandom", "SplittableRandom",
                "UUID.randomUUID", "System.currentTimeMillis", "System.nanoTime", "Instant.now", "LocalDate.now");
        var hits = new ArrayList<String>();
        var files = 0;
        for (var file : javaSources()) {
            var path = file.toString().replace('\\', '/');
            if (!path.contains("/domain/distill/") && !path.contains("/engine/distill/")) {
                continue;
            }
            files++;
            var text = codeOnly(Files.readString(file));
            for (var name : banned) {
                if (text.contains(name)) {
                    hits.add(file.getFileName() + " " + name);
                }
            }
        }
        assertTrue(files > 0, "distill packages");
        assertTrue(hits.isEmpty(), hits.toString());
    }

    @Test
    void distillPackagesObeyC23AndC16() throws IOException {
        var clocks = List.of("System.currentTimeMillis", "System.nanoTime", "Instant.now", "LocalDate.now");
        var draws = List.of("Math.random", "new Random", "ThreadLocalRandom", "SplittableRandom");
        var clockHits = new ArrayList<String>();
        var drawHits = new ArrayList<String>();
        var files = 0;
        for (var file : javaSources()) {
            var path = file.toString().replace('\\', '/');
            if (!path.contains("/domain/distill/") && !path.contains("/engine/distill/")) {
                continue;
            }
            files++;
            var text = codeOnly(Files.readString(file));
            for (var name : clocks) {
                if (text.contains(name)) {
                    clockHits.add(file.getFileName() + " " + name);
                }
            }
            for (var name : draws) {
                if (text.contains(name)) {
                    drawHits.add(file.getFileName() + " " + name);
                }
            }
        }
        assertTrue(files > 0, "distill packages");
        assertTrue(clockHits.isEmpty(), "C23 " + clockHits);
        assertTrue(drawHits.isEmpty(), "C16 " + drawHits);
    }

    @Test
    void domainAndEngineDoNotChangeAttributesHealthLevelOrDamage() throws IOException {
        var banned = List.of("AttributeModifier", "setHealth", "setLevel", "addModifier", "setBaseValue");
        var hits = new ArrayList<String>();
        for (var file : javaSources()) {
            var path = file.toString().replace('\\', '/');
            if (!path.contains("/domain/") && !path.contains("/engine/")) {
                continue;
            }
            var text = codeOnly(Files.readString(file));
            for (var name : banned) {
                if (text.contains(name)) {
                    hits.add(file.getFileName() + " " + name);
                }
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
        var levels = new ArrayList<String>();
        for (var file : javaSources()) {
            if (codeOnly(Files.readString(file)).contains("setLevel")) {
                levels.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of("NativeLevelAdjuster.java"), levels);
    }

    @Test
    void thePatternPackageObeysC21WithNoException() throws IOException {
        var banned = List.of("PlayerInputEvent", "getCurrentInput", "getInventory(", "getEnderChest", "getFoodLevel",
                "getSaturation", "getLevel(", "MobGoals", "getBrain(", "getTarget(", "IntentBoard", "MobSession",
                "getThreatTable", "getLocation(", "getVelocity(");
        var hits = new ArrayList<String>();
        var root = Path.of("src/main/java/akyrion/thepredict/adapter/server/patterns");
        try (var files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var text = codeOnly(Files.readString(file));
                for (var name : banned) {
                    if (text.contains(name)) {
                        hits.add(file.getFileName() + " " + name);
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }

    @Test
    void thePredictAddsNoCueAndKeepsOnlyTheVanillaMeleeSwing() throws IOException {
        var gestures = List.of("playSound(", "spawnParticle(", "playEffect(", "swingHand(", "setAggressive(",
                "setGlowing(", "setGlowColorOverride(", "TextDisplay", "ItemDisplay", "BlockDisplay");
        var hits = new ArrayList<String>();
        for (var file : javaSources()) {
            var text = Files.readString(file);
            var name = file.getFileName().toString();
            if (name.equals("CombatActionsAdapter.java")) {
                text = text.replace("swingMainHand", "");
            } else if (text.contains("swingMainHand")) {
                hits.add(name + " swingMainHand");
            }
            for (var gesture : gestures) {
                if (text.contains(gesture)) {
                    hits.add(name + " " + gesture);
                }
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }

    @Test
    void nothingWritesAScoreboardTeam() throws IOException {
        var banned = List.of("registerNewTeam", "unregisterTeam", ".addEntry", ".removeEntry",
                "setAllowFriendlyFire", "setCanSeeFriendlyInvisibles", "Team.Option");
        var hits = new ArrayList<String>();
        for (var file : javaSources()) {
            var text = codeOnly(Files.readString(file));
            for (var name : banned) {
                if (text.contains(name)) {
                    hits.add(file.getFileName() + " " + name);
                }
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }

    @Test
    void nothingSetsAVelocityOrKnocksBack() throws IOException {
        var hits = new ArrayList<String>();
        for (var file : javaSources()) {
            var text = Files.readString(file);
            if (text.contains("setVelocity") || text.contains(".knockback(")) {
                hits.add(file.getFileName().toString());
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }

    @Test
    void theTickSignalReaderDoesNotAskForALocationOrAVelocity() throws IOException {
        var text = Files.readString(Path.of("src/main/java/akyrion/thepredict/adapter/server/context/TickSignalReader.java"));
        assertFalse(text.contains("getLocation"));
        assertFalse(text.contains("getVelocity"));
        var wind = new ArrayList<String>();
        for (var file : javaSources()) {
            var name = file.getFileName().toString();
            if (name.equals("ImpactTiming.java") || name.equals("SkillChoice.java")) {
                continue;
            }
            if (codeOnly(Files.readString(file)).contains("effectiveWindUpTicks")) {
                wind.add(name);
            }
        }
        assertTrue(wind.isEmpty(), wind.toString());
        assertTrue(Files.readString(Path.of("src/main/java/akyrion/thepredict/domain/SkillChoice.java"))
                .contains("ImpactTiming.effectiveWindUpTicks"));
    }

    @Test
    void chunksAreReadOnlyThroughTheSnapshotter() throws IOException {
        var chunkAt = new ArrayList<String>();
        var async = new ArrayList<String>();
        var blocks = new ArrayList<String>();
        var banned = List.of("getBlockAt", "getBlockData", "rayTraceBlocks", "rayTrace", "lineOfSightExists",
                "getChunkAtAsync");
        var worldType = java.util.regex.Pattern.compile("getType\\s*\\([^)]*,");
        for (var file : javaSources()) {
            var name = file.getFileName().toString();
            var text = codeOnly(Files.readString(file));
            if (text.contains("getChunkAt")) {
                chunkAt.add(name);
            }
            if (text.contains("getChunkAtAsync")) {
                async.add(name);
            }
            for (var call : banned) {
                if (text.contains(call) && !name.equals("ZoneColumnReader.java") && !name.equals("ZoneSnapshotter.java")) {
                    blocks.add(name + " " + call);
                }
            }
            if (worldType.matcher(text).find() && !name.equals("ZoneColumnReader.java")
                    && !name.equals("ZoneSnapshotter.java")) {
                blocks.add(name + " getType");
            }
        }
        assertEquals(List.of("ZoneSnapshotter.java"), chunkAt);
        assertTrue(async.isEmpty(), async.toString());
        assertTrue(blocks.isEmpty(), blocks.toString());
        var snapshotter = Files.readString(Path.of(
                "src/main/java/akyrion/thepredict/adapter/server/zone/ZoneSnapshotter.java"));
        var loaded = snapshotter.indexOf("isChunkLoaded");
        var at = snapshotter.indexOf("getChunkAt");
        var shot = snapshotter.indexOf("getChunkSnapshot(true, false, false, false)");
        assertTrue(loaded >= 0 && loaded < at && at < shot, snapshotter);
    }

    @Test
    void thereIsOneZoneThread() throws IOException {
        var hits = new ArrayList<String>();
        for (var file : javaSources()) {
            if (codeOnly(Files.readString(file)).contains("ThePredict-Zone")) {
                hits.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of("ZoneWorker.java"), hits);
    }

    /** Drops block and line comments so a javadoc mention is not a call. */
    private static String codeOnly(String text) {
        var withoutBlocks = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return withoutBlocks.replaceAll("//[^\\n]*", " ");
    }

    /**
     * Drops text blocks, string literals and character literals (SA-19), for the rules about calls: a warning that
     * names a MythicMobs mechanic to the author is not a call. Rules that look for a literal keep {@link #codeOnly}.
     */
    private static String withoutText(String code) {
        var withoutBlocks = code.replaceAll("(?s)\"\"\".*?\"\"\"", " ");
        return withoutBlocks.replaceAll("\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)+'", " ");
    }

    @Test
    void enrichedThreatReachesTheNativeTableOnlyThroughOneWriterAndOneAdapter() throws IOException {
        var callers = new ArrayList<String>();
        var nativeWriters = new ArrayList<String>();
        for (var file : javaSources()) {
            var text = Files.readString(file);
            if (THREAT_PORT_CALL.matcher(text).find()) {
                callers.add(file.getFileName().toString());
            }
            if (NATIVE_THREAT_WRITE.matcher(text).find()) {
                nativeWriters.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of("ThreatChanges.java"), callers, "only the engine writer checks the generation (I1)");
        assertEquals(List.of("MythicCastSource.java"), nativeWriters, "only the adapter writes the native table");
    }

    @Test
    void noSourceFixesReducesOrTauntsThreatOrEditsTheTableMapInThisRelease() throws IOException {
        var found = new ArrayList<String>();
        for (var file : javaSources()) {
            var matcher = FORBIDDEN_THREAT_WRITE.matcher(Files.readString(file));
            while (matcher.find()) {
                found.add(file.getFileName() + ": " + matcher.group().replaceAll("\\s+", " "));
            }
        }
        assertTrue(found.isEmpty(), "no threatSet, Taunt, reduceThreat or map edits in 0.0.1 (M11.4): "
                + String.join(", ", found));
    }

    @Test
    void groupCodeDoesNotRetargetOrImportAThreatTable() throws IOException {
        var forbidden = java.util.regex.Pattern.compile(
                "\\b(?:setTarget|threatSet|Taunt|targetHighestThreat|importThreatTable|sudoskill)\\b");
        var found = new ArrayList<String>();
        for (var file : javaSources()) {
            var matcher = forbidden.matcher(withoutText(codeOnly(Files.readString(file))));
            if (matcher.find()) {
                found.add(file.getFileName() + ": " + matcher.group());
            }
        }
        assertTrue(found.isEmpty(), found.toString());
        assertTrue(forbidden.matcher(withoutText("helper.sudoskill(mob);")).find());
        assertFalse(forbidden.matcher(withoutText("var forced = \"sudoskill\";")).find());
    }

    @Test
    void theTickLayerReadsOnlyItsOwnGroupCopy() throws IOException {
        var text = codeOnly(Files.readString(Path.of(
                "src/main/java/akyrion/thepredict/engine/TickLayer.java")));
        assertFalse(text.contains("GroupState"), text);
        assertFalse(text.contains("groupBooks"), text);
        assertTrue(text.contains("groupCopy"));
    }

    @Test
    void theThreatRulesSeeCallsAndReferencesWhateverTheirSpacing() {
        for (var forbidden : List.of("table.threatSet (entity, 5)", "table.Taunt(entity)", "helper.taunt\n(mob, p)",
                "helper.reduceThreat ( mob, p, 1)", "Stream.of(t).forEach(ThreatTable :: threatSet)",
                "ActiveMob::taunt", "BukkitAPIHelper:: reduceThreat", "mob.getThreatTable ( )\n    .put(id, 9.0)",
                "t.getThreatTable().merge(id, 1.0, Double::sum)", "t.getThreatTable().computeIfPresent(id, f)",
                "t.getThreatTable() . clear()", "t.getThreatTable().remove(id)", "t.getThreatTable().replaceAll(f)")) {
            assertTrue(FORBIDDEN_THREAT_WRITE.matcher(forbidden).find(), forbidden);
        }
        for (var allowed : List.of("t.getThreatTable().get(id)", "{@code threatSet} is not used",
                "t.getThreatTable().getOrDefault(id, 0.0)", "tauntLine(", "t.getThreatTable().size()")) {
            assertFalse(FORBIDDEN_THREAT_WRITE.matcher(allowed).find(), allowed);
        }
        assertTrue(THREAT_PORT_CALL.matcher("actions . addThreat (key, p, 1)").find());
        assertTrue(THREAT_PORT_CALL.matcher("forEach(actions:: addThreat)").find());
        assertTrue(NATIVE_THREAT_WRITE.matcher("table.threatLoss\n(entity, 2)").find());
        assertFalse(NATIVE_THREAT_WRITE.matcher("({@code threatGain}) or removes").find());
    }

    @Test
    void theCompositionRootMayWireEveryPackageButNeverTheJevLibrariesAndNoPackageReachesIt(@TempDir Path root)
            throws IOException {
        var directory = root.resolve(BASE_PACKAGE.replace('.', '/'));
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("Wiring.java"), String.join("\n", "package " + BASE_PACKAGE + ";", "",
                "import akyrion.thepredict.adapter.jev.SystemOneClient;",
                "import akyrion.thepredict.adapter.server.goals.GoalOwnership;",
                "import akyrion.thepredict.config.JevSettings;",
                "import akyrion.thepredict.admin.ThePredictCommand;",
                "import akyrion.thepredict.engine.Coordinator;",
                "import io.lumine.mythic.bukkit.MythicBukkit;",
                "import org.bukkit.plugin.java.JavaPlugin;",
                "import com.google.gson.Gson;",
                "import java.net.http.HttpClient;",
                "class Wiring {}"));
        write(root, "engine", "Reaching", "import akyrion.thepredict.ThePredictPlugin;");
        write(root, "admin", "Reaching", "import akyrion.thepredict.SessionServices;");
        write(root, "adapter.server.goals", "Reaching", "import akyrion.thepredict.CatalogReloader;");
        var violations = violations(root);
        assertEquals(5, violations.size(), String.join(System.lineSeparator(), violations));
        assertEquals(2, violations.stream().filter(violation -> violation.startsWith("Wiring.java:")
                && violation.contains(" D8 ")).count());
        assertTrue(violations.stream().anyMatch(violation -> violation.contains(" D2 akyrion.thepredict.ThePredictPlugin")));
        assertTrue(violations.stream().anyMatch(violation -> violation.contains(" D6 akyrion.thepredict.SessionServices")));
        assertTrue(violations.stream().anyMatch(violation -> violation.contains(" D5 akyrion.thepredict.CatalogReloader")));
    }

    @Test
    void onlyThePluginEntryPointIsPublicInTheCompositionRoot() throws IOException {
        var publicTypes = new ArrayList<String>();
        try (Stream<Path> files = Files.list(Path.of("src", "main", "java", "akyrion", "thepredict"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                var matcher = PUBLIC_TYPE.matcher(Files.readString(file));
                while (matcher.find()) {
                    publicTypes.add(matcher.group(1));
                }
            }
        }
        assertEquals(List.of("ThePredictPlugin"), publicTypes);
    }

    @Test
    void noProductionSourceNamesWhatM12Retired() throws IOException {
        var found = new ArrayList<String>();
        for (var file : javaSources()) {
            var text = Files.readString(file);
            RETIRED_BY_M12.stream().filter(text::contains).forEach(name -> found.add(file.getFileName() + ": " + name));
        }
        assertTrue(found.isEmpty(), String.join(System.lineSeparator(), found));
    }

    @Test
    void onlyTheSchemaOneMigratorStillNamesTheRetiredProvider() throws IOException {
        var allowed = List.of(Path.of("src", "main", "java", "akyrion", "thepredict", "config",
                "ConfigurationMigration.java"), Path.of("src", "main", "resources", "migration", "report-template.md"));
        var found = new ArrayList<String>();
        try (Stream<Path> files = Stream.concat(Files.walk(Path.of("src", "main")), Stream.of(Path.of("README.md")))) {
            for (var file : files.filter(ArchitectureRulesTest::isTextSource).sorted().toList()) {
                if (!allowed.contains(file) && RETIRED_PROVIDER.matcher(Files.readString(file)).find()) {
                    found.add(file.toString());
                }
            }
        }
        assertTrue(found.isEmpty(), String.join(System.lineSeparator(), found));
        assertTrue(RETIRED_PROVIDER.matcher("provider.type: OPEN" + "ROUTER").find());
    }

    @Test
    void storageAdapterImportsOnlyDomainEngineJavaAndSqlite() throws IOException {
        var forbidden = List.of("org.bukkit", "io.papermc", "io.lumine", "akyrion.thepredict.adapter.server",
                "akyrion.thepredict.adapter.jev", "akyrion.thepredict.adapter.config", "akyrion.thepredict.config",
                "akyrion.thepredict.admin");
        var found = new ArrayList<String>();
        var root = Path.of("src", "main", "java", "akyrion", "thepredict", "adapter", "storage");
        try (Stream<Path> files = Files.list(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (var line : Files.readAllLines(file)) {
                    var trimmed = line.trim();
                    if (!trimmed.startsWith("import ")) {
                        continue;
                    }
                    for (var prefix : forbidden) {
                        if (trimmed.contains(prefix)) {
                            found.add(file.getFileName() + " " + trimmed);
                        }
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), String.join(System.lineSeparator(), found));
    }

    @Test
    void productionSourcesDoNotUseReflection() throws IOException {
        var found = new ArrayList<String>();
        for (var file : javaSources()) {
            found.addAll(reflectionHits(file));
        }
        assertTrue(found.isEmpty(), String.join(System.lineSeparator(), found));
    }

    @Test
    void aCommentOrLiteralThatNamesReflectionIsNotAUse(@TempDir Path root) throws IOException {
        var file = root.resolve("Sample.java");
        Files.writeString(file, "class Sample {\n    String note = \"setAccessible\";\n"
                + "    /** java.lang.reflect */\n}\n");
        assertTrue(reflectionHits(file).isEmpty(), reflectionHits(file).toString());
        Files.writeString(file, "class Sample {\n    void leak() { Object.class.getDeclaredField(\"a\")"
                + ".setAccessible(true); }\n}\n");
        assertFalse(reflectionHits(file).isEmpty());
    }

    @Test
    void productionScenarioFactsAndDuelMeasureUseTheFullDecisionTimingConstructor() throws IOException {
        var files = new ArrayList<>(javaSources());
        files.add(Path.of("src", "test", "java", "akyrion", "thepredict", "adapter", "jev", "ScenarioFacts.java"));
        files.add(Path.of("src", "test", "java", "akyrion", "thepredict", "engine", "duel", "DuelMeasure.java"));
        var found = new ArrayList<String>();
        for (var file : files) {
            if (Files.isRegularFile(file)) {
                found.addAll(shortDecisionTiming(file));
            }
        }
        assertTrue(found.isEmpty(), String.join(System.lineSeparator(), found));
    }

    @Test
    void aShortDecisionTimingConstructorIsRejected(@TempDir Path root) throws IOException {
        var file = root.resolve("Old.java");
        Files.writeString(file, "class Old {\n    DecisionTiming timing() {\n"
                + "        return new DecisionTiming(20, 20, 100, 0.5, 0.2, 1);\n    }\n}\n");
        assertFalse(shortDecisionTiming(file).isEmpty());
        Files.writeString(file, "class Old {\n    DecisionTiming timing() {\n"
                + "        return new DecisionTiming(20, 20, 100, 0.5, 0.2, 1, QuestionDetail.FULL, false,\n"
                + "                CastMode.ANY_READY, 0, UntypedCastMode.TACTIC_ONLY, TickLayerMode.CASTS,\n"
                + "                ReactionScope.ALL, java.util.Set.of(), 0, DecisionCadence.STANDARD, 6, 100);\n"
                + "    }\n}\n");
        assertTrue(shortDecisionTiming(file).isEmpty(), shortDecisionTiming(file).toString());
    }

    @Test
    void memoryStaysOutOfVocabularyAndDoesNotWriteHiddenState() throws IOException {
        var hits = new ArrayList<String>();
        for (var file : javaSources()) {
            var text = Files.readString(file);
            var name = file.toString().replace('\\', '/');
            if (name.contains("/vocabulary/") && (text.contains("MemoryCache") || text.contains("PlayerMemory"))) {
                hits.add(name + " vocabulary");
            }
            if (name.contains("Memory") && (text.contains("PersistentDataContainer") || text.contains(".setMaxHealth(")
                    || text.contains("setCustomName") || text.contains("memory_"))) {
                hits.add(name + " hidden");
            }
            if (text.contains("mysql-connector") || text.contains("MysqlPlayerMemoryStore")
                    || text.contains("JdbcDialect")) {
                hits.add(name + " mysql");
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }

    @Test
    void onlyMemoryEvalReadsCampaignLabels() throws IOException {
        var hits = new ArrayList<String>();
        try (var walk = Files.walk(Path.of("src", "test", "java"))) {
            for (var file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
                var name = file.getFileName().toString();
                if (name.equals("MemoryEval.java") || name.equals("CampaignScript.java")
                        || name.equals("ArchitectureRulesTest.java")) {
                    continue;
                }
                var text = Files.readString(file);
                if (text.contains("CampaignScript") && text.contains(".label()")) {
                    hits.add(name);
                }
            }
        }
        assertTrue(hits.isEmpty(), hits.toString());
    }

    private static List<String> reflectionHits(Path file) throws IOException {
        var hits = new ArrayList<String>();
        var inBlock = new boolean[1];
        var number = 0;
        for (var line : Files.readAllLines(file)) {
            number++;
            var code = stripCommentsAndLiterals(line, inBlock);
            if (code.contains("java.lang.reflect") || code.contains("setAccessible")) {
                hits.add(file.getFileName() + ":" + number);
            }
        }
        return hits;
    }

    private static List<String> shortDecisionTiming(Path file) throws IOException {
        var hits = new ArrayList<String>();
        var inBlock = new boolean[1];
        var code = new StringBuilder();
        for (var line : Files.readAllLines(file)) {
            code.append(stripCommentsAndLiterals(line, inBlock)).append('\n');
        }
        var text = code.toString();
        var needle = "new DecisionTiming(";
        var from = 0;
        while (from < text.length()) {
            var at = text.indexOf(needle, from);
            if (at < 0) {
                return hits;
            }
            var args = argumentCount(text, at + needle.length() - 1);
            if (args != 18) {
                hits.add(file.getFileName() + " arguments " + args);
            }
            from = at + needle.length();
        }
        return hits;
    }

    /** Top-level arguments of the call whose opening parenthesis is {@code openParen}. Nested calls do not count. */
    private static int argumentCount(String text, int openParen) {
        var depth = 0;
        var commas = 0;
        var any = false;
        for (var index = openParen; index < text.length(); index++) {
            var ch = text.charAt(index);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return any ? commas + 1 : 0;
                }
            } else if (depth == 1 && ch == ',') {
                commas++;
            } else if (depth == 1 && !Character.isWhitespace(ch)) {
                any = true;
            }
        }
        return -1;
    }

    private static boolean isTextSource(Path file) {
        var name = file.getFileName().toString();
        return Files.isRegularFile(file) && (name.endsWith(".java") || name.endsWith(".yml") || name.endsWith(".txt")
                || name.endsWith(".md") || name.endsWith(".example"));
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static void write(Path root, String relativePackage, String type, String body) throws IOException {
        var directory = root.resolve((BASE_PACKAGE + "." + relativePackage).replace('.', '/'));
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(type + ".java"),
                "package " + BASE_PACKAGE + "." + relativePackage + ";\n\n" + body + "\n");
    }

    private static List<String> violations(Path sourceRoot) throws IOException {
        var violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                checkFile(sourceRoot, file, violations);
            }
        }
        return violations;
    }

    private static void checkFile(Path sourceRoot, Path file, List<String> violations) throws IOException {
        var packageName = sourceRoot.relativize(file.getParent()).toString().replace(file.getFileSystem().getSeparator(), ".");
        var relative = packageName.equals(BASE_PACKAGE) ? ""
                : packageName.startsWith(BASE_PACKAGE + ".") ? packageName.substring(BASE_PACKAGE.length() + 1) : null;
        var module = relative == null ? Optional.<String>empty() : moduleOf(relative);
        var lines = Files.readAllLines(file);
        var inBlockComment = new boolean[1];
        var code = new ArrayList<String>(lines.size());
        for (var line : lines) {
            code.add(stripCommentsAndLiterals(line, inBlockComment).trim());
        }
        var declared = declaredPackage(code);
        if (!declared.equals(packageName)) {
            violations.add(file + ": D9 package declaration '" + declared + "' does not match path " + packageName);
        }
        if (module.isEmpty() || !RULES.containsKey(module.orElseThrow())) {
            violations.add(file + ": no dependency rule for package " + packageName);
            return;
        }
        for (var index = 0; index < code.size(); index++) {
            for (var reference : references(code.get(index))) {
                var rule = check(module.orElseThrow(), reference);
                if (rule.isPresent()) {
                    violations.add(file.getFileName() + ":" + (index + 1) + " " + rule.orElseThrow() + " "
                            + reference + " (" + file + ")");
                }
            }
        }
    }

    private static String declaredPackage(List<String> code) {
        for (var line : code) {
            var matcher = PACKAGE.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private static List<String> references(String code) {
        var references = new ArrayList<String>();
        if (code.startsWith("package ")) {
            return references;
        }
        var importMatcher = IMPORT.matcher(code);
        if (importMatcher.find()) {
            references.add(importMatcher.group(2));
            return references;
        }
        var qualified = QUALIFIED.matcher(code);
        while (qualified.find()) {
            references.add(qualified.group(1));
        }
        return references;
    }

    private static Optional<String> moduleOf(String relativePackage) {
        String module = null;
        for (var candidate : RULES.keySet()) {
            var matches = candidate.isEmpty() ? relativePackage.isEmpty()
                    : relativePackage.equals(candidate) || relativePackage.startsWith(candidate + ".");
            if (matches && (module == null || candidate.length() > module.length())) {
                module = candidate;
            }
        }
        return Optional.ofNullable(module);
    }

    private static Optional<String> check(String module, String reference) {
        var rule = RULES.get(module);
        if (reference.startsWith(BASE_PACKAGE + ".")) {
            var target = reference.substring(BASE_PACKAGE.length() + 1);
            if (rule.internal().stream().anyMatch(allowed -> target.startsWith(allowed + "."))) {
                return Optional.empty();
            }
            return Optional.of(isAnotherAdapter(module, target) ? "D7" : rule.id());
        }
        if (reference.startsWith("java.net.http.") && !rule.external().contains("java.net.http.")) {
            return Optional.of(rule.id());
        }
        return rule.external().stream().anyMatch(reference::startsWith) ? Optional.empty() : Optional.of(rule.id());
    }

    private static boolean isAnotherAdapter(String module, String target) {
        return module.startsWith("adapter.") && target.startsWith("adapter.")
                && !adapterOf(module).equals(adapterOf(target));
    }

    private static String adapterOf(String relativePackage) {
        var parts = relativePackage.split("\\.");
        return parts.length < 2 ? relativePackage : parts[0] + "." + parts[1];
    }

    private static String stripCommentsAndLiterals(String line, boolean[] inBlockComment) {
        var rest = STRING_LITERAL.matcher(CHAR_LITERAL.matcher(line).replaceAll("' '")).replaceAll("\"\"");
        var code = new StringBuilder();
        while (!rest.isEmpty()) {
            if (inBlockComment[0]) {
                var end = rest.indexOf("*/");
                if (end < 0) {
                    return code.toString();
                }
                inBlockComment[0] = false;
                rest = rest.substring(end + 2);
                continue;
            }
            var lineComment = rest.indexOf(LINE_COMMENT);
            var blockComment = rest.indexOf("/*");
            if (blockComment >= 0 && (lineComment < 0 || blockComment < lineComment)) {
                code.append(rest, 0, blockComment);
                inBlockComment[0] = true;
                rest = rest.substring(blockComment + 2);
            } else {
                code.append(lineComment >= 0 ? rest.substring(0, lineComment) : rest);
                return code.toString();
            }
        }
        return code.toString();
    }

    private record Rule(String id, List<String> external, List<String> internal) {
    }
}
